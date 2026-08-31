package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import quiz.thaton3app.nazo.records.NewRecordBadge
import quiz.thaton3app.nazo.ui.theme.*
import kotlin.random.Random

@Composable
fun QuizCompleteScreen(
    score: Int,
    totalQuestions: Int,
    timeSpent: String,
    difficulty: String,
    bestPercent: Int = -1,
    isNewRecord: Boolean = false,
    onPlayAnother: () -> Unit,
    onReviewAnswers: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
    LaunchedEffect(Unit) {
        delay(100)
        showHeader = true
        delay(150)
        showCard = true
        if (isSuccess) {
            triggerConfetti = true
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
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Spacer(Modifier.height(40.dp))

                // Header
                AnimatedVisibility(
                    visible = showHeader,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -40 } + fadeIn()
                ) {
                    Text(
                        text = "Quiz Complete",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(24.dp))

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
                                text = "Personal best on $difficulty: $bestPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = NazoTextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Stats Row
                AnimatedVisibility(
                    visible = showStats,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Timer, title = "Time", value = timeSpent)
                        StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.TrackChanges, title = "Accuracy", value = "$accuracy%")
                        StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Speed, title = "Difficulty", value = difficulty)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Action Buttons
                AnimatedVisibility(
                    visible = showButtons,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Column {
                        Button(
                            onClick = onPlayAnother,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NazoPrimary),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play Another Quiz", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = onReviewAnswers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NazoSurfaceVariant),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Review Answers & Explanations", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Confetti Overlay
        if (triggerConfetti) {
            ConfettiBurst(modifier = Modifier.fillMaxSize())
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

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String) {
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
                .background(NazoTextSecondary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = NazoTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

// --- Physics-based Confetti Engine ---

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var color: Color,
    var size: Float
)

@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val particles = remember { mutableStateListOf<Particle>() }
    val frame = remember { mutableStateOf(0) }

    // Standard appealing confetti colors
    val colors = listOf(
        Color(0xFFFFC107), Color(0xFFE91E63), Color(0xFF00BCD4),
        Color(0xFF8BC34A), Color(0xFF9C27B0), Color(0xFFFF5722)
    )

    LaunchedEffect(Unit) {
        // Initialize particles
        val newParticles = List(70) {
            Particle(
                x = 0f, y = 0f, // Will be set relative to canvas center
                vx = Random.nextFloat() * 1200 - 600,
                vy = -(Random.nextFloat() * 1000 + 400),
                color = colors.random(),
                size = Random.nextFloat() * 20f + 10f
            )
        }
        particles.addAll(newParticles)

        // Physics loop
        var lastFrameTime = withFrameNanos { it }
        while (isActive && particles.isNotEmpty()) {
            withFrameNanos { frameTime ->
                val delta = (frameTime - lastFrameTime) / 1_000_000_000f // Delta time in seconds
                lastFrameTime = frameTime

                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.vy += 1800f * delta // Gravity
                    p.x += p.vx * delta
                    p.y += p.vy * delta

                    // Remove if fallen off screen deeply
                    if (p.y > 3000f) {
                        iterator.remove()
                    }
                }
            }
            frame.value++ // force Canvas redraw each frame
        }
    }

    Canvas(modifier = modifier) {
        // FIXED: By observing `frame.value` INSIDE the draw phase, Compose will correctly invalidate 
        // the draw layer and render the explosion instead of a static un-updated dot.
        @Suppress("UNUSED_VARIABLE")
        val currentFrame = frame.value 
        
        val centerX = size.width / 2
        val centerY = size.height / 3 // Erupt from upper middle

        particles.forEach { p ->
            drawRect(
                color = p.color,
                topLeft = Offset(centerX + p.x, centerY + p.y),
                size = Size(p.size, p.size * 0.6f),
                alpha = 1f - (p.y / size.height).coerceIn(0f, 1f) * 0.5f // Fade slightly as they fall
            )
        }
    }
}

