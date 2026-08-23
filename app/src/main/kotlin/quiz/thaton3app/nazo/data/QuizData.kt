package quiz.thaton3app.nazo.data

// The basic structure of a single question
data class Question(
    val id: Int,
    val theme: String,
    val text: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

// Our temporary local database for testing
object DummyData {
    val sampleQuestions = listOf(
        Question(
            id = 1,
            theme = "CURSED TECHNIQUES",
            text = "Which Cursed Technique allows Domain Expansion: Malevolent Shrine?",
            options = listOf(
                "Ten Shadows Technique", 
                "Malevolent Shrine (Sukuna)", 
                "Infinite Void (Gojo)", 
                "Idle Transfiguration"
            ),
            correctAnswer = "Malevolent Shrine (Sukuna)",
            explanation = "Malevolent Shrine is Sukuna's innate Domain Expansion — a rare open-barrier domain that guarantees Cleave and Dismantle across a wide radius."
        ),
        Question(
            id = 2,
            theme = "CURSED TECHNIQUES",
            text = "Who is known as the 'King of Curses'?",
            options = listOf("Satoru Gojo", "Megumi Fushiguro", "Ryomen Sukuna", "Yuji Itadori"),
            correctAnswer = "Ryomen Sukuna",
            explanation = "Ryomen Sukuna is a legendary cursed spirit from the Heian Era, widely feared and recognized as the undisputed King of Curses."
        )
    )

    /**
     * When no API key is configured we still want to honour the user's requested question
     * count. The local prototype set is small, so we cycle through it (re-tagging ids) until
     * the requested size is reached.
     */
    fun buildFallbackQuestions(count: Int): List<Question> {
        if (count <= sampleQuestions.size) return sampleQuestions.take(count)
        val out = mutableListOf<Question>()
        repeat(count) { i ->
            val base = sampleQuestions[i % sampleQuestions.size]
            out += base.copy(id = i + 1)
        }
        return out
    }
}

