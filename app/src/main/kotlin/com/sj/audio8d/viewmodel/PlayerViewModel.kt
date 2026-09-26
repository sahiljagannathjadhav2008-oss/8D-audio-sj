package com.sj.audio8d.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sj.audio8d.data.FavoritesRepository
import com.sj.audio8d.data.RecentlyPlayedRepository
import com.sj.audio8d.data.SettingsRepository
import com.sj.audio8d.data.Song
import com.sj.audio8d.dsp.DspParameters
import com.sj.audio8d.playback.PlaybackUiState
import com.sj.audio8d.playback.PlayerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerRepository = PlayerRepository(application)
    private val favoritesRepository = FavoritesRepository(application)
    private val recentlyPlayedRepository = RecentlyPlayedRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val playbackState: StateFlow<PlaybackUiState> = playerRepository.state

    private val _dspParams = MutableStateFlow(DspParameters.DEFAULT)
    val dspParams: StateFlow<DspParameters> = _dspParams

    val favoriteIds: StateFlow<Set<Long>> = favoritesRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val recentIds: StateFlow<List<Long>> = recentlyPlayedRepository.recentIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            playerRepository.connect()
            settingsRepository.defaultDspParameters.collect { defaults ->
                _dspParams.value = defaults
                playerRepository.applyDspParameters(defaults)
            }
        }
        // Drives smooth seek-bar updates without waiting on player callbacks alone.
        viewModelScope.launch {
            while (true) {
                playerRepository.tickPosition()
                delay(500)
            }
        }
    }

    fun setLibrary(songs: List<Song>) = playerRepository.setLibrary(songs)

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        playerRepository.playSong(song, queue)
        viewModelScope.launch { recentlyPlayedRepository.recordPlayed(song.id) }
    }

    fun togglePlayPause() = playerRepository.togglePlayPause()
    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun seekForward10() = playerRepository.seekRelative(10_000)
    fun seekBack10() = playerRepository.seekRelative(-10_000)
    fun skipNext() = playerRepository.skipNext()
    fun skipPrevious() = playerRepository.skipPrevious()
    fun jumpToQueueIndex(index: Int) = playerRepository.jumpToQueueIndex(index)
    fun toggleShuffle() = playerRepository.setShuffle(!playbackState.value.shuffleEnabled)
    fun cycleRepeatMode() = playerRepository.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) {
        playerRepository.setPlaybackSpeed(speed)
        viewModelScope.launch { settingsRepository.setPlaybackSpeed(speed) }
    }

    fun addToQueueNext(song: Song) = playerRepository.addToQueueNext(song)
    fun removeFromQueue(index: Int) = playerRepository.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = playerRepository.moveInQueue(from, to)
    fun clearQueue() = playerRepository.clearQueueKeepCurrent()

    fun toggleFavorite(songId: Long) = viewModelScope.launch { favoritesRepository.toggleFavorite(songId) }

    /** Applied live, mid-playback — see [PlayerRepository.applyDspParameters] and the processor's smoothing. */
    fun updateDspParameters(params: DspParameters) {
        _dspParams.value = params
        playerRepository.applyDspParameters(params)
    }

    fun saveAsDefaultPreset(params: DspParameters) {
        viewModelScope.launch { settingsRepository.saveDefaultDspParameters(params) }
    }

    fun runCurrentSongIn8D() {
        updateDspParameters(_dspParams.value.copy(enabled = true))
    }

    fun disable8D() {
        updateDspParameters(_dspParams.value.copy(enabled = false))
    }

    override fun onCleared() {
        playerRepository.release()
        super.onCleared()
    }
}
