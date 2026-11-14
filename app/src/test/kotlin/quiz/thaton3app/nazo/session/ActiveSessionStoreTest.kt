package quiz.thaton3app.nazo.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.settings.FakeSharedPreferences
import quiz.thaton3app.nazo.modes.guessing_game.GuessPayload
import quiz.thaton3app.nazo.modes.guessing_game.GuessRoundResult

class ActiveSessionStoreTest {
    private val now = 2_000_000_000_000L

    private fun question(id: Int, correct: String = "A$id") = Question(
        id = id,
        anime = "Exact AI Anime $id",
        theme = "LORE",
        difficulty = "Hard",
        text = "Generated question $id?",
        options = listOf("A$id", "B$id", "C$id", "D$id"),
        correctAnswer = correct,
        explanation = "Generated explanation $id",
    )

    private fun quiz(
        mode: String = "normal",
        destination: ActiveQuizSession.Destination = ActiveQuizSession.Destination.QUIZ,
    ) = ActiveQuizSession(
        savedAtEpochMs = now,
        destination = destination,
        mode = mode,
        questions = listOf(question(1), question(2), question(3)),
        userAnswers = listOf("A1"),
        currentQuestionIndex = 1,
        score = 1,
        difficulty = "Hard",
        aiGenerated = true,
        daily = false,
        pendingAnswer = "B2",
        pendingTimeUp = false,
        questionRemainingMs = 12_345L,
        quizElapsedMs = 44_000L,
        survivalWrongs = if (mode == "survival") 2 else 0,
        blitzRemainingMs = if (mode == "blitz") 31_000L else 0L,
        versusStage = 1,
        versusP1Score = 0,
        versusP1Answers = emptyList(),
    )

    @Test
    fun aiQuizRoundTripPreservesExactQuestionsProgressAndTimers() {
        val prefs = FakeSharedPreferences()
        val store = ActiveSessionStore(prefs) { now + 2_000L }
        val original = quiz()

        store.save(original)
        val restored = store.load() as ActiveQuizSession

        assertEquals(original, restored)
        assertEquals("Generated question 2?", restored.questions[1].text)
        assertEquals(listOf("A2", "B2", "C2", "D2"), restored.questions[1].options)
        assertEquals("B2", restored.pendingAnswer)
        assertTrue(restored.aiGenerated)
        assertEquals(
            10_345L,
            ActiveSessionStore.remainingAfterRestore(
                restored.questionRemainingMs,
                restored.savedAtEpochMs,
                now + 2_000L,
            ),
        )
    }

    @Test
    fun survivalAndBlitzModeSpecificStateRoundTrips() {
        listOf(quiz("survival"), quiz("blitz")).forEach { original ->
            val prefs = FakeSharedPreferences()
            val store = ActiveSessionStore(prefs) { now }
            store.save(original)
            val restored = store.load()
            assertEquals(original, restored)
        }
    }

    @Test
    fun versusHandoffRestoresPlayerOneAnswersWithoutStartingPlayerTwoOver() {
        val questions = listOf(question(1), question(2))
        val handoff = ActiveQuizSession(
            savedAtEpochMs = now,
            destination = ActiveQuizSession.Destination.VERSUS_HANDOFF,
            mode = "versus",
            questions = questions,
            userAnswers = emptyList(),
            currentQuestionIndex = 0,
            score = 0,
            difficulty = "Medium",
            aiGenerated = true,
            daily = false,
            pendingAnswer = null,
            pendingTimeUp = false,
            questionRemainingMs = 0L,
            quizElapsedMs = 30_000L,
            survivalWrongs = 0,
            blitzRemainingMs = 0L,
            versusStage = 2,
            versusP1Score = 1,
            versusP1Answers = listOf("A1", "B2"),
        )
        val store = ActiveSessionStore(FakeSharedPreferences()) { now }

        store.save(handoff)

        assertEquals(handoff, store.load())
    }

    @Test
    fun guessingRoundTripPreservesPayloadResultsAndReveal() {
        val payload = GuessPayload(
            targetEntity = "Satoru Gojo",
            aliases = listOf("Gojo"),
            imageQuery = "Satoru Gojo Jujutsu Kaisen",
            easyMediumOptions = listOf("Satoru Gojo", "Yuji", "Megumi", "Nobara"),
            hardAutocompletePool = listOf("Satoru Gojo", "Yuji", "Megumi", "Nobara"),
        )
        val result = GuessRoundResult(
            round = 1,
            target = "Satoru Gojo",
            aliases = listOf("Gojo"),
            imageQuery = payload.imageQuery,
            correct = true,
            answerText = "Gojo",
            points = 120,
            remainingFraction = 0.5f,
        )
        val original = ActiveGuessSession(
            savedAtEpochMs = now,
            topic = "Jujutsu Kaisen",
            difficulty = "Medium",
            totalRounds = 3,
            round = 1,
            score = 120,
            results = listOf(result),
            roundResult = result,
            phase = SavedGuessPhase.Playing(payload, "https://example.test/gojo.jpg"),
            roundTimerStarted = true,
            roundRemainingMs = 9_000L,
        )
        val store = ActiveSessionStore(FakeSharedPreferences()) { now }

        store.save(original)

        assertEquals(original, store.load())
    }

    @Test
    fun staleFutureAndCorruptSessionsAreClearedInsteadOfResumed() {
        val stalePrefs = FakeSharedPreferences()
        ActiveSessionStore(stalePrefs) { now }.save(
            quiz().copy(savedAtEpochMs = now - ActiveSessionStore.MAX_AGE_MS - 1L)
        )
        assertNull(ActiveSessionStore(stalePrefs) { now }.load())
        assertFalse(stalePrefs.contains(ActiveSessionStore.KEY))

        val futurePrefs = FakeSharedPreferences()
        ActiveSessionStore(futurePrefs) { now }.save(quiz().copy(savedAtEpochMs = now + 1L))
        assertNull(ActiveSessionStore(futurePrefs) { now }.load())

        val corruptPrefs = FakeSharedPreferences()
        corruptPrefs.edit().putString(ActiveSessionStore.KEY, "{not-json").commit()
        assertNull(ActiveSessionStore(corruptPrefs) { now }.load())
        assertFalse(corruptPrefs.contains(ActiveSessionStore.KEY))
    }

    @Test
    fun structuralValidationRejectsImpossibleQuizProgress() {
        val invalid = quiz().copy(
            userAnswers = listOf("A1", "B2"),
            currentQuestionIndex = 1,
            score = 2,
        )

        try {
            ActiveSessionStore(FakeSharedPreferences()) { now }.save(invalid)
            fail("invalid progress should not be persisted")
        } catch (_: IllegalArgumentException) {
            // Expected: answers cannot exceed the current question index.
        }
    }

    @Test
    fun clearRemovesCompletedOrQuitSession() {
        val prefs = FakeSharedPreferences()
        val store = ActiveSessionStore(prefs) { now }
        store.save(quiz())
        assertTrue(prefs.contains(ActiveSessionStore.KEY))

        store.clear()

        assertNull(store.load())
    }
}
