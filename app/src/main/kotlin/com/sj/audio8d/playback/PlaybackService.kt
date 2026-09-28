package com.sj.audio8d.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import com.sj.audio8d.MainActivity
import com.sj.audio8d.dsp.Circular8DAudioProcessor

/**
 * Hosts the single [ExoPlayer] instance and its [MediaSession] so playback
 * (including the live 8D effect) keeps running when the app is minimized,
 * the screen is locked, or the user switches apps. Media3 handles audio
 * focus, the media notification, and headphone-disconnect pause behavior
 * for us via this session — see the MediaSession/ExoPlayer defaults below.
 */
// ExoPlayer.Builder(Context, RenderersFactory) and RenderersFactory are @UnstableApi in
// Media3; needed to install EightDRenderersFactory. Scoped to this class only.
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val processor = Circular8DAudioProcessor()
        eightDProcessorInstance = processor

        val player = ExoPlayer.Builder(applicationContext, EightDRenderersFactory(applicationContext, processor))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause on wired/Bluetooth headphone disconnect
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        eightDProcessorInstance = null
        super.onDestroy()
    }

    companion object {
        // The service and the UI/ViewModel run in the same process (no
        // android:process is declared for PlaybackService), so the live DSP
        // processor instance is shared this way rather than through a Binder.
        @Volatile
        var eightDProcessorInstance: Circular8DAudioProcessor? = null
            private set
    }
}
