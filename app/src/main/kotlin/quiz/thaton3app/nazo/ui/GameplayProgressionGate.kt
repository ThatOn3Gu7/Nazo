package quiz.thaton3app.nazo.ui

/** Identifies the exact rendered quiz question that requested progression. */
internal data class QuizProgressionTicket(
    val sessionId: Long,
    val stage: Int,
    val questionIndex: Int,
)

/**
 * State-layer one-shot gate for Quiz, Survival, Blitz and Versus progression.
 *
 * A button's enabled state cannot protect against two callbacks dispatched
 * before Compose redraws. Tickets bind the callback to both the game session
 * and the rendered question; consuming a ticket is atomic on the UI thread and
 * completion leaves it consumed even though the final index no longer changes.
 */
internal class QuizProgressionGate {
    private var sessionId = 0L
    private var consumed: QuizProgressionTicket? = null

    fun startSession() {
        sessionId++
        consumed = null
    }

    fun ticket(stage: Int, questionIndex: Int): QuizProgressionTicket =
        QuizProgressionTicket(sessionId, stage, questionIndex)

    fun tryConsume(
        ticket: QuizProgressionTicket,
        currentStage: Int,
        currentQuestionIndex: Int,
    ): Boolean {
        if (ticket.sessionId != sessionId ||
            ticket.stage != currentStage ||
            ticket.questionIndex != currentQuestionIndex ||
            ticket == consumed
        ) {
            return false
        }
        consumed = ticket
        return true
    }
}

/** Identifies the exact rendered guessing-game reveal that requested Next. */
internal data class GuessProgressionTicket(
    val sessionId: Long,
    val round: Int,
)

/** State-layer one-shot gate for next-round and final-result recording. */
internal class GuessProgressionGate {
    private var sessionId = 0L
    private var consumed: GuessProgressionTicket? = null

    fun startSession() {
        sessionId++
        consumed = null
    }

    fun ticket(round: Int): GuessProgressionTicket = GuessProgressionTicket(sessionId, round)

    fun tryConsume(ticket: GuessProgressionTicket, currentRound: Int): Boolean {
        if (ticket.sessionId != sessionId || ticket.round != currentRound || ticket == consumed) {
            return false
        }
        consumed = ticket
        return true
    }
}
