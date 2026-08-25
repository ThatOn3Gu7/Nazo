package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.ui.components.rememberHapticBack

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardAccent
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardTrack
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCardMuted
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoStatsCardBg
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

private data class DifficultyStat(val label: String, val played: Int, val accuracyPercent: Int)

// New data class for the anime stats
private data class MasteredAnimeStat(val rank: Int, val title: String, val quizzes: Int, val avgScore: Int)

private data class StatsData(
    val level: Int,
    val currentXp: Int,
    val xpForNextLevel: Int,
    val totalQuizzes: Int,
    val overallAccuracyPercent: Int,
    val currentStreakDays: Int,
    val bestTopic: String?, 
    val difficulty: List<DifficultyStat>,
    val topAnime: List<MasteredAnimeStat>,
)

private fun QuizStats.toStatsData(): StatsData {
    val overallAccuracyPercent = if (totalQuestionsAnswered > 0) {
        totalCorrect * 100 / totalQuestionsAnswered
    } else {
        0
    }

    val difficultyOrder = listOf("Easy", "Medium", "Hard", "Otaku Master")
    val difficulty = difficultyOrder.map { label ->
        val played = difficultyPlays[label] ?: 0
        val answered = difficultyAnswered[label] ?: 0
        val correct = difficultyCorrect[label] ?: 0
        val accuracyPercent = if (answered > 0) correct * 100 / answered else 0
        DifficultyStat(label, played, accuracyPercent)
    }

    val ranked = animeAnswered.mapNotNull { (anime, answered) ->
        val correct = animeCorrect[anime] ?: 0
        if (answered <= 0) null else AnimeAcc(anime, answered, correct * 100 / answered)
    }.sortedWith(
        compareByDescending<AnimeAcc> { it.avgScore }.thenByDescending { it.answered }
    ).take(3)

    val topAnime = ranked.mapIndexed { index, e ->
        MasteredAnimeStat(index + 1, e.anime, e.answered, e.avgScore)
    }
    val bestTopic = topAnime.firstOrNull()?.title

    // Lightweight progression: XP from correct answers + completed quizzes.
    val xp = totalCorrect * 10 + totalQuizzes * 5
    val xpForNextLevel = 200
    val level = (xp / xpForNextLevel) + 1
    val currentXp = xp % xpForNextLevel

    return StatsData(
        level = level,
        currentXp = currentXp,
        xpForNextLevel = xpForNextLevel,
        totalQuizzes = totalQuizzes,
        overallAccuracyPercent = overallAccuracyPercent,
        currentStreakDays = currentStreakDays,
        bestTopic = bestTopic,
        difficulty = difficulty,
        topAnime = if (topAnime.isEmpty()) {
            listOf(
                MasteredAnimeStat(1, "—", 0, 0),
                MasteredAnimeStat(2, "—", 0, 0),
                MasteredAnimeStat(3, "—", 0, 0),
            )
        } else {
            topAnime
        },
    )
}

private data class AnimeAcc(val anime: String, val answered: Int, val avgScore: Int)

@Composable
fun StatisticsScreen(
    stats: QuizStats = QuizStats(),
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    val data = stats.toStatsData()

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
            Spacer(Modifier.height(28.dp))
            ScreenHeader(title = "Statistics & Insights", onBackClick = onBackClick)
            Spacer(Modifier.height(20.dp))
            RankCard(data)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatTile(
                    icon = Icons.Filled.DoneAll,
                    label = "Total Quizzes",
                    value = data.totalQuizzes.toString(),
                    subtitle = "Completed",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Filled.Speed,
                    label = "Overall Accuracy",
                    value = "${data.overallAccuracyPercent}%",
                    subtitle = "Correct answers",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatTile(
                    icon = Icons.Filled.LocalFireDepartment,
                    label = "Current Streak",
                    value = "${data.currentStreakDays} Days",
                    subtitle = "Keep it burning",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Filled.TrackChanges,
                    label = "Best Topic",
                    value = data.bestTopic ?: "—",
                    subtitle = if (data.bestTopic == null) "No quizzes yet" else "",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            DifficultyCard(data.difficulty)
            
            // --- NEW COMPONENTS START HERE ---
            Spacer(Modifier.height(16.dp))
            TopAnimeCard(data.topAnime)
            Spacer(Modifier.height(24.dp))
            ShareButton()
            Spacer(Modifier.height(24.dp))
            // --- NEW COMPONENTS END HERE ---
            
        }
        NazoBottomNav(selected = NazoTab.Settings, onHomeClick = onHomeClick, onSettingsClick = onBackClick)
    }
}

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NazoTextPrimary)
    }
}

@Composable
private fun RankCard(stats: StatsData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoDarkCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NazoDarkCardAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = NazoOnDarkCard, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CURRENT RANK",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoOnDarkCardMuted,
                )
                Text(
                    text = "Level ${stats.level} Otaku",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoOnDarkCard,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = NazoOnDarkCardMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        val progress = if (stats.xpForNextLevel > 0) {
            (stats.currentXp.toFloat() / stats.xpForNextLevel).coerceIn(0f, 1f)
        } else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(NazoDarkCardTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NazoOnDarkCard)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${stats.currentXp} / ${stats.xpForNextLevel} XP",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoOnDarkCardMuted,
            )
            Text(
                text = "${stats.xpForNextLevel - stats.currentXp} XP to Level ${stats.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoOnDarkCardMuted,
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NazoSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp), color = NazoTextPrimary, fontWeight = FontWeight.Bold)
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
        }
    }
}

@Composable
private fun DifficultyCard(rows: List<DifficultyStat>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .padding(16.dp)
    ) {
        Text(
            text = "Quizzes by Difficulty",
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { index, row ->
            DifficultyRow(row)
            if (index != rows.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DifficultyRow(row: DifficultyStat) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = row.label, style = MaterialTheme.typography.bodyLarge, color = NazoTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = "${row.played} played", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NazoSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.accuracyPercent / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(NazoDarkCard)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${row.accuracyPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TopAnimeCard(rows: List<MasteredAnimeStat>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Top Mastered Anime",
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Filled.AutoAwesome, 
                contentDescription = null, 
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        rows.forEachIndexed { index, row ->
            TopAnimeRow(row)
            if (index != rows.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopAnimeRow(row: MasteredAnimeStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NazoPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = NazoOnPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${row.quizzes} Quizzes",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${row.avgScore}% Avg",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ShareButton() {
    Button(
        onClick = { /* TODO: Hook up share intent */ },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoPrimary,
            contentColor = NazoOnPrimary
        ),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "Share My Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
