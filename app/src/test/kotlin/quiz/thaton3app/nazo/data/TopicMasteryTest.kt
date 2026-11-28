package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicMasteryTest {

    @Test
    fun completedQuizKeepsOnlyTheLatestEightOrderedOutcomesPerTopic() {
        val outcomes = listOf(false, false, true, true, true, false, true, true, true, true)
        val questions = outcomes.indices.map { index -> question("Naruto", "Question $index") }
        val answers = outcomes.map { correct -> if (correct) "Correct" else "Wrong" }

        val stats = QuizStats().record("Medium", questions, answers)

        assertEquals(10, stats.animeAnswered["Naruto"])
        assertEquals(7, stats.animeCorrect["Naruto"])
        assertEquals("11101111", stats.animeRecentOutcomes["Naruto"])
    }

    @Test
    fun masteryDistinguishesStrongImprovingWeakAndBuildingTopics() {
        val stats = QuizStats(
            totalQuestionsAnswered = 26,
            totalCorrect = 19,
            animeAnswered = mapOf(
                "Strong" to 8,
                "Improving" to 8,
                "Needs work" to 8,
                "Building" to 2,
            ),
            animeCorrect = mapOf(
                "Strong" to 7,
                "Improving" to 4,
                "Needs work" to 6,
                "Building" to 2,
            ),
            animeRecentOutcomes = mapOf(
                "Strong" to "11111110",
                "Improving" to "00001111",
                "Needs work" to "11111100",
                "Building" to "11",
            ),
        )

        val mastery = stats.topicMastery().associateBy(TopicMastery::topic)

        assertEquals(TopicMasteryStatus.Strong, mastery.getValue("Strong").status)
        assertEquals(TopicMasteryStatus.Improving, mastery.getValue("Improving").status)
        assertEquals(TopicMasteryStatus.NeedsPractice, mastery.getValue("Needs work").status)
        assertEquals(75, mastery.getValue("Needs work").accuracyPercent)
        assertEquals(50, mastery.getValue("Needs work").recentAccuracyPercent)
        assertEquals(TopicMasteryStatus.Building, mastery.getValue("Building").status)
        assertEquals(100, mastery.getValue("Improving").trendDeltaPercent)
        assertEquals(100, mastery.getValue("Improving").recentAccuracyPercent)

        val highlights = topicMasteryHighlights(mastery.values.toList(), limit = 4)
        assertEquals(4, highlights.map(TopicMastery::status).distinct().size)
    }

    @Test
    fun legacyAggregateStatsStillProduceAUsefulConfidenceSignal() {
        val mastery = QuizStats(
            totalQuestionsAnswered = 6,
            totalCorrect = 5,
            animeAnswered = mapOf("One Piece" to 6),
            animeCorrect = mapOf("One Piece" to 5),
        ).topicMastery().single()

        assertEquals(83, mastery.accuracyPercent)
        assertEquals(83, mastery.recentAccuracyPercent)
        assertTrue(mastery.confidencePercent >= 65)
        assertEquals(TopicMasteryStatus.Strong, mastery.status)
    }

    @Test
    fun guessingRoundsFeedTheSameRecentMasteryWindowInPlayedOrder() {
        val stats = QuizStats().recordGuessing(
            difficulty = "Hard",
            topic = "Bleach",
            answered = 4,
            correct = 2,
            outcomes = listOf(false, true, false, true),
        )

        assertEquals("0101", stats.animeRecentOutcomes["Bleach"])
    }

    private fun question(anime: String, text: String) = Question(
        anime = anime,
        text = text,
        options = listOf("Correct", "Wrong"),
        correctAnswer = "Correct",
        explanation = "Explanation",
    )
}
