package quiz.thaton3app.nazo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [ApiClient.parseQuestions] — the AI reply validator.
 *
 * These run without any network access: the malformed payloads that a live
 * model will never reliably produce on demand (wrong answer keys, duplicate
 * options, short batches) are fed in as fixtures instead. The rule under test
 * is absolute: invalid data must be REJECTED, never silently promoted into a
 * scored quiz (the old parser answered `options.first()` whenever the declared
 * correctAnswer did not match).
 */
class QuizResponseParserTest {

    private val topic = "Naruto"

    /** JSON-encodes a plain Kotlin string as a JSON string literal. */
    private fun enc(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Builds a JSON options array from plain option texts. */
    private fun opts(vararg o: String): String = o.joinToString(",", "[", "]") { enc(it) }

    /**
     * One question object with sane defaults; every field overridable.
     * Pass `correct = null` to omit the correctAnswer key entirely.
     */
    private fun q(
        question: String = "Who is the Fifth Hokage?",
        options: String = opts("Tsunade", "Jiraiya", "Orochimaru", "Danzo"),
        correct: String? = "Tsunade",
    ): String {
        val correctJson = if (correct == null) "" else ",\"correctAnswer\":${enc(correct)}"
        return "{\"question\":${enc(question)},\"options\":$options," +
            "\"theme\":\"CHARACTERS\",\"explanation\":\"e\"$correctJson}"
    }

    private fun parse(json: String, expectedCount: Int = 0) =
        ApiClient.parseQuestions(json, topic, expectedCount)

    // ---------------------------------------------------------------- valid

    @Test
    fun validBareArrayParses() {
        val qs = parse("[" + q() + "]", expectedCount = 1)
        assertEquals(1, qs.size)
        val item = qs[0]
        assertEquals("Who is the Fifth Hokage?", item.text)
        assertEquals(listOf("Tsunade", "Jiraiya", "Orochimaru", "Danzo"), item.options)
        assertEquals("Tsunade", item.correctAnswer)
        // Invariant that makes scoring safe: the scored answer is one of the options.
        assertTrue(item.correctAnswer in item.options)
    }

    @Test
    fun wrappedObjectAndFencedAndReasoningPayloadsParse() {
        assertEquals(1, parse("{\"questions\":[" + q() + "]}", 1).size)
        assertEquals(1, parse("Sure!\n```json\n[" + q() + "]\n```\nHope that helps.", 1).size)
        assertEquals(1, parse("<think>hmm, tsunade probably...</think>[" + q() + "]", 1).size)
    }

    @Test
    fun answersMatchIgnoringCaseAndWhitespace() {
        // The model's declared answer may differ cosmetically from the option
        // text. That is a MATCH, and the option's own text becomes the answer.
        for (declared in listOf("tsunade", "  Tsunade  ", "TsuNade")) {
            val qs = parse("[" + q(correct = declared) + "]", 1)
            assertEquals("Tsunade", qs[0].correctAnswer)
        }
    }

    @Test
    fun numberOptionsCoerceToStringsConsistently() {
        // "How many tailed beasts?"-style options arrive as JSON numbers.
        val raw = "[{\"question\":\"How many?\",\"options\":[1,\"2\",3,4]," +
            "\"correctAnswer\":\"3\",\"theme\":\"T\"}]"
        val qs = parse(raw, 1)
        assertEquals(listOf("1", "2", "3", "4"), qs[0].options)
        assertEquals("3", qs[0].correctAnswer)
    }

    // ------------------------------------------------------------- malformed

    @Test
    fun malformedJsonThrows() {
        assertThrows(Exception::class.java) { parse("I am not JSON at all.") }
    }

    @Test
    fun missingAnswerRejectsQuestion() {
        // No correctAnswer key: the old parser silently scored options.first().
        assertThrows(Exception::class.java) { parse("[" + q(correct = null) + "]", 1) }
    }

    @Test
    fun answerMatchingNoOptionRejectsQuestion() {
        assertThrows(Exception::class.java) { parse("[" + q(correct = "Kakashi") + "]", 1) }
        // A letter/index answer is NOT a licence to guess either.
        assertThrows(Exception::class.java) { parse("[" + q(correct = "A") + "]", 1) }
    }

    @Test
    fun blankAnswerRejectsQuestion() {
        assertThrows(Exception::class.java) { parse("[" + q(correct = "   ") + "]", 1) }
    }

    @Test
    fun badQuestionIsRejectedNotRescuedByOptionsFirst() {
        // 2 requested, one question has a wrong-answer key: the payload must
        // FAIL the count check (1 of 2), proving the bad question was dropped
        // instead of being scored as options.first() ("Tsunade").
        val err = assertThrows(Exception::class.java) {
            parse("[" + q(correct = "Kakashi") + "," + q() + "]", 2)
        }
        assertTrue(err.message!!.contains("1 of 2"))
    }

    @Test
    fun duplicateOptionsReject() {
        assertThrows(Exception::class.java) {
            parse("[" + q(options = opts("Tsunade", "Tsunade", "Jiraiya", "Danzo")) + "]", 1)
        }
        // Case-insensitive duplicates count too.
        assertThrows(Exception::class.java) {
            parse("[" + q(options = opts("Tsunade", "tsunade", "Jiraiya", "Danzo")) + "]", 1)
        }
    }

    @Test
    fun incorrectOptionCountsReject() {
        assertThrows(Exception::class.java) {
            parse("[" + q(options = opts("A", "B", "C"), correct = "A") + "]", 1)
        }
        assertThrows(Exception::class.java) {
            parse("[" + q(options = opts("A", "B", "C", "D", "E"), correct = "A") + "]", 1)
        }
    }

    @Test
    fun blankOptionRejectsQuestion() {
        // A blank entry is not silently dropped to shrink the set to three.
        assertThrows(Exception::class.java) {
            parse("[" + q(options = opts("Tsunade", "", "Jiraiya", "Danzo")) + "]", 1)
        }
    }

    @Test
    fun missingOptionsRejectsQuestion() {
        assertThrows(Exception::class.java) {
            parse("[{\"question\":\"Q?\",\"correctAnswer\":\"A\",\"theme\":\"T\"}]", 1)
        }
    }

    @Test
    fun blankQuestionRejectsQuestion() {
        assertThrows(Exception::class.java) { parse("[" + q(question = "   ") + "]", 1) }
    }

    @Test
    fun nonObjectElementDoesNotPoisonTheBatch() {
        // One garbage array element is one malformed question — the two good
        // ones still come through.
        val qs = parse("[" + q() + ",\"garbage\"," + q(question = "Who is the Sixth Hokage?") + "]", 2)
        assertEquals(2, qs.size)
    }

    // ------------------------------------------------------- batch completeness

    @Test
    fun incompleteQuestionSetThrows() {
        val err = assertThrows(Exception::class.java) {
            parse("[" + q() + "]", expectedCount = 10)
        }
        assertTrue(err.message!!.contains("1 of 10"))
    }

    @Test
    fun allInvalidPayloadThrows() {
        val err = assertThrows(Exception::class.java) {
            parse(
                "[" + q(correct = "Kakashi") + "," + q(options = opts("A", "A", "B", "C")) + "]",
                5,
            )
        }
        assertTrue(err.message!!.contains("0 of 5"))
    }

    @Test
    fun extrasAreTruncatedToTheRequestedCount() {
        val three = "[" + q() + "," + q(question = "Q2?") + "," + q(question = "Q3?") + "]"
        assertEquals(2, parse(three, 2).size)
        assertEquals(3, parse(three, 3).size)
    }

    @Test
    fun everyAcceptedQuestionAlwaysHasItsAnswerAmongOptions() {
        val qs = parse("[" + q() + "," + q(question = "Q2?", correct = "  jiraiya ") + "]", 2)
        qs.forEach { assertTrue(it.correctAnswer in it.options) }
        assertEquals("Jiraiya", qs[1].correctAnswer)
    }
}
