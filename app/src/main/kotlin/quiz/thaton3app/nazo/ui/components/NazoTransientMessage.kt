package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** The direction in which a transient message gives its occupied space back. */
enum class NazoMessageAxis { Vertical, Horizontal }

internal fun <T : Any> retainLastTransientValue(current: T?, retained: T?): T? =
    current ?: retained

/**
 * Shows nullable feedback without clearing its contents before the exit can run.
 *
 * Passing `value.orEmpty()` directly to [AnimatedVisibility] makes the message
 * disappear on the same frame that `visible` becomes false. The visibility
 * container then animates an empty child, which is why surrounding controls
 * appear to snap despite having an exit transition. This host keeps the last
 * non-null value mounted until fade + size collapse have both completed.
 */
@Composable
fun <T : Any> NazoTransientMessage(
    value: T?,
    axis: NazoMessageAxis = NazoMessageAxis.Vertical,
    modifier: Modifier = Modifier,
    label: String = "nazoTransientMessage",
    content: @Composable (T) -> Unit,
) {
    var retainedValue by remember { mutableStateOf(value) }
    SideEffect {
        retainedValue = retainLastTransientValue(value, retainedValue)
    }
    val visibleValue = retainLastTransientValue(value, retainedValue)
    val reduceMotion = rememberSystemReduceMotion()

    val enter = if (reduceMotion) {
        fadeIn(tween(90))
    } else {
        fadeIn(tween(180, easing = FastOutSlowInEasing)) + when (axis) {
            NazoMessageAxis.Vertical -> expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            )
            NazoMessageAxis.Horizontal -> expandHorizontally(
                expandFrom = Alignment.Start,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            )
        }
    }
    val exit = if (reduceMotion) {
        fadeOut(tween(70))
    } else {
        fadeOut(tween(160, easing = FastOutSlowInEasing)) + when (axis) {
            NazoMessageAxis.Vertical -> shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            )
            NazoMessageAxis.Horizontal -> shrinkHorizontally(
                shrinkTowards = Alignment.Start,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            )
        }
    }

    AnimatedVisibility(
        visible = value != null,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
    ) {
        if (visibleValue != null) content(visibleValue)
    }
}
