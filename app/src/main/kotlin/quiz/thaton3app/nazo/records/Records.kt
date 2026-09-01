package quiz.thaton3app.nazo.records

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary

/**
 * Personal bests (Phase 4). Its own tiny store ("nazo_records") so nothing in
 * QuizStats' JSON schema changes. Records are per difficulty:
 *  - Quiz: best accuracy percent (comparable across question counts).
 *  - Guessing: best total points per difficulty + round count (points scale
 *    with rounds, so cross-round-count comparisons would be unfair).
 * A run only counts as a NEW record when it strictly beats the stored best
 * and is above zero (no "New Record!" for a 0% first game).
 */
class RecordsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_records", Context.MODE_PRIVATE)

    fun quizBestPercent(difficulty: String): Int =
        prefs.getInt("quiz_best_$difficulty", -1)

    /** Persists the run if it beats the stored best; true = show the badge. */
    fun submitQuiz(difficulty: String, correct: Int, total: Int): Boolean {
        if (total <= 0) return false
        val pct = correct * 100 / total
        val prev = quizBestPercent(difficulty)
        if (pct > prev) prefs.edit().putInt("quiz_best_$difficulty", pct).apply()
        return pct > prev && pct > 0
    }

    fun guessBestPoints(difficulty: String, rounds: Int): Int =
        prefs.getInt("guess_best_${difficulty}_$rounds", -1)

    /** Persists the run if it beats the stored best; true = show the badge. */
    fun submitGuess(difficulty: String, rounds: Int, points: Int): Boolean {
        val prev = guessBestPoints(difficulty, rounds)
        if (points > prev) prefs.edit().putInt("guess_best_${difficulty}_$rounds", points).apply()
        return points > prev && points > 0
    }
}

/**
 * The "New Record!" celebration badge: pops in with a bouncy scale after the
 * results screen's own entrance animations have played (700ms), with a light
 * haptic tap. Render it only when the run actually set a record.
 */
@Composable
fun NewRecordBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        shown = true
        Haptics.light(context)
        Sounds.record(context) // opt-in fanfare, no-op when sounds are disabled
    }
    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium,
            ),
        ) + fadeIn(tween(220)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoPrimary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = NazoOnPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Record!",
                color = NazoOnPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
