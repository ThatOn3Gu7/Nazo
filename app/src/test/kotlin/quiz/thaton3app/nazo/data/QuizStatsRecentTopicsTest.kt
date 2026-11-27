package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizStatsRecentTopicsTest {

    @Test
    fun completedRunsKeepSixTopicsNewestFirstAndMoveReplaysToTheFront() {
        var stats = QuizStats()
        (1..7).forEach { index ->
            val question = question("Topic $index")
            stats = stats.record("Medium", listOf(question), listOf(question.correctAnswer))
        }

        assertEquals(
            listOf("Topic 7", "Topic 6", "Topic 5", "Topic 4", "Topic 3", "Topic 2"),
            stats.recentTopics,
        )

        val replay = question("Topic 4")
        stats = stats.record("Medium", listOf(replay), listOf(replay.correctAnswer))

        assertEquals(
            listOf("Topic 4", "Topic 7", "Topic 6", "Topic 5", "Topic 3", "Topic 2"),
            stats.recentTopics,
        )
    }

    @Test
    fun guessingTopicsJoinTheSameRecentListAndUnknownTopicsDoNot() {
        val stats = QuizStats(recentTopics = listOf("Naruto"))
            .recordGuessing("Medium", "Bleach", answered = 3, correct = 2)
            .recordGuessing("Medium", "", answered = 3, correct = 2)

        assertEquals(listOf("Bleach", "Naruto"), stats.recentTopics)
    }

    private fun question(topic: String) = Question(
        anime = topic,
        text = "Question about $topic",
        options = listOf("A", "B", "C", "D"),
        correctAnswer = "A",
    )
}
