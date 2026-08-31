package quiz.thaton3app.nazo.modes.guessing_game

/** How the player answers a round. */
enum class GuessInputMode { CHOICE, AUTOCOMPLETE }

data class GuessDifficultySpec(
    val label: String,
    val basePoints: Int,
    val inputMode: GuessInputMode,
    val secondsPerRound: Int,
    /**
     * How obscured the mystery image is at the START of the round, as a
     * fraction of the maximum effect (blur radius or pixelation depth) —
     * it then eases down to fully sharp as the timer runs out:
     * Easy 50%, Medium 60%, Hard 80%, Otaku Master 100%.
     */
    val startEffectFraction: Float,
)

/**
 * Guessing-game rules, in one spot: per-difficulty base points, input mode,
 * round DURATION (Easy 25s, Medium 20s, Hard 15s, Otaku Master 10s) and
 * starting obscuration. The quiz mode keeps its own (longer) timings in
 * [quiz.thaton3app.nazo.data.QuizEngine] — they are independent on purpose.
 */
object GuessScoring {

    private val SPECS: Map<String, GuessDifficultySpec> = mapOf(
        "Easy" to GuessDifficultySpec("Easy", 100, GuessInputMode.CHOICE, 25, 0.5f),
        "Medium" to GuessDifficultySpec("Medium", 150, GuessInputMode.CHOICE, 20, 0.6f),
        "Hard" to GuessDifficultySpec("Hard", 200, GuessInputMode.AUTOCOMPLETE, 15, 0.8f),
        "Otaku Master" to GuessDifficultySpec("Otaku Master", 300, GuessInputMode.AUTOCOMPLETE, 10, 1.0f),
    )

    fun specFor(difficultyLabel: String): GuessDifficultySpec =
        SPECS[difficultyLabel] ?: SPECS.getValue("Easy")

    fun durationMsFor(difficultyLabel: String): Long =
        specFor(difficultyLabel).secondsPerRound * 1000L

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
