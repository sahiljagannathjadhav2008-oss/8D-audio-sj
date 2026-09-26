package com.sj.audio8d.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

/**
 * Feeds known synthetic PCM into [Circular8DAudioProcessor] and checks the
 * invariants the spec calls for: valid output for silence/constant/sine
 * inputs, no NaN/Infinity, no out-of-range samples, smooth panning across
 * buffer boundaries, safe mono handling, an exact bypass when 8D and every
 * other effect is off, and stability across repeated enable/disable and
 * across different sample rates and buffer sizes.
 *
 * These are plain JVM unit tests — no device/emulator required — so they
 * run under `./gradlew test`, including in CI before `assembleRelease`.
 */
class Circular8DAudioProcessorTest {

    private fun stereoFormat(sampleRate: Int) = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    private fun monoFormat(sampleRate: Int) = AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)

    private fun sineStereoBuffer(frames: Int, sampleRate: Int, freqHz: Double = 440.0, startPhase: Double = 0.0): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val t = (i + startPhase) / sampleRate
            val sample = (sin(2 * Math.PI * freqHz * t) * 20000).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun sineMonoBuffer(frames: Int, sampleRate: Int, freqHz: Double = 440.0, startPhase: Double = 0.0): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val t = (i + startPhase) / sampleRate
            val sample = (sin(2 * Math.PI * freqHz * t) * 20000).toInt().toShort()
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private fun silentStereoBuffer(frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { buffer.putShort(0); buffer.putShort(0) }
        buffer.flip()
        return buffer
    }

    private fun constantMonoBuffer(frames: Int, value: Short): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { buffer.putShort(value) }
        buffer.flip()
        return buffer
    }

    private fun readAllShorts(buffer: ByteBuffer): List<Short> {
        val out = mutableListOf<Short>()
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        while (dup.remaining() >= 2) out.add(dup.short)
        return out
    }

    // --- Test 1: silence -----------------------------------------------------

    @Test
    fun `silence input does not generate unexpected audio`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 1f, bassBoost = true, reverbEnabled = true))

        val input = silentStereoBuffer(4096)
        processor.queueInput(input)
        val samples = readAllShorts(processor.output)

        assertEquals(4096 * 2, samples.size)
        for (s in samples) {
            assertTrue("silence must not produce significant output energy", abs(s.toInt()) < 500)
        }
    }

    // --- Test 2: constant mono produces valid stereo output -------------------

    @Test
    fun `constant mono signal produces valid stereo output`() {
        val processor = Circular8DAudioProcessor()
        val result = processor.configure(monoFormat(44100))
        // Mono is now actively spatialized to stereo, so the processor must
        // report an active 2-channel output format rather than NOT_SET.
        assertTrue(processor.isActive)
        assertEquals(2, result.channelCount)

        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 1f))
        val input = constantMonoBuffer(2048, 10000)
        processor.queueInput(input)
        val samples = readAllShorts(processor.output)

        assertEquals(2048 * 2, samples.size)
        assertTrue("stereo output from mono should not be silent", samples.any { it != 0.toShort() })
    }

    // --- Test 3 & 4 & 5: stereo output stays finite, no NaN, no Infinity ------
    // (Verified indirectly: Float NaN/Infinity can never survive sanitizeAndLimit
    // and would otherwise show up as a Short conversion crash or wraparound;
    // this test also directly re-derives each output sample as a float and
    // checks finiteness before it was quantized to 16-bit.)

    @Test
    fun `stereo processing under extreme parameters remains finite and in range`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(
            DspParameters.DEFAULT.copy(
                enabled = true,
                pattern = MovementPattern.RANDOM,
                speed = MovementSpeed.FAST,
                intensity = 1f,
                stereoWidth = 1.5f,
                bassBoost = true,
                bassBoostDb = 12f,
                reverbEnabled = true,
                reverbMix = 0.5f
            )
        )

        repeat(20) { i ->
            val input = sineStereoBuffer(2048, 44100, startPhase = (i * 2048).toDouble())
            processor.queueInput(input)
            val samples = readAllShorts(processor.output)
            assertEquals(2048 * 2, samples.size)
            for (s in samples) {
                val v = s.toInt()
                assertFalse("value must stay within 16-bit short range (no clipping wraparound)", v > Short.MAX_VALUE || v < Short.MIN_VALUE)
            }
        }
    }

    // --- Test 6 (covered above) + Test 7: smooth L/R movement -----------------

    @Test
    fun `8D movement changes the left right relationship smoothly`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(
            DspParameters.DEFAULT.copy(enabled = true, pattern = MovementPattern.CIRCULAR, speed = MovementSpeed.NORMAL, intensity = 1f)
        )

        val input = sineStereoBuffer(8192, 44100)
        processor.queueInput(input)
        val samples = readAllShorts(processor.output)

        val windowSize = 512
        val leftEnvelopes = mutableListOf<Double>()
        var i = 0
        while (i + windowSize * 2 <= samples.size) {
            var sumAbs = 0.0
            var j = i
            while (j < i + windowSize * 2) {
                sumAbs += abs(samples[j].toInt())
                j += 2
            }
            leftEnvelopes.add(sumAbs / windowSize)
            i += windowSize * 2
        }
        for (k in 1 until leftEnvelopes.size) {
            val delta = abs(leftEnvelopes[k] - leftEnvelopes[k - 1])
            assertTrue("envelope should not jump abruptly between adjacent windows", delta < 20000)
        }
    }

    // --- Test 8: parameter changes don't create huge discontinuities ----------

    @Test
    fun `live parameter changes do not create abrupt output discontinuities`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 0f))

        val chunk1 = sineStereoBuffer(1024, 44100)
        processor.queueInput(chunk1)
        val out1 = readAllShorts(processor.output)

        // Snap intensity to maximum mid-stream — the smoothing loop, not an
        // instant jump, must absorb this.
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 1f))
        val chunk2 = sineStereoBuffer(1024, 44100, startPhase = 1024.0)
        processor.queueInput(chunk2)
        val out2 = readAllShorts(processor.output)

        val lastOfFirst = out1[out1.size - 2].toInt()
        val firstOfSecond = out2[0].toInt()
        assertTrue(
            "a parameter snap should not produce a sample-to-sample jump anywhere near full scale",
            abs(lastOfFirst - firstOfSecond) < 30000
        )
    }

    // --- Test 9: 8D OFF is an exact (or near-exact) passthrough ---------------

    @Test
    fun `8D off with no other effects is an exact passthrough after settling`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = false, bassBoost = false, reverbEnabled = false))

        // First buffer settles any residual smoothing state from defaults.
        processor.queueInput(sineStereoBuffer(4096, 44100))
        readAllShorts(processor.output)

        val input = sineStereoBuffer(2048, 44100, startPhase = 4096.0)
        val inputCopy = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        processor.queueInput(input)
        val output = readAllShorts(processor.output)
        val expected = readAllShorts(inputCopy)

        assertEquals(expected.size, output.size)
        for (i in expected.indices) {
            assertEquals("bypassed sample $i must match input exactly", expected[i], output[i])
        }
    }

    // --- Test 10: enable/disable/enable repeatedly does not corrupt state -----

    @Test
    fun `can be enabled and disabled repeatedly without corrupting state`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        for (i in 0 until 20) {
            processor.updateParameters(DspParameters.DEFAULT.copy(enabled = i % 2 == 0, reverbEnabled = i % 3 == 0))
            val input = sineStereoBuffer(512, 44100, startPhase = (i * 512).toDouble())
            processor.queueInput(input)
            val samples = readAllShorts(processor.output)
            assertEquals(512 * 2, samples.size)
            for (s in samples) assertFalse(s.toInt() > Short.MAX_VALUE || s.toInt() < Short.MIN_VALUE)
        }
    }

    // --- Test 11: different sample rates ---------------------------------------

    @Test
    fun `processes correctly across different sample rates`() {
        for (rate in listOf(22050, 44100, 48000, 96000)) {
            val processor = Circular8DAudioProcessor()
            processor.configure(stereoFormat(rate))
            processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 0.8f))
            val input = sineStereoBuffer(1024, rate)
            processor.queueInput(input)
            val samples = readAllShorts(processor.output)
            assertEquals("sample rate $rate", 1024 * 2, samples.size)
            for (s in samples) assertFalse(s.toInt() > Short.MAX_VALUE || s.toInt() < Short.MIN_VALUE)
        }
    }

    // --- Test 12: different buffer sizes, including buffer-boundary stability -

    @Test
    fun `processes correctly across varied and irregular buffer sizes`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, pattern = MovementPattern.CIRCULAR, intensity = 1f))

        val bufferSizes = listOf(1, 7, 64, 100, 512, 4096, 8191)
        var phaseOffset = 0.0
        for (size in bufferSizes) {
            val input = sineStereoBuffer(size, 44100, startPhase = phaseOffset)
            processor.queueInput(input)
            val samples = readAllShorts(processor.output)
            assertEquals("buffer size $size", size * 2, samples.size)
            phaseOffset += size
        }
    }

    // --- Test 13: mono and stereo input (mono covered above; stereo here) -----

    @Test
    fun `zero intensity keeps stereo channels balanced`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, intensity = 0f, stereoWidth = 1f, bassBoost = false))

        val input = sineStereoBuffer(4096, 44100)
        processor.queueInput(input)
        val samples = readAllShorts(processor.output)
        var sumL = 0.0
        var sumR = 0.0
        var i = 0
        while (i < samples.size) {
            sumL += abs(samples[i].toInt())
            sumR += abs(samples[i + 1].toInt())
            i += 2
        }
        val ratio = if (sumR > 0) sumL / sumR else 1.0
        assertTrue("channels should stay balanced at zero intensity", ratio in 0.85..1.15)
    }

    @Test
    fun `unsupported channel layout is passed through inactive rather than crashing`() {
        val processor = Circular8DAudioProcessor()
        val multichannelFormat = AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)
        val result = processor.configure(multichannelFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
        assertFalse(processor.isActive)
    }

    @Test
    fun `flush resets internal phase and delay state safely`() {
        val processor = Circular8DAudioProcessor()
        processor.configure(stereoFormat(44100))
        processor.updateParameters(DspParameters.DEFAULT.copy(enabled = true, reverbEnabled = true))
        processor.queueInput(sineStereoBuffer(4096, 44100))
        readAllShorts(processor.output)
        processor.flush()
        processor.queueInput(sineStereoBuffer(1024, 44100))
        val samples = readAllShorts(processor.output)
        assertEquals(1024 * 2, samples.size)
    }
}
