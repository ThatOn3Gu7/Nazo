package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.daily.DailyBonusChip
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.records.NewRecordBadge
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.CelebrationOverlay
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.NazoPrimaryButton
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.ShareResultCard
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.theme.*

@Composable
fun QuizCompleteScreen(
    score: Int,
    totalQuestions: Int,
    timeSpent: String,
    difficulty: String,
    bestPercent: Int = -1,
    isNewRecord: Boolean = false,
    dailyBonusXp: Int = 0,
    // "Survival" | "Blitz" | null (normal quiz). Adjusts the heading and the
    // personal-best caption: those modes track a COUNT (longest run / most
    // correct in 60s) in bestPercent's slot, not a percentage.
    modeLabel: String? = null,
    onPlayAnother: () -> Unit,
    onReviewAnswers: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val landscape = isLandscape()
    val accuracy = if (totalQuestions > 0) ((score.toFloat() / totalQuestions) * 100).toInt() else 0
    val isSuccess = accuracy >= 50

    // Staggered visibility states
    var showHeader by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }
    var triggerConfetti by remember { mutableStateOf(false) }

    // Number roll states
    val animatedAccuracy by animateIntAsState(
        targetValue = if (showCard) accuracy else 0,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "accuracy"
    )
    
    val animatedScore by animateIntAsState(
        targetValue = if (showCard) score else 0,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "score"
    )

    // Circular progress animation
    val progressAnim by animateFloatAsState(
        targetValue = if (showCard) accuracy.toFloat() / 100f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing, delayMillis = 200),
        label = "progress"
    )

    // Orchestrate entrances
    val context = LocalContext.current
    val celebrationStyle = remember { ThemePreferences(context).celebrationStyle }
    LaunchedEffect(Unit) {
        Sounds.complete(context) // opt-in, no-op when sounds are disabled
        delay(100)
        showHeader = true
        delay(150)
        showCard = true
        if (isSuccess) {
            triggerConfetti = true
            // Per-variant cue queued after the completion arpeggio (opt-in).
            Sounds.celebration(context, celebrationStyle)
        }
        delay(200)
        showStats = true
        delay(200)
        showButtons = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        // Landscape gives each pane its own scroll, so the page
                        // must not scroll as well.
                        if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState())
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Spacer(Modifier.height(if (landscape) 12.dp else 40.dp))

                // Header
                AnimatedVisibility(
                    visible = showHeader,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -40 } + fadeIn()
                ) {
                    Text(
                        text = when (modeLabel) {
                            "Survival" -> "Run over — you survived $score!"
                            "Blitz" -> "Time's up!"
                            else -> if (difficulty == "Practice") "Practice Complete" else "Quiz Complete"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(if (landscape) 12.dp else 24.dp))

                TwoPaneResults(
                    landscape = landscape,
                    ring = {
                // Big Trophy Score Card
                AnimatedVisibility(
                    visible = showCard,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    ScoreCardContent(
                        animatedAccuracy = animatedAccuracy,
                        accuracy = accuracy,
                        animatedScore = animatedScore,
                        totalQuestions = totalQuestions,
                        progressAnim = progressAnim,
                        isSuccess = isSuccess
                    )
                }

                // Personal best (Phase 4): the record badge pops in with a bouncy
                // scale once the card has landed; otherwise a quiet caption shows
                // the standing best for this difficulty.
                if (isNewRecord || bestPercent >= 0) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isNewRecord) {
                            NewRecordBadge()
                        } else {
                            Text(
                                text = when (modeLabel) {
                                    "Survival" -> "Longest run: $bestPercent correct"
                                    "Blitz" -> "Best blitz: $bestPercent correct"
                                    else -> "Personal best on $difficulty: $bestPercent%"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NazoTextSecondary
                            )
                        }
                    }
                }

                // Daily-challenge bonus (Phase 5): pops in a beat after the
                // record badge so the celebrations read as a sequence.
                if (dailyBonusXp > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DailyBonusChip(bonusXp = dailyBonusXp)
                    }
                }

                    },
                    stats = {
                // Stats Row
                AnimatedVisibility(
                    visible = showStats,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Time",
                            value = timeSpent,
                            motion = StatMotion.Tick,
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Accuracy",
                            value = "$accuracy%",
                            motion = StatMotion.Spin,
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "Difficulty",
                            value = difficulty,
                            motion = StatMotion.Rev,
                        )
                    }
                }

                Spacer(Modifier.height(if (landscape) 16.dp else 32.dp))

                // Action Buttons
                AnimatedVisibility(
                    visible = showButtons,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Column {
                        NazoPrimaryButton(
                            onClick = onPlayAnother,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play Another Quiz", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        NazoSecondaryButton(
                            onClick = onReviewAnswers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            muted = true,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Review Answers & Explanations", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Share the run as a themed image card (system share sheet).
                        NazoSecondaryButton(
                            onClick = {
                                Haptics.light(context)
                                ShareResultCard.share(
                                    context = context,
                                    heading = when (modeLabel) {
                                        "Survival" -> "Survival Run"
                                        "Blitz" -> "60-Second Blitz"
                                        else -> "Quiz Complete"
                                    },
                                    headline = if (modeLabel != null) "$score" else "$accuracy%",
                                    headlineCaption = if (modeLabel != null) "correct" else "accuracy",
                                    stats = listOf(
                                        "Score" to "$score / $totalQuestions",
                                        "Difficulty" to difficulty,
                                        "Time" to timeSpent,
                                    ),
                                    background = NazoBackground,
                                    surface = NazoSurface,
                                    primary = NazoPrimary,
                                    onPrimary = NazoOnPrimary,
                                    textPrimary = NazoTextPrimary,
                                    textSecondary = NazoTextSecondary,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            muted = true,
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Result", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                    },
                )
            }
        }

        // Celebration overlay — variant is a user preference (Appearance → Celebrations)
        if (triggerConfetti) {
            CelebrationOverlay(
                style = celebrationStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Results layout.
 *
 * Portrait is the original stack: ring, record badge, stats row, buttons.
 *
 * Landscape puts the score ring (with the record badge and daily bonus beneath
 * it, inside the same pane) on the left, and the three stat cards plus the
 * action buttons on the right — so nothing is stretched and the whole result is
 * visible without scrolling on most devices.
 */
@Composable
private fun TwoPaneResults(
    landscape: Boolean,
    ring: @Composable () -> Unit,
    stats: @Composable () -> Unit,
) {
    if (!landscape) {
        Column {
            ring()
            Spacer(Modifier.height(24.dp))
            stats()
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ring()
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            stats()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScoreCardContent(
    animatedAccuracy: Int,
    accuracy: Int,
    animatedScore: Int,
    totalQuestions: Int,
    progressAnim: Float,
    isSuccess: Boolean
) {
    // Continuous floating animation for trophy (scoped here so it doesn't recompose the whole screen)
    val floatInfinite = rememberInfiniteTransition(label = "float")
    val floatOffset by floatInfinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val message = when {
        accuracy >= 90 -> "Impressive run — you're nearly an Otaku Master on this arc."
        accuracy >= 70 -> "Great job! Your anime knowledge is sharp."
        accuracy >= 50 -> "Good effort! A little more training and you'll be unstoppable."
        else -> "Keep watching, keep learning. You'll get it next time!"
    }

    val icon = if (isSuccess) Icons.Outlined.EmojiEvents else Icons.Outlined.FitnessCenter

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NazoSurface, NazoSurfaceVariant.copy(alpha = 0.3f))
                )
            )
            .border(
                width = 1.dp,
                color = NazoPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Progress Ring & Trophy
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background track
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = NazoPrimary.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Animated sweeping progress arc
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NazoPrimary.copy(alpha = 0.4f),
                            NazoPrimary,
                            NazoPrimary.copy(alpha = 0.8f),
                            NazoPrimary.copy(alpha = 0.4f) // FIXED: Re-added the start color at the end to make the gradient loop seamlessly at 3 o'clock!
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progressAnim,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Floating inner trophy
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .offset(y = floatOffset.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NazoPrimary, NazoPrimary.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(38.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Animated Numbers
        Text(
            text = "$animatedAccuracy%",
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp),
            color = NazoPrimary,
            fontWeight = FontWeight.ExtraBold
        )

        Surface(
            color = NazoPrimary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        ) {
            Text(
                text = "$animatedScore / $totalQuestions Correct",
                style = MaterialTheme.typography.titleMedium,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/** Which tap animation a [StatCard] icon plays. */
private enum class StatMotion { Tick, Spin, Rev }

/**
 * A result statistic whose icon animates when tapped.
 *
 * The icons are drawn with [Canvas] rather than taken from the Material set,
 * because each animation has to move ONE PART of the icon -- the stopwatch
 * needle, the plotter arm, the gauge needle -- while the body stays put.
 * A Material icon is a single static path, so rotating it spins the whole
 * thing, bezel and all, which is not what any of these should do.
 *
 * Every motion is one-shot and driven by a single `Animatable`. The value is
 * read inside the Canvas draw lambda, so a tap redraws only this 20dp surface
 * and never recomposes the card, the screen, or the entrance/score animations.
 */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    motion: StatMotion,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 0f = at rest, 1f = animation complete.
    val progress = remember { Animatable(0f) }
    val iconTint = NazoTextSecondary

    fun play() {
        // Restarting mid-flight would jump; let the current pass finish.
        if (progress.isRunning) return
        scope.launch {
            Haptics.light(context)
            progress.snapTo(0f)
            val duration = when (motion) {
                StatMotion.Tick -> 900
                StatMotion.Spin -> 1500
                StatMotion.Rev -> 900
            }
            progress.animateTo(1f, tween(duration, easing = LinearEasing))
            progress.snapTo(0f)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoTextSecondary.copy(alpha = 0.08f))
                // No ripple: the icon's own motion is the feedback, and a
                // ripple on a 40dp circle swamps it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { play() },
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val t = progress.value
                when (motion) {
                    StatMotion.Tick -> drawStopwatch(t, iconTint)
                    StatMotion.Spin -> drawPlotter(t, iconTint)
                    StatMotion.Rev -> drawGauge(t, iconTint)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = NazoTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}


// ---------------------------------------------------------------------------
// Stat icons, drawn by hand so one moving part can animate on its own.
// ---------------------------------------------------------------------------

/**
 * Stopwatch whose needle sweeps while the case buzzes.
 *
 * The body shakes on a fast decaying sine -- the "buzzing alarm clock" read --
 * while the needle sweeps a full turn. Shake amplitude decays so it rings hard
 * and settles, rather than vibrating forever.
 */
private fun DrawScope.drawStopwatch(t: Float, tint: Color) {
    val stroke = size.minDimension * 0.09f
    val decay = (1f - t) * (1f - t)
    // ~7 shakes across the play, dying out towards the end.
    val shake = size.minDimension * 0.05f * decay * sin(t * 14f * PI).toFloat()
    val cx = size.width / 2f + shake
    val cy = size.height / 2f + size.height * 0.06f
    val radius = size.minDimension * 0.36f

    // Case.
    drawCircle(
        color = tint,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // Crown and the two little side lugs of a wind-up alarm.
    drawLine(
        color = tint,
        start = Offset(cx, cy - radius - stroke * 0.2f),
        end = Offset(cx, cy - radius - stroke * 1.5f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    // Needle: one full sweep, eased so it starts fast and settles.
    val sweep = (1f - (1f - t) * (1f - t)) * 360f
    val angle = Math.toRadians((sweep - 90f).toDouble())
    val needle = radius * 0.68f
    drawLine(
        color = tint,
        start = Offset(cx, cy),
        end = Offset(
            cx + needle * kotlin.math.cos(angle).toFloat(),
            cy + needle * sin(angle).toFloat(),
        ),
        strokeWidth = stroke * 0.9f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = tint, radius = stroke * 0.55f, center = Offset(cx, cy))
}

/**
 * Sand-plotter: an arm sweeps out and traces a spiral, then retracts.
 *
 * First half draws the pattern as the arm swings out; second half pulls the arm
 * back to its start while the drawing stays. The trail is built as a path of
 * points along a spiral so the line genuinely follows the needle tip.
 */
private fun DrawScope.drawPlotter(t: Float, tint: Color) {
    val stroke = size.minDimension * 0.07f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.42f

    // Tray.
    drawCircle(
        color = tint.copy(alpha = 0.45f),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = stroke * 0.8f),
    )

    // Outbound for the first 65%, retract over the rest.
    val draw = (t / 0.65f).coerceAtMost(1f)
    val retract = ((t - 0.65f) / 0.35f).coerceIn(0f, 1f)
    val turns = 2.2f

    if (draw > 0f) {
        val path = Path()
        val steps = 72
        val upTo = (steps * draw).toInt().coerceAtLeast(1)
        for (i in 0..upTo) {
            val f = i / steps.toFloat()
            val a = Math.toRadians((f * turns * 360f - 90f).toDouble())
            val r = radius * 0.82f * f
            val x = cx + r * kotlin.math.cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round),
        )
    }

    // Arm: tracks the tip while drawing, then swings back to centre.
    val tipF = draw * (1f - retract)
    val armAngle = Math.toRadians((tipF * turns * 360f - 90f).toDouble())
    val armLen = radius * 0.82f * tipF
    val tipX = cx + armLen * kotlin.math.cos(armAngle).toFloat()
    val tipY = cy + armLen * sin(armAngle).toFloat()
    drawLine(
        color = tint,
        start = Offset(cx, cy),
        end = Offset(tipX, tipY),
        strokeWidth = stroke * 0.8f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = tint, radius = stroke * 0.5f, center = Offset(cx, cy))
    if (armLen > 0.01f) {
        drawCircle(color = tint, radius = stroke * 0.42f, center = Offset(tipX, tipY))
    }
}

/**
 * Rev counter: the needle blips up the dial and drops back.
 *
 * Two throttle blips, the second smaller, each snapping up quickly and falling
 * back more slowly -- how a real tacho behaves, since the engine picks up
 * faster than it spins down. Only the needle moves; the dial and its ticks
 * stay fixed.
 */
private fun DrawScope.drawGauge(t: Float, tint: Color) {
    val stroke = size.minDimension * 0.08f
    val cx = size.width / 2f
    val cy = size.height * 0.62f
    val radius = size.minDimension * 0.40f

    // Dial arc: 180deg sweep, like a speedometer.
    drawArc(
        color = tint.copy(alpha = 0.5f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round),
    )
    // Three ticks so the needle has something to read against.
    for (i in 0..2) {
        val a = Math.toRadians((180f + i * 90f).toDouble())
        val outer = radius
        val inner = radius * 0.78f
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = Offset(
                cx + inner * kotlin.math.cos(a).toFloat(),
                cy + inner * sin(a).toFloat(),
            ),
            end = Offset(
                cx + outer * kotlin.math.cos(a).toFloat(),
                cy + outer * sin(a).toFloat(),
            ),
            strokeWidth = stroke * 0.5f,
            cap = StrokeCap.Round,
        )
    }

    // Two blips: fast attack, slower decay.
    fun blip(local: Float, peak: Float): Float = when {
        local <= 0f || local >= 1f -> 0f
        local < 0.28f -> peak * (local / 0.28f)
        else -> peak * (1f - (local - 0.28f) / 0.72f)
    }
    val rev = blip(t / 0.5f, 1f) + blip((t - 0.5f) / 0.5f, 0.62f)

    // Rest at the left stop, sweeping right as revs climb.
    val angle = Math.toRadians((180f + rev.coerceIn(0f, 1f) * 170f).toDouble())
    val needle = radius * 0.86f
    drawLine(
        color = tint,
        start = Offset(cx, cy),
        end = Offset(
            cx + needle * kotlin.math.cos(angle).toFloat(),
            cy + needle * sin(angle).toFloat(),
        ),
        strokeWidth = stroke * 0.85f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = tint, radius = stroke * 0.6f, center = Offset(cx, cy))
}

