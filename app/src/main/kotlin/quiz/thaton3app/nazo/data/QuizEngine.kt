package quiz.thaton3app.nazo.data

/**
 * Quiz "engine" — central place for difficulty-driven rules.
 *
 * Today this owns the per-question countdown seconds, but it is intentionally the
 * single source of truth so future difficulty rules (question phrasing, harder
 * pools, etc.) live in one spot. Timings are keyed by the difficulty *label*
 * produced by the Home screen's `Difficulty` enum.
 *
 * Seconds per question (product owner, 2026-08-23, updated):
 *   Easy = 40, Medium = 30, Hard = 20, Otaku Master = 10.
 */
object QuizEngine {

    data class DifficultySpec(
        val label: String,
        val secondsPerQuestion: Int,
    )

    private val SPECS: Map<String, DifficultySpec> = mapOf(
        "Easy" to DifficultySpec("Easy", 40),
        "Medium" to DifficultySpec("Medium", 30),
        "Hard" to DifficultySpec("Hard", 20),
        "Otaku Master" to DifficultySpec("Otaku Master", 10),
    )

    fun specFor(difficultyLabel: String): DifficultySpec =
        SPECS[difficultyLabel] ?: SPECS.getValue("Easy")
}
