package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareResultCardContentTest {

    @Test
    fun regularQuizExportsOnlyTheResultSummary() {
        val card = ShareResultCardContent.quiz(
            score = 4,
            totalQuestions = 5,
            elapsedMillis = 92_000,
            difficulty = "Hard",
            daily = false,
            modeLabel = null,
        )

        assertEquals("FOCUSED QUIZ", card.eyebrow)
        assertEquals("Quiz complete", card.heading)
        assertEquals("80%", card.headline)
        assertEquals("accuracy", card.headlineCaption)
        assertEquals(
            listOf(
                ShareResultCardStat("Score", "4 / 5"),
                ShareResultCardStat("Difficulty", "Hard"),
                ShareResultCardStat("Time", "1m 32s"),
            ),
            card.stats,
        )
        assertNull(card.highlight)
    }

    @Test
    fun dailyCardUsesTheFixedLadderPathAndPerfectHighlight() {
        val card = ShareResultCardContent.quiz(
            score = 5,
            totalQuestions = 5,
            elapsedMillis = 61_000,
            difficulty = "Daily",
            daily = true,
            modeLabel = null,
        )

        assertEquals("DAILY LADDER", card.eyebrow)
        assertEquals("Daily Ladder cleared", card.heading)
        assertEquals("100%", card.headline)
        assertEquals("Easy → Medium → Hard", card.stats[1].value)
        assertEquals("PERFECT CLEAR", card.highlight)
    }

    @Test
    fun survivalAndBlitzLeadWithCorrectCountInsteadOfPercentage() {
        val survival = ShareResultCardContent.quiz(
            score = 7,
            totalQuestions = 9,
            elapsedMillis = 80_000,
            difficulty = "Otaku Master",
            daily = false,
            modeLabel = "Survival",
        )
        val blitz = ShareResultCardContent.quiz(
            score = 8,
            totalQuestions = 11,
            elapsedMillis = 60_000,
            difficulty = "Medium",
            daily = false,
            modeLabel = "Blitz",
        )

        assertEquals("7", survival.headline)
        assertEquals("correct", survival.headlineCaption)
        assertEquals(ShareResultCardStat("Accuracy", "77%"), survival.stats[1])
        assertEquals("8", blitz.headline)
        assertEquals(ShareResultCardStat("Window", "60 seconds"), blitz.stats[2])
    }

    @Test
    fun adaptiveCardSharesOnlyItsKnownFinalTier() {
        val card = ShareResultCardContent.quiz(
            score = 3,
            totalQuestions = 5,
            elapsedMillis = 45_000,
            difficulty = "Adaptive",
            daily = false,
            modeLabel = null,
            adaptiveFinalDifficulty = "Otaku Master",
            isNewRecord = true,
        )

        assertEquals("ADAPTIVE QUIZ", card.eyebrow)
        assertEquals(ShareResultCardStat("Final tier", "Otaku Master"), card.stats[1])
        assertEquals("NEW PERSONAL BEST", card.highlight)
    }

    @Test
    fun versusCardUsesAnonymousPlayerLabelsAndAClampedFinalScore() {
        val card = ShareResultCardContent.versus(
            playerOneScore = 6,
            playerTwoScore = 3,
            totalQuestions = 5,
            difficulty = "Hard",
        )

        assertEquals("HEAD-TO-HEAD", card.eyebrow)
        assertEquals("5–3", card.headline)
        assertEquals("PLAYER 1 WINS", card.highlight)
        assertEquals(ShareResultCardStat("Player 1", "5 / 5"), card.stats[0])
        assertEquals(ShareResultCardStat("Player 2", "3 / 5"), card.stats[1])
        assertFalse(card.toString().contains("topic", ignoreCase = true))
    }

    @Test
    fun guessingCardNeverNeedsTheTypedTopicOrProfileData() {
        val card = ShareResultCardContent.guessing(
            score = 2_450,
            solvedRounds = 2,
            totalRounds = 3,
            difficulty = "Medium",
        )
        val visibleText = buildList {
            add(card.eyebrow)
            add(card.heading)
            add(card.headline)
            add(card.headlineCaption)
            add(card.shareText)
            card.stats.forEach { add(it.label); add(it.value) }
        }.joinToString(" ")

        assertEquals("2450", card.headline)
        assertEquals(ShareResultCardStat("Rounds solved", "2 / 3"), card.stats[0])
        assertFalse(visibleText.contains("topic", ignoreCase = true))
        assertFalse(visibleText.contains("profile", ignoreCase = true))
        assertFalse(visibleText.contains("answer", ignoreCase = true))
    }

    @Test
    fun unknownOrFreeFormDifficultyIsReplacedRatherThanExported() {
        val privateInput = "My private custom setup\nuser@example.com"
        val card = ShareResultCardContent.guessing(
            score = 100,
            solvedRounds = 1,
            totalRounds = 1,
            difficulty = privateInput,
        )

        val visibleText = card.stats.joinToString(" ") { "${it.label} ${it.value}" } +
            " ${card.eyebrow} ${card.heading} ${card.headline} ${card.shareText}"
        assertEquals("Mixed", card.stats.last().value)
        assertFalse(visibleText.contains("user@example.com"))
    }

    @Test
    fun malformedCountsAreClampedBeforeSharing() {
        val card = ShareResultCardContent.quiz(
            score = 99,
            totalQuestions = 4,
            elapsedMillis = -1,
            difficulty = "Easy",
            daily = false,
            modeLabel = null,
        )

        assertEquals("100%", card.headline)
        assertEquals(ShareResultCardStat("Score", "4 / 4"), card.stats[0])
        assertEquals(ShareResultCardStat("Time", "—"), card.stats[2])
        assertTrue(card.shareText.startsWith("I scored 100%"))
    }
}
