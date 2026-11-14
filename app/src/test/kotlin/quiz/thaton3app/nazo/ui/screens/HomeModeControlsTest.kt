package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.ui.mode.NazoMode

/** Contracts for the mode-dependent groups whose visibility animates on Home. */
class HomeModeControlsTest {

    @Test
    fun eachModeGetsTheExpectedCountControls() {
        assertEquals(HomeCountControls.Questions, homeCountControls(NazoMode.QUIZ))
        assertEquals(HomeCountControls.Questions, homeCountControls(NazoMode.VERSUS))
        assertEquals(HomeCountControls.Rounds, homeCountControls(NazoMode.GUESSING))
        assertEquals(HomeCountControls.None, homeCountControls(NazoMode.SURVIVAL))
        assertEquals(HomeCountControls.None, homeCountControls(NazoMode.BLITZ))
    }

    @Test
    fun practiceBelongsOnlyToQuizWhenTheDeckIsNotEmpty() {
        assertTrue(homePracticeVisible(NazoMode.QUIZ, practiceCount = 1))
        assertFalse(homePracticeVisible(NazoMode.QUIZ, practiceCount = 0))
        NazoMode.entries.filterNot { it == NazoMode.QUIZ }.forEach { mode ->
            assertFalse(homePracticeVisible(mode, practiceCount = 12))
        }
    }

    @Test
    fun challengeSummaryAlwaysReflectsTheVisibleModeSpecificChoice() {
        assertEquals(
            "Medium • 10 questions",
            challengeSummary(NazoMode.QUIZ, Difficulty.MEDIUM, 10, 3),
        )
        assertEquals(
            "Hard • 1 round",
            challengeSummary(NazoMode.GUESSING, Difficulty.HARD, 15, 1),
        )
        assertEquals(
            "Easy • 5 questions",
            challengeSummary(NazoMode.VERSUS, Difficulty.EASY, 5, 3),
        )
        assertEquals(
            "Otaku Master • endless run",
            challengeSummary(NazoMode.SURVIVAL, Difficulty.OTAKU_MASTER, 5, 3),
        )
        assertEquals(
            "Easy • 60-second run",
            challengeSummary(NazoMode.BLITZ, Difficulty.EASY, 5, 3),
        )
    }
}
