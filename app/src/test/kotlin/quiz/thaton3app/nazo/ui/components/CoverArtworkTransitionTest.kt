package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtworkTransitionTest {

    @Test
    fun motionStaysInsideTheRestrainedDesignBudget() {
        assertTrue(CoverArtworkTransitionDurationMs in 400..600)
        assertTrue(CoverArtworkIncomingScale in 1f..1.02f)
    }

    @Test
    fun earlyRetargetKeepsOnlyDominantOutgoingAndLatestIncoming() {
        val plan = retargetCoverTransition(
            outgoing = "aurora",
            incoming = "sunset",
            fraction = 0.25f,
            outgoingStartAlpha = 1f,
            outgoingStartScale = 1f,
            incomingStartAlpha = 0f,
            incomingStartScale = CoverArtworkIncomingScale,
            latest = "ocean",
        )

        assertEquals("aurora", plan.outgoing)
        assertEquals("ocean", plan.incoming)
        assertEquals(0.75f, plan.outgoingStartAlpha, 0.0001f)
        assertEquals(0f, plan.incomingStartAlpha, 0f)
    }

    @Test
    fun lateRetargetKeepsCurrentTargetInsteadOfStackingOldLayers() {
        val plan = retargetCoverTransition(
            outgoing = "aurora",
            incoming = "sunset",
            fraction = 0.75f,
            outgoingStartAlpha = 1f,
            outgoingStartScale = 1f,
            incomingStartAlpha = 0f,
            incomingStartScale = CoverArtworkIncomingScale,
            latest = "ocean",
        )

        assertEquals("sunset", plan.outgoing)
        assertEquals("ocean", plan.incoming)
        assertEquals(0.75f, plan.outgoingStartAlpha, 0.0001f)
    }

    @Test
    fun selectingDominantLayerAgainEasesItBackWithoutDuplicateLayer() {
        val plan = retargetCoverTransition(
            outgoing = "aurora",
            incoming = "sunset",
            fraction = 0.25f,
            outgoingStartAlpha = 1f,
            outgoingStartScale = 1f,
            incomingStartAlpha = 0f,
            incomingStartScale = CoverArtworkIncomingScale,
            latest = "aurora",
        )

        assertNull(plan.outgoing)
        assertEquals("aurora", plan.incoming)
        assertEquals(0.75f, plan.incomingStartAlpha, 0.0001f)
    }

    @Test
    fun layerMathClampsAndEndsAtExpectedOpacityAndScale() {
        assertEquals(1f, transitionLayerAlpha(1f, -1f, incoming = false), 0f)
        assertEquals(0f, transitionLayerAlpha(1f, 2f, incoming = false), 0f)
        assertEquals(0f, transitionLayerAlpha(0f, -1f, incoming = true), 0f)
        assertEquals(1f, transitionLayerAlpha(0f, 2f, incoming = true), 0f)
        assertEquals(1f, transitionLayerScale(CoverArtworkIncomingScale, 1f, true), 0f)
        assertTrue(transitionLayerScale(1f, 1f, false) < 1f)
    }
}
