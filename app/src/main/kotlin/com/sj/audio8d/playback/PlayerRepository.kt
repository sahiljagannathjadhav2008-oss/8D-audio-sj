package com.sj.audio8d.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sj.audio8d.data.Song
import com.sj.audio8d.dsp.DspParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val eightDRunning: Boolean = false
)

/**
 * Thin wrapper around a [MediaController] connected to [PlaybackService].
 * The UI layer never talks to ExoPlayer directly — every control action
 * (play, seek, skip, queue edit, 8D toggle) goes through this class so
 * playback keeps working identically whether the activity is foregrounded
 * or the service is running headless in the background.
 */
class PlayerRepository(private val context: Context) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    private var songsById: Map<Long, Song> = emptyMap()

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controller = suspendController(future)
        controller?.addListener(playerListener)
        refreshState()
    }

    private suspend fun suspendController(
        future: com.google.common.util.concurrent.ListenableFuture<MediaController>
    ): MediaController = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        future.addListener({
            runCatching { future.get() }
                .onSuccess { cont.resume(it) }
                .onFailure { cont.cancel() }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshState()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refreshState()
        override fun onRepeatModeChanged(repeatMode: Int) = refreshState()
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) =
            refreshState()
    }

    private fun refreshState() {
        val c = controller ?: return
        val currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
        val queueIds = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        _state.value = _state.value.copy(
            currentSong = currentId?.let { songsById[it] },
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            playbackSpeed = c.playbackParameters.speed,
            queue = queueIds.mapNotNull { songsById[it] },
            currentIndex = c.currentMediaItemIndex
        )
    }

    /** Called periodically (e.g. every 500ms) by the UI while a song is active, to keep the seek bar smooth. */
    fun tickPosition() {
        val c = controller ?: return
        _state.value = _state.value.copy(positionMs = c.currentPosition.coerceAtLeast(0))
    }

    fun setLibrary(songs: List<Song>) {
        songsById = songs.associateBy { it.id }
    }

    fun playSong(song: Song, queue: List<Song>) {
        val c = controller ?: return
        songsById = (songsById.values + queue + song).associateBy { it.id }
        val items = queue.ifEmpty { listOf(song) }.map { it.toMediaItem() }
        val startIndex = items.indexOfFirst { it.mediaId == song.id.toString() }.coerceAtLeast(0)
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceIn(0, c.duration.coerceAtLeast(0))
        c.seekTo(target)
    }

    fun skipNext() = controller?.seekToNextMediaItem()
    fun skipPrevious() = controller?.seekToPreviousMediaItem()

    fun jumpToQueueIndex(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun addToQueueNext(song: Song) {
        val c = controller ?: return
        songsById = songsById + (song.id to song)
        c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), song.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    fun clearQueueKeepCurrent() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (i != current) c.removeMediaItem(i)
        }
    }

    // --- 8D control -------------------------------------------------------

    fun applyDspParameters(params: DspParameters) {
        PlaybackService.eightDProcessorInstance?.updateParameters(params)
        _state.value = _state.value.copy(eightDRunning = params.enabled)
    }

    fun currentDspParameters(): DspParameters =
        PlaybackService.eightDProcessorInstance?.currentParameters() ?: DspParameters.DEFAULT

    fun release() {
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()
}
