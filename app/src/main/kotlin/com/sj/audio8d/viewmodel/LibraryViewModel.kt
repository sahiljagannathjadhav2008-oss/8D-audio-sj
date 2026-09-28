package com.sj.audio8d.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sj.audio8d.data.MusicRepository
import com.sj.audio8d.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Loaded(val songs: List<Song>) : LibraryUiState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            val songs = repository.loadAllSongs()
            _uiState.value = if (songs.isEmpty()) LibraryUiState.Empty else LibraryUiState.Loaded(songs)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchResults(): List<Song> {
        val state = _uiState.value
        val songs = (state as? LibraryUiState.Loaded)?.songs ?: return emptyList()
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return emptyList()
        return songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }

    fun songsByArtist(): Map<String, List<Song>> =
        ((_uiState.value as? LibraryUiState.Loaded)?.songs ?: emptyList()).groupBy { it.artist }

    fun songsByAlbum(): Map<String, List<Song>> =
        ((_uiState.value as? LibraryUiState.Loaded)?.songs ?: emptyList()).groupBy { it.album }
}
