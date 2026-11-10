package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Shared corner-morph press language
// ---------------------------------------------------------------------------

internal const val NazoExpressivePressInDurationMs = 90
internal const val NazoExpressiveQuickTapCompletionMs = 45
internal const val NazoExpressiveReleaseDurationMs = 180
internal const val NazoExpressiveCancelDurationMs = 120
internal const val NazoExpressiveQuickTapPeak = 0.72f

internal const val NazoPillRestCornerPercent = 50f
internal const val NazoPillPressedCornerPercent = 24f

/** Interpolates one corner value without ever allowing animation overshoot. */
internal fun nazoCornerMorphValue(
    restingCorner: Float,
    pressedCorner: Float,
    progress: Float,
): Float {
    val fraction = progress.coerceIn(0f, 1f)
    return restingCorner + (pressedCorner - restingCorner) * fraction
}

/**
 * Pure corner policy for the Practice-button reference. Fifty percent is its
 * original pill silhouette; the held state becomes noticeably more rectangular
 * without changing layout, scale, content, elevation, or colour.
 */
internal fun nazoExpressiveCornerPercent(progress: Float): Float = nazoCornerMorphValue(
    restingCorner = NazoPillRestCornerPercent,
    pressedCorner = NazoPillPressedCornerPercent,
    progress = progress,
)

/**
 * A very short tap should become visible without being forced all the way to
 * the held endpoint. The partial peak avoids the old one-frame-looking jump to
 * a fully rectangular shape immediately before release.
 */
internal fun nazoQuickTapCompletionTarget(progress: Float): Float {
    val current = progress.coerceIn(0f, 1f)
    return maxOf(current, NazoExpressiveQuickTapPeak)
}

/** Completes only the missing fraction up to the restrained quick-tap peak. */
internal fun nazoQuickTapCompletionDurationMs(progress: Float): Int {
    val current = progress.coerceIn(0f, 1f)
    val remaining = nazoQuickTapCompletionTarget(current) - current
    if (remaining <= 0f) return 0
    return (NazoExpressiveQuickTapCompletionMs *
        (remaining / NazoExpressiveQuickTapPeak))
        .roundToInt()
        .coerceIn(1, NazoExpressiveQuickTapCompletionMs)
}

/**
 * Holds one expressive press animation. Reading [progress] from a graphics or
 * draw modifier invalidates only that phase, so the button does not recompose
 * on every animation frame.
 */
@Stable
class NazoExpressivePressState internal constructor(
    private val animation: Animatable<Float, AnimationVector1D>,
) {
    val progress: Float
        get() = animation.value
}

/**
 * Reuses the Practice timing for a control whose resting geometry is expressed
 * in dp. Callers use the returned shape for clip, background and border, and
 * pass the same [interactionSource] to their existing clickable. The shape only
 * changes drawing and clipping; measurement, padding and content stay fixed.
 */
@Composable
fun rememberNazoRoundedCornerMorphShape(
    interactionSource: MutableInteractionSource,
    restingCorner: Dp,
    pressedCorner: Dp = restingCorner * 0.5f,
    enabled: Boolean = true,
): Shape {
    val press = rememberNazoExpressivePressState(interactionSource, enabled)
    val corner = nazoCornerMorphValue(
        restingCorner = restingCorner.value,
        pressedCorner = pressedCorner.value,
        progress = press.progress,
    ).dp
    return RoundedCornerShape(corner)
}

/** The same reusable policy for pills and circles whose corners are percentages. */
@Composable
fun rememberNazoPillCornerMorphShape(
    interactionSource: MutableInteractionSource,
    restingCornerPercent: Float = NazoPillRestCornerPercent,
    pressedCornerPercent: Float = NazoPillPressedCornerPercent,
    enabled: Boolean = true,
): Shape {
    val press = rememberNazoExpressivePressState(interactionSource, enabled)
    val corner = nazoCornerMorphValue(
        restingCorner = restingCornerPercent,
        pressedCorner = pressedCornerPercent,
        progress = press.progress,
    ).roundToInt()
    return RoundedCornerShape(corner)
}

/**
 * Observes the same interaction source already owned by clickable/Button.
 *
 * Press holds at the fully morphed shape until release. A too-fast tap completes
 * only to a restrained visible peak, then every real release eases home over a
 * fixed, gradual curve instead of handing a tiny distance to an aggressive
 * spring. Cancellation (for example when a parent scroll takes the gesture)
 * skips the completion beat and eases directly to rest, so this observer never
 * steals or changes gesture ownership. Reduced motion and disabled controls stay
 * at rest and create no decorative response.
 */
@Composable
fun rememberNazoExpressivePressState(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): NazoExpressivePressState {
    val reduceMotion = rememberSystemReduceMotion()
    val animation = remember { Animatable(0f) }
    val state = remember(animation) { NazoExpressivePressState(animation) }

    LaunchedEffect(interactionSource, enabled, reduceMotion) {
        if (!enabled || reduceMotion) {
            animation.snapTo(0f)
            return@LaunchedEffect
        }

        val activePresses = mutableSetOf<PressInteraction.Press>()
        var animationJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    activePresses += interaction
                    animationJob?.cancel()
                    animationJob = launch {
                        animation.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = NazoExpressivePressInDurationMs,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                }

                is PressInteraction.Release -> {
                    activePresses -= interaction.press
                    animationJob?.cancel()
                    animationJob = launch {
                        if (activePresses.isNotEmpty()) {
                            animation.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = NazoExpressivePressInDurationMs,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        } else {
                            val completionStart = animation.value
                            val completionTarget = nazoQuickTapCompletionTarget(completionStart)
                            val completionMs = nazoQuickTapCompletionDurationMs(completionStart)
                            if (completionMs > 0) {
                                animation.animateTo(
                                    targetValue = completionTarget,
                                    animationSpec = tween(
                                        durationMillis = completionMs,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            animation.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = NazoExpressiveReleaseDurationMs,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    }
                }

                is PressInteraction.Cancel -> {
                    activePresses -= interaction.press
                    animationJob?.cancel()
                    animationJob = launch {
                        animation.animateTo(
                            targetValue = if (activePresses.isEmpty()) 0f else 1f,
                            animationSpec = tween(
                                durationMillis = if (activePresses.isEmpty()) {
                                    NazoExpressiveCancelDurationMs
                                } else {
                                    NazoExpressivePressInDurationMs
                                },
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                }

                // Focus, hover and drag interactions share some Material
                // sources; they must not interrupt an in-flight release.
                else -> Unit
            }
        }
    }

    return state
}
