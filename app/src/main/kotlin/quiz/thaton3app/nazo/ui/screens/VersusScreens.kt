package quiz.thaton3app.nazo.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.CelebrationOverlay
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// ---------------------------------------------------------------------------
// Versus (pass & play): two players answer the SAME questions on one phone.
// Player 1 plays the whole quiz, the handoff screen asks for the phone to be
// passed, Player 2 plays the same set (options re-shuffled), and the results
// screen shows the head-to-head. Party mode: nothing is written to stats or
// records, so guest answers never pollute the owner's numbers.
// ---------------------------------------------------------------------------

/** Interstitial between the two turns: "pass the phone to Player 2". */
@Composable
fun VersusHandoffScreen(
    p1Score: Int,
    totalQuestions: Int,
    onPlayer2Ready: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Player 1 is done!",
                style = MaterialTheme.typography.headlineMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                // P1's score stays SECRET until the end — no pressure hints.
                text = "Pass the phone to Player 2.\nSame $totalQuestions questions, shuffled answers.\nPlayer 1's score stays secret until the end!",
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
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
                        onPlayer2Ready()
                    },
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("I'm Player 2 — Let's go!", color = NazoOnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Head-to-head results: winner banner + both scores side by side. */
@Composable
fun VersusResultsScreen(
    p1Score: Int,
    p2Score: Int,
    totalQuestions: Int,
    topic: String,
    difficulty: String,
    onPlayAgain: () -> Unit,
    onHomeClick: () -> Unit,
) {
    val context = LocalContext.current
    val celebrationStyle = remember { ThemePreferences(context).celebrationStyle }
    val tie = p1Score == p2Score
    val winner = if (p1Score >= p2Score) 1 else 2

    var showContent by remember { mutableStateOf(false) }
    var triggerConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Sounds.complete(context)
        showContent = true
        if (!tie) {
            triggerConfetti = true
            Sounds.celebration(context, celebrationStyle)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (tie) "It's a tie!" else "Player $winner wins!",
                style = MaterialTheme.typography.headlineMedium,
                color = if (tie) NazoTextPrimary else NazoSuccess,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Topic: ${topic.ifBlank { "Any popular anime" }} • $difficulty",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerScoreCard(
                    label = "Player 1",
                    score = p1Score,
                    total = totalQuestions,
                    isWinner = !tie && winner == 1,
                    modifier = Modifier.weight(1f),
                )
                PlayerScoreCard(
                    label = "Player 2",
                    score = p2Score,
                    total = totalQuestions,
                    isWinner = !tie && winner == 2,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(36.dp))

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
                    },
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rematch", color = NazoOnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    },
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Home", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (triggerConfetti) {
            CelebrationOverlay(
                style = celebrationStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlayerScoreCard(
    label: String,
    score: Int,
    total: Int,
    isWinner: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) NazoSuccess else NazoTextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$score",
            style = MaterialTheme.typography.headlineLarge,
            color = if (isWinner) NazoSuccess else NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "of $total",
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
        )
        if (isWinner) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "WINNER",
                style = MaterialTheme.typography.labelSmall,
                color = NazoSuccess,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
