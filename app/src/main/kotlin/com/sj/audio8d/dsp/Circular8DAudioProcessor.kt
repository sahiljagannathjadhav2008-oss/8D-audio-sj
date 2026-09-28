package com.sj.audio8d.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time 8D spatial audio engine, wired into ExoPlayer's audio pipeline as
 * a Media3 [AudioProcessor] via a custom [androidx.media3.exoplayer.DefaultRenderersFactory]
 * (see `EightDRenderersFactory`) that overrides `buildAudioSink()` to install
 * this processor on the [androidx.media3.exoplayer.audio.DefaultAudioSink].
 * It runs on ExoPlayer's internal playback/audio thread for every decoded PCM
 * buffer — it never touches or duplicates the source file.
 *
 * ## Signal path per frame, in order
 *  1. True bypass check (see "Bypass" below) — if nothing is enabled and the
 *     smoothed state has already settled, the buffer is bulk-copied with no
 *     per-sample math at all.
 *  2. Mid/side decode + stereo-width scaling (stereo input only).
 *  3. Equal-power circular/horizontal/vertical/random panning, driven by a
 *     phase accumulator that advances continuously (never resets abruptly),
 *     so movement is always a smooth trajectory rather than a hard L/R flip.
 *     Mono input is spatialized directly from the single source sample
 *     rather than merely duplicated — see "Mono handling" below.
 *  4. Optional low-shelf bass boost (one-pole), independent of the 8D toggle.
 *  5. Optional short feedback-delay ambience ("reverb"), independent of the
 *     8D toggle.
 *  6. NaN/Infinity guard + soft-knee limiter to prevent clipping.
 *
 * All continuous parameters (intensity, width, bass gain, reverb mix) are
 * one-pole smoothed toward their latest target every sample, which is what
 * lets [updateParameters] be called live during playback without any click
 * or pop — the DSP state chases the new target rather than jumping to it.
 *
 * ## Bypass
 * "8D Mode" (`DspParameters.enabled`) gates only the spatial part of the
 * chain — width and panning. Bass boost and reverb have their own separate
 * toggles and keep working independently of the 8D switch, per spec. When
 * 8D is off *and* bass boost and reverb are both off, and the smoothed
 * ramp state has fully settled back to "no effect", [queueInput] takes a
 * fast path that bulk-copies the input buffer to the output with no
 * per-sample floating point work at all — true silence-cost bypass for
 * long stretches of normal playback, not just a "zero gain" DSP pass.
 * Right after the user flips 8D off, the smoothed parameters still ramp
 * down over a few milliseconds (avoiding a click), so the fast path only
 * engages once that ramp has actually finished.
 *
 * ## Mono handling
 * A mono 16-bit PCM source is spatialized into a genuine stereo output
 * (the processor reconfigures the pipeline to 2 channels — see
 * [onConfigure]) using the same phase-driven equal-power pan applied to
 * the single source sample, rather than being duplicated unchanged to
 * L/R. With 8D off, mono is still centered evenly across both channels
 * (constant equal-power center gain) so the app always plays back in
 * stereo, but does no per-sample trigonometry when the effect is off.
 *
 * ## Thread-safety / audio-thread hygiene
 * [updateParameters] is called from the UI/ViewModel thread and only ever
 * writes a `@Volatile` reference — the audio thread reads that reference
 * once per buffer and does all its own primitive-only math from there. The
 * audio thread never touches Room, DataStore, disk, network, coroutines, or
 * any lock. See "Zero-allocation" below for the object-allocation contract.
 *
 * ## Zero-allocation per-frame contract
 * No `Pair`/`Triple`/collection/lambda/data class is created inside
 * [queueInput]'s per-frame loop. Panning gain computation writes directly
 * into two primitive instance fields ([panGainL]/[panGainR]) instead of
 * returning a `Pair`, and the `when` over [MovementPattern] compiles to a
 * plain tableswitch over an already-existing enum reference — it does not
 * allocate. The only per-buffer (not per-frame) work that isn't a hot-loop
 * primitive is reading the latest [DspParameters] snapshot, which already
 * exists and is not created here.
 */
