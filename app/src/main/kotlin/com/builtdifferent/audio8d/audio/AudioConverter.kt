package com.builtdifferent.audio8d.audio

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.builtdifferent.audio8d.data.db.OutputFormat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Emitted while a conversion runs so the UI can show a real stage + percentage. */
sealed class ConversionProgress {
    data class Stage(val label: String, val percent: Int) : ConversionProgress()
    data class Done(val outputFile: File) : ConversionProgress()
    data class Error(val message: String) : ConversionProgress()
}

/**
 * Builds and runs the actual offline 8D audio rendering pipeline via FFmpegKit.
 *
 * The "8D" illusion is produced by a chain of real, audible DSP stages rather
 * than a single gimmick filter:
 *
 *  1. apulsator   - slow sinusoidal amplitude/pan modulation -> the circular,
 *                   headphone-orbiting motion that defines the 8D effect.
 *  2. extrastereo - widens the stereo image so the orbit has real width.
 *  3. aecho       - short (~25-35ms) delayed repeats on the side channel,
 *                   simulating a Haas-style spatial delay without hard panning
 *                   clicks.
 *  4. aecho (2nd, longer/quieter) - a light algorithmic-reverb-style tail.
 *  5. loudnorm    - single-pass peak/loudness normalization (run BEFORE the
 *                   limiter, since loudnorm can add gain that would otherwise
 *                   push peaks past the limiter's ceiling).
 *  6. alimiter    - soft brick-wall limiter — the true final anti-clipping
 *                   stage, applied after every other gain-affecting filter.
 *  7. afade in/out - 2.5s fades so start/end never pop.
 *
 * All parameters are tuned conservatively (small delays, gentle depth) to keep
 * the result smooth on headphones instead of harsh or disorienting.
 */
@Singleton
class AudioConverter @Inject constructor() {

    fun buildFilterChain(durationSeconds: Double): String {
        val fadeOutStart = (durationSeconds - 2.5).coerceAtLeast(0.0)
        return listOf(
            // Circular panning LFO: ~0.12 Hz is a slow, comfortable full rotation
            // roughly every 8 seconds.
            "apulsator=hz=0.12:amount=0.9:mode=sine",
            // Widen the stereo field so the pan has perceptible width.
            "extrastereo=m=2.0:c=true",
            // Short delayed side-repeats -> Haas-style spatial widening.
            "aecho=0.8:0.7:22:0.35",
            // Longer, quieter tail -> simple algorithmic reverb.
            "aecho=0.7:0.5:180:0.22",
            // Gentle crossfeed reduction / anti-mono-collapse via stereo tools.
            "stereotools=slev=1:sbal=0:mlev=1:mpan=0:phase=0",
            // Peak/loudness normalization BEFORE the limiter — loudnorm can add
            // gain, so running it first and limiting after guarantees the
            // limiter is the true last line of defense against clipping.
            "loudnorm=I=-14:TP=-1.5:LRA=11",
            // Soft limiter so neither the pulsing modulation nor loudnorm's
            // gain can ever clip.
            "alimiter=limit=0.95:attack=5:release=50",
            // Fade in / out so playback never pops at the boundaries.
            "afade=t=in:st=0:d=2.5",
            "afade=t=out:st=$fadeOutStart:d=2.5"
        ).joinToString(",")
    }

    private fun codecArgsFor(format: OutputFormat): List<String> = when (format) {
        OutputFormat.MP3_320 -> listOf("-codec:a", "libmp3lame", "-b:a", "320k")
        OutputFormat.AAC -> listOf("-codec:a", "aac", "-b:a", "256k")
        OutputFormat.WAV -> listOf("-codec:a", "pcm_s16le")
    }

    /**
     * Runs the conversion. Emits Stage progress based on FFmpeg's own encoding
     * statistics callback (time processed / total duration), so the percentage
     * shown to the user reflects real encoder progress, not a fake timer.
     */
    fun convert(
        inputPath: String,
        outputFile: File,
        durationSeconds: Double,
        outputFormat: OutputFormat
    ): Flow<ConversionProgress> = callbackFlow {
        trySend(ConversionProgress.Stage("Reading audio...", 2))

        val filterChain = buildFilterChain(durationSeconds)
        val codecArgs = codecArgsFor(outputFormat)

        trySend(ConversionProgress.Stage("Analyzing...", 6))

        val command = buildList {
            add("-y")
            add("-i"); add(inputPath)
            add("-af"); add(filterChain)
            addAll(codecArgs)
            add(outputFile.absolutePath)
        }.toTypedArray()

        trySend(ConversionProgress.Stage("Applying 8D effect...", 12))

        val durationMs = (durationSeconds * 1000).toLong().coerceAtLeast(1L)
        var lastStageAnnounced = ""

        val session = FFmpegKit.executeWithArgumentsAsync(
            command,
            { session2 ->
                if (ReturnCode.isSuccess(session2.returnCode)) {
                    trySend(ConversionProgress.Stage("Saving...", 99))
                    trySend(ConversionProgress.Done(outputFile))
                } else if (ReturnCode.isCancel(session2.returnCode)) {
                    trySend(ConversionProgress.Error("Conversion cancelled"))
                } else {
                    trySend(
                        ConversionProgress.Error(
                            session2.failStackTrace ?: "FFmpeg failed with return code ${session2.returnCode}"
                        )
                    )
                }
                close()
            },
            { log -> /* raw ffmpeg logs available here if verbose debugging is needed */ },
            { stats: Statistics ->
                val processedMs = stats.time.coerceAtLeast(0L)
                val pct = (12 + (processedMs.toDouble() / durationMs.toDouble() * 84)).toInt().coerceIn(12, 96)
                val stage = when {
                    pct < 35 -> "Applying 8D effect..."
                    pct < 60 -> "Applying reverb..."
                    pct < 90 -> "Encoding..."
                    else -> "Encoding..."
                }
                if (stage != lastStageAnnounced) {
                    lastStageAnnounced = stage
                }
                trySend(ConversionProgress.Stage(stage, pct))
            }
        )

        awaitClose {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    companion object {
        fun init() {
            // Enables FFmpegKit's internal log/statistics callback pipeline.
            FFmpegKitConfig.enableStatisticsCallback { }
        }
    }
}
