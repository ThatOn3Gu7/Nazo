package quiz.thaton3app.nazo.hints

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import kotlin.random.Random

/**
 * Lifeline rules (Phase 4). Self-contained: which hint a difficulty gets,
 * how many hints a game carries, and the pure hint transforms. UI lives in
 * this package too (HintPill / HintRevealPill) so both game modes share the
 * exact same look and motion.
 *
 * Rules (owner-approved roadmap):
 *  - Quiz Easy/Medium: hide 2 wrong options ("50/50").
 *  - Quiz Hard/Otaku Master: reveal the first letter of the answer.
 *  - Guessing (all difficulties): reveal the first letters of the target
 *    (2 more letters per use).
 *  - Limited supply per game, scaling with length; one use per question.
 */
object HintEngine {

    /** Quiz supply: 5 questions → 1, 10 → 2, 15 → 3. */
    fun quizSupply(totalQuestions: Int): Int = when {
        totalQuestions <= 5 -> 1
        totalQuestions <= 10 -> 2
        else -> 3
    }

    /** Guessing supply: 1 round → 1, 3 → 2, 5+ → 3. */
    fun guessSupply(totalRounds: Int): Int = when {
        totalRounds <= 1 -> 1
        totalRounds <= 3 -> 2
        else -> 3
    }

    /** Hard tiers get the letter hint; lower tiers get the 50/50. */
    fun usesLetterHint(difficulty: String): Boolean =
        difficulty.equals("Hard", ignoreCase = true) ||
            difficulty.equals("Otaku Master", ignoreCase = true)

    /**
     * Picks 2 wrong options to hide. Seeded by the question index so a
     * recomposition never reshuffles which options vanished.
     */
    fun optionsToHide(options: List<String>, correctAnswer: String, seed: Int): Set<String> =
        options.filter { it != correctAnswer }
            .shuffled(Random(seed.toLong()))
            .take(2)
            .toSet()

    /** First letter (or digit) of the answer, uppercased. */
    fun firstLetter(answer: String): String {
        val c = answer.trim().firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return c.uppercaseChar().toString()
    }

    /** Letters uncovered per guessing-hint use. */
    const val GUESS_LETTERS_PER_HINT = 2

    /**
     * Masks the target name, revealing the first [letters] characters
     * (spaces stay visible so the word shape reads): "NA•••O U•••••I".
     */
    fun maskedReveal(name: String, letters: Int): String {
        var remaining = letters
        return buildString {
            name.trim().forEach { ch ->
                when {
                    ch == ' ' -> append(' ')
                    remaining > 0 -> {
                        append(ch.uppercaseChar())
                        remaining--
                    }
                    else -> append('•')
                }
            }
        }
    }
}

/**
 * The lifeline button: 💡 Hint ×N. Colors crossfade between the enabled
 * (accent) and disabled (muted) looks; the count crossfades when spent.
 */
@Composable
fun HintPill(
    remaining: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (enabled) NazoPrimary.copy(alpha = 0.16f)
        else NazoTextSecondary.copy(alpha = 0.10f),
        animationSpec = tween(220),
        label = "hintBg",
    )
    val fg by animateColorAsState(
        targetValue = if (enabled) NazoPrimary else NazoTextSecondary.copy(alpha = 0.6f),
        animationSpec = tween(220),
        label = "hintFg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lightbulb,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Hint",
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = remaining,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "hintCount",
        ) { n ->
            Text(
                text = "×$n",
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The revealed hint itself ("Starts with "N"" / "NA•••O"), accent-tinted. */
@Composable
fun HintRevealPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NazoPrimary.copy(alpha = 0.12f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = NazoPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}
