package quiz.thaton3app.nazo.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import quiz.thaton3app.nazo.data.Question

class InstantMistakeRematchTest {

    @Test
    fun rematchContainsOnlyAnsweredMistakesFromTheCompletedRun() {
        val correct = question("Correct", "A")
        val wrong = question("Wrong", "B")
        val timedOut = question("Timed out", "C")
        val neverReached = question("Never reached", "D")

        val rematch = instantMistakeRematchQuestions(
            questions = listOf(correct, wrong, timedOut, neverReached),
            answers = listOf("A", "Not B", null),
        )

        assertEquals(listOf(wrong, timedOut), rematch)
    }

    @Test
    fun rematchKeepsFirstMissOrderAndDropsDuplicateQuestionIdentities() {
        val first = question("First miss", "A")
        val duplicate = first.copy(options = listOf("A", "C"))
        val second = question("Second miss", "B")

        val rematch = instantMistakeRematchQuestions(
            questions = listOf(first, duplicate, second),
            answers = listOf("wrong", "also wrong", "wrong"),
        )

        assertEquals(listOf(first.identity, second.identity), rematch.map(Question::identity))
    }

    private fun question(text: String, correctAnswer: String) = Question(
        anime = "Nazo",
        text = text,
        options = listOf(correctAnswer, "Wrong"),
        correctAnswer = correctAnswer,
        explanation = "Explanation",
    )
}
