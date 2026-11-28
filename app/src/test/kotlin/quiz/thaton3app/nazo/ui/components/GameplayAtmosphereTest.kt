package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.ui.mode.GameplayPresentation

class GameplayAtmosphereTest {

    @Test
    fun everyQuestionModeReceivesItsOwnAtmosphereIdentity() {
        assertEquals(
            GameplayAtmosphereKind.Quiz,
            gameplayAtmosphereKind(GameplayPresentation.QUIZ),
        )
        assertEquals(
            GameplayAtmosphereKind.Survival,
            gameplayAtmosphereKind(GameplayPresentation.SURVIVAL),
        )
        assertEquals(
            GameplayAtmosphereKind.Blitz,
            gameplayAtmosphereKind(GameplayPresentation.BLITZ),
        )
        assertEquals(
            GameplayAtmosphereKind.Versus,
            gameplayAtmosphereKind(GameplayPresentation.VERSUS),
        )
        assertTrue(GameplayAtmosphereKind.entries.contains(GameplayAtmosphereKind.Guessing))
    }

    @Test
    fun motionCadenceIsModeSpecificAndReducedMotionStartsNoClock() {
        val periods = GameplayAtmosphereKind.entries.map { kind ->
            requireNotNull(gameplayAtmospherePeriodMillis(kind, motionAllowed = true))
        }

        assertEquals(GameplayAtmosphereKind.entries.size, periods.distinct().size)
        GameplayAtmosphereKind.entries.forEach { kind ->
            assertNull(gameplayAtmospherePeriodMillis(kind, motionAllowed = false))
        }
    }

    @Test
    fun versusAtmosphereFollowsTheActivePlayerSide() {
        assertEquals(0, versusAtmosphereSide("P1"))
        assertEquals(1, versusAtmosphereSide("P2"))
        assertEquals(1, versusAtmosphereSide(" p2 "))
        assertEquals(0, versusAtmosphereSide(null))
    }
}
