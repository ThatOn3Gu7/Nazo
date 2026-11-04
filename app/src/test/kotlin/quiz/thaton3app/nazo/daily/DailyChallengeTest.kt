package quiz.thaton3app.nazo.daily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizStats

/**
 * JVM unit tests for [DailyChallenge.questionsFor] determinism.
 *
 * The daily challenge promises one identical five-question run per date.
 * The old implementation fed it LocalQuestionBank.getQuestions()'s unseeded
 * output, so the seeded option shuffle permuted an already-randomised option
 * list — repeated calls could show the same question with different option
 * order. These tests pin the guarantee: same epoch day -> identical questions,
 * order and options, no matter how often or in what company it is computed.
 */
class DailyChallengeTest {

    private val days = listOf(20_000L, 20_001L, 20_123L, 25_000L)

    private fun signature(qs: List<Question>) =
        qs.map { listOf(it.identity, it.options.joinToString("|"), it.correctAnswer) }

    // ---------------------------------------------------------- determinism

    @Test
    fun sameEpochDayIsIdenticalEveryTime() {
        for (day in days) {
            val first = DailyChallenge.questionsFor(day)
            repeat(3) {
                assertEquals(signature(first), signature(DailyChallenge.questionsFor(day)))
            }
        }
    }

    @Test
    fun interleavedCallsForOtherDaysDoNotChangeTheRun() {
        // The regression that motivated this suite: computing other days in
        // between must not perturb one day's questions or option order.
        val day = 20_000L
        val first = signature(DailyChallenge.questionsFor(day))
        DailyChallenge.questionsFor(day + 1)
        DailyChallenge.questionsFor(day + 345)
        DailyChallenge.questionsFor(25_000L)
        assertEquals(first, signature(DailyChallenge.questionsFor(day)))
    }

    @Test
    fun differentDaysProduceDifferentRuns() {
        assertNotEquals(
            signature(DailyChallenge.questionsFor(20_000L)),
            signature(DailyChallenge.questionsFor(20_001L)),
        )
        assertNotEquals(
            signature(DailyChallenge.questionsFor(25_000L)),
            signature(DailyChallenge.questionsFor(25_001L)),
        )
    }

    // ------------------------------------------------------------- shape

    @Test
    fun everyDayHasExactlyFiveQuestions() {
        for (day in days) {
            assertEquals(DailyChallenge.QUESTION_COUNT, DailyChallenge.questionsFor(day).size)
            assertEquals(5, DailyChallenge.questionsFor(day).size)
        }
    }

    @Test
    fun everyQuestionHasFourDistinctOptionsAndItsAnswerAmongThem() {
        for (day in days) {
            for (q in DailyChallenge.questionsFor(day)) {
                assertEquals(4, q.options.size)
                assertTrue(q.options.all { it.isNotBlank() })
                assertEquals(4, q.options.toSet().size)
                assertTrue(
                    "scored answer must be one of the options: ${q.correctAnswer}",
                    q.correctAnswer in q.options,
                )
            }
        }
    }

    @Test
    fun difficultyRampIsTwoEasyTwoMediumOneHardOrOtaku() {
        for (day in days) {
            val qs = DailyChallenge.questionsFor(day)
            val counts = qs.groupingBy { it.difficulty.lowercase() }.eachCount()
            assertEquals(2, counts["easy"])
            assertEquals(2, counts["medium"])
            assertEquals(
                1,
                (counts["hard"] ?: 0) + (counts["otaku master"] ?: 0),
            )
        }
    }

    // ---------------------------------------------------------- day boundary

    @Test
    fun dayBoundaryStaysOnQuizStatsLocalEpochDay() {
        assertEquals(QuizStats.localEpochDay(), DailyChallenge.todayEpochDay())
    }
}
