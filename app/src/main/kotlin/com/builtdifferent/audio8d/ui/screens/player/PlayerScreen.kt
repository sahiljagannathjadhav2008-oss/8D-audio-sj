package com.builtdifferent.audio8d.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerScreen(
    conversionIdArg: String?,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val conversionId = conversionIdArg?.toLongOrNull() ?: return
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(conversionId) { viewModel.load(conversionId) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(state.entity?.title ?: "", style = MaterialTheme.typography.titleLarge)
        Text(
            state.entity?.artist ?: "Unknown artist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        Slider(
            value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            onValueChange = { fraction -> viewModel.seekTo((fraction * state.durationMs).toLong()) }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    if (state.entity?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite"
                )
            }
            IconButton(onClick = { viewModel.seekBackward() }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10s")
            }
            FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { viewModel.seekForward() }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s")
            }
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                AssistChip(onClick = { showSpeedMenu = true }, label = { Text("${state.speed}x") })
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        DropdownMenuItem(text = { Text("${speed}x") }, onClick = {
                            viewModel.setSpeed(speed); showSpeedMenu = false
                        })
                    }
                }
            }
            Box {
                AssistChip(
                    onClick = { showSleepMenu = true },
                    label = { Text(state.sleepTimerMinutes?.let { "Sleep: ${it}m" } ?: "Sleep timer") }
                )
                DropdownMenu(expanded = showSleepMenu, onDismissRequest = { showSleepMenu = false }) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        DropdownMenuItem(text = { Text("$minutes min") }, onClick = {
                            viewModel.setSleepTimer(minutes); showSleepMenu = false
                        })
                    }
                    DropdownMenuItem(text = { Text("Off") }, onClick = {
                        viewModel.setSleepTimer(null); showSleepMenu = false
                    })
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
