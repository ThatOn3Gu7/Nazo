package quiz.thaton3app.nazo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.QuizStats

class PersonalizedHomeFocusTest {
    private val today = 20_000L

    @Test
    fun streakAtRiskBeatsOutstandingMistakes() {
        val focus = personalizedHomeFocus(
            stats = QuizStats(
                totalQuizzes = 5,
                currentStreakDays = 5,
                bestStreakDays = 5,
                lastQuizEpochDay = today - 1,
            ),
            dailyCompleted = false,
            practiceCount = 4,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.Daily, focus.action)
        assertTrue(focus.title.contains("5-day streak"))
    }

    @Test
    fun aShieldCoveredYesterdayStillMakesTodayTheStreakPriority() {
        val focus = personalizedHomeFocus(
            stats = QuizStats(
                totalQuizzes = 5,
                currentStreakDays = 5,
                bestStreakDays = 5,
                lastQuizEpochDay = today - 2,
                protectedStreakEpochDay = today - 1,
            ),
            dailyCompleted = false,
            practiceCount = 2,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.Daily, focus.action)
    }

    @Test
    fun mistakesBecomeThePriorityOnceTheStreakIsSafe() {
        val focus = personalizedHomeFocus(
            stats = QuizStats(
                totalQuizzes = 3,
                currentStreakDays = 2,
                bestStreakDays = 2,
                lastQuizEpochDay = today,
            ),
            dailyCompleted = false,
            practiceCount = 1,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.Practice, focus.action)
        assertTrue(focus.title.contains("1 miss"))
    }

    @Test
    fun pendingDailyIsTheDefaultBeforeTopicRecommendations() {
        val focus = personalizedHomeFocus(
            stats = statsWithRecentWeakTopic(),
            dailyCompleted = false,
            practiceCount = 0,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.Daily, focus.action)
    }

    @Test
    fun recentlyWeakTopicIsRecommendedAfterDailyAndMistakesAreClear() {
        val focus = personalizedHomeFocus(
            stats = statsWithRecentWeakTopic(),
            dailyCompleted = true,
            practiceCount = 0,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.TopicQuiz, focus.action)
        assertEquals("Bleach", focus.topic)
        assertEquals("Adaptive", focus.difficulty)
        assertTrue(focus.title.contains("Strengthen"))
    }

    @Test
    fun mostRecentTopicContinuesWhenNoRecentTopicNeedsPractice() {
        val focus = personalizedHomeFocus(
            stats = QuizStats(
                totalQuizzes = 2,
                totalQuestionsAnswered = 8,
                totalCorrect = 7,
                animeAnswered = mapOf("Frieren" to 8),
                animeCorrect = mapOf("Frieren" to 7),
                animeRecentOutcomes = mapOf("Frieren" to "11110111"),
                recentTopics = listOf("Frieren"),
            ),
            dailyCompleted = true,
            practiceCount = 0,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.TopicQuiz, focus.action)
        assertEquals("Frieren", focus.topic)
        assertTrue(focus.title.contains("Frieren"))
    }

    @Test
    fun newProfileFallsBackToMixedAdaptiveQuizAfterDaily() {
        val focus = personalizedHomeFocus(
            stats = QuizStats(),
            dailyCompleted = true,
            practiceCount = 0,
            todayEpochDay = today,
        )

        assertEquals(HomeFocusAction.TopicQuiz, focus.action)
        assertEquals("", focus.topic)
        assertEquals(5, focus.questionCount)
    }

    private fun statsWithRecentWeakTopic() = QuizStats(
        totalQuizzes = 4,
        totalQuestionsAnswered = 8,
        totalCorrect = 3,
        animeAnswered = mapOf("Bleach" to 8),
        animeCorrect = mapOf("Bleach" to 3),
        animeRecentOutcomes = mapOf("Bleach" to "10100010"),
        recentTopics = listOf("Bleach"),
    )
}
