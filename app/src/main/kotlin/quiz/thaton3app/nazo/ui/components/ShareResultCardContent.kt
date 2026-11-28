package quiz.thaton3app.nazo.ui.components

/**
 * Privacy-safe, fixed-vocabulary content for a shareable result image.
 *
 * The model deliberately has no profile name, avatar, topic, question, answer,
 * provider, or free-form text fields. Result screens can therefore export this
 * model without accidentally placing personal setup or quiz content in a PNG.
 */
class ShareResultCardContent private constructor(
    val eyebrow: String,
    val heading: String,
    val headline: String,
    val headlineCaption: String,
    val stats: List<ShareResultCardStat>,
    val highlight: String?,
    val shareText: String,
) {
    init {
        require(stats.size == RESULT_CARD_STAT_COUNT)
        require(
            listOf(eyebrow, heading, headline, headlineCaption, shareText)
                .plus(stats.flatMap { listOf(it.label, it.value) })
                .plus(listOfNotNull(highlight))
                .all(::isSafeResultCardText)
        )
    }

    companion object {
        fun quiz(
            score: Int,
            totalQuestions: Int,
            elapsedMillis: Long,
            difficulty: String,
            daily: Boolean,
            modeLabel: String?,
            adaptiveFinalDifficulty: String? = null,
            isNewRecord: Boolean = false,
        ): ShareResultCardContent {
            val total = totalQuestions.coerceAtLeast(0)
            val correct = score.coerceIn(0, total)
            val accuracy = percentage(correct, total)
            val mode = resultCardMode(
                daily = daily,
                modeLabel = modeLabel,
                difficulty = difficulty,
            )
            val safeDifficulty = safeDifficultyLabel(
                if (mode == ResultCardMode.Adaptive) adaptiveFinalDifficulty else difficulty
            )
            val stats = when (mode) {
                ResultCardMode.Daily -> listOf(
                    ShareResultCardStat("Score", "$correct / $total"),
                    ShareResultCardStat("Path", "Easy → Medium → Hard"),
                    ShareResultCardStat("Time", resultCardDuration(elapsedMillis)),
                )
                ResultCardMode.Survival -> listOf(
                    ShareResultCardStat("Answered", "$total"),
                    ShareResultCardStat("Accuracy", "$accuracy%"),
                    ShareResultCardStat("Time", resultCardDuration(elapsedMillis)),
                )
                ResultCardMode.Blitz -> listOf(
                    ShareResultCardStat("Answered", "$total"),
                    ShareResultCardStat("Accuracy", "$accuracy%"),
                    ShareResultCardStat("Window", "60 seconds"),
                )
                ResultCardMode.Practice -> listOf(
                    ShareResultCardStat("Score", "$correct / $total"),
                    ShareResultCardStat("Set", "Mistake rematch"),
                    ShareResultCardStat("Time", resultCardDuration(elapsedMillis)),
                )
                ResultCardMode.Adaptive -> listOf(
                    ShareResultCardStat("Score", "$correct / $total"),
                    ShareResultCardStat("Final tier", safeDifficulty),
                    ShareResultCardStat("Time", resultCardDuration(elapsedMillis)),
                )
                ResultCardMode.Quiz -> listOf(
                    ShareResultCardStat("Score", "$correct / $total"),
                    ShareResultCardStat("Difficulty", safeDifficulty),
                    ShareResultCardStat("Time", resultCardDuration(elapsedMillis)),
                )
            }
            val countMode = mode == ResultCardMode.Survival || mode == ResultCardMode.Blitz
            val headline = if (countMode) "$correct" else "$accuracy%"
            val caption = if (countMode) "correct" else "accuracy"
            return ShareResultCardContent(
                eyebrow = mode.eyebrow,
                heading = mode.heading,
                headline = headline,
                headlineCaption = caption,
                stats = stats,
                highlight = resultCardHighlight(
                    isNewRecord = isNewRecord,
                    perfect = total > 0 && correct == total,
                    daily = daily,
                ),
                shareText = if (countMode) {
                    "I got $correct correct in ${mode.shareName} on Nazo."
                } else {
                    "I scored $accuracy% in ${mode.shareName} on Nazo."
                },
            )
        }

        fun versus(
            playerOneScore: Int,
            playerTwoScore: Int,
            totalQuestions: Int,
            difficulty: String,
        ): ShareResultCardContent {
            val total = totalQuestions.coerceAtLeast(0)
            val playerOne = playerOneScore.coerceIn(0, total)
            val playerTwo = playerTwoScore.coerceIn(0, total)
            val outcome = when {
                playerOne == playerTwo -> "MATCH DRAWN"
                playerOne > playerTwo -> "PLAYER 1 WINS"
                else -> "PLAYER 2 WINS"
            }
            return ShareResultCardContent(
                eyebrow = "HEAD-TO-HEAD",
                heading = "Versus match complete",
                headline = "$playerOne–$playerTwo",
                headlineCaption = "final score",
                stats = listOf(
                    ShareResultCardStat("Player 1", "$playerOne / $total"),
                    ShareResultCardStat("Player 2", "$playerTwo / $total"),
                    ShareResultCardStat("Difficulty", safeDifficultyLabel(difficulty)),
                ),
                highlight = outcome,
                shareText = when {
                    playerOne == playerTwo -> "Our Nazo Versus match ended $playerOne–$playerTwo."
                    playerOne > playerTwo -> "Player 1 won our Nazo Versus match $playerOne–$playerTwo."
                    else -> "Player 2 won our Nazo Versus match $playerTwo–$playerOne."
                },
            )
        }

        fun guessing(
            score: Int,
            solvedRounds: Int,
            totalRounds: Int,
            difficulty: String,
            isNewRecord: Boolean = false,
        ): ShareResultCardContent {
            val total = totalRounds.coerceAtLeast(0)
            val solved = solvedRounds.coerceIn(0, total)
            val safeScore = score.coerceAtLeast(0)
            val accuracy = percentage(solved, total)
            return ShareResultCardContent(
                eyebrow = "GUESSING GAME",
                heading = "Image challenge complete",
                headline = "$safeScore",
                headlineCaption = "points",
                stats = listOf(
                    ShareResultCardStat("Rounds solved", "$solved / $total"),
                    ShareResultCardStat("Accuracy", "$accuracy%"),
                    ShareResultCardStat("Difficulty", safeDifficultyLabel(difficulty)),
                ),
                highlight = resultCardHighlight(
                    isNewRecord = isNewRecord,
                    perfect = total > 0 && solved == total,
                    daily = false,
                ),
                shareText = "I scored $safeScore points in Nazo's Guessing Game.",
            )
        }
    }
}

