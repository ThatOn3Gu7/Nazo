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
}
