package quiz.thaton3app.nazo.data

import kotlin.math.abs

/** Difficulty tiers Adaptive mode can target, ordered from gentlest to hardest. */
enum class AdaptiveTier(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    OTAKU_MASTER("Otaku Master");

    companion object {
        fun fromLabel(label: String): AdaptiveTier? =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
    }
}

enum class AdaptiveReason {
    StartingPoint,
    TopicMastery,
    StrongPace,
    RecentMiss,
    TimedOut,
    HintSupport,
    Steady,
}

/** Small process-restorable controller state; no answer text or personal data. */
data class AdaptiveQuizState(
    val tier: AdaptiveTier = AdaptiveTier.MEDIUM,
    val strongStreak: Int = 0,
    val recentMisses: Int = 0,
    val shiftCount: Int = 0,
    val reason: AdaptiveReason = AdaptiveReason.StartingPoint,
)

data class AdaptiveAttempt(
    val correct: Boolean,
    /** Fraction of the question clock remaining when the answer was graded. */
    val remainingFraction: Float,
    val usedHint: Boolean,
    val timedOut: Boolean,
)

/**
 * Deterministic Adaptive-mode policy.
 *
 * A miss eases the next target by one tier. Correct answers only raise it after
 * two clean, brisk answers in a row, so a lucky tap cannot cause a spike. Hint
 * use and slower correct answers hold the tier steady. The controller therefore
 * responds to accuracy, speed, help used, and recent misses without changing a
 * question that is already on screen.
 */
object AdaptiveDifficulty {
    const val LABEL = "Adaptive"
    private const val FAST_REMAINING_FRACTION = 0.45f

    fun isAdaptive(label: String): Boolean = label.equals(LABEL, ignoreCase = true)

    fun startingState(mastery: TopicMastery?): AdaptiveQuizState {
        val tier = when (mastery?.status) {
            TopicMasteryStatus.Strong -> AdaptiveTier.HARD
            TopicMasteryStatus.NeedsPractice -> AdaptiveTier.EASY
            TopicMasteryStatus.Improving,
            TopicMasteryStatus.Building,
            null -> AdaptiveTier.MEDIUM
        }
        return AdaptiveQuizState(
            tier = tier,
            reason = if (mastery == null) AdaptiveReason.StartingPoint else AdaptiveReason.TopicMastery,
        )
    }

    fun recordAttempt(
        state: AdaptiveQuizState,
        attempt: AdaptiveAttempt,
    ): AdaptiveQuizState {
        val remaining = attempt.remainingFraction.coerceIn(0f, 1f)
        if (attempt.timedOut) {
            return shiftDown(
                state.copy(strongStreak = 0, recentMisses = (state.recentMisses + 1).coerceAtMost(3)),
                AdaptiveReason.TimedOut,
            )
        }
        if (!attempt.correct) {
            return shiftDown(
                state.copy(strongStreak = 0, recentMisses = (state.recentMisses + 1).coerceAtMost(3)),
                AdaptiveReason.RecentMiss,
            )
        }
        if (attempt.usedHint) {
            return state.copy(
                strongStreak = 0,
                recentMisses = 0,
                reason = AdaptiveReason.HintSupport,
            )
        }
        if (remaining < FAST_REMAINING_FRACTION) {
            return state.copy(
                strongStreak = 0,
                recentMisses = 0,
                reason = AdaptiveReason.Steady,
            )
        }

        val streak = state.strongStreak + 1
        return if (streak >= 2) {
            shiftUp(
                state.copy(strongStreak = 0, recentMisses = 0),
                AdaptiveReason.StrongPace,
            )
        } else {
            state.copy(
                strongStreak = streak,
                recentMisses = 0,
                reason = AdaptiveReason.Steady,
            )
        }
    }

    /**
     * Moves the closest available target-tier question into the next slot.
     * The answered prefix never changes, which keeps answers, stats, and
     * process-restored progress aligned with their questions.
     */
    fun prioritizeNextQuestion(
        questions: List<Question>,
        answeredQuestionIndex: Int,
        target: AdaptiveTier,
        lockFinalQuestion: Boolean = false,
    ): List<Question> {
        val nextIndex = answeredQuestionIndex + 1
        if (nextIndex !in questions.indices) return questions
        val candidateEnd = if (lockFinalQuestion && nextIndex < questions.lastIndex) {
            questions.lastIndex
        } else {
            questions.size
        }
        val bestIndex = (nextIndex until candidateEnd).minWithOrNull(
            compareBy<Int> { index ->
                val tier = AdaptiveTier.fromLabel(questions[index].difficulty) ?: AdaptiveTier.MEDIUM
                abs(tier.ordinal - target.ordinal)
            }.thenBy { it },
        ) ?: return questions
        if (bestIndex == nextIndex) return questions
        return questions.toMutableList().apply {
            val next = this[nextIndex]
            this[nextIndex] = this[bestIndex]
            this[bestIndex] = next
        }
    }

    /** Balanced candidate order used by the local bank before live reprioritizing. */
    fun candidateTierSequence(count: Int): List<AdaptiveTier> {
        if (count <= 0) return emptyList()
        val cycle = listOf(
            AdaptiveTier.MEDIUM,
            AdaptiveTier.EASY,
            AdaptiveTier.HARD,
            AdaptiveTier.OTAKU_MASTER,
        )
        return List(count) { cycle[it % cycle.size] }
    }

    fun statusMessage(state: AdaptiveQuizState): String = when (state.reason) {
        AdaptiveReason.StartingPoint -> "Calibrating from ${state.tier.label}"
        AdaptiveReason.TopicMastery -> "Topic mastery set the starting point"
        AdaptiveReason.StrongPace -> "Strong accuracy and pace raised the target"
        AdaptiveReason.RecentMiss -> "A recent miss kept the next question approachable"
        AdaptiveReason.TimedOut -> "The timeout eased the next target"
        AdaptiveReason.HintSupport -> "Hint use held the next target steady"
        AdaptiveReason.Steady -> "Accuracy and pace are holding steady"
    }

    private fun shiftDown(state: AdaptiveQuizState, reason: AdaptiveReason): AdaptiveQuizState {
        val target = AdaptiveTier.entries[(state.tier.ordinal - 1).coerceAtLeast(0)]
        return state.copy(
            tier = target,
            shiftCount = state.shiftCount + if (target != state.tier) 1 else 0,
            reason = reason,
        )
    }

    private fun shiftUp(state: AdaptiveQuizState, reason: AdaptiveReason): AdaptiveQuizState {
        val target = AdaptiveTier.entries[
            (state.tier.ordinal + 1).coerceAtMost(AdaptiveTier.entries.lastIndex)
        ]
        return state.copy(
            tier = target,
            shiftCount = state.shiftCount + if (target != state.tier) 1 else 0,
            reason = reason,
        )
    }
}
