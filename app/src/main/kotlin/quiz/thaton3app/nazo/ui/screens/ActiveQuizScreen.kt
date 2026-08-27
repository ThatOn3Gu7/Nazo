package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizEngine
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.*

@Composable
fun ActiveQuizScreen(
    question: Question,
    currentQuestionIndex: Int,
    totalQuestions: Int,
    difficulty: String = "Medium",
    onNextQuestion: (Boolean, String?) -> Unit,
    onCloseClick: () -> Unit,
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isTimeUp by remember { mutableStateOf(false) }
    val secondsPerQuestion = QuizEngine.specFor(difficulty).secondsPerQuestion
    var remainingSeconds by remember { mutableIntStateOf(secondsPerQuestion) }
    val context = LocalContext.current
    var showQuitDialog by remember { mutableStateOf(false) }

    // Lifecycle-safe countdown: one timer per question (keyed on the index). The
    // coroutine auto-cancels when this screen leaves composition, and it stops early
    // the moment the user answers. On timeout the answer is revealed and the
    // question counts as incorrect (the user is "eliminated").
    LaunchedEffect(currentQuestionIndex) {
        selectedAnswer = null
        isTimeUp = false
        remainingSeconds = secondsPerQuestion
        while (remainingSeconds > 0 && selectedAnswer == null) {
            delay(1000)
            remainingSeconds--
            // Escalating buzz in the final 5 seconds: starts at 30% strength, ramps
            // up each second, peaks with a 100% buzz the moment time hits zero.
            when (remainingSeconds) {
                5 -> Haptics.tick(context, 30)
                4 -> Haptics.tick(context, 36)   // ~20% stronger than the previous
                3 -> Haptics.tick(context, 47)   // ~30% stronger than the previous
                2 -> Haptics.tick(context, 66)   // ~40% stronger than the previous
                1 -> Haptics.tick(context, 85)   // last second before zero
                0 -> Haptics.timeUp(context)     // time's up — full-strength buzz
            }
        }
        if (remainingSeconds == 0 && selectedAnswer == null) {
            isTimeUp = true
        }
    }

    val isAnswered = selectedAnswer != null
    val reveal = isAnswered || isTimeUp
    val isCorrect = selectedAnswer == question.correctAnswer

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            
            // Header with Progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { Haptics.light(context); showQuitDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NazoTextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.theme, // e.g., "Jujutsu Kaisen: Shibuya Arc"
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Question ${currentQuestionIndex + 1} of $totalQuestions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = remainingSeconds.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isTimeUp) NazoError else if (remainingSeconds <= 5) NazoError else NazoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            val progressAnim = animateFloatAsState(
                targetValue = (currentQuestionIndex + 1) / totalQuestions.toFloat(),
                animationSpec = tween(durationMillis = 400),
                label = "quizProgress"
            ).value
            LinearProgressIndicator(
                progress = { progressAnim },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = NazoPrimary,
                trackColor = NazoSurface
            )
            
            Spacer(Modifier.height(24.dp))
            
            AnimatedContent(
                targetState = question,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(120)) },
                label = "questionTransition"
            ) { q ->
                Column {
                // Question Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NazoSurface)
                        .padding(24.dp)
                ) {
                    Text(
                        text = q.theme.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NazoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = q.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Options
                val labels = listOf("A", "B", "C", "D")
                q.options.forEachIndexed { index, optionText ->
                    val isThisSelected = selectedAnswer == optionText
                    val isThisCorrectAnswer = optionText == q.correctAnswer

                    val bgColor by animateColorAsState(
                        targetValue = when {
                            !reveal -> NazoSurfaceVariant
                            isThisCorrectAnswer -> Color(0xFFD4E7D5) // Light green mockup color
                            isThisSelected && !isThisCorrectAnswer -> Color(0xFFF2D5D5) // Light red mockup color
                            else -> NazoSurfaceVariant // Unselected when answered
                        },
                        animationSpec = tween(220),
                        label = "optionBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = when {
                            !reveal -> Color.Transparent
                            isThisCorrectAnswer -> Color(0xFF2E7D32) // Dark green
                            isThisSelected && !isThisCorrectAnswer -> Color(0xFFC62828) // Dark red
                            else -> Color.Transparent
                        },
                        animationSpec = tween(220),
                        label = "optionBorder"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(50))
                            .clickable(enabled = !reveal) {
                                if (optionText == q.correctAnswer) Haptics.light(context)
                                else Haptics.doubleLight(context)
                                selectedAnswer = optionText
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OptionCircle(
                            reveal = reveal,
                            isThisCorrectAnswer = isThisCorrectAnswer,
                            isThisSelected = isThisSelected,
                            label = labels[index]
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (reveal && (isThisCorrectAnswer || isThisSelected)) borderColor else NazoTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Explanation Card (shows after answering OR when time runs out)
                AnimatedVisibility(
                    visible = reveal,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(120))
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(NazoSurface)
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = NazoPrimary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Explanation", style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = q.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NazoTextSecondary,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(20.dp))

                            // Next Question Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(NazoPrimary)
                                    .clickable {
                                        Haptics.light(context)
                                        onNextQuestion(isCorrect, selectedAnswer)
                                        selectedAnswer = null // Reset for next question
                                    }
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (currentQuestionIndex == totalQuestions - 1) "Finish Quiz" else "Next Question", color = NazoOnPrimary, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OptionCircle(
    reveal: Boolean,
    isThisCorrectAnswer: Boolean,
    isThisSelected: Boolean,
    label: String,
) {
    val circleColor = animateColorAsState(
        targetValue = when {
            !reveal -> NazoBackground
            isThisCorrectAnswer -> Color(0xFF2E7D32)
            isThisSelected && !isThisCorrectAnswer -> Color(0xFFC62828)
            else -> NazoBackground
        },
        animationSpec = tween(220),
        label = "optionCircle"
    ).value
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(circleColor),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !(reveal && (isThisCorrectAnswer || isThisSelected)),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary, fontWeight = FontWeight.Bold)
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = reveal && isThisCorrectAnswer,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(120))
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = reveal && isThisSelected && !isThisCorrectAnswer,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(120))
        ) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
    }
}

            Spacer(Modifier.height(32.dp))
        }
        }

        if (showQuitDialog) {
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(NazoSurface)
                            .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(NazoError),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "!",
                                color = NazoOnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Quit quiz?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Do you really want to quit? Your progress in this quiz will be lost.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(22.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                .background(NazoPrimary)
                                .clickable { Haptics.light(context); showQuitDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Stay",
                                    color = NazoOnPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, NazoError, RoundedCornerShape(16.dp))
                                .clickable {
                                    Haptics.light(context)
                                    showQuitDialog = false
                                    onCloseClick()
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Quit",
                                    color = NazoError,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
        }
    }
}

