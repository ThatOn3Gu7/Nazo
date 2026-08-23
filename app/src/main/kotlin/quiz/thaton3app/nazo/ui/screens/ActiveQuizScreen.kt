package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizEngine
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.*

@Composable
fun ActiveQuizScreen(
    question: Question,
    currentQuestionIndex: Int,
    totalQuestions: Int,
    difficulty: String = "Medium",
    onNextQuestion: (Boolean, String?) -> Unit,
    onCloseClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isTimeUp by remember { mutableStateOf(false) }
    val secondsPerQuestion = QuizEngine.specFor(difficulty).secondsPerQuestion
    var remainingSeconds by remember { mutableIntStateOf(secondsPerQuestion) }

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
        }
        if (remainingSeconds == 0 && selectedAnswer == null) {
            isTimeUp = true
        }
    }

    val isAnswered = selectedAnswer != null
    val reveal = isAnswered || isTimeUp
    val isCorrect = selectedAnswer == question.correctAnswer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            
            // Header with Progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCloseClick,
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
            LinearProgressIndicator(
                progress = { (currentQuestionIndex + 1) / totalQuestions.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = NazoPrimary,
                trackColor = NazoSurface
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Question Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .padding(24.dp)
            ) {
                Text(
                    text = question.theme.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = question.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Options
            val labels = listOf("A", "B", "C", "D")
            question.options.forEachIndexed { index, optionText ->
                val isThisSelected = selectedAnswer == optionText
                val isThisCorrectAnswer = optionText == question.correctAnswer
                
                val bgColor = when {
                    !reveal -> NazoSurfaceVariant
                    isThisCorrectAnswer -> Color(0xFFD4E7D5) // Light green mockup color
                    isThisSelected && !isThisCorrectAnswer -> Color(0xFFF2D5D5) // Light red mockup color
                    else -> NazoSurfaceVariant // Unselected when answered
                }

                val borderColor = when {
                    !reveal -> Color.Transparent
                    isThisCorrectAnswer -> Color(0xFF2E7D32) // Dark green
                    isThisSelected && !isThisCorrectAnswer -> Color(0xFFC62828) // Dark red
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(50))
                        .clickable(enabled = !reveal) { selectedAnswer = optionText }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !reveal -> NazoBackground
                                    isThisCorrectAnswer -> Color(0xFF2E7D32)
                                    isThisSelected && !isThisCorrectAnswer -> Color(0xFFC62828)
                                    else -> NazoBackground
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reveal && isThisCorrectAnswer) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else if (reveal && isThisSelected && !isThisCorrectAnswer) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text(labels[index], style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
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
            if (reveal) {
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
                        text = question.explanation,
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
            Spacer(Modifier.height(32.dp))
        }
        NazoBottomNav(selected = NazoTab.Home, onHomeClick = onCloseClick, onSettingsClick = onSettingsClick)
    }
}

