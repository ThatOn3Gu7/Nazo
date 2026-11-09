package quiz.thaton3app.nazo.ui.mode

/** The five play experiences exposed from Home. */
enum class NazoMode(val label: String) {
    QUIZ("Quiz"),
    GUESSING("Guessing Game"),
    SURVIVAL("Survival"),
    BLITZ("Blitz"),
    VERSUS("Versus"),
}

/**
 * Stable copy and interaction cues for one mode.
 *
 * These are deliberately presentation-only. Keeping the identity policy in one
 * place lets setup, gameplay and results tell the same story without coupling
 * any of it to grading, timers, scoring or persistence.
 */
data class ModeIdentitySpec(
    val eyebrow: String,
    val headline: String,
    val description: String,
    val cues: List<String>,
)

fun modeIdentitySpec(mode: NazoMode): ModeIdentitySpec = when (mode) {
    NazoMode.QUIZ -> ModeIdentitySpec(
        eyebrow = "CALM & FOCUSED",
        headline = "A calm test of what you know",
        description = "Polished questions, clear progress, and time to think.",
        cues = listOf("Clear progress", "Explanations"),
    )
    NazoMode.GUESSING -> ModeIdentitySpec(
        eyebrow = "MYSTERY REVEAL",
        headline = "Watch the clue come into focus",
        description = "Read the image as it gradually reveals itself.",
        cues = listOf("Image clues", "Gradual reveal"),
    )
    NazoMode.SURVIVAL -> ModeIdentitySpec(
        eyebrow = "ENDURANCE RUN",
        headline = "Protect three lives and keep going",
        description = "The pressure rises with every life you lose.",
        cues = listOf("3 lives", "Endless run"),
    )
    NazoMode.BLITZ -> ModeIdentitySpec(
        eyebrow = "60-SECOND SPRINT",
        headline = "Answer fast. Keep moving.",
        description = "One global clock, rapid feedback, no pauses.",
        cues = listOf("60 seconds", "Auto-advance"),
    )
    NazoMode.VERSUS -> ModeIdentitySpec(
        eyebrow = "HEAD-TO-HEAD",
        headline = "Two players. One shared challenge.",
        description = "Take turns, then compare both runs side by side.",
        cues = listOf("Pass & play", "Same questions"),
    )
}

enum class GameplayPresentation {
    QUIZ,
    SURVIVAL,
    BLITZ,
    VERSUS,
}

/** Special modes take precedence over optional quiz badges such as Daily/AI. */
fun gameplayPresentation(
    endless: Boolean,
    blitz: Boolean,
    playerLabel: String?,
): GameplayPresentation = when {
    blitz -> GameplayPresentation.BLITZ
    endless -> GameplayPresentation.SURVIVAL
    playerLabel != null -> GameplayPresentation.VERSUS
    else -> GameplayPresentation.QUIZ
}

enum class SurvivalDanger(val intensity: Float) {
    STEADY(0f),
    EXPOSED(0.28f),
    LAST_LIFE(0.72f),
    DEFEATED(1f),
}

fun survivalDanger(livesLeft: Int): SurvivalDanger = when {
    livesLeft >= 3 -> SurvivalDanger.STEADY
    livesLeft == 2 -> SurvivalDanger.EXPOSED
    livesLeft == 1 -> SurvivalDanger.LAST_LIFE
    else -> SurvivalDanger.DEFEATED
}

fun survivalDangerLabel(livesLeft: Int): String = when (survivalDanger(livesLeft)) {
    SurvivalDanger.STEADY -> "All lives intact"
    SurvivalDanger.EXPOSED -> "Pressure rising"
    SurvivalDanger.LAST_LIFE -> "Last life"
    SurvivalDanger.DEFEATED -> "Run ending"
}

enum class BlitzUrgency {
    CRUISING,
    PRESSURE,
    FINAL_SECONDS,
    TIME_UP,
}

fun blitzUrgency(secondsLeft: Int): BlitzUrgency = when {
    secondsLeft <= 0 -> BlitzUrgency.TIME_UP
    secondsLeft <= 5 -> BlitzUrgency.FINAL_SECONDS
    secondsLeft <= 15 -> BlitzUrgency.PRESSURE
    else -> BlitzUrgency.CRUISING
}

fun blitzClockText(secondsLeft: Int): String =
    "00:${secondsLeft.coerceAtLeast(0).coerceAtMost(99).toString().padStart(2, '0')}"

enum class MysteryRevealStage(val label: String) {
    OBSCURED("HEAVILY OBSCURED"),
    EMERGING("TAKING SHAPE"),
    NEARLY_CLEAR("ALMOST CLEAR"),
    REVEALED("REVEALED"),
}

/** [remainingFraction] counts down from one while the visual clue sharpens. */
fun mysteryRevealStage(remainingFraction: Float, revealed: Boolean = false): MysteryRevealStage {
    if (revealed || remainingFraction <= 0f) return MysteryRevealStage.REVEALED
    return when {
        remainingFraction > 0.66f -> MysteryRevealStage.OBSCURED
        remainingFraction > 0.28f -> MysteryRevealStage.EMERGING
        else -> MysteryRevealStage.NEARLY_CLEAR
    }
}

fun questionTransitionDurationMs(presentation: GameplayPresentation): Int =
    if (presentation == GameplayPresentation.BLITZ) 180 else 300

fun questionTransitionSlideFraction(presentation: GameplayPresentation): Float = when (presentation) {
    GameplayPresentation.QUIZ -> 0.08f
    GameplayPresentation.SURVIVAL -> 0.08f
    GameplayPresentation.BLITZ -> 0.14f
    GameplayPresentation.VERSUS -> 0.06f
}
