package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import quiz.thaton3app.nazo.ui.mode.GameplayPresentation
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/** Visual identities are deliberately quiet so gameplay content remains dominant. */
internal enum class GameplayAtmosphereKind { Quiz, Survival, Blitz, Versus, Guessing }

internal fun gameplayAtmosphereKind(
    presentation: GameplayPresentation,
): GameplayAtmosphereKind = when (presentation) {
    GameplayPresentation.QUIZ -> GameplayAtmosphereKind.Quiz
    GameplayPresentation.SURVIVAL -> GameplayAtmosphereKind.Survival
    GameplayPresentation.BLITZ -> GameplayAtmosphereKind.Blitz
    GameplayPresentation.VERSUS -> GameplayAtmosphereKind.Versus
}

/** A null period means a settled first frame and, importantly, no infinite clock. */
internal fun gameplayAtmospherePeriodMillis(
    kind: GameplayAtmosphereKind,
    motionAllowed: Boolean,
): Int? = if (!motionAllowed) {
    null
} else {
    when (kind) {
        GameplayAtmosphereKind.Quiz -> 18_000
        GameplayAtmosphereKind.Survival -> 2_200
        GameplayAtmosphereKind.Blitz -> 2_800
        GameplayAtmosphereKind.Versus -> 12_000
        GameplayAtmosphereKind.Guessing -> 16_000
    }
}

internal fun versusAtmosphereSide(playerLabel: String?): Int =
    if (playerLabel?.trim()?.uppercase() == "P2") 1 else 0

/**
 * Subtle mode identity behind active gameplay:
 *
 * - Quiz breathes through two slow accent halos.
 * - Survival carries a heartbeat-like danger vignette that strengthens as lives fall.
 * - Blitz moves short speed trails across the field.
 * - Versus balances opposing side glows and emphasizes the active player side.
 * - Guessing turns faint focus rings around the mystery image.
 *
 * The component is decorative and never handles input. Reduced motion callers pass
 * [motionAllowed] as false, leaving the same identity visible as a static frame without
 * creating an infinite transition.
 */
@Composable
internal fun GameplayAtmosphere(
    kind: GameplayAtmosphereKind,
    motionAllowed: Boolean,
    survivalIntensity: Float = 0f,
    versusActiveSide: Int = 0,
    modifier: Modifier = Modifier,
) {
    val displayedSurvivalIntensity = animateFloatAsState(
        targetValue = survivalIntensity.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (motionAllowed) 520 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "survivalAtmosphereIntensity",
    ).value
    val periodMillis = gameplayAtmospherePeriodMillis(kind, motionAllowed)
    val phase = if (periodMillis != null) {
        val transition = rememberInfiniteTransition(label = "gameplayAtmosphere")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "gameplayAtmospherePhase",
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        when (kind) {
            GameplayAtmosphereKind.Quiz -> drawQuizAtmosphere(phase)
            GameplayAtmosphereKind.Survival -> drawSurvivalAtmosphere(
                phase = phase,
                danger = displayedSurvivalIntensity,
            )
            GameplayAtmosphereKind.Blitz -> drawBlitzAtmosphere(phase)
            GameplayAtmosphereKind.Versus -> drawVersusAtmosphere(
                phase = phase,
                activeSide = versusActiveSide.coerceIn(0, 1),
            )
            GameplayAtmosphereKind.Guessing -> drawGuessingAtmosphere(phase)
        }
    }
}

private fun DrawScope.drawQuizAtmosphere(phase: Float) {
    val angle = phase * 2f * PI.toFloat()
    val radius = maxOf(size.width, size.height) * 0.52f
    val first = Offset(
        x = size.width * (0.20f + sin(angle) * 0.035f),
        y = size.height * (0.22f + cos(angle) * 0.025f),
    )
    val second = Offset(
        x = size.width * (0.80f - sin(angle) * 0.03f),
        y = size.height * (0.78f - cos(angle) * 0.02f),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(NazoPrimary.copy(alpha = 0.045f), NazoPrimary.copy(alpha = 0f)),
            center = first,
            radius = radius,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                NazoTextSecondary.copy(alpha = 0.025f),
                NazoTextSecondary.copy(alpha = 0f),
            ),
            center = second,
            radius = radius * 0.82f,
        ),
    )
}

