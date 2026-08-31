package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom wavy progress indicator: a stroked ring whose radius oscillates with a traveling
 * sine wave, so the wave appears to chase its own tail. Shared by the quiz loading
 * screen and the guessing game's preparing / image-fetch spinners.
 */
@Composable
fun WavySpinner(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wavySpinner")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavyPhase",
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val baseR = (size.minDimension / 2f) - strokeWidth / 2f
        val amp = 3.dp.toPx()
        val waves = 5
        val steps = 160
        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = t * 2f * PI.toFloat()
            val r = baseR + amp * sin(waves * angle + phase)
            val x = size.width / 2f + r * cos(angle)
            val y = size.height / 2f + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
