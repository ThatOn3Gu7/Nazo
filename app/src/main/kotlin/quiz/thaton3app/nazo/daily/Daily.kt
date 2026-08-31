package quiz.thaton3app.nazo.daily

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import kotlin.random.Random

/**
 * Daily Challenge (Phase 5): one date-seeded 5-question run per day, drawn
 * entirely from the LOCAL question bank so it works fully offline. The seed
 * is the epoch day, so everyone (and every retry of the composable) gets the
 * same puzzle for the same date. Completing it feeds the normal stats/streak
 * pipeline (it runs through the regular quiz flow) and pays bonus XP on top.
 */
object DailyChallenge {

    const val QUESTION_COUNT = 5

    /** Local-midnight day boundary — shared with streaks (QuizStats.localEpochDay). */
    fun todayEpochDay(): Long = QuizStats.localEpochDay()

    /** Bonus XP: 20 for showing up + 10 per correct answer (max 70). */
    fun bonusXpFor(correct: Int): Int = 20 + 10 * correct

    fun maxBonusXp(): Int = bonusXpFor(QUESTION_COUNT)

    fun questionsForToday(): List<Question> = questionsFor(todayEpochDay())

    /**
     * Deterministic selection: the bank's getQuestions() shuffles internally,
     * so the universe is re-sorted by question text first, then a seeded
     * Random picks a difficulty ramp (2 Easy, 2 Medium, 1 Hard/Otaku).
     * Option order is re-shuffled with a per-question seed so a mid-day
     * restart shows the identical quiz.
     */
    fun questionsFor(epochDay: Long): List<Question> {
        val all = LocalQuestionBank.getQuestions(Int.MAX_VALUE)
            .distinctBy { it.text }
            .sortedBy { it.text }
        val rng = Random(epochDay * 31 + 7)
        val picked = mutableListOf<Question>()
        fun pickFrom(pool: List<Question>, n: Int) {
            val remaining = pool.filter { p -> picked.none { it.text == p.text } }
            picked += remaining.shuffled(rng).take(n)
        }
        fun pool(vararg difficulties: String) =
            all.filter { q -> difficulties.any { q.difficulty.equals(it, ignoreCase = true) } }
        pickFrom(pool("Easy"), 2)
        pickFrom(pool("Medium"), 2)
        pickFrom(pool("Hard", "Otaku Master"), 1)
        if (picked.size < QUESTION_COUNT) pickFrom(all, QUESTION_COUNT - picked.size)
        return picked.mapIndexed { i, q ->
            q.copy(options = q.options.shuffled(Random(epochDay * 131 + i)))
        }
    }
}

/**
 * Completion state for the daily challenge, in its own tiny store
 * ("nazo_daily") — nothing in the existing stats schema changes. Tracks the
 * last completed day (one run per day), lifetime completion count (feeds the
 * daily achievements) and the accumulated bonus XP (added on top of the
 * stats-derived XP when the level is displayed).
 */
class DailyStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_daily", Context.MODE_PRIVATE)

    fun isCompletedToday(): Boolean =
        prefs.getLong("last_day", -1L) == DailyChallenge.todayEpochDay()

    fun lastScore(): Int = prefs.getInt("last_score", 0)

    fun lastBonus(): Int = prefs.getInt("last_bonus", 0)

    fun completedCount(): Int = prefs.getInt("completed_count", 0)

    fun totalBonusXp(): Int = prefs.getInt("bonus_xp_total", 0)

    /**
     * Marks today's challenge as completed and banks the bonus XP.
     * Returns the bonus earned (0 if today was somehow already recorded).
     */
    fun recordCompletion(correct: Int, total: Int): Int {
        if (isCompletedToday()) return 0
        val bonus = DailyChallenge.bonusXpFor(correct)
        prefs.edit()
            .putLong("last_day", DailyChallenge.todayEpochDay())
            .putInt("last_score", correct)
            .putInt("last_total", total)
            .putInt("last_bonus", bonus)
            .putInt("completed_count", completedCount() + 1)
            .putInt("bonus_xp_total", totalBonusXp() + bonus)
            .apply()
        return bonus
    }
}

/**
 * The home-screen entry card. Pending: accent gradient, pulsing bolt and a
 * play chip. Completed: calm green check + today's result, tap disabled
 * until tomorrow. The card morphs between the two with animateContentSize.
 */
@Composable
fun DailyChallengeCard(
    completed: Boolean,
    lastScore: Int,
    lastBonus: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(NazoPrimary.copy(alpha = 0.16f), NazoPrimary.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, NazoPrimary.copy(alpha = if (completed) 0.15f else 0.35f), shape)
            .clickable(enabled = !completed) { onPlay() }
            .padding(16.dp)
            .animateContentSize(tween(260)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gentle heartbeat on the bolt while the challenge is waiting.
        val pulse = rememberInfiniteTransition(label = "dailyPulse")
        val iconScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (completed) 1f else 1.12f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "dailyPulseScale",
        )
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (completed) NazoSuccess else NazoPrimary),
            contentAlignment = Alignment.Center,
        ) {
            // Only the ICON pulses — the circle stays put, so the scale can
            // never visually overflow the card's padding (graphicsLayer scale
            // doesn't affect layout, so a pulsing container would bleed
            // toward the card edge).
            Icon(
                imageVector = if (completed) Icons.Filled.TaskAlt else Icons.Filled.Bolt,
                contentDescription = null,
                tint = NazoOnPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "DAILY CHALLENGE",
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (completed) {
                    "Cleared ${lastScore}/${DailyChallenge.QUESTION_COUNT} · +$lastBonus XP earned"
                } else {
                    "${DailyChallenge.QUESTION_COUNT} mixed questions · up to +${DailyChallenge.maxBonusXp()} XP"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (completed) "Come back tomorrow for a new one" else "New puzzle every day · works offline",
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
            )
        }
        if (!completed) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play daily challenge",
                    tint = NazoPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * "+70 XP · Daily Bonus" chip for the results screen — pops in with a bouncy
 * scale a beat AFTER the New Record badge would (1s), so the two celebrations
 * read as a sequence instead of clashing.
 */
@Composable
fun DailyBonusChip(bonusXp: Int, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(tween(220)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSuccess.copy(alpha = 0.15f))
                .border(1.dp, NazoSuccess.copy(alpha = 0.4f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = NazoSuccess,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "+$bonusXp XP · Daily Bonus",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoSuccess,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
