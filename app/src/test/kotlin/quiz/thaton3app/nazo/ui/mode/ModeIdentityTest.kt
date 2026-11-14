package quiz.thaton3app.nazo.ui.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeIdentityTest {

    @Test
    fun everyModeHasDistinctCompleteIdentityCopy() {
        val specs = NazoMode.entries.map(::modeIdentitySpec)

        assertEquals(NazoMode.entries.size, specs.map { it.eyebrow }.distinct().size)
        assertEquals(NazoMode.entries.size, specs.map { it.headline }.distinct().size)
        specs.forEach { spec ->
            assertTrue(spec.description.isNotBlank())
            assertEquals(2, spec.cues.size)
        }
    }

    @Test
    fun specialGameplayPresentationHasDeterministicPrecedence() {
        assertEquals(GameplayPresentation.QUIZ, gameplayPresentation(false, false, null))
        assertEquals(GameplayPresentation.VERSUS, gameplayPresentation(false, false, "P1"))
        assertEquals(GameplayPresentation.SURVIVAL, gameplayPresentation(true, false, null))
        assertEquals(GameplayPresentation.BLITZ, gameplayPresentation(true, true, "P2"))
    }

    @Test
    fun survivalDangerEscalatesOnlyAsLivesDisappear() {
        assertEquals(SurvivalDanger.STEADY, survivalDanger(3))
        assertEquals(SurvivalDanger.EXPOSED, survivalDanger(2))
        assertEquals(SurvivalDanger.LAST_LIFE, survivalDanger(1))
        assertEquals(SurvivalDanger.DEFEATED, survivalDanger(0))
        assertEquals("Last life", survivalDangerLabel(1))
        assertTrue(survivalDanger(1).intensity > survivalDanger(2).intensity)
    }

    @Test
    fun blitzClockAndUrgencyStayPurelyPresentational() {
        assertEquals("00:60", blitzClockText(60))
        assertEquals("00:05", blitzClockText(5))
        assertEquals("00:00", blitzClockText(-4))
        assertEquals(BlitzUrgency.CRUISING, blitzUrgency(60))
        assertEquals(BlitzUrgency.PRESSURE, blitzUrgency(15))
        assertEquals(BlitzUrgency.FINAL_SECONDS, blitzUrgency(5))
        assertEquals(BlitzUrgency.TIME_UP, blitzUrgency(0))
    }

    @Test
    fun mysteryStagesMoveFromObscuredToRevealed() {
        assertEquals(MysteryRevealStage.OBSCURED, mysteryRevealStage(1f))
        assertEquals(MysteryRevealStage.EMERGING, mysteryRevealStage(0.5f))
        assertEquals(MysteryRevealStage.NEARLY_CLEAR, mysteryRevealStage(0.1f))
        assertEquals(MysteryRevealStage.REVEALED, mysteryRevealStage(0.8f, revealed = true))
    }

    @Test
    fun blitzUsesQuickerMoreDirectionalVisualHandoff() {
        assertTrue(
            questionTransitionDurationMs(GameplayPresentation.BLITZ) <
                questionTransitionDurationMs(GameplayPresentation.QUIZ)
        )
        assertTrue(
            questionTransitionSlideFraction(GameplayPresentation.BLITZ) >
                questionTransitionSlideFraction(GameplayPresentation.QUIZ)
        )
        assertNotEquals(
            questionTransitionSlideFraction(GameplayPresentation.VERSUS),
            questionTransitionSlideFraction(GameplayPresentation.BLITZ),
        )
    }
}
