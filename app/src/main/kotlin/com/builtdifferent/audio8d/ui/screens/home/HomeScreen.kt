package com.builtdifferent.audio8d.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.utils.FileUtils

@Composable
fun HomeScreen(
    onOpenConvert: (Long) -> Unit,
    onOpenPlayer: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversions by viewModel.recentConversions.collectAsState()
    val totalBytes by viewModel.totalStorageBytes.collectAsState()

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudio(uri) { result ->
                when (result) {
                    is ImportResult.AlreadyConverted -> onOpenPlayer(result.conversionId)
                    is ImportResult.NeedsConversion -> onOpenConvert(result.conversionId)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("8D Audio Converter", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Storage used: ${FileUtils.humanReadableSize(totalBytes ?: 0L)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import Audio")
        }

        Spacer(Modifier.height(24.dp))
        Text("Recent Files", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (conversions.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(conversions, key = { it.id }) { entity ->
                    ConversionCard(entity = entity, onClick = {
                        if (entity.status == "COMPLETED") onOpenPlayer(entity.id) else onOpenConvert(entity.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text("No conversions yet — import a song to get started", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConversionCard(entity: ConversionEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    entity.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            StatusPill(status = entity.status)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "COMPLETED" -> "8D ✔" to MaterialTheme.colorScheme.secondary
        "PROCESSING" -> "Converting" to MaterialTheme.colorScheme.primary
        "FAILED" -> "Failed" to MaterialTheme.colorScheme.error
        else -> "Pending" to MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
