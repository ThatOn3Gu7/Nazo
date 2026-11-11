package quiz.thaton3app.nazo.ui

import org.junit.Assert.assertEquals
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
        assertTrue(policy.coverVideoPlaybackAllowed)
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
        // Playback is allowed in principle, but no live plane exists until the
        // first settled frame, so the prepared poster remains the only layer.
        assertTrue(policy.coverVideoPlaybackAllowed)
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
        assertTrue(policy.coverVideoPlaybackAllowed)
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
        assertFalse(policy.coverVideoPlaybackAllowed)
    }

    @Test
    fun predictiveBackKeepsTheCurrentCoverSurfacePlaying() {
        val policy = profileRenderPolicy(
            currentScreen = Screen.Profile,
            transitionCurrent = Screen.Profile,
            transitionTarget = Screen.Home,
            predictiveBackTarget = Screen.Home,
        )

        // Decorative clocks can stand down during the scrub, but the already
        // visible video must advance continuously until the gesture commits.
        assertFalse(policy.activeVisualWork)
        assertTrue(policy.liveCoverSurfaceAllowed)
        assertTrue(policy.coverVideoPlaybackAllowed)
    }

    @Test
    fun reducedMotionKeepsNavigationConnectedWithoutSliding() {
        assertEquals(0f, screenNavigationSlideFraction(reduceMotion = true), 0f)
        assertEquals(
            quiz.thaton3app.nazo.ui.components.ScreenNavigationSlideFraction,
            screenNavigationSlideFraction(reduceMotion = false),
            0f,
        )
        assertTrue(
            screenNavigationDurationMs(reduceMotion = true) <
                screenNavigationDurationMs(reduceMotion = false)
        )
    }

    @Test
    fun settingsRotationTransfersOnlyAReachableScrollOffset() {
        assertEquals(640, settingsScrollTransferOffset(outgoingValue = 640, incomingMaxValue = 900))
        assertEquals(420, settingsScrollTransferOffset(outgoingValue = 640, incomingMaxValue = 420))
        assertEquals(0, settingsScrollTransferOffset(outgoingValue = 640, incomingMaxValue = -1))
    }

    @Test
    fun quizCompleteEntranceGateIsConsumedForReviewBackPreviews() {
        val gate = QuizCompleteEntranceGate()

        assertFalse(gate.shouldPlayEntrance())
        gate.armForCompletedQuiz()
        assertTrue(gate.shouldPlayEntrance())

        gate.markEntranceStarted()
        assertFalse(gate.shouldPlayEntrance())
        // Repeated ordinary/predictive back compositions remain settled.
        assertFalse(gate.shouldPlayEntrance())

        // A later completed quiz owns a fresh one-shot entrance.
        gate.armForCompletedQuiz()
        assertTrue(gate.shouldPlayEntrance())
    }

    @Test
    fun quizElapsedLabelUsesTheFrozenCompletionSnapshot() {
        val startedAt = 10_000L
        val finishedAt = 75_999L

        assertEquals("1m 5s", formatQuizElapsed(startedAt, finishedAt))
        // Predictive-back progress may recompose Results long after this point,
        // but there is deliberately no current-clock input to advance the label.
        assertEquals("1m 5s", formatQuizElapsed(startedAt, finishedAt))
        assertEquals("0m 0s", formatQuizElapsed(0L, finishedAt))
        assertEquals("0m 0s", formatQuizElapsed(startedAt, 0L))
        assertEquals("0m 0s", formatQuizElapsed(finishedAt, startedAt))
    }
}
