package quiz.thaton3app.nazo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for rapid Next/Finish dispatch at the state boundary. */
class GameplayProgressionGateTest {

    @Test
    fun normalSurvivalAndBlitzConsumeEachRenderedQuestionOnlyOnce() {
        listOf("normal", "survival", "blitz").forEach { mode ->
            val gate = QuizProgressionGate()
            gate.startSession()
            val firstQuestion = gate.ticket(stage = 0, questionIndex = 0)
            var progressions = 0

            repeat(5) {
                if (gate.tryConsume(firstQuestion, currentStage = 0, currentQuestionIndex = 0)) {
                    progressions++
                }
            }
            assertEquals("$mode rapid Next must be one-shot", 1, progressions)

            val secondQuestion = gate.ticket(stage = 0, questionIndex = 1)
            assertTrue(gate.tryConsume(secondQuestion, currentStage = 0, currentQuestionIndex = 1))
            assertFalse(gate.tryConsume(secondQuestion, currentStage = 0, currentQuestionIndex = 1))
        }
    }

    @Test
    fun normalQuizFinishRecordsCompletionOnlyOnce() {
        val gate = QuizProgressionGate()
        gate.startSession()
        val finish = gate.ticket(stage = 0, questionIndex = 9)
        var resultWrites = 0

        repeat(4) {
            if (gate.tryConsume(finish, currentStage = 0, currentQuestionIndex = 9)) {
                resultWrites++ // mirrors the guarded stats/record/result branch
            }
        }

        assertEquals(1, resultWrites)
    }

    @Test
    fun versusTicketsCannotSkipHandoffOrDuplicatePlayerTwoResults() {
        val gate = QuizProgressionGate()
        gate.startSession()
        val playerOneFinish = gate.ticket(stage = 1, questionIndex = 4)

        assertTrue(gate.tryConsume(playerOneFinish, currentStage = 1, currentQuestionIndex = 4))
        // State has synchronously moved to P2/question 0. A queued P1 callback
        // cannot consume the new stage even before the old UI is gone.
        assertFalse(gate.tryConsume(playerOneFinish, currentStage = 2, currentQuestionIndex = 0))

        val playerTwoFinish = gate.ticket(stage = 2, questionIndex = 4)
        var resultNavigations = 0
        repeat(3) {
            if (gate.tryConsume(playerTwoFinish, currentStage = 2, currentQuestionIndex = 4)) {
                resultNavigations++
            }
        }
        assertEquals(1, resultNavigations)
    }

    @Test
    fun ticketFromAnOlderQuizSessionCannotAdvanceANewRun() {
        val gate = QuizProgressionGate()
        gate.startSession()
        val old = gate.ticket(stage = 0, questionIndex = 0)
        gate.startSession()

        assertFalse(gate.tryConsume(old, currentStage = 0, currentQuestionIndex = 0))
        assertTrue(
            gate.tryConsume(
                gate.ticket(stage = 0, questionIndex = 0),
                currentStage = 0,
                currentQuestionIndex = 0,
            )
        )
    }

    @Test
    fun guessingNextCannotSkipRoundsAndFinalResultRecordingIsOneShot() {
        val gate = GuessProgressionGate()
        gate.startSession()
        val roundOne = gate.ticket(round = 1)
        var nextRoundTransitions = 0
        repeat(5) {
            if (gate.tryConsume(roundOne, currentRound = 1)) nextRoundTransitions++
        }
        assertEquals(1, nextRoundTransitions)

        val finalRound = gate.ticket(round = 3)
        var statsAndResultWrites = 0
        repeat(5) {
            if (gate.tryConsume(finalRound, currentRound = 3)) statsAndResultWrites++
        }
        assertEquals(1, statsAndResultWrites)
    }

    @Test
    fun guessingTicketFromOldSessionOrWrongRoundIsRejected() {
        val gate = GuessProgressionGate()
        gate.startSession()
        val old = gate.ticket(round = 1)
        gate.startSession()

        assertFalse(gate.tryConsume(old, currentRound = 1))
        val current = gate.ticket(round = 1)
        assertFalse(gate.tryConsume(current, currentRound = 2))
        assertTrue(gate.tryConsume(current, currentRound = 1))
    }
}
