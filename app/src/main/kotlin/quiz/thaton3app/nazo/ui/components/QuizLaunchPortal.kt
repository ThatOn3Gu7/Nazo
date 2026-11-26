package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary

/** Root-relative bounds captured from the Home start button. */
data class QuizLaunchOrigin(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        fun from(rect: Rect) = QuizLaunchOrigin(rect.left, rect.top, rect.right, rect.bottom)
    }
}

/** A single frame of the button -> viewport -> centre hand-off. */
internal data class QuizLaunchPortalFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val alpha: Float,
)

private fun mix(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction.coerceIn(0f, 1f)

/**
 * Pure geometry for the launch portal. Progress 0..1 grows the button to cover
 * the viewport; 1..2 folds that cover into the destination's centre.
 */
internal fun quizLaunchPortalFrame(
    origin: QuizLaunchOrigin,
    viewportWidth: Float,
    viewportHeight: Float,
    terminalDiameter: Float,
    progress: Float,
    initialCornerRadius: Float,
): QuizLaunchPortalFrame {
    val safeWidth = viewportWidth.coerceAtLeast(1f)
    val safeHeight = viewportHeight.coerceAtLeast(1f)
    val fallbackWidth = safeWidth * 0.72f
    val originWidth = (origin.right - origin.left).takeIf { it > 1f } ?: fallbackWidth
    val originHeight = (origin.bottom - origin.top).takeIf { it > 1f } ?: 58f
    val originLeft = origin.left.takeIf { it.isFinite() }
        ?.coerceIn(0f, (safeWidth - 1f).coerceAtLeast(0f))
        ?: (safeWidth - originWidth) / 2f
    val originTop = origin.top.takeIf { it.isFinite() }
        ?.coerceIn(0f, (safeHeight - 1f).coerceAtLeast(0f))
        ?: (safeHeight - originHeight) / 2f
    val startWidth = originWidth.coerceIn(1f, safeWidth - originLeft)
    val startHeight = originHeight.coerceIn(1f, safeHeight - originTop)
    val p = progress.coerceIn(0f, 2f)

    if (p <= 1f) {
        return QuizLaunchPortalFrame(
            left = mix(originLeft, 0f, p),
            top = mix(originTop, 0f, p),
            width = mix(startWidth, safeWidth, p),
            height = mix(startHeight, safeHeight, p),
            cornerRadius = mix(
                initialCornerRadius.coerceAtMost(startHeight / 2f),
                0f,
                p,
            ),
            alpha = 1f,
        )
    }

    val retreat = p - 1f
    val diameter = terminalDiameter.coerceIn(1f, minOf(safeWidth, safeHeight))
    val endLeft = (safeWidth - diameter) / 2f
    val endTop = (safeHeight - diameter) / 2f
    return QuizLaunchPortalFrame(
        left = mix(0f, endLeft, retreat),
        top = mix(0f, endTop, retreat),
        width = mix(safeWidth, diameter, retreat),
        height = mix(safeHeight, diameter, retreat),
        cornerRadius = mix(0f, diameter / 2f, retreat),
        alpha = if (retreat < 0.78f) 1f else ((1f - retreat) / 0.22f).coerceIn(0f, 1f),
    )
}

internal const val QuizLaunchPortalDwellMillis = 700L
private const val QuizLaunchRingPeriodMillis = 2_400
private const val QuizLaunchRingSamples = 72

internal fun quizLaunchPortalMessageAlpha(progress: Float): Float {
    val expansion = progress.coerceIn(0f, 1f)
    val retreat = (progress - 1f).coerceIn(0f, 1f)
    return ((expansion - 0.24f) / 0.28f).coerceIn(0f, 1f) *
        (1f - retreat * 1.2f).coerceIn(0f, 1f)
}

/** One point on a breathing, rotating closed wave. A full phase returns home. */
internal fun quizLaunchRingRadius(
    baseRadius: Float,
    waveAmplitude: Float,
    waveCount: Int,
    angleRadians: Float,
    phaseFraction: Float,
    direction: Int,
    bounceOffsetRadians: Float = 0f,
): Float {
    val phase = phaseFraction.coerceIn(0f, 1f) * 2f * PI.toFloat()
    val spin = phase * direction.coerceIn(-1, 1)
    val bounce = 1f + 0.055f * sin(phase * 2f + bounceOffsetRadians)
    val snake = waveAmplitude * (
        sin(waveCount.coerceAtLeast(1) * angleRadians - spin) +
            0.28f * sin((waveCount.coerceAtLeast(1) + 2) * angleRadians + spin * 2f)
    )
    return (baseRadius * bounce + snake).coerceAtLeast(0f)
}

private fun DrawScope.drawQuizLaunchRing(
    path: Path,
    centre: Offset,
    baseRadius: Float,
    amplitude: Float,
    waveCount: Int,
    phaseFraction: Float,
    direction: Int,
    bounceOffsetRadians: Float,
    color: Color,
    strokeWidth: Float,
) {
    path.reset()
    for (sample in 0..QuizLaunchRingSamples) {
        val angle = sample.toFloat() / QuizLaunchRingSamples * 2f * PI.toFloat()
        val radius = quizLaunchRingRadius(
            baseRadius = baseRadius,
            waveAmplitude = amplitude,
            waveCount = waveCount,
            angleRadians = angle,
            phaseFraction = phaseFraction,
            direction = direction,
            bounceOffsetRadians = bounceOffsetRadians,
        )
        val point = Offset(
            x = centre.x + cos(angle) * radius,
            y = centre.y + sin(angle) * radius,
        )
        if (sample == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

/**
 * One app-level portal, drawn over the one live navigation tree. The Home
 * button grows to cover the viewport; only once it is fully covered does the
 * host open the already-ready local challenge underneath it. It then holds
 * long enough to read before folding into the centre to reveal the question.
 */
@Composable
internal fun QuizLaunchPortal(
    requestId: Long,
    origin: QuizLaunchOrigin,
    sourceLabel: String,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(requestId) { Animatable(0f) }
    var covered by remember(requestId) { mutableStateOf(false) }

    LaunchedEffect(requestId) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        )
        if (!covered) {
            covered = true
            onCovered()
        }
        // Keep the fully-open message readable while the question builds and
        // settles behind an opaque, fixed-size layer. This real-time dwell is
        // not shortened by a device's animator-duration scale, so it both
        // protects readability and keeps destination work away from collapse.
        // A true Remove animations setting bypasses the portal entirely.
        delay(QuizLaunchPortalDwellMillis)
        progress.animateTo(
            targetValue = 2f,
            animationSpec = tween(durationMillis = 380, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    val ringTransition = rememberInfiniteTransition(label = "quizLaunchRings")
    val ringPhase = ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(QuizLaunchRingPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quizLaunchRingPhase",
    )
    val blocker = remember { MutableInteractionSource() }
    val shapePath = remember { Path() }
    val innerRingPath = remember { Path() }
    val outerRingPath = remember { Path() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Opening quiz" }
            // The launch action is already claimed. Block accidental taps on
            // Home/Quiz while the cover is moving without adding a ripple.
            .clickable(interactionSource = blocker, indication = null) {},
    ) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }
        val terminalDiameter = with(density) { 58.dp.toPx() }
        val initialCornerRadius = with(density) { 18.dp.toPx() }
        val primary = NazoPrimary
        val onPrimary = NazoOnPrimary

        // Keep one full-viewport layout node throughout the transition. The old
        // version resized and repositioned a composable every frame, forcing
        // measure/layout work exactly while Quiz was entering underneath. This
        // draw-only shape and graphics-layer content leave layout completely
        // stable during both expansion and collapse.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress.value
                    val frame = quizLaunchPortalFrame(
                        origin = origin,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        terminalDiameter = terminalDiameter,
                        progress = p,
                        initialCornerRadius = initialCornerRadius,
                    )
                    val firstFramesAlpha = (p / 0.05f).coerceIn(0f, 1f)
                    val shapeAlpha = frame.alpha * firstFramesAlpha
                    val portalMessageAlpha = quizLaunchPortalMessageAlpha(p) * shapeAlpha

                    shapePath.reset()
                    shapePath.addRoundRect(
                        RoundRect(
                            left = frame.left,
                            top = frame.top,
                            right = frame.left + frame.width,
                            bottom = frame.top + frame.height,
                            cornerRadius = CornerRadius(frame.cornerRadius),
                        )
                    )
                    drawPath(path = shapePath, color = primary.copy(alpha = shapeAlpha))

                    clipPath(shapePath) {
                        if (portalMessageAlpha > 0f) {
                            val centre = Offset(
                                x = frame.left + frame.width / 2f,
                                y = frame.top + frame.height / 2f,
                            )
                            val portalSize = minOf(frame.width, frame.height)
                            val phase = ringPhase.value
                            drawQuizLaunchRing(
                                path = innerRingPath,
                                centre = centre,
                                baseRadius = portalSize * 0.17f,
                                amplitude = portalSize * 0.018f,
                                waveCount = 5,
                                phaseFraction = phase,
                                direction = 1,
                                bounceOffsetRadians = 0f,
                                color = onPrimary.copy(alpha = 0.16f * portalMessageAlpha),
                                strokeWidth = max(1f, 1.6.dp.toPx()),
                            )
                            drawQuizLaunchRing(
                                path = outerRingPath,
                                centre = centre,
                                baseRadius = portalSize * 0.29f,
                                amplitude = portalSize * 0.022f,
                                waveCount = 6,
                                phaseFraction = phase,
                                direction = -1,
                                bounceOffsetRadians = PI.toFloat() * 0.6f,
                                color = onPrimary.copy(alpha = 0.09f * portalMessageAlpha),
                                strokeWidth = max(1f, 1.2.dp.toPx()),
                            )
                        }
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sourceLabel,
                color = onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val p = progress.value
                    val frame = quizLaunchPortalFrame(
                        origin = origin,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        terminalDiameter = terminalDiameter,
                        progress = p,
                        initialCornerRadius = initialCornerRadius,
                    )
                    alpha = (1f - p.coerceIn(0f, 1f) / 0.38f).coerceIn(0f, 1f) *
                        frame.alpha * (p / 0.05f).coerceIn(0f, 1f)
                    translationX = frame.left + frame.width / 2f - viewportWidth / 2f
                    translationY = frame.top + frame.height / 2f - viewportHeight / 2f
                },
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val messageAlpha = quizLaunchPortalMessageAlpha(progress.value)
                    alpha = messageAlpha
                    val messageScale = 0.9f + 0.1f * messageAlpha
                    scaleX = messageScale
                    scaleY = messageScale
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(onPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Opening your challenge",
                    color = onPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Setting the first question in motion",
                    color = onPrimary.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
