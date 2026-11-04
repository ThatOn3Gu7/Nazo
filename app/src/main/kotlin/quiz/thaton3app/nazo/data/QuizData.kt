package quiz.thaton3app.nazo.data

// The basic structure of a single question.
// `anime` is the series the question belongs to (used for "top mastered anime"
// stats); `theme` is a finer sub-category (characters / story / powers / ...).
data class Question(
    val id: Int = 0,
    val anime: String = "",
    val theme: String = "",
    val difficulty: String = "Medium",
    val text: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val explanation: String = "",
) {
    /**
     * Returns a copy with the option ORDER shuffled. The [correctAnswer] string is
     * untouched, so the right answer simply lands in a different position — keeping
     * repeated questions from feeling repetitive across runs.
     */
    fun withShuffledOptions(): Question = copy(options = options.shuffled())

    /**
     * Stable identity for deduplication: the franchise plus the normalized
     * question text. Question text ALONE is not unique — several series carry
     * generic questions like "What is the name of the first arc?" — so keying on
     * text lets one franchise silently drop another's perfectly valid question.
     * Keying on anime + text only removes true duplicates (same franchise, same
     * wording). Used by LocalQuestionBank.getQuestions, DailyChallenge and
     * MissedQuestionsStore.
     */
    val identity: String
        get() = anime.trim().lowercase() + "||" + text.trim().lowercase()
}
