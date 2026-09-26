package com.sj.audio8d.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sj.audio8d.dsp.MovementPattern
import com.sj.audio8d.ui.theme.CyanAccent
import com.sj.audio8d.ui.theme.CyanAccentDim
import kotlin.math.cos
import kotlin.math.sin

/**
 * A glowing point tracing the same trajectory the DSP is applying to the
 * audio. It reads the same [MovementPattern] and speed the processor uses so
 * it is a representation of the real effect, not an independent animation —
 * the audio's own [com.sj.audio8d.dsp.Circular8DAudioProcessor] phase math is
 * mirrored here rather than duplicated with different constants.
 */
@Composable
fun EightDVisualizer(
    modifier: Modifier = Modifier,
    running: Boolean,
    pattern: MovementPattern,
    speedHz: Float
)
 {
    val transition = rememberInfiniteTransition(label = "8d-visualizer")
    val periodMs = if (speedHz > 0.001f) (1000f / speedHz).toInt().coerceIn(400, 20000) else 4000
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (running) periodMs else periodMs * 6, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = CyanAccentDim.copy(alpha = 0.35f),
            radius = radius,
            center = center,
            style = Stroke(width = 2f)
        )

        val (x, y) = when (pattern) {
            MovementPattern.CIRCULAR -> sin(phase) to -cos(phase)
            MovementPattern.HORIZONTAL -> sin(phase) to 0f
            MovementPattern.VERTICAL -> 0f to -cos(phase)
            MovementPattern.RANDOM -> {
                val px = (sin(phase) * 0.6f + sin(phase * 2.37f + 1.3f) * 0.3f + sin(phase * 0.53f + 0.7f) * 0.1f)
                val py = -(0.6f * cos(phase * 1.1f) + 0.4f * sin(phase * 0.7f + 0.5f))
                px to py
            }
        }
        val point = Offset(center.x + x * radius, center.y + y * radius)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CyanAccent.copy(alpha = if (running) 0.9f else 0.25f), CyanAccent.copy(alpha = 0f)),
                center = point,
                radius = radius * 0.55f
            ),
            radius = radius * 0.55f,
            center = point
        )
        drawCircle(
            color = if (running) CyanAccent else CyanAccentDim,
            radius = 8f,
            center = point
        )
    }
}
