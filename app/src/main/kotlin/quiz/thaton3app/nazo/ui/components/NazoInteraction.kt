package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Nazo's shared physical response for buttons, navigation items and selectable
 * cards. It is deliberately small: the ripple still communicates the exact
 * touch location while this scale makes the whole control feel connected to
 * the finger. The transform is a graphics layer, so it never triggers layout.
 */
internal const val NazoPressedScale = 0.98f
internal const val NazoPressInDurationMs = 70
internal const val NazoPressOutDurationMs = 140

/** Pure policy kept separate so reduced-motion and disabled behaviour stay testable. */
internal fun nazoPressTargetScale(
    pressed: Boolean,
    enabled: Boolean,
    reduceMotion: Boolean,
    pressedScale: Float = NazoPressedScale,
): Float = if (pressed && enabled && !reduceMotion) {
    pressedScale.coerceIn(0.9f, 1f)
} else {
    1f
}

/**
 * Applies the shared press transform while leaving click semantics, indication
 * and haptics to the control itself. Callers pass the same interaction source
 * to their Button/clickable/toggleable modifier.
 */
@Composable
fun Modifier.nazoPressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = NazoPressedScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberSystemReduceMotion()
    val target = nazoPressTargetScale(
        pressed = pressed,
        enabled = enabled,
        reduceMotion = reduceMotion,
        pressedScale = pressedScale,
    )
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = if (pressed) NazoPressInDurationMs else NazoPressOutDurationMs,
                easing = FastOutSlowInEasing,
            )
        },
        label = "nazoPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ---------------------------------------------------------------------------
// Expressive press prototype
// ---------------------------------------------------------------------------

internal const val NazoExpressivePressInDurationMs = 90
internal const val NazoExpressiveQuickTapCompletionMs = 45

internal const val NazoPillRestCornerPercent = 50f
internal const val NazoPillPressedCornerPercent = 24f

/**
 * Pure corner policy for the Practice-button prototype. Fifty percent is its
 * original pill silhouette; the held state becomes noticeably more rectangular
 * without changing layout, scale, content, elevation, or colour.
 */
internal fun nazoExpressiveCornerPercent(progress: Float): Float {
    val fraction = progress.coerceIn(0f, 1f)
    return NazoPillRestCornerPercent +
        (NazoPillPressedCornerPercent - NazoPillRestCornerPercent) * fraction
}

/**
 * A very short tap may release before the press-in tween reaches its end. Give
 * only the missing portion of the morph a tiny completion beat so the response
 * stays visible, without delaying the click callback itself.
 */
internal fun nazoQuickTapCompletionDurationMs(progress: Float): Int {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    if (remaining <= 0f) return 0
    return (NazoExpressiveQuickTapCompletionMs * remaining)
        .roundToInt()
        .coerceAtLeast(1)
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
 * Observes the same interaction source already owned by clickable/Button.
 *
 * Press holds at the fully morphed shape until release. A real release completes
 * a too-fast tap before springing home; cancellation (for example when a parent
 * scroll takes the gesture) returns immediately instead, so this observer never
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
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    activePresses += interaction
                    animation.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = NazoExpressivePressInDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }

                is PressInteraction.Release -> {
                    activePresses -= interaction.press
                    if (activePresses.isNotEmpty()) {
                        animation.animateTo(1f)
                    } else {
                        val completionMs = nazoQuickTapCompletionDurationMs(animation.value)
                        if (completionMs > 0) {
                            animation.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = completionMs,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                        animation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }

                is PressInteraction.Cancel -> {
                    activePresses -= interaction.press
                    animation.animateTo(
                        targetValue = if (activePresses.isEmpty()) 0f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }

                else -> Unit
            }
        }
    }

    return state
}
