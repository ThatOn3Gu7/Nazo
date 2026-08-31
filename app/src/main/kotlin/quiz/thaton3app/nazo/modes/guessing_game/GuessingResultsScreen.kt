package quiz.thaton3app.nazo.modes.guessing_game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.*

/**
 * End-of-game summary for the guessing game: total time-decay score, rounds
 * solved, best round, and a per-round breakdown. Same visual language as the
 * quiz's complete screen (staggered entrances, animated score + progress ring).
 */
@Composable
fun GuessingResultsScreen(
    score: Int,
    results: List<GuessRoundResult>,
    topic: String,
    difficulty: String,
    onPlayAgain: () -> Unit,
    onHomeClick: () -> Unit,
) {
    val context = LocalContext.current
    val total = results.size
    val solved = results.count { it.correct }
    val eliminated = total > 0 && !results.last().correct
    val cleared = total > 0 && solved == total
    val accuracy = if (total > 0) solved.toFloat() / total * 100f else 0f

    var showHeader by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showRounds by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    val animatedScore by animateIntAsState(
        targetValue = if (showCard) score else 0,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "guessScore"
    )
    val progressAnim by animateFloatAsState(
        targetValue = if (showCard) accuracy / 100f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing, delayMillis = 150),
        label = "guessProgress"
    )

    LaunchedEffect(Unit) {
        delay(100)
        showHeader = true
        delay(150)
        showCard = true
        delay(200)
        showRounds = true
        delay(200)
        showButtons = true
    }

    val heading = when {
        cleared -> "All rounds cleared!"
        eliminated -> "Eliminated!"
        else -> "Game over"
    }
    val headingColor = when {
        cleared -> NazoSuccess
        eliminated -> NazoError
        else -> NazoTextPrimary
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

                AnimatedVisibility(
                    visible = showHeader,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -40 } + fadeIn()
                ) {
                    Column {
                        Text(
                            text = heading,
                            style = MaterialTheme.typography.headlineMedium,
                            color = headingColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Topic: ${topic.ifBlank { "Any popular anime" }} • $difficulty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = showCard,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    ScoreCard(
                        animatedScore = animatedScore,
                        progressAnim = progressAnim,
                        solved = solved,
                        total = total,
                        eliminated = eliminated,
                        cleared = cleared,
                    )
                }

                Spacer(Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = showRounds,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Column {
                        Text(
                            text = "ROUNDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = NazoTextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        results.forEach { r ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(NazoSurface)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (r.correct) NazoSuccess else NazoError),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (r.correct) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Round ${r.round}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NazoTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = r.target,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = NazoTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${r.points}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (r.correct) NazoSuccess else NazoError,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (r.correct) "${(r.remainingFraction * 100).toInt()}% time left" else "missed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NazoTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = showButtons,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(NazoPrimary)
                                .clickable {
                                    Haptics.light(context)
                                    onPlayAgain()
                                }
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play Again", color = NazoOnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(NazoSurfaceVariant)
                                .clickable {
                                    Haptics.light(context)
                                    onHomeClick()
                                }
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Home", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ScoreCard(
    animatedScore: Int,
    progressAnim: Float,
    solved: Int,
    total: Int,
    eliminated: Boolean,
    cleared: Boolean,
) {
    val message = when {
        cleared -> "Flawless — every image un-blurred in time."
        eliminated -> "The timer caught you out. The next image will be kinder."
        else -> "Good run — the next image is waiting."
    }

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
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = NazoPrimary.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NazoPrimary.copy(alpha = 0.4f),
                            NazoPrimary,
                            NazoPrimary.copy(alpha = 0.8f)
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progressAnim,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (eliminated) NazoError else NazoPrimary,
                                (if (eliminated) NazoError else NazoPrimary).copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (eliminated) Icons.Outlined.FitnessCenter else Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = NazoOnPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "$animatedScore",
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp),
            color = NazoPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "TOTAL POINTS",
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            color = NazoPrimary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Solved $solved of $total rounds",
                style = MaterialTheme.typography.titleMedium,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