data class ShareResultCardStat(
    val label: String,
    val value: String,
) {
    init {
        require(isSafeResultCardText(label))
        require(isSafeResultCardText(value))
    }
}

internal const val RESULT_CARD_PRIVACY_NOTE =
    "Shares game stats only — never your profile, questions, or answers."

internal const val RESULT_CARD_STAT_COUNT = 3
private const val RESULT_CARD_TEXT_LIMIT = 80

private enum class ResultCardMode(
    val eyebrow: String,
    val heading: String,
    val shareName: String,
) {
    Quiz("FOCUSED QUIZ", "Quiz complete", "a Quiz challenge"),
    Daily("DAILY LADDER", "Daily Ladder cleared", "the Daily Ladder"),
    Survival("SURVIVAL REPORT", "Survival run complete", "Survival"),
    Blitz("60-SECOND REPORT", "Blitz complete", "Blitz"),
    Practice("MISTAKE REMATCH", "Practice complete", "a Practice run"),
    Adaptive("ADAPTIVE QUIZ", "Adaptive quiz complete", "an Adaptive Quiz"),
}

private fun resultCardMode(
    daily: Boolean,
    modeLabel: String?,
    difficulty: String,
): ResultCardMode = when {
    daily -> ResultCardMode.Daily
    modeLabel.equals("Survival", ignoreCase = true) -> ResultCardMode.Survival
    modeLabel.equals("Blitz", ignoreCase = true) -> ResultCardMode.Blitz
    difficulty.equals("Practice", ignoreCase = true) -> ResultCardMode.Practice
    difficulty.equals("Adaptive", ignoreCase = true) -> ResultCardMode.Adaptive
    else -> ResultCardMode.Quiz
}

private fun percentage(value: Int, total: Int): Int =
    if (total <= 0) 0 else (value.toLong() * 100L / total).toInt()

internal fun resultCardDuration(elapsedMillis: Long): String {
    if (elapsedMillis <= 0L) return "—"
    val seconds = elapsedMillis / 1_000L
    return "${seconds / 60}m ${seconds % 60}s"
}

private fun resultCardHighlight(
    isNewRecord: Boolean,
    perfect: Boolean,
    daily: Boolean,
): String? = when {
    isNewRecord -> "NEW PERSONAL BEST"
    perfect -> "PERFECT CLEAR"
    daily -> "DAILY CLEAR"
    else -> null
}

private fun safeDifficultyLabel(raw: String?): String {
    val normalized = raw?.trim().orEmpty()
    return listOf("Easy", "Medium", "Hard", "Otaku Master", "Adaptive")
        .firstOrNull { it.equals(normalized, ignoreCase = true) }
        ?: "Mixed"
}

private fun isSafeResultCardText(text: String): Boolean =
    text.isNotBlank() &&
        text.length <= RESULT_CARD_TEXT_LIMIT &&
        text.none { it.isISOControl() || it == '\n' || it == '\r' }
