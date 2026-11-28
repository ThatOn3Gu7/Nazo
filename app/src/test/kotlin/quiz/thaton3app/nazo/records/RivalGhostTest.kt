package quiz.thaton3app.nazo.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.settings.FakeSharedPreferences

class RivalGhostTest {
    private fun setup(
        topic: String = "Naruto",
        difficulty: String = "Hard",
        count: Int = 5,
    ) = RivalGhostSetup(topic, difficulty, count)

    @Test
    fun firstCompletionCreatesADurableGhost() {
        val prefs = FakeSharedPreferences()
        val store = RivalGhostStore(prefs) { 1_000L }

        val result = store.submit(setup("  Naruto   Shippuden "), 3, 5, 80_000L)
        val restored = RivalGhostStore(prefs).get().single()

        assertEquals(RivalGhostVerdict.FirstGhost, result.verdict)
        assertTrue(result.savedAsBest)
        assertEquals("Naruto Shippuden", restored.setup.topic)
        assertEquals(3, restored.correct)
        assertEquals(80_000L, restored.elapsedMillis)
    }

    @Test
    fun higherScoreWinsEvenWhenItTakesLonger() {
        val store = RivalGhostStore(FakeSharedPreferences()) { 2_000L }
        store.submit(setup(), 3, 5, 45_000L)

        val result = store.submit(setup(), 4, 5, 90_000L)

        assertEquals(RivalGhostVerdict.PlayerWon, result.verdict)
        assertTrue(result.savedAsBest)
        assertEquals(4, store.find(setup())?.correct)
        assertEquals(90_000L, store.find(setup())?.elapsedMillis)
    }

    @Test
    fun equalScoreMustBeFasterToReplaceTheGhost() {
        var now = 1_000L
        val store = RivalGhostStore(FakeSharedPreferences()) { now++ }
        store.submit(setup(), 4, 5, 60_000L)

        val slower = store.submit(setup(), 4, 5, 61_000L)
        val faster = store.submit(setup(), 4, 5, 55_000L)

        assertEquals(RivalGhostVerdict.RivalWon, slower.verdict)
        assertFalse(slower.savedAsBest)
        assertEquals(RivalGhostVerdict.PlayerWon, faster.verdict)
        assertTrue(faster.savedAsBest)
        assertEquals(55_000L, store.find(setup())?.elapsedMillis)
    }

    @Test
    fun exactScoreAndTimeIsAPhotoFinish() {
        val store = RivalGhostStore(FakeSharedPreferences()) { 1_000L }
        store.submit(setup(), 4, 5, 60_000L)

        val result = store.submit(setup(), 4, 5, 60_000L)

        assertEquals(RivalGhostVerdict.Draw, result.verdict)
        assertFalse(result.savedAsBest)
    }

    @Test
    fun setupsMatchCaseAndWhitespaceButNotDifficultyOrLength() {
        val store = RivalGhostStore(FakeSharedPreferences()) { 1_000L }
        store.submit(setup("One   Piece", "Medium", 10), 8, 10, 100_000L)

        assertNotNull(store.find(setup(" one piece ", "medium", 10)))
        assertEquals(null, store.find(setup("One Piece", "Hard", 10)))
        assertEquals(null, store.find(setup("One Piece", "Medium", 5)))
    }

    @Test
    fun liveRaceProjectsScoreAndProgressFromTheSavedFinishTime() {
        val profile = RivalGhostProfile(
            setup = setup(count = 10),
            correct = 8,
            totalQuestions = 10,
            elapsedMillis = 100_000L,
            recordedAtEpochMs = 1_000L,
        )

        val halfway = RivalGhostPolicy.liveRace(profile, 50_000L)
        val finished = RivalGhostPolicy.liveRace(profile, 120_000L)

        assertEquals(0.5f, halfway.progressFraction)
        assertEquals(5, halfway.projectedAnswered)
        assertEquals(4, halfway.projectedCorrect)
        assertEquals(50_000L, halfway.targetMillisRemaining)
        assertEquals(1f, finished.progressFraction)
        assertEquals(10, finished.projectedAnswered)
        assertEquals(8, finished.projectedCorrect)
        assertEquals(0L, finished.targetMillisRemaining)
    }

    @Test
    fun automaticHistoryKeepsOnlyTheTwelveNewestSetups() {
        var now = 1_000L
        val store = RivalGhostStore(FakeSharedPreferences()) { now++ }
        repeat(RivalGhostPolicy.MAX_PROFILES + 2) { index ->
            store.submit(setup(topic = "Topic $index"), 2, 5, 60_000L)
        }

        val profiles = store.get()
        assertEquals(RivalGhostPolicy.MAX_PROFILES, profiles.size)
        assertEquals("Topic 13", profiles.first().setup.topic)
        assertFalse(profiles.any { it.setup.topic == "Topic 0" })
        assertFalse(profiles.any { it.setup.topic == "Topic 1" })
    }

    @Test
    fun timeLabelUsesCompactRaceClockFormatting() {
        assertEquals("0:00", rivalGhostTimeLabel(0L))
        assertEquals("1:02", rivalGhostTimeLabel(62_000L))
    }

    @Test
    fun malformedStorageRepairsToAnEmptyDocument() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(RivalGhostStore.KEY, "{broken").commit()

        val store = RivalGhostStore(prefs)

        assertTrue(store.get().isEmpty())
        assertTrue(RivalGhostJson.decode(prefs.getString(RivalGhostStore.KEY, null)!!).isEmpty())
    }

    @Test
    fun malformedProfilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RivalGhostJson.decode(
                """{"version":1,"profiles":[{"topic":"Naruto","difficulty":"Hard","questionCount":5,"correct":6,"totalQuestions":5,"elapsedMillis":1000,"recordedAt":1}]}"""
            )
        }
    }
}
