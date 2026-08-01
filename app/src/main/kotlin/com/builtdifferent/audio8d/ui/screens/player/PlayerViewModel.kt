package com.builtdifferent.audio8d.ui.screens.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.data.repository.ConversionRepository
import com.builtdifferent.audio8d.service.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val entity: ConversionEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val repeatEnabled: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val isControllerReady: Boolean = false
)

/**
 * Drives playback through a [MediaController] connected to [PlaybackService]'s
 * single [androidx.media3.session.MediaSession] — NOT a private ExoPlayer.
 * This is what keeps the in-app Player screen, the notification, lockscreen
 * controls, and Bluetooth headset transport buttons all acting on the same
 * playback state instead of silently diverging.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ConversionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var pendingConversionId: Long? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _uiState.value = _uiState.value.copy(speed = playbackParameters.speed)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.value = _uiState.value.copy(repeatEnabled = repeatMode != Player.REPEAT_MODE_OFF)
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(playerListener) }
            _uiState.value = _uiState.value.copy(isControllerReady = true)
            pendingConversionId?.let { loadInternal(it) }
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    _uiState.value = _uiState.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    fun load(conversionId: Long) {
        if (controller == null) {
            pendingConversionId = conversionId
        } else {
            loadInternal(conversionId)
        }
    }

    private fun loadInternal(conversionId: Long) {
        viewModelScope.launch {
            val entity = repository.findById(conversionId) ?: return@launch
            _uiState.value = _uiState.value.copy(entity = entity)
            val path = entity.convertedFilePath ?: return@launch
            controller?.apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
                prepare()
                play()
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun seekForward(ms: Long = 10_000) {
        controller?.let { it.seekTo((it.currentPosition + ms).coerceAtMost(it.duration.coerceAtLeast(0))) }
    }

    fun seekBackward(ms: Long = 10_000) {
        controller?.let { it.seekTo((it.currentPosition - ms).coerceAtLeast(0)) }
    }

    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val entity = _uiState.value.entity ?: return@launch
            val updated = entity.copy(isFavorite = !entity.isFavorite)
            repository.update(updated)
            _uiState.value = _uiState.value.copy(entity = updated)
        }
    }

    fun setSleepTimer(minutes: Int?) {
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = minutes)
        if (minutes != null) {
            viewModelScope.launch {
                delay(minutes * 60_000L)
                if (_uiState.value.sleepTimerMinutes == minutes) {
                    controller?.pause()
                    _uiState.value = _uiState.value.copy(sleepTimerMinutes = null)
                }
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
