package com.sj.audio8d.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.sj.audio8d.dsp.Circular8DAudioProcessor

/**
 * The only supported way to splice a custom [androidx.media3.common.audio.AudioProcessor]
 * into ExoPlayer's audio pipeline is by overriding [buildAudioSink] on a
 * [DefaultRenderersFactory] subclass — [androidx.media3.exoplayer.ExoPlayer.Builder]
 * has no public "set audio sink" hook. This factory does exactly that and
 * nothing else, so every other renderer (video is unused here, but this
 * still keeps standard decoder/extension behavior) stays on Media3's
 * defaults.
 */
class EightDRenderersFactory(
    context: Context,
    private val eightDProcessor: Circular8DAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(eightDProcessor))
            .build()
    }
}
