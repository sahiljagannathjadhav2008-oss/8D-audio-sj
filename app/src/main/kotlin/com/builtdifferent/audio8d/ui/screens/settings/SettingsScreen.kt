package com.builtdifferent.audio8d.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.builtdifferent.audio8d.data.db.OutputFormat
import com.builtdifferent.audio8d.utils.FileUtils

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row {
            listOf("System" to null, "Light" to false, "Dark" to true).forEach { (label, value) ->
                FilterChip(
                    selected = state.darkTheme == value,
                    onClick = { viewModel.setDarkTheme(value) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Default output format", style = MaterialTheme.typography.titleMedium)
        Row {
            OutputFormat.values().forEach { format ->
                FilterChip(
                    selected = state.defaultFormat == format,
                    onClick = { viewModel.setDefaultFormat(format) },
                    label = { Text(format.displayName) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Cache", style = MaterialTheme.typography.titleMedium)
        Text(
            "Temporary import cache: ${FileUtils.humanReadableSize(state.cacheSizeBytes)}",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = { viewModel.clearCache() }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Clear cache")
        }

        Spacer(Modifier.height(24.dp))
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "8D Audio Converter — built by Built Different. All conversion runs fully offline on-device.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
