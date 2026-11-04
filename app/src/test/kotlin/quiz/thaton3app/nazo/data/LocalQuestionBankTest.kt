package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the built-in question bank ([LocalQuestionBank]) and
 * [Question.identity] — regression coverage for the bank-integrity and
 * cross-franchise dedup work of the repair sequence.
 *
 * Everything here is pure data/logic: no network, no clock. The bank's
 * [LocalQuestionBank.getQuestions] shuffles its output on purpose, so its
 * tests only assert invariants that hold for every permutation — never a
 * particular random draw.
 */
class LocalQuestionBankTest {

    companion object {
        /** Tiers the Daily Challenge difficulty ramp is built on. */
        private val KNOWN_DIFFICULTIES = setOf("Easy", "Medium", "Hard", "Otaku Master")

        /**
         * Generic question texts that genuinely occur in more than one
         * franchise's bank entries — the exact case text-only dedup broke on.
         */
        private val GENERIC_TEXTS = mapOf(
            "What is the name of the first arc?" to
                listOf("Attack on Titan", "Demon Slayer", "Jujutsu Kaisen", "Mob Psycho 100", "My Hero Academia"),
            "What is the name of the second arc?" to
                listOf("Attack on Titan", "Demon Slayer", "Jujutsu Kaisen", "Mob Psycho 100", "My Hero Academia"),
            "What is the name of the third arc?" to
                listOf("Attack on Titan", "Demon Slayer", "Jujutsu Kaisen", "Mob Psycho 100", "My Hero Academia"),
            "Who is the priest?" to
                listOf("Frieren", "Trigun"),
        )
    }

    // ---- bank integrity --------------------------------------------------

    @Test
    fun everyBankQuestionIsWellFormed() {
        val pool = LocalQuestionBank.stablePool()
        assertTrue("bank must not be empty", pool.isNotEmpty())
        for (q in pool) {
            val where = "${q.anime}: ${q.text}"
            assertTrue("$where: anime is blank", q.anime.isNotBlank())
            assertTrue("$where: text is blank", q.text.isNotBlank())
            assertTrue("$where: explanation is blank", q.explanation.isNotBlank())
            assertTrue("$where: difficulty is blank", q.difficulty.isNotBlank())
            assertTrue(
                "$where: unknown difficulty '${q.difficulty}'",
                q.difficulty in KNOWN_DIFFICULTIES,
            )
            assertEquals("$where: expected exactly 4 options", 4, q.options.size)
            assertTrue("$where: options contain a blank", q.options.all { it.isNotBlank() })
            assertEquals("$where: options are not distinct", 4, q.options.toSet().size)
            assertEquals(
                "$where: correctAnswer must appear exactly once among options",
                1,
                q.options.count { it == q.correctAnswer },
            )
        }
    }

    @Test
    fun stablePoolHasNoDuplicateIdentities() {
        val pool = LocalQuestionBank.stablePool()
        assertEquals(
            "same-franchise duplicate questions must be deduped",
            pool.size,
            pool.map { it.identity }.toSet().size,
        )
    }

    // ---- identity: cross-franchise copies survive ------------------------

    @Test
    fun identityCollapsesSameFranchiseDuplicates() {
        val a = Question(anime = "Frieren", text = "  Who is the priest? ")
        val b = Question(anime = "frieren", text = "Who is the priest?")
        assertEquals(a.identity, b.identity)
    }

    @Test
    fun identityKeepsDifferentFranchisesApart() {
        val a = Question(anime = "Frieren", text = "Who is the priest?")
        val b = Question(anime = "Trigun", text = "Who is the priest?")
        assertNotEquals(a.identity, b.identity)
    }

    @Test
    fun genericQuestionsKeepEveryFranchisesCopy() {
        // The regression this guards: several series ask the same generic
        // wording ("What is the name of the first arc?"). Text-only dedup
        // silently dropped every franchise but the first; identity dedup
        // keeps each franchise's own copy. These groups exist in the bank.
        val pool = LocalQuestionBank.stablePool()
        for ((text, franchises) in GENERIC_TEXTS) {
            val copies = pool.filter { it.text.equals(text, ignoreCase = true) }
            for (anime in franchises) {
                assertTrue(
                    "'$anime' copy of '$text' must survive dedup",
                    copies.any { it.anime.equals(anime, ignoreCase = true) },
                )
            }
        }
    }

    // ---- getQuestions invariants (any shuffle) ---------------------------

    @Test
    fun getQuestionsAlwaysReturnsDistinctAnswerableQuestions() {
        repeat(10) {
            for (count in listOf(1, 5, 12)) {
                val qs = LocalQuestionBank.getQuestions(count)
                assertTrue("requested $count, got ${qs.size}", qs.size <= count)
                assertEquals(
                    "duplicate identities in one draw",
                    qs.size,
                    qs.map { it.identity }.toSet().size,
                )
                for (q in qs) {
                    assertEquals(
                        "${q.text}: answer must survive the option shuffle",
                        1,
                        q.options.count { it == q.correctAnswer },
                    )
                    assertEquals("${q.text}: option set must survive the shuffle", 4, q.options.toSet().size)
                }
            }
        }
    }

    @Test
    fun getQuestionsTopicFilterOnlyReturnsMatchingQuestions() {
        val qs = LocalQuestionBank.getQuestions(8, topic = "Frieren")
        assertTrue("expected Frieren questions in the bank", qs.isNotEmpty())
        for (q in qs) {
            assertTrue(
                "topic filter leaked unrelated question: ${q.text}",
                q.anime.contains("Frieren", ignoreCase = true) ||
                    q.theme.contains("Frieren", ignoreCase = true) ||
                    q.text.contains("Frieren", ignoreCase = true),
            )
        }
    }
}
