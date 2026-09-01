package quiz.thaton3app.nazo.session

/**
 * Per-launch memory of everything the player has already been asked, shared
 * by BOTH game modes (owner request: one system for quiz + guessing game).
 *
 * Purely in-memory (object state), so it naturally scopes to one app session:
 * a fresh launch starts with a clean slate, exactly as specified. Within a
 * session it guarantees:
 *
 *  - QUIZ: a question answered (or failed) in ANY earlier round never comes
 *    back — the local bank prefers unseen questions, AI providers are told
 *    which questions to avoid IN THE PROMPT, and AI/cached responses are
 *    re-ordered to put unseen questions first.
 *  - GUESSING GAME: a target (or any of its aliases) from ANY earlier game
 *    this session is added to the AI's avoid list — previously that list
 *    only lasted one game.
 *
 * Matching is on normalized text (case/punctuation-insensitive) so cosmetic
 * rewording by the model ("Whats Luffy's dream?" vs "What's Luffy's dream?")
 * still counts as the same question. Avoid lists sent to providers are
 * capped to the most recent entries to keep prompts small.
 */
object SessionMemory {

    private const val MAX_PROMPT_QUESTIONS = 40
    private const val MAX_PROMPT_TARGETS = 40

    /** Normalized question texts seen this session. */
    private val seenQuestions = HashSet<String>()

    /** Original question texts, insertion-ordered (for prompt avoid lists). */
    private val questionHistory = LinkedHashSet<String>()

    /** Guessing-game targets + aliases played this session (original text). */
    private val guessTargets = LinkedHashSet<String>()

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Marks a quiz question as asked-and-answered this session. */
    @Synchronized
    fun recordQuestion(text: String) {
        if (text.isBlank()) return
        if (seenQuestions.add(normalize(text))) questionHistory.add(text.trim())
    }

    /** True when this question (modulo case/punctuation) was already played. */
    @Synchronized
    fun isQuestionSeen(text: String): Boolean = normalize(text) in seenQuestions

    /** Most recent seen questions, for the provider prompt's avoid list. */
    @Synchronized
    fun questionAvoidList(): List<String> = questionHistory.toList().takeLast(MAX_PROMPT_QUESTIONS)

    /** Marks a guessing-game target (or alias) as played this session. */
    @Synchronized
    fun recordGuessTarget(name: String) {
        if (name.isNotBlank()) guessTargets.add(name.trim())
    }

    /** All targets/aliases played this session (capped, most recent last). */
    @Synchronized
    fun guessAvoidList(): List<String> = guessTargets.toList().takeLast(MAX_PROMPT_TARGETS)
}
