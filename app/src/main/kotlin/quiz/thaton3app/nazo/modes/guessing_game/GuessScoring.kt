package quiz.thaton3app.nazo.modes.guessing_game

import quiz.thaton3app.nazo.data.QuizEngine

/** How the player answers a round. */
enum class GuessInputMode { CHOICE, AUTOCOMPLETE }

data class GuessDifficultySpec(
    val label: String,
    val basePoints: Int,
    val inputMode: GuessInputMode,
)

/**
 * Guessing-game rules, in one spot (mirrors [QuizEngine] for the quiz mode):
 * per-difficulty base points + input mode. The per-round countdown DURATION is
 * shared with [QuizEngine], so both modes agree on the difficulty timings
 * (Easy = 40s, Medium = 30s, Hard = 20s, Otaku Master = 10s).
 */
object GuessScoring {

    private val SPECS: Map<String, GuessDifficultySpec> = mapOf(
        "Easy" to GuessDifficultySpec("Easy", 100, GuessInputMode.CHOICE),
        "Medium" to GuessDifficultySpec("Medium", 150, GuessInputMode.CHOICE),
        "Hard" to GuessDifficultySpec("Hard", 200, GuessInputMode.AUTOCOMPLETE),
        "Otaku Master" to GuessDifficultySpec("Otaku Master", 300, GuessInputMode.AUTOCOMPLETE),
    )

    fun specFor(difficultyLabel: String): GuessDifficultySpec =
        SPECS[difficultyLabel] ?: SPECS.getValue("Easy")

    fun durationMsFor(difficultyLabel: String): Long =
        QuizEngine.specFor(difficultyLabel).secondsPerQuestion * 1000L

    /**
     * Time-decay scoring: a correct answer scores proportionally to the fraction
     * of the timer that was left — full [base] points for an instant answer,
     * decaying linearly to a 1-point floor for a last-second answer.
     *
     *   points = round(base × remainingFraction), minimum 1
     */
    fun pointsFor(base: Int, remainingFraction: Float): Int =
        (base * remainingFraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
}
