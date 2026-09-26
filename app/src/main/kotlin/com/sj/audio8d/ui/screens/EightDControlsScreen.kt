@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sj.audio8d.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sj.audio8d.dsp.DspParameters
import com.sj.audio8d.dsp.MovementPattern
import com.sj.audio8d.dsp.MovementSpeed
import com.sj.audio8d.ui.theme.CyanAccent
import com.sj.audio8d.ui.theme.TextSecondary
import com.sj.audio8d.viewmodel.PlayerViewModel

@Composable
fun EightDControlsScreen(playerViewModel: PlayerViewModel, onClose: () -> Unit) {
    val params by playerViewModel.dspParams.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("8D Audio Settings", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        Spacer(Modifier.height(16.dp))

        SettingRow("8D Mode", "Turn on to experience 8D sound") {
            Switch(
                checked = params.enabled,
                onCheckedChange = { playerViewModel.updateDspParameters(params.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = CyanAccent)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Movement Speed", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MovementSpeed.values().forEach { speed ->
                SelectableChip(
                    label = speed.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = params.speed == speed,
                    onClick = { playerViewModel.updateDspParameters(params.copy(speed = speed)) }
                )
            }
        }
        if (params.speed == MovementSpeed.CUSTOM) {
            Text("Custom speed: ${"%.2f".format(params.customSpeedHz)} Hz", color = TextSecondary)
            Slider(
                value = params.customSpeedHz,
                onValueChange = { playerViewModel.updateDspParameters(params.copy(customSpeedHz = it)) },
                valueRange = 0.02f..0.4f,
                colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
            )
        }

        Spacer(Modifier.height(16.dp))
        LabeledSlider(
            label = "8D Intensity",
            valueLabel = "${(params.intensity * 100).toInt()}%",
            value = params.intensity,
            onValueChange = { playerViewModel.updateDspParameters(params.copy(intensity = it)) }
        )

        Spacer(Modifier.height(16.dp))
        Text("Movement Pattern", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MovementPattern.values().forEach { pattern ->
                SelectableChip(
                    label = pattern.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = params.pattern == pattern,
                    onClick = { playerViewModel.updateDspParameters(params.copy(pattern = pattern)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        LabeledSlider(
            label = "Stereo Width",
            valueLabel = "${(params.stereoWidth / 1.5f * 100).toInt()}%",
            value = params.stereoWidth,
            onValueChange = { playerViewModel.updateDspParameters(params.copy(stereoWidth = it)) },
            valueRange = 0f..1.5f
        )

        Spacer(Modifier.height(12.dp))
        SettingRow("Bass Boost", null) {
            Switch(
                checked = params.bassBoost,
                onCheckedChange = { playerViewModel.updateDspParameters(params.copy(bassBoost = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = CyanAccent)
            )
        }
        SettingRow("Reverb (Ambience)", null) {
            Switch(
                checked = params.reverbEnabled,
                onCheckedChange = { playerViewModel.updateDspParameters(params.copy(reverbEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = CyanAccent)
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { playerViewModel.updateDspParameters(DspParameters.DEFAULT.copy(enabled = params.enabled)) }) {
                Text("Reset")
            }
            Button(onClick = { playerViewModel.saveAsDefaultPreset(params) }) {
                Text("Apply")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        control()
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(valueLabel, color = TextSecondary)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
    )
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanAccent,
            selectedLabelColor = androidx.compose.ui.graphics.Color.Black
        )
    )
}
