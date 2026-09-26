package com.sj.audio8d.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.data.Song
import com.sj.audio8d.ui.components.SongRow
import com.sj.audio8d.ui.theme.CyanAccent
import com.sj.audio8d.viewmodel.LibraryUiState
import com.sj.audio8d.viewmodel.LibraryViewModel
import com.sj.audio8d.viewmodel.PlayerViewModel

@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onOpenAllSongs: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRecentlyPlayed: () -> Unit,
    onSongClick: () -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val recentIds by playerViewModel.recentIds.collectAsState()

    when (val state = uiState) {
        LibraryUiState.Loading -> CenteredLoader()
        LibraryUiState.Empty -> EmptyLibraryMessage()
        is LibraryUiState.Loaded -> {
            playerViewModel.setLibrary(state.songs)
            val songsById = state.songs.associateBy { it.id }
            val recentSongs = recentIds.mapNotNull { songsById[it] }.take(5)
            val artistCounts = state.songs.groupBy { it.artist }

            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("8D Audio", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
                if (recentSongs.isNotEmpty()) {
                    item { SectionHeader("Recently Played", onOpenRecentlyPlayed) }
                    items(recentSongs) { song ->
                        SongRow(song = song, onClick = {
                            playerViewModel.playSong(song, state.songs)
                            onSongClick()
                        })
                    }
                }
                item { SectionHeader("Your Music", null) }
                items(artistCounts.entries.toList().take(10)) { (artist, songs) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(artist, style = MaterialTheme.typography.bodyLarge)
                            Text("${songs.size} songs", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onOpenArtists) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (onSeeAll != null) {
            Text("See all", color = CyanAccent, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(4.dp).clickable(onClick = onSeeAll))
        }
    }
}

@Composable
fun CenteredLoader() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = CyanAccent)
        Spacer(Modifier.height(12.dp))
        Text("Loading your music...")
    }
}

@Composable
fun EmptyLibraryMessage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No music found", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Add some audio files to your device storage and reopen the app.")
    }
}
