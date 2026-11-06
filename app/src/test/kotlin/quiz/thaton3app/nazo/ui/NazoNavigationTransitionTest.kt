package quiz.thaton3app.nazo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lifecycle policy contracts for Profile inside the root AnimatedContent. */
class NazoNavigationTransitionTest {

    @Test
    fun settledProfileRunsVisualWorkAndAllowsLiveCoverSurface() {
        val policy = profileRenderPolicy(
            currentScreen = Screen.Profile,
            transitionCurrent = Screen.Profile,
            transitionTarget = Screen.Profile,
            predictiveBackTarget = null,
        )

        assertTrue(policy.activeVisualWork)
        assertTrue(policy.liveCoverSurfaceAllowed)
    }

    @Test
    fun preAnimateToFrameIsAlreadyTreatedAsAnOrdinaryProfileEntry() {
        // The stack commits before SeekableTransitionState.animateTo updates
        // targetState. This was the frame that used to start every decoder and
        // gradient clock before Profile even had final bounds.
        val policy = profileRenderPolicy(
            currentScreen = Screen.Profile,
            transitionCurrent = Screen.Home,
            transitionTarget = Screen.Home,
            predictiveBackTarget = null,
        )

        assertFalse(policy.activeVisualWork)
        assertFalse(policy.liveCoverSurfaceAllowed)
    }

    @Test
    fun ordinaryProfileEntryUsesPosterUntilTransitionSettlement() {
        val policy = profileRenderPolicy(
            currentScreen = Screen.Profile,
            transitionCurrent = Screen.Home,
            transitionTarget = Screen.Profile,
            predictiveBackTarget = null,
        )

        assertFalse(policy.activeVisualWork)
        assertFalse(policy.liveCoverSurfaceAllowed)
    }

    @Test
    fun ordinaryExitPausesWorkButKeepsExistingSurfaceAttached() {
        val policy = profileRenderPolicy(
            currentScreen = Screen.Home,
            transitionCurrent = Screen.Profile,
            transitionTarget = Screen.Home,
            predictiveBackTarget = null,
        )

        assertFalse(policy.activeVisualWork)
        assertTrue(policy.liveCoverSurfaceAllowed)
    }

    @Test
    fun predictiveBackPausesWorkWithoutReplacingTheCurrentVideoFrame() {
        val policy = profileRenderPolicy(
            currentScreen = Screen.Profile,
            transitionCurrent = Screen.Profile,
            transitionTarget = Screen.Home,
            predictiveBackTarget = Screen.Home,
        )

        assertFalse(policy.activeVisualWork)
        assertTrue(policy.liveCoverSurfaceAllowed)
    }
}
