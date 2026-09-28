package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.ui.components.SongRow
import com.sj.audio8d.viewmodel.LibraryUiState
import com.sj.audio8d.viewmodel.LibraryViewModel
import com.sj.audio8d.viewmodel.PlayerViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllSongsScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onOpenSearch: () -> Unit,
    onSongClick: () -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val favoriteIds by playerViewModel.favoriteIds.collectAsState()

    androidx.compose.foundation.layout.Column {
        TopAppBar(
            title = { Text("All Songs") },
            actions = {
                IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
            }
        )
        when (val state = uiState) {
            is LibraryUiState.Loaded -> {
                LazyColumn {
                    items(state.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isFavorite = song.id in favoriteIds,
                            onClick = {
                                playerViewModel.playSong(song, state.songs)
                                onSongClick()
                            },
                            onFavoriteClick = { playerViewModel.toggleFavorite(song.id) }
                        )
                    }
                }
            }
            LibraryUiState.Empty -> EmptyLibraryMessage()
            LibraryUiState.Loading -> CenteredLoader()
        }
    }
}