private fun DrawScope.drawSurvivalAtmosphere(phase: Float, danger: Float) {
    val heartbeat = ((sin(phase * 2f * PI.toFloat()) + 1f) / 2f)
    val alpha = 0.025f + danger * 0.105f + heartbeat * (0.008f + danger * 0.025f)
    val radius = maxOf(size.width, size.height) * (0.70f - danger * 0.06f)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(NazoError.copy(alpha = 0f), NazoError.copy(alpha = alpha)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        ),
    )
}

private fun DrawScope.drawBlitzAtmosphere(phase: Float) {
    val laneCount = 9
    repeat(laneCount) { index ->
        val lanePhase = (phase + index.toFloat() / laneCount) % 1f
        val length = size.width * (0.12f + (index % 3) * 0.025f)
        val startX = lanePhase * (size.width + length * 2f) - length
        val y = size.height * ((index + 0.65f) / laneCount)
        drawLine(
            color = NazoPrimary.copy(alpha = 0.025f + (index % 3) * 0.008f),
            start = Offset(startX, y),
            end = Offset(startX + length, y - size.height * 0.035f),
            strokeWidth = if (index % 3 == 0) 1.4.dp.toPx() else 0.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawVersusAtmosphere(phase: Float, activeSide: Int) {
    val angle = phase * 2f * PI.toFloat()
    val leftBreath = 0.75f + ((sin(angle) + 1f) / 2f) * 0.25f
    val rightBreath = 0.75f + ((sin(angle + PI.toFloat()) + 1f) / 2f) * 0.25f
    val leftStrength = if (activeSide == 0) 1f else 0.62f
    val rightStrength = if (activeSide == 1) 1f else 0.62f
    val radius = maxOf(size.width, size.height) * 0.62f

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                NazoPrimary.copy(alpha = 0.055f * leftStrength * leftBreath),
                NazoPrimary.copy(alpha = 0f),
            ),
            center = Offset(-size.width * 0.05f, size.height * 0.5f),
            radius = radius,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                NazoTextSecondary.copy(alpha = 0.05f * rightStrength * rightBreath),
                NazoTextSecondary.copy(alpha = 0f),
            ),
            center = Offset(size.width * 1.05f, size.height * 0.5f),
            radius = radius,
        ),
    )
    val centerAlpha = 0.018f + 0.008f * ((cos(angle) + 1f) / 2f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                NazoPrimary.copy(alpha = 0f),
                NazoPrimary.copy(alpha = centerAlpha),
                NazoPrimary.copy(alpha = 0f),
            ),
        ),
        topLeft = Offset(size.width / 2f - 0.5.dp.toPx(), 0f),
        size = Size(1.dp.toPx(), size.height),
    )
}

private fun DrawScope.drawGuessingAtmosphere(phase: Float) {
    val angle = phase * 2f * PI.toFloat()
    val landscape = size.width > size.height
    val center = if (landscape) {
        Offset(size.width * 0.27f, size.height * 0.56f)
    } else {
        Offset(size.width * 0.5f, size.height * 0.42f)
    }
    val baseRadius = minOf(size.width, size.height) * (0.18f + sin(angle) * 0.008f)
    repeat(3) { index ->
        drawCircle(
            color = NazoPrimary.copy(alpha = 0.035f - index * 0.007f),
            radius = baseRadius * (1f + index * 0.38f),
            center = center,
            style = Stroke(width = if (index == 0) 1.2.dp.toPx() else 0.8.dp.toPx()),
        )
    }
    val sweepRadius = baseRadius * 1.72f
    drawLine(
        color = NazoPrimary.copy(alpha = 0.035f),
        start = center,
        end = Offset(
            x = center.x + cos(angle) * sweepRadius,
            y = center.y + sin(angle) * sweepRadius,
        ),
        strokeWidth = 0.8.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
