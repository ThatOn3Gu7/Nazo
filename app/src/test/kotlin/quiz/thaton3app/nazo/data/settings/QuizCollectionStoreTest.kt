package quiz.thaton3app.nazo.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCollectionStoreTest {

    private fun setup(
        topic: String = "Naruto",
        mode: String = "QUIZ",
        difficulty: String = "Hard",
        amount: Int = 10,
    ) = QuizCollectionSetup(topic, mode, difficulty, amount)

    @Test
    fun savingRoundTripsEverySetupField() {
        val prefs = FakeSharedPreferences()
        val store = QuizCollectionStore(prefs)

        val saved = store.save(setup(topic = "  Naruto   Shippuden  "))
        val restored = QuizCollectionStore(prefs).get().single()

        assertEquals(saved, restored)
        assertEquals("Naruto Shippuden", restored.topic)
        assertEquals("QUIZ", restored.mode)
        assertEquals("Hard", restored.difficulty)
        assertEquals(10, restored.amount)
        assertTrue(restored.id.isNotBlank())
    }

    @Test
    fun duplicateSetupMovesToTheFrontWithoutCreatingAnotherCopy() {
        val store = QuizCollectionStore(FakeSharedPreferences())
        val first = store.save(setup(topic = "Naruto"))
        store.save(setup(topic = "One Piece"))

        val repeated = store.save(setup(topic = " naruto ", difficulty = "hard"))

        assertEquals(first.id, repeated.id)
        assertEquals(listOf("Naruto", "One Piece"), store.get().map { it.topic })
    }

    @Test
    fun allFiveModesKeepOnlyTheirMeaningfulLength() {
        val store = QuizCollectionStore(FakeSharedPreferences())

        store.save(setup(mode = "QUIZ", amount = 15))
        store.save(setup(topic = "Bleach", mode = "VERSUS", amount = 5))
        store.save(setup(topic = "Luffy", mode = "GUESSING", amount = 3))
        store.save(setup(topic = "Chainsaw Man", mode = "SURVIVAL", amount = 99))
        store.save(setup(topic = "Frieren", mode = "BLITZ", amount = 99))

        val byMode = store.get().associateBy { it.mode }
        assertEquals(15, byMode.getValue("QUIZ").amount)
        assertEquals(5, byMode.getValue("VERSUS").amount)
        assertEquals(3, byMode.getValue("GUESSING").amount)
        assertEquals(0, byMode.getValue("SURVIVAL").amount)
        assertEquals(0, byMode.getValue("BLITZ").amount)
    }

    @Test
    fun adaptiveCollectionsAreQuizOnly() {
        val store = QuizCollectionStore(FakeSharedPreferences())

        assertNotNull(store.save(setup(difficulty = "Adaptive")))
        assertThrows(IllegalArgumentException::class.java) {
            store.save(setup(mode = "SURVIVAL", difficulty = "Adaptive", amount = 0))
        }
    }

    @Test
    fun collectionLimitNeverSilentlyRetiresAFavorite() {
        val store = QuizCollectionStore(FakeSharedPreferences())
        repeat(QuizCollectionPolicy.MAX_COLLECTIONS) { index ->
            store.save(setup(topic = "Topic $index"))
        }

        assertThrows(IllegalStateException::class.java) {
            store.save(setup(topic = "One too many"))
        }
        val collections = store.get()
        assertEquals(QuizCollectionPolicy.MAX_COLLECTIONS, collections.size)
        assertEquals("Topic 7", collections.first().topic)
        assertTrue(collections.any { it.topic == "Topic 0" })
    }

    @Test
    fun deletionIsDurableAndUnknownIdsAreHarmless() {
        val prefs = FakeSharedPreferences()
        val store = QuizCollectionStore(prefs)
        val saved = store.save(setup())

        assertFalse(store.delete("missing"))
        assertTrue(store.delete(saved.id))
        assertTrue(QuizCollectionStore(prefs).get().isEmpty())
    }

    @Test
    fun malformedStorageRepairsToAStableEmptyDocument() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(QuizCollectionStore.KEY, "{not json").commit()
        val store = QuizCollectionStore(prefs)

        assertTrue(store.get().isEmpty())
        val repaired = prefs.getString(QuizCollectionStore.KEY, null)
        assertNotNull(repaired)
        assertTrue(QuizCollectionJson.decode(repaired!!).isEmpty())
    }
}
