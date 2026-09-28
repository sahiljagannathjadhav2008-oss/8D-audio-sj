package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.ui.components.SongRow
import com.sj.audio8d.viewmodel.PlayerViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by playerViewModel.playbackState.collectAsState()

    androidx.compose.foundation.layout.Column {
        TopAppBar(title = { Text("Play Queue") })
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(state.queue.withIndex().toList(), key = { it.value.id }) { (index, song) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        color = if (index == state.currentIndex) androidx.compose.ui.graphics.Color(0xFF22D3EE)
                        else androidx.compose.ui.graphics.Color.Gray
                    )
                    SongRow(
                        song = song,
                        onClick = { playerViewModel.jumpToQueueIndex(index) },
                        onMenuClick = { playerViewModel.removeFromQueue(index) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { playerViewModel.clearQueue() }) { Text("Clear Queue") }
            Button(onClick = { playerViewModel.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Text("  Shuffle")
            }
        }
    }
}
