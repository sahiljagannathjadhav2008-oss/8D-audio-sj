package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.sj.audio8d.ui.components.SongRow
import com.sj.audio8d.viewmodel.LibraryUiState
import com.sj.audio8d.viewmodel.LibraryViewModel
import com.sj.audio8d.viewmodel.PlayerViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSongClick: () -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val allSongs = (uiState as? LibraryUiState.Loaded)?.songs ?: emptyList()
    val favoriteIds by playerViewModel.favoriteIds.collectAsState()
    val favoriteSongs = allSongs.filter { it.id in favoriteIds }

    androidx.compose.foundation.layout.Column {
        TopAppBar(
            title = { Text("Favorites") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
        if (favoriteSongs.isEmpty()) {
            Text("No favorites yet", modifier = androidx.compose.ui.Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(favoriteSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isFavorite = true,
                        onClick = {
                            playerViewModel.playSong(song, favoriteSongs)
                            onSongClick()
                        },
                        onFavoriteClick = { playerViewModel.toggleFavorite(song.id) }
                    )
                }
            }
        }
    }
}
