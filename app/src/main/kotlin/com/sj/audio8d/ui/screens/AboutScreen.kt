package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.BuildConfig
import com.sj.audio8d.ui.theme.TextSecondary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column {
        TopAppBar(
            title = { Text("About") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Text("8D Audio", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "8D Audio applies a real-time spatial movement effect to your local music " +
                    "while it plays. The effect is generated live by a custom audio processor in " +
                    "the playback pipeline — no file is ever converted, copied, or uploaded. " +
                    "Everything, including the effect itself, runs fully offline.",
                color = TextSecondary
            )
            Spacer(Modifier.height(20.dp))
            Text("Developer", style = MaterialTheme.typography.titleMedium)
            Text("Sahil Jadhav", color = TextSecondary)
            Spacer(Modifier.height(20.dp))
            Text("Version", style = MaterialTheme.typography.titleMedium)
            Text(BuildConfig.VERSION_NAME, color = TextSecondary)
        }
    }
}
