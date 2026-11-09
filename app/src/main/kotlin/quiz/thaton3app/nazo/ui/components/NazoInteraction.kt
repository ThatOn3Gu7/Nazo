package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

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
