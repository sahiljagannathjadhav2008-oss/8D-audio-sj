package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sj.audio8d.viewmodel.LibraryViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(libraryViewModel: LibraryViewModel, onBack: () -> Unit) {
    val grouped = libraryViewModel.songsByArtist().toSortedMap()
    androidx.compose.foundation.layout.Column {
        TopAppBar(
            title = { Text("Artists") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
        LazyColumn {
            items(grouped.entries.toList()) { (artist, songs) ->
                ListItem(
                    headlineContent = { Text(artist) },
                    supportingContent = { Text("${songs.size} songs") }
                )
            }
        }
    }
}
