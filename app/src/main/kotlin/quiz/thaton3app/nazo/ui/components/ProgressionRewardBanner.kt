package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.progression.ProgressionReward
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoStatsCardBg
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * One bounded completion reward. It summarizes XP and milestones already
 * derived from persisted game data; it does not grant or store anything.
 */
@Composable
fun ProgressionRewardBanner(
    reward: ProgressionReward,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    var visible by remember(reward, animateEntrance, reduceMotion) {
        mutableStateOf(!animateEntrance || reduceMotion)
    }
    LaunchedEffect(reward, animateEntrance, reduceMotion) {
        if (animateEntrance && !reduceMotion) {
            delay(620)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
            initialOffsetY = { it / 3 },
        ),
        modifier = modifier,
    ) {
        val milestone = reward.leveledUp || reward.unlockedAchievementTitles.isNotEmpty()
        Surface(
            color = if (milestone) NazoPrimary.copy(alpha = 0.10f) else NazoStatsCardBg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (milestone) NazoPrimary.copy(alpha = 0.34f)
                else NazoTextSecondary.copy(alpha = 0.10f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (reward.leveledUp) NazoPrimary else NazoPrimary.copy(alpha = 0.14f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (reward.leveledUp) Icons.Filled.EmojiEvents
                            else Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (reward.leveledUp) NazoOnPrimary else NazoPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (reward.leveledUp) "LEVEL UP" else "XP EARNED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reward.leveledUp) NazoSuccess else NazoTextSecondary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = if (reward.leveledUp) {
                                "Level ${reward.progress.level} reached"
                            } else {
                                "${reward.progress.xpToNextLevel} XP to Level ${reward.progress.level + 1}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Surface(
                        color = NazoPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "+${reward.xpEarned} XP",
                            style = MaterialTheme.typography.labelLarge,
                            color = NazoPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                if (reward.unlockedAchievementTitles.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = NazoPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (reward.unlockedAchievementTitles.size == 1) {
                                    "Achievement unlocked"
                                } else {
                                    "${reward.unlockedAchievementTitles.size} achievements unlocked"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = NazoTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = reward.unlockedAchievementTitles.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NazoTextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