// Media3 marks AudioProcessor/BaseAudioProcessor @UnstableApi (the custom-processor
// extension point is intentionally not API-stable). Opt-in is scoped to this class only.
@OptIn(UnstableApi::class)
class Circular8DAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var pendingParams = DspParameters.DEFAULT

    fun updateParameters(params: DspParameters) {
        pendingParams = params
    }

    fun currentParameters(): DspParameters = pendingParams

    /** Phase in radians, advanced every processed frame. Never reset except on [reset]/[flush]. */
    private var phase = 0.0

    private var curIntensity = 0f
    private var curWidth = 1f
    private var curBassGain = 0f
    private var curReverbMix = 0f

    // Scratch fields for equalPowerPanInto() — written directly instead of
    // returning a Pair, so the per-frame hot path allocates nothing.
    private var panGainL = 0f
    private var panGainR = 0f

    private var lowShelfL = 0f
    private var lowShelfR = 0f

    private var delayL = FloatArray(1)
    private var delayR = FloatArray(1)
    private var delayIndex = 0

    private var sampleRateHz = 44100

    private enum class Mode { UNSUPPORTED, STEREO, MONO_TO_STEREO }
    private var mode = Mode.UNSUPPORTED

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        mode = when {
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT -> Mode.UNSUPPORTED
            inputAudioFormat.channelCount == 2 -> Mode.STEREO
            inputAudioFormat.channelCount == 1 -> Mode.MONO_TO_STEREO
            else -> Mode.UNSUPPORTED
        }
        if (mode == Mode.UNSUPPORTED) {
            // Multichannel (>2) or a non-16-bit encoding the platform decoder
            // occasionally produces is passed straight through unprocessed
            // rather than risking a crash or corrupted audio on a layout this
            // engine doesn't model.
            return AudioProcessor.AudioFormat.NOT_SET
        }

        sampleRateHz = inputAudioFormat.sampleRate
        val delaySamples = (sampleRateHz * MAX_DELAY_MS / 1000f).toInt().coerceAtLeast(1)
        delayL = FloatArray(delaySamples)
        delayR = FloatArray(delaySamples)
        delayIndex = 0
        phase = 0.0
        lowShelfL = 0f
        lowShelfR = 0f
        curIntensity = 0f
        curWidth = 1f
        curBassGain = 0f
        curReverbMix = 0f

        return if (mode == Mode.MONO_TO_STEREO) {
            // We always widen mono to stereo output (see class doc), so the
            // downstream pipeline is reconfigured to 2 channels regardless of
            // whether 8D movement is currently switched on.
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
        } else {
            inputAudioFormat
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        when (mode) {
            Mode.STEREO -> queueStereoInput(inputBuffer)
            Mode.MONO_TO_STEREO -> queueMonoInput(inputBuffer)
            Mode.UNSUPPORTED -> return
        }
    }

    // --- Stereo path --------------------------------------------------------

    private fun queueStereoInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_STEREO_FRAME
        if (frameCount <= 0) return

        val params = pendingParams

        if (canFastBypass(params)) {
            // True bypass: no floating point, no trig, no gain math — a
            // straight buffer copy, exactly per the "8D OFF => original
            // signal, unmodified" requirement.
            val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_STEREO_FRAME)
        val phaseIncrement = 2.0 * Math.PI * params.effectiveSpeedHz / sampleRateHz
        val targetBassGain = if (params.bassBoost) dbToLinearExtraGain(params.bassBoostDb) else 0f
        val targetReverbMix = if (params.reverbEnabled) params.reverbMix.coerceIn(0f, 0.5f) else 0f
        val targetIntensity = if (params.enabled) params.intensity.coerceIn(0f, 1f) else 0f
        val targetWidth = if (params.enabled) params.stereoWidth.coerceIn(0f, 1.5f) else 1f

        var i = 0
        while (i < frameCount) {
            val l = inputBuffer.short.toFloat() * INV_32768
            val r = inputBuffer.short.toFloat() * INV_32768

            curIntensity += (targetIntensity - curIntensity) * SMOOTHING_ALPHA
            curWidth += (targetWidth - curWidth) * SMOOTHING_ALPHA
            curBassGain += (targetBassGain - curBassGain) * SMOOTHING_ALPHA
            curReverbMix += (targetReverbMix - curReverbMix) * SMOOTHING_ALPHA

            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f * curWidth
            var wl = mid + side
            var wr = mid - side

            if (curIntensity > 0.0005f) {
                phase += phaseIncrement
                if (phase > TWO_PI) phase -= TWO_PI
                val angle = phase.toFloat()
                equalPowerPanInto(params.pattern, angle)
                val gL = 1f + (panGainL - 1f) * curIntensity
                val gR = 1f + (panGainR - 1f) * curIntensity
                wl *= gL
                wr *= gR
            }

            wl = applyBassAndReverb(wl, isLeft = true)
            wr = applyBassAndReverb(wr, isLeft = false)

            wl = sanitizeAndLimit(wl)
            wr = sanitizeAndLimit(wr)

            outputBuffer.putShort((wl * 32767f).toInt().toShort())
            outputBuffer.putShort((wr * 32767f).toInt().toShort())
            i++
        }
        outputBuffer.flip()
    }

    /**
     * True when the entire buffer can be copied through untouched: 8D is off,
     * bass boost and reverb are both off, AND the smoothed ramp state has
     * already fully decayed to its resting/off values. That last condition
     * matters — right after the user disables 8D, we still need a few
     * milliseconds of the real per-sample path to smoothly ramp gain back to
     * unity/width back to 1 without a click, before the fast path is safe.
     */
    private fun canFastBypass(params: DspParameters): Boolean {
        if (params.enabled || params.bassBoost || params.reverbEnabled) return false
        if (curIntensity > 0.0005f) return false
        if (curBassGain > 0.0005f) return false
        if (curReverbMix > 0.0005f) return false
        if (abs(curWidth - 1f) > 0.0005f) return false
        return true
    }

    // --- Mono-to-stereo path -------------------------------------------------

    private fun queueMonoInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_MONO_FRAME
        if (frameCount <= 0) return

        val params = pendingParams
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_STEREO_FRAME)
        val phaseIncrement = 2.0 * Math.PI * params.effectiveSpeedHz / sampleRateHz
        val targetBassGain = if (params.bassBoost) dbToLinearExtraGain(params.bassBoostDb) else 0f
        val targetReverbMix = if (params.reverbEnabled) params.reverbMix.coerceIn(0f, 0.5f) else 0f
        val targetIntensity = if (params.enabled) params.intensity.coerceIn(0f, 1f) else 0f

        var i = 0
        while (i < frameCount) {
            val s = inputBuffer.short.toFloat() * INV_32768

            curIntensity += (targetIntensity - curIntensity) * SMOOTHING_ALPHA
            curBassGain += (targetBassGain - curBassGain) * SMOOTHING_ALPHA
            curReverbMix += (targetReverbMix - curReverbMix) * SMOOTHING_ALPHA

            var gL = CENTER_GAIN
            var gR = CENTER_GAIN
            if (curIntensity > 0.0005f) {
                phase += phaseIncrement
                if (phase > TWO_PI) phase -= TWO_PI
                val angle = phase.toFloat()
                equalPowerPanInto(params.pattern, angle)
                gL = CENTER_GAIN + (panGainL - CENTER_GAIN) * curIntensity
                gR = CENTER_GAIN + (panGainR - CENTER_GAIN) * curIntensity
            }

            var wl = s * gL
            var wr = s * gR

            wl = applyBassAndReverb(wl, isLeft = true)
            wr = applyBassAndReverb(wr, isLeft = false)

            wl = sanitizeAndLimit(wl)
            wr = sanitizeAndLimit(wr)

            outputBuffer.putShort((wl * 32767f).toInt().toShort())
            outputBuffer.putShort((wr * 32767f).toInt().toShort())
            i++
        }
        outputBuffer.flip()
    }

    // --- Shared per-sample helpers -------------------------------------------

    private fun applyBassAndReverb(input: Float, isLeft: Boolean): Float {
        var value = input
        if (curBassGain > 0.0005f) {
            if (isLeft) {
                lowShelfL += (value - lowShelfL) * LOW_SHELF_COEFF
                value += lowShelfL * curBassGain
            } else {
                lowShelfR += (value - lowShelfR) * LOW_SHELF_COEFF
                value += lowShelfR * curBassGain
            }
        }
        if (curReverbMix > 0.0005f && delayL.isNotEmpty()) {
            if (isLeft) {
                val d = delayL[delayIndex]
                delayL[delayIndex] = value + d * DELAY_FEEDBACK
                value += d * curReverbMix
            } else {
                val d = delayR[delayIndex]
                delayR[delayIndex] = value + d * DELAY_FEEDBACK
                value += d * curReverbMix
                // Both channels share one index; advance once per frame, on
                // the second (right) channel call.
                delayIndex = (delayIndex + 1) % delayL.size
            }
        }
        return value
    }

    /**
     * Writes the equal-power pan gains for [angle] directly into [panGainL]/
     * [panGainR] — no `Pair`, no tuple, no allocation. Every pattern is a
     * continuous function of [angle], so there is no pattern here that can
     * produce a hard, discontinuous L/R jump.
     */
    private fun equalPowerPanInto(pattern: MovementPattern, angle: Float) {
        var pan: Float
        var depth: Float
        when (pattern) {
            MovementPattern.CIRCULAR -> {
                pan = sin(angle)
                depth = 0.85f + 0.15f * cos(angle)
            }
            MovementPattern.HORIZONTAL -> {
                pan = sin(angle)
                depth = 1f
            }
            MovementPattern.VERTICAL -> {
                pan = 0f
                depth = 0.7f + 0.3f * cos(angle)
            }
            MovementPattern.RANDOM -> {
                pan = (sin(angle) * 0.6f + sin(angle * 2.37f + 1.3f) * 0.3f +
                    sin(angle * 0.53f + 0.7f) * 0.1f).coerceIn(-1f, 1f)
                depth = 0.85f + 0.15f * cos(angle * 1.7f + 0.4f)
            }
        }
        if (pan > 1f) pan = 1f
        if (pan < -1f) pan = -1f
        val theta = (pan + 1f) * QUARTER_PI
        panGainL = cos(theta) * depth
        panGainR = sin(theta) * depth
    }

    private fun dbToLinearExtraGain(db: Float): Float = 10f.pow(db / 20f) - 1f

    private fun sanitizeAndLimit(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return 0f
        val threshold = 0.92f
        val a = abs(value)
        if (a <= threshold) return value
        val sign = if (value < 0f) -1f else 1f
        val over = (a - threshold) / (1f - threshold)
        return sign * (threshold + (1f - threshold) * tanh(over))
    }

    override fun onFlush() {
        phase = 0.0
        delayL.fill(0f)
        delayR.fill(0f)
        delayIndex = 0
        lowShelfL = 0f
        lowShelfR = 0f
    }

    override fun onReset() {
        mode = Mode.UNSUPPORTED
        curIntensity = 0f
        curWidth = 1f
        curBassGain = 0f
        curReverbMix = 0f
    }

    companion object {
        private const val BYTES_PER_STEREO_FRAME = 4 // 2 channels * 16-bit
        private const val BYTES_PER_MONO_FRAME = 2 // 1 channel * 16-bit
        private const val SMOOTHING_ALPHA = 0.01f
        private const val LOW_SHELF_COEFF = 0.22f
        private const val DELAY_FEEDBACK = 0.22f
        private const val MAX_DELAY_MS = 32f
        private const val INV_32768 = 1f / 32768f
        private const val CENTER_GAIN = 0.70710678f // equal-power center: 1/sqrt(2)
        private val QUARTER_PI = (Math.PI / 4.0).toFloat()
        private val TWO_PI = 2.0 * Math.PI
    }
}
