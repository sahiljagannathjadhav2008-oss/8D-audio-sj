package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.ui.components.SongRow
import com.sj.audio8d.viewmodel.LibraryUiState
import com.sj.audio8d.viewmodel.LibraryViewModel
import com.sj.audio8d.viewmodel.PlayerViewModel

@Composable
fun SearchScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSongClick: () -> Unit
) {
    val query by libraryViewModel.searchQuery.collectAsState()
    val uiState by libraryViewModel.uiState.collectAsState()
    val allSongs = (uiState as? LibraryUiState.Loaded)?.songs ?: emptyList()

    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = { libraryViewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search songs, artists, albums...") }
            )
        }
        val results = libraryViewModel.searchResults()
        LazyColumn {
            items(results, key = { it.id }) { song ->
                SongRow(song = song, onClick = {
                    playerViewModel.playSong(song, allSongs)
                    onSongClick()
                })
            }
        }
    }
}
