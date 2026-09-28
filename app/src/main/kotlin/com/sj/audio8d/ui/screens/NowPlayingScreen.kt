package com.sj.audio8d.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sj.audio8d.ui.components.EightDVisualizer
import com.sj.audio8d.ui.components.formatDuration
import com.sj.audio8d.ui.theme.CardBackground
import com.sj.audio8d.ui.theme.CyanAccent
import com.sj.audio8d.ui.theme.TextSecondary
import com.sj.audio8d.viewmodel.PlayerViewModel

@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpen8DControls: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val state by playerViewModel.playbackState.collectAsState()
    val dsp by playerViewModel.dspParams.collectAsState()
    val song = state.currentSong

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back") }
            Text("Now Playing", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onOpenQueue) { Icon(Icons.Default.QueueMusic, contentDescription = "Queue") }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackground),
            contentAlignment = Alignment.Center
        ) {
            if (dsp.enabled) {
                EightDVisualizer(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    running = state.isPlaying,
                    pattern = dsp.pattern,
                    speedHz = dsp.effectiveSpeedHz
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = CyanAccent, modifier = Modifier.height(72.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(song?.title ?: "Nothing playing", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(song?.artist ?: "Select a song from your library", color = TextSecondary)

        Spacer(Modifier.height(12.dp))

        if (dsp.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headset, contentDescription = null, tint = CyanAccent, modifier = Modifier.height(18.dp))
                Text("  RUNNING IN 8D", color = CyanAccent, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(onClick = { playerViewModel.runCurrentSongIn8D() }, enabled = song != null) {
                Icon(Icons.Default.Headset, contentDescription = null)
                Text("  RUN IN 8D")
            }
        }

        Spacer(Modifier.height(16.dp))

        Slider(
            value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            onValueChange = { playerViewModel.seekTo((it * state.durationMs).toLong()) },
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(state.durationMs), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffleEnabled) CyanAccent else TextSecondary)
            }
            IconButton(onClick = { playerViewModel.skipPrevious() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous") }
            IconButton(
                onClick = { playerViewModel.togglePlayPause() },
                modifier = Modifier.height(64.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = CyanAccent,
                    modifier = Modifier.height(48.dp)
                )
            }
            IconButton(onClick = { playerViewModel.skipNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
            IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                Icon(Icons.Default.Repeat, contentDescription = "Repeat",
                    tint = if (state.repeatMode != 0) CyanAccent else TextSecondary)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { playerViewModel.seekBack10() }) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s")
            }
            Text("${state.playbackSpeed}x", modifier = Modifier.align(Alignment.CenterVertically))
            IconButton(onClick = { playerViewModel.seekForward10() }) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onOpen8DControls, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Text("  8D Controls")
        }
    }
}
