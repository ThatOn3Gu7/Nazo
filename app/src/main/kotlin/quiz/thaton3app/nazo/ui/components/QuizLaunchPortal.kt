package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
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

internal enum class QuizLaunchHandoffState { Idle, AwaitingQuestion, RevealingQuestion }
internal enum class QuizLaunchHandoffEvent {
    PortalStarted,
    LoadingReached,
    QuestionReached,
    RevealFinished,
    Cancelled,
}

/** Keeps generated launches alive across Loading without affecting direct launches. */
internal fun nextQuizLaunchHandoff(
    state: QuizLaunchHandoffState,
    event: QuizLaunchHandoffEvent,
): QuizLaunchHandoffState = when (event) {
    QuizLaunchHandoffEvent.PortalStarted,
    QuizLaunchHandoffEvent.RevealFinished,
    QuizLaunchHandoffEvent.Cancelled -> QuizLaunchHandoffState.Idle

    QuizLaunchHandoffEvent.LoadingReached -> QuizLaunchHandoffState.AwaitingQuestion
    QuizLaunchHandoffEvent.QuestionReached -> if (state == QuizLaunchHandoffState.AwaitingQuestion) {
        QuizLaunchHandoffState.RevealingQuestion
    } else {
        state
    }
}

/**
 * One app-level portal, drawn over the one live navigation tree. The Home
 * button grows to cover the viewport; only once it is fully covered does the
 * host start generation/navigation underneath it. It then folds into the
 * centre to reveal Loading, a dialog, or an already-ready first question.
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
        // Give the newly selected destination one frame plus a small settling
        // beat before uncovering it. This hides the ordinary navigation swap.
        delay(90)
        progress.animateTo(
            targetValue = 2f,
            animationSpec = tween(durationMillis = 380, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    val blocker = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Opening quiz" }
            // The launch action is already claimed. Block accidental taps on
            // Home/Loading while the cover is moving without adding a ripple.
            .clickable(interactionSource = blocker, indication = null) {},
    ) {
        val density = LocalDensity.current
        val frame = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = with(density) { maxWidth.toPx() },
            viewportHeight = with(density) { maxHeight.toPx() },
            terminalDiameter = with(density) { 58.dp.toPx() },
            progress = progress.value,
            initialCornerRadius = with(density) { 18.dp.toPx() },
        )
        val expansion = progress.value.coerceIn(0f, 1f)
        val retreat = (progress.value - 1f).coerceIn(0f, 1f)
        val portalMessageAlpha = ((expansion - 0.24f) / 0.28f).coerceIn(0f, 1f) *
            (1f - retreat * 1.2f).coerceIn(0f, 1f)
        val sourceLabelAlpha = (1f - expansion / 0.38f).coerceIn(0f, 1f)
        val firstFramesAlpha = (progress.value / 0.05f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(
                    width = with(density) { frame.width.toDp() },
                    height = with(density) { frame.height.toDp() },
                )
                .graphicsLayer { alpha = frame.alpha * firstFramesAlpha }
                .clip(RoundedCornerShape(with(density) { frame.cornerRadius.toDp() }))
                .background(NazoPrimary)
                .drawBehind {
                    if (portalMessageAlpha > 0f) {
                        val radius = size.minDimension * (0.14f + expansion * 0.16f)
                        drawCircle(
                            color = NazoOnPrimary.copy(alpha = 0.13f * portalMessageAlpha),
                            radius = radius,
                            style = Stroke(width = max(1f, size.minDimension * 0.006f)),
                        )
                        drawCircle(
                            color = NazoOnPrimary.copy(alpha = 0.07f * portalMessageAlpha),
                            radius = radius * 1.42f,
                            style = Stroke(width = max(1f, size.minDimension * 0.004f)),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = sourceLabel,
                color = NazoOnPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = sourceLabelAlpha },
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = portalMessageAlpha
                    val messageScale = 0.9f + 0.1f * portalMessageAlpha
                    scaleX = messageScale
                    scaleY = messageScale
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(NazoOnPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = NazoOnPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Opening your challenge",
                    color = NazoOnPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Setting the first question in motion",
                    color = NazoOnPrimary.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Generated quizzes arrive after the first portal has already handed off to
 * Loading. Reveal that eventual question out from Loading rather than treating
 * it as an unrelated navigation push.
 */
@Composable
internal fun QuizLaunchDestinationReveal(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reveal = remember(active) { Animatable(if (active) 0f else 1f) }
    var blocking by remember(active) { mutableStateOf(active) }

    LaunchedEffect(active) {
        if (active) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
            blocking = false
            onFinished()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (!active || reveal.value >= 1f) {
                        drawContent()
                    } else {
                        val startRadius = 28.dp.toPx()
                        val maxRadius = sqrt(size.width * size.width + size.height * size.height) / 2f
                        val radius = mix(startRadius, maxRadius, reveal.value)
                        val path = Path().apply {
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            addOval(
                                Rect(
                                    left = centre.x - radius,
                                    top = centre.y - radius,
                                    right = centre.x + radius,
                                    bottom = centre.y + radius,
                                )
                            )
                        }
                        clipPath(path) { this@drawWithContent.drawContent() }
                    }
                },
        ) {
            content()
        }

        if (blocking) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = interaction, indication = null) {},
            )
        }
    }
}
