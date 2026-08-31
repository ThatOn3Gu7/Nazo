package quiz.thaton3app.nazo.achievements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoStatsCardBg
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Achievements (Phase 5). Every badge is computed PURELY from data the app
 * already persists (QuizStats + personal-best records + daily-challenge
 * counts) — nothing new is written, so the set can grow later without any
 * migration. Locked badges expose progress toward unlocking.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val unlocked: Boolean,
    val progress: Float,
    val progressText: String,
)

object AchievementEngine {

    fun compute(
        stats: QuizStats,
        hasPerfectQuiz: Boolean,
        dailiesCompleted: Int,
    ): List<Achievement> {
        fun frac(cur: Int, target: Int) = (cur.toFloat() / target).coerceIn(0f, 1f)
        val games = stats.totalQuizzes
        val answered = stats.totalQuestionsAnswered
        val accuracy = if (answered > 0) stats.totalCorrect * 100 / answered else 0
        val topics = stats.animeAnswered.keys.count { it.isNotBlank() && it != "Unknown" }
        val elitePlays = (stats.difficultyPlays["Hard"] ?: 0) +
            (stats.difficultyPlays["Otaku Master"] ?: 0)
        val sharpReady = answered >= 20
        val best = stats.bestStreakDays
        return listOf(
            Achievement(
                "first_game", "First Steps", "Finish your first game in any mode.",
                Icons.Filled.TaskAlt, games >= 1, frac(games, 1), "$games / 1 game",
            ),
            Achievement(
                "ten_games", "Getting Serious", "Finish 10 games across both modes.",
                Icons.Filled.DoneAll, games >= 10, frac(games, 10), "$games / 10 games",
            ),
            Achievement(
                "fifty_games", "Marathon Otaku", "Finish 50 games. True dedication.",
                Icons.Filled.MilitaryTech, games >= 50, frac(games, 50), "$games / 50 games",
            ),
            Achievement(
                "hundred_answers", "Century Club", "Answer 100 questions in total.",
                Icons.Filled.Bolt, answered >= 100, frac(answered, 100), "$answered / 100 answered",
            ),
            Achievement(
                "sharpshooter", "Sharpshooter", "Reach 80% overall accuracy (needs 20+ answers).",
                Icons.Filled.TrackChanges, sharpReady && accuracy >= 80,
                if (sharpReady) frac(accuracy, 80) else frac(answered, 20),
                if (sharpReady) "$accuracy% / 80% accuracy" else "$answered / 20 answered",
            ),
            Achievement(
                "flawless", "Flawless", "Score 100% in any quiz.",
                Icons.Filled.WorkspacePremium, hasPerfectQuiz,
                if (hasPerfectQuiz) 1f else 0f,
                if (hasPerfectQuiz) "Perfect run achieved" else "No perfect run yet",
            ),
            Achievement(
                "streak_3", "On Fire", "Play 3 days in a row.",
                Icons.Filled.LocalFireDepartment, best >= 3, frac(best, 3), "$best / 3 days",
            ),
            Achievement(
                "streak_7", "Unstoppable", "Play 7 days in a row.",
                Icons.Filled.Whatshot, best >= 7, frac(best, 7), "$best / 7 days",
            ),
            Achievement(
                "elite", "Elite Challenger", "Finish a game on Hard or Otaku Master.",
                Icons.Filled.School, elitePlays >= 1, frac(elitePlays, 1), "$elitePlays / 1 game",
            ),
            Achievement(
                "daily_first", "Daily Ritual", "Complete your first Daily Challenge.",
                Icons.Filled.CalendarMonth, dailiesCompleted >= 1,
                frac(dailiesCompleted, 1), "$dailiesCompleted / 1 daily",
            ),
            Achievement(
                "daily_seven", "Devoted", "Complete 7 Daily Challenges.",
                Icons.Filled.EmojiEvents, dailiesCompleted >= 7,
                frac(dailiesCompleted, 7), "$dailiesCompleted / 7 dailies",
            ),
            Achievement(
                "explorer", "Genre Hopper", "Answer questions from 5 different anime.",
                Icons.Filled.Explore, topics >= 5, frac(topics, 5), "$topics / 5 anime",
            ),
        )
    }
}

/** Back-out easing so badges pop slightly past full size, then settle. */
private val BadgePop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Badge-wall card for the Statistics screen, styled to match its siblings
 * (rounded 20, NazoStatsCardBg, hairline border). Badges pop in staggered;
 * tapping one expands an animated detail panel with the description and an
 * animated progress bar; tapping again (or another badge) morphs it.
 */
@Composable
fun AchievementsCard(
    achievements: List<Achievement>,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    val unlockedCount = achievements.count { it.unlocked }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoStatsCardBg)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .animateContentSize(tween(260)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NazoPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$unlockedCount / ${achievements.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        achievements.chunked(4).forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, achievement ->
                    AchievementBadge(
                        achievement = achievement,
                        selected = selectedId == achievement.id,
                        animate = animate,
                        index = rowIndex * 4 + colIndex,
                        onClick = {
                            Haptics.light(context)
                            selectedId =
                                if (selectedId == achievement.id) null else achievement.id
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (rowIndex != achievements.chunked(4).lastIndex) Spacer(Modifier.height(14.dp))
        }

        // Detail panel: keeps the last selection rendered during the collapse
        // so the exit animation never shows an empty box.
        var lastSelected by remember { mutableStateOf<Achievement?>(null) }
        val selected = achievements.firstOrNull { it.id == selectedId }
        if (selected != null) lastSelected = selected
        AnimatedVisibility(
            visible = selected != null,
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            val detail = lastSelected
            if (detail != null) {
                AnimatedContent(
                    targetState = detail,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "achievementDetail",
                ) { a ->
                    Column(Modifier.padding(top = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = a.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = NazoTextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            if (a.unlocked) {
                                Text(
                                    text = "UNLOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NazoSuccess,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = a.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = NazoTextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        val barProgress by animateFloatAsState(
                            targetValue = if (a.unlocked) 1f else a.progress,
                            animationSpec = tween(500),
                            label = "achievementProgress",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(NazoSurface),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(if (a.unlocked) NazoSuccess else NazoPrimary),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = a.progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (a.unlocked) NazoSuccess else NazoTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    selected: Boolean,
    animate: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Staggered pop-in with a slight overshoot, matching the screen's
    // one-shot `animate` gate so it only plays once per visit.
    val appear by animateFloatAsState(
        targetValue = if (animate) 1f else 0f,
        animationSpec = tween(320, delayMillis = 45 * index, easing = BadgePop),
        label = "badgeAppear",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) NazoPrimary else Color.Transparent,
        animationSpec = tween(200),
        label = "badgeRing",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = appear
                    scaleY = appear
                    alpha = appear.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(if (achievement.unlocked) NazoPrimary.copy(alpha = 0.18f) else NazoSurface)
                .border(2.dp, ring, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = achievement.icon,
                contentDescription = achievement.title,
                tint = if (achievement.unlocked) NazoPrimary
                else NazoTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
