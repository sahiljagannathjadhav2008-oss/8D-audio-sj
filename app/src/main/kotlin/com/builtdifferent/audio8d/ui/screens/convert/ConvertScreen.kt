package com.builtdifferent.audio8d.ui.screens.convert

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConvertScreen(
    conversionIdArg: String?,
    onFinished: (Long) -> Unit,
    viewModel: ConvertViewModel = hiltViewModel()
) {
    val conversionId = conversionIdArg?.toLongOrNull() ?: return
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(conversionId) {
        viewModel.start(conversionId)
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onFinished(conversionId)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.percent / 100f,
        animationSpec = tween(durationMillis = 400),
        label = "progress"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(state.entity?.title ?: "Converting...", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            state.entity?.artist ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            if (state.percent >= 100 && state.error == null) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(72.dp)
                )
            } else {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("${state.percent}%", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(Modifier.height(28.dp))

        if (state.percent >= 100 && state.error == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("8D", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            Text(state.stage, style = MaterialTheme.typography.bodyMedium)
        }

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { viewModel.start(conversionId) }) { Text("Retry") }
        }

        Spacer(Modifier.height(24.dp))
        if (state.percent < 100 && state.error == null) {
            TextButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
        }
    }
}
