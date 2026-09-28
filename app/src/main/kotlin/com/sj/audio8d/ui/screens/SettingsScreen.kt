package com.sj.audio8d.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.viewmodel.PlayerViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(playerViewModel: PlayerViewModel, onOpenAbout: () -> Unit, onOpenFavorites: () -> Unit) {
    val dsp by playerViewModel.dspParams.collectAsState()

    Column {
        TopAppBar(title = { Text("Settings") })
        Text("Audio", modifier = Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleMedium)
        ListItem(headlineContent = { Text("Default 8D State") }, supportingContent = { Text(if (dsp.enabled) "On" else "Off") })
        ListItem(headlineContent = { Text("Default Movement Speed") }, supportingContent = { Text(dsp.speed.name.lowercase().replaceFirstChar { it.uppercase() }) })
        ListItem(headlineContent = { Text("Default Intensity") }, supportingContent = { Text("${(dsp.intensity * 100).toInt()}%") })
        ListItem(headlineContent = { Text("Stereo Width") }, supportingContent = { Text("${(dsp.stereoWidth / 1.5f * 100).toInt()}%") })
        ListItem(headlineContent = { Text("Bass Boost") }, supportingContent = { Text(if (dsp.bassBoost) "On" else "Off") })
        ListItem(headlineContent = { Text("Ambience / Reverb") }, supportingContent = { Text(if (dsp.reverbEnabled) "On" else "Off") })
        HorizontalDivider()
        Text("App", modifier = Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("Favorites") },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFavorites)
        )
        ListItem(
            headlineContent = { Text("About") },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout)
        )
    }
}
