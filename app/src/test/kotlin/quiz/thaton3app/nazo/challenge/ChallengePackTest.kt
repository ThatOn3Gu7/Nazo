package quiz.thaton3app.nazo.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.settings.FakeSharedPreferences

class ChallengePackTest {
    @Test
    fun catalogIsShippedReviewedAndDeterministic() {
        val stableIdentities = LocalQuestionBank.stablePool().map { it.identity }.toSet()

        assertEquals(4, ChallengePackCatalog.all.size)
        assertEquals(
            listOf(
                "shinobi-legacy-v1",
                "grand-line-odyssey-v1",
                "titan-frontlines-v1",
                "hashira-trial-v1",
            ),
            ChallengePackCatalog.all.map(ChallengePack::id),
        )
        ChallengePackCatalog.all.forEach { pack ->
            assertEquals(8, pack.questionCount)
            assertEquals(pack.questionCount, pack.questions.map { it.identity }.distinct().size)
            assertTrue(pack.questions.all { it.identity in stableIdentities })
            assertTrue(pack.questions.all { it.options.size == 4 })
            assertTrue(pack.questions.all { it.correctAnswer in it.options })
            assertTrue(pack.questions.all { it.explanation.isNotBlank() })
        }
    }

    @Test
    fun progressTracksBestLastCompletionsAndFirstMastery() {
        var now = 100L
        val store = ChallengePackProgressStore(FakeSharedPreferences()) { now++ }
        val pack = ChallengePackCatalog.all.first()

        val first = store.submit(pack.id, 5)
        val lowerReplay = store.submit(pack.id, 3)
        val mastery = store.submit(pack.id, pack.questionCount)
        val masteredReplay = store.submit(pack.id, pack.questionCount)

        assertTrue(first.newBest)
        assertEquals(null, first.previousBest)
        assertFalse(lowerReplay.newBest)
        assertEquals(5, lowerReplay.progress.bestScore)
        assertEquals(3, lowerReplay.progress.lastScore)
        assertTrue(mastery.newBest)
        assertTrue(mastery.firstMastery)
        assertFalse(masteredReplay.firstMastery)
        assertEquals(4, masteredReplay.progress.completionCount)
        assertTrue(masteredReplay.progress.mastered)
    }

    @Test
    fun progressJsonRoundTripsAllMeaningfulState() {
        val original = listOf(
            ChallengePackProgress(
                packId = ChallengePackCatalog.all[1].id,
                bestScore = 7,
                questionCount = 8,
                completionCount = 4,
                lastScore = 6,
                lastPlayedAtEpochMs = 123_456L,
            ),
        )

        val encoded = ChallengePackProgressJson.encode(original)

        assertEquals(original, ChallengePackProgressJson.decode(encoded))
        assertNotEquals("", encoded)
    }

    @Test
    fun corruptProgressIsRepairedWithoutCrashing() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(
            ChallengePackProgressStore.KEY,
            "{\"version\":1,\"packs\":[{\"id\":\"unknown\",\"bestScore\":99}]}",
        ).commit()
        val store = ChallengePackProgressStore(prefs) { 10L }

        assertTrue(store.get().isEmpty())
        assertEquals(emptyList<ChallengePackProgress>(), store.get())
    }
}
