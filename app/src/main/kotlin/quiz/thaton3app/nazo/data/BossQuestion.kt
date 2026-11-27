package quiz.thaton3app.nazo.data

import kotlin.math.abs

/** Deterministic policy for an optional final, higher-value Quiz question. */
object BossQuestion {
    const val BONUS_XP = 20

    fun targetTier(
        difficulty: String,
        adaptiveTarget: AdaptiveTier = AdaptiveTier.MEDIUM,
    ): AdaptiveTier {
        val baseline = if (AdaptiveDifficulty.isAdaptive(difficulty)) {
            adaptiveTarget
        } else {
            AdaptiveTier.fromLabel(difficulty) ?: AdaptiveTier.MEDIUM
        }
        return AdaptiveTier.entries[
            (baseline.ordinal + 1).coerceAtMost(AdaptiveTier.entries.lastIndex)
        ]
    }

    fun isFinale(enabled: Boolean, questionIndex: Int, questionCount: Int): Boolean =
        enabled && questionCount > 0 && questionIndex == questionCount - 1

    fun bonusXp(enabled: Boolean, correct: Boolean): Int =
        if (enabled && correct) BONUS_XP else 0

    /**
     * Keeps ordinary fixed-tier candidates first and reserves the closest
     * available elevated candidate for the final slot. Identity deduplication
     * prevents the finale from also appearing earlier in the same run.
     */
    fun buildLocalDeck(
        regularCandidates: List<Question>,
        bossCandidates: List<Question>,
        count: Int,
        enabled: Boolean,
        difficulty: String,
        adaptiveTarget: AdaptiveTier = AdaptiveTier.MEDIUM,
    ): List<Question> {
        if (count <= 0) return emptyList()
        if (!enabled) return regularCandidates.distinctBy(Question::identity).take(count)
        val target = targetTier(difficulty, adaptiveTarget)
        val allBossCandidates = (bossCandidates + regularCandidates).distinctBy(Question::identity)
        val boss = closestCandidate(allBossCandidates, target)
            ?: return regularCandidates.distinctBy(Question::identity).take(count)
        val regular = (regularCandidates + bossCandidates)
            .distinctBy(Question::identity)
            .filterNot { it.identity == boss.identity }
            .take((count - 1).coerceAtLeast(0))
        return (regular + boss).take(count)
    }

    /** Moves one elevated candidate to the final slot without changing deck size. */
    fun arrangeGeneratedDeck(
        candidates: List<Question>,
        enabled: Boolean,
        difficulty: String,
        adaptiveTarget: AdaptiveTier = AdaptiveTier.MEDIUM,
    ): List<Question> {
        if (!enabled || candidates.isEmpty()) return candidates
        val target = targetTier(difficulty, adaptiveTarget)
        val boss = closestCandidate(candidates, target) ?: return candidates
        val bossIndex = candidates.indexOfFirst { it === boss }.takeIf { it >= 0 } ?: return candidates
        return candidates.filterIndexed { index, _ -> index != bossIndex } + boss
    }

    private fun closestCandidate(
        candidates: List<Question>,
        target: AdaptiveTier,
    ): Question? = candidates.withIndex().minWithOrNull(
        compareBy<IndexedValue<Question>> { indexed ->
            val tier = AdaptiveTier.fromLabel(indexed.value.difficulty) ?: AdaptiveTier.MEDIUM
            abs(tier.ordinal - target.ordinal)
        }.thenByDescending { indexed ->
            AdaptiveTier.fromLabel(indexed.value.difficulty)?.ordinal ?: AdaptiveTier.MEDIUM.ordinal
        }.thenBy { it.index },
    )?.value
}
