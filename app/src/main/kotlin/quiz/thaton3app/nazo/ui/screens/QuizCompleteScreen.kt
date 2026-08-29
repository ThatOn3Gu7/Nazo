package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.*

@Composable
fun QuizCompleteScreen(
    score: Int,
    totalQuestions: Int,
    timeSpent: String,
    difficulty: String,
    onPlayAnother: () -> Unit,
    onReviewAnswers: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val accuracy = ((score.toFloat() / totalQuestions) * 100).toInt()

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
            Text(
                text = "Quiz Complete",
                style = MaterialTheme.typography.headlineMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            
            // Big Trophy Score Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NazoPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "$accuracy%",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$score / $totalQuestions Correct!",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Impressive run — you're nearly an Otaku Master on this arc.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Timer, title = "Time", value = timeSpent)
                StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.TrackChanges, title = "Accuracy", value = "$accuracy%")
                StatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Speed, title = "Difficulty", value = difficulty)
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Action Buttons
            Button(
                onClick = onPlayAnother,
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
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

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = NazoTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

