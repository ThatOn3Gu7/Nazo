package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal const val ButtonCardContinuityDurationMs = 320

/** Visual identity carried from a tapped control into its destination heading. */
internal data class ButtonCardContinuityRequest(
    val id: Long,
    val destinationKey: String,
    val sourceBounds: Rect,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

internal fun buttonCardContinuityCanStart(bounds: Rect, reduceMotion: Boolean): Boolean =
    !reduceMotion &&
        bounds.left.isFinite() && bounds.top.isFinite() &&
        bounds.right.isFinite() && bounds.bottom.isFinite() &&
        bounds.width >= 40f && bounds.height >= 40f

internal fun buttonCardContinuityRect(source: Rect, target: Rect, progress: Float): Rect {
    val t = progress.coerceIn(0f, 1f)
    fun between(start: Float, end: Float): Float = start + ((end - start) * t)
    return Rect(
        left = between(source.left, target.left),
        top = between(source.top, target.top),
        right = between(source.right, target.right),
        bottom = between(source.bottom, target.bottom),
    )
}

/**
 * Destination headings publish stable root bounds while staying visually hidden
 * beneath the travelling identity card. Their semantics remain available so a
 * screen reader can announce the newly opened destination immediately.
 */
internal fun Modifier.buttonCardContinuityTarget(
    hidden: Boolean,
    onBoundsChanged: (Rect) -> Unit,
): Modifier = this
    .onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (bounds.width > 0f && bounds.height > 0f) onBoundsChanged(bounds)
    }
    .graphicsLayer { alpha = if (hidden) 0f else 1f }

/**
 * One lightweight app-level card bridges a source control and the real heading
 * composed on the destination screen. It is deliberately not interactive and
 * carries no duplicate accessibility node.
 */
@Composable
internal fun ButtonCardContinuityOverlay(
    request: ButtonCardContinuityRequest,
    targetBounds: Rect?,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(request.id) { Animatable(0f) }
    var started by remember(request.id) { mutableStateOf(false) }
    val hasTarget = targetBounds != null

    LaunchedEffect(request.id, hasTarget) {
        if (hasTarget && !started) {
            started = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ButtonCardContinuityDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            onFinished(request.id)
        }
    }

    val target = targetBounds ?: request.sourceBounds
    val bounds = buttonCardContinuityRect(request.sourceBounds, target, progress.value)
    val density = LocalDensity.current
    val width = with(density) { bounds.width.coerceAtLeast(1f).toDp() }
    val height = with(density) { bounds.height.coerceAtLeast(1f).toDp() }
    val iconSize = 38.dp - (8.dp * progress.value)
    val corner = 19.dp - (7.dp * progress.value)
    val supportingAlpha = (1f - (progress.value * 1.45f)).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        Row(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                    )
                }
                .size(width = width, height = height)
                .shadow(
                    elevation = 7.dp * (1f - progress.value),
                    shape = RoundedCornerShape(corner),
                    clip = false,
                )
                .clip(RoundedCornerShape(corner))
                .background(NazoSurface)
                .border(
                    width = 1.dp,
                    color = NazoPrimary.copy(alpha = 0.22f + (0.10f * progress.value)),
                    shape = RoundedCornerShape(corner),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(12.dp - (3.dp * progress.value)))
                    .background(NazoPrimary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = request.icon,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(20.dp - (2.dp * progress.value)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingAlpha > 0.01f && height >= 52.dp) {
                    Text(
                        text = request.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = NazoTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = supportingAlpha },
                    )
                }
            }
        }
    }
}
