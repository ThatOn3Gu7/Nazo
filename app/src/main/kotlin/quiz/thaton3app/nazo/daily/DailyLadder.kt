package quiz.thaton3app.nazo.daily

/**
 * The Daily's visible three-stage difficulty path.
 *
 * Keeping the path as data makes question selection and every UI surface agree:
 * Home previews the same Warm-up → Climb → Summit progression that gameplay
 * advances and Results marks complete. It remains fixed and local rather than
 * adapting mid-run; everyone therefore keeps the same reproducible daily set.
 */
enum class DailyLadderStage(
    val title: String,
    val difficultyLabel: String,
    val questionCount: Int,
    private val acceptedDifficulties: Set<String>,
) {
    WARM_UP("Warm-up", "Easy", 2, setOf("Easy")),
    CLIMB("Climb", "Medium", 2, setOf("Medium")),
    SUMMIT("Summit", "Hard", 1, setOf("Hard", "Otaku Master"));

    fun acceptsDifficulty(difficulty: String): Boolean =
        acceptedDifficulties.any { it.equals(difficulty, ignoreCase = true) }
}

data class DailyLadderProgress(
    val stage: DailyLadderStage,
    /** Zero-based position in [DailyLadder.stages]. */
    val stageIndex: Int,
    /** One-based question position inside [stage]. */
    val questionInStage: Int,
    val questionIndex: Int,
) {
    val stageNumber: Int get() = stageIndex + 1
    val totalStages: Int get() = DailyLadder.stages.size
}

object DailyLadder {
    const val QUESTION_COUNT = 5

    val stages: List<DailyLadderStage> = DailyLadderStage.entries

    /** Returns null for an index outside the five-question Daily path. */
    fun progressForQuestion(questionIndex: Int): DailyLadderProgress? {
        if (questionIndex !in 0 until QUESTION_COUNT) return null
        var stageStart = 0
        stages.forEachIndexed { stageIndex, stage ->
            val stageEnd = stageStart + stage.questionCount
            if (questionIndex < stageEnd) {
                return DailyLadderProgress(
                    stage = stage,
                    stageIndex = stageIndex,
                    questionInStage = questionIndex - stageStart + 1,
                    questionIndex = questionIndex,
                )
            }
            stageStart = stageEnd
        }
        return null
    }

    fun stageForQuestion(questionIndex: Int): DailyLadderStage? =
        progressForQuestion(questionIndex)?.stage
}
