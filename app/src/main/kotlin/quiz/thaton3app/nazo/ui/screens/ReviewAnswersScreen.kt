package quiz.thaton3app.nazo.ui.screens

import androidx.compose.ui.platform.LocalContext
import quiz.thaton3app.nazo.ui.components.Haptics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

@Composable
fun ReviewAnswersScreen(
    questions: List<Question>,
    userAnswers: List<String?>,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { Haptics.soft(LocalContext.current); onBackClick() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Review Answers",
                    style = MaterialTheme.typography.titleLarge,
                    color = NazoTextPrimary,
                )
            }
            Spacer(Modifier.height(20.dp))

            questions.forEachIndexed { index, question ->
                val userAnswer = userAnswers.getOrNull(index)
                ReviewCard(question = question, userAnswer = userAnswer, index = index + 1)
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        NazoBottomNav(
            selected = NazoTab.Home,
            onHomeClick = onHomeClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
private fun ReviewCard(question: Question, userAnswer: String?, index: Int) {
    val labels = listOf("A", "B", "C", "D")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .padding(20.dp),
    ) {
        Text(
            text = "QUESTION $index",
            style = MaterialTheme.typography.labelSmall,
            color = NazoPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = question.text,
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        question.options.forEachIndexed { optIndex, option ->
            val isCorrectOption = option == question.correctAnswer
            val isUserOption = option == userAnswer
            val bgColor = when {
                isCorrectOption -> Color(0xFFD4E7D5)
                isUserOption -> Color(0xFFF2D5D5)
                else -> NazoSurfaceVariant
            }
            val borderColor = when {
                isCorrectOption -> Color(0xFF2E7D32)
                isUserOption -> Color(0xFFC62828)
                else -> Color.Transparent
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCorrectOption -> Color(0xFF2E7D32)
                                isUserOption -> Color(0xFFC62828)
                                else -> NazoBackground
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isCorrectOption -> Icon(
                            Icons.Filled.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        isUserOption && !isCorrectOption -> Icon(
                            Icons.Filled.Close,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Text(
                            labels.getOrElse(optIndex) { "?" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Explanation",
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = question.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
        )
    }
}
