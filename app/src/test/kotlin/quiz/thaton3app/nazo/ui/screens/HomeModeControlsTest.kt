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
    fun portalBelongsToKnownLocalBankLaunchesOnly() {
        // Offline question modes can reveal ready local content directly.
        assertTrue(homeLaunchUsesPortal(NazoMode.QUIZ, offline = true))
        assertTrue(homeLaunchUsesPortal(NazoMode.SURVIVAL, offline = true))
        assertTrue(homeLaunchUsesPortal(NazoMode.VERSUS, offline = true))

        // Their online path may need AI Loading, which is already the correct
        // and informative destination; never queue the portal in front of it.
        assertFalse(homeLaunchUsesPortal(NazoMode.QUIZ, offline = false))
        assertFalse(homeLaunchUsesPortal(NazoMode.SURVIVAL, offline = false))
        assertFalse(homeLaunchUsesPortal(NazoMode.VERSUS, offline = false))

        // Blitz is always local. Guessing always has its own preparation flow.
        assertTrue(homeLaunchUsesPortal(NazoMode.BLITZ, offline = false))
        assertTrue(homeLaunchUsesPortal(NazoMode.BLITZ, offline = true))
        assertFalse(homeLaunchUsesPortal(NazoMode.GUESSING, offline = false))
        assertFalse(homeLaunchUsesPortal(NazoMode.GUESSING, offline = true))
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
    fun adaptiveDifficultyBelongsOnlyToQuiz() {
        assertTrue(adaptiveDifficultyAvailable(NazoMode.QUIZ))
        NazoMode.entries.filterNot { it == NazoMode.QUIZ }.forEach { mode ->
            assertFalse(adaptiveDifficultyAvailable(mode))
            assertEquals(
                Difficulty.MEDIUM,
                homeDifficultyForMode(mode, Difficulty.ADAPTIVE),
            )
        }
        assertEquals(
            Difficulty.ADAPTIVE,
            homeDifficultyForMode(NazoMode.QUIZ, Difficulty.ADAPTIVE),
        )
    }

    @Test
    fun challengeSummaryAlwaysReflectsTheVisibleModeSpecificChoice() {
        assertEquals(
            "Medium • 10 questions",
            challengeSummary(NazoMode.QUIZ, Difficulty.MEDIUM, 10, 3),
        )
        assertEquals(
            "Adaptive • 5 questions",
            challengeSummary(NazoMode.QUIZ, Difficulty.ADAPTIVE, 5, 3),
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
