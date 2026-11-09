package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.ui.Screen

/**
 * Pins the overlay's transition math and winner selection — the values that
 * decide where the persistent video circle sits and how opaque it is during
 * navigation and predictive back. These mirror NazoApp's screen
 * transitionSpec; if that spec changes, these tests are the tripwire.
 */
class AvatarOverlayTest {

    private fun eased(x: Float): Float =
        FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

    // ---- avatarOverlayTransform ----

    @Test
    fun sharedNavigationMotionStaysSubtleAndResponsive() {
        assertTrue(ScreenNavigationTransitionDurationMs in 200..280)
        assertTrue(ScreenNavigationSlideFraction in 0.05f..0.12f)
    }

    @Test
    fun atRestTheOverlayIsFullyVisibleAndUnmoved() {
        // Every flag combination must collapse to the same rest state —
        // a lingering predictiveBackTarget after a settle must not dim or
        // shift the circle.
        for (predictive in listOf(false, true)) {
            for (targetIs in listOf(false, true)) {
                val t = avatarOverlayTransform(
                    fraction = 0.7f,
                    transitioning = false,
                    predictiveTargetPresent = predictive,
                    targetIsPredictive = targetIs,
                    initialIsPredictive = predictive && !targetIs,
                    winnerIsTarget = true,
                    edgeDirection = -1,
                    containerWidth = 1080f,
                )
                assertEquals(1f, t.alpha, 0f)
                assertEquals(0f, t.slideX, 0f)
            }
        }
    }

    @Test
    fun ordinaryIncomingOverlayMirrorsTheSubtlePushSlideAndFade() {
        val width = 1080f
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = false,
                targetIsPredictive = false,
                initialIsPredictive = false,
                winnerIsTarget = true,
                edgeDirection = 1,
                ordinaryDirection = 1,
                containerWidth = width,
            )
            assertEquals(eased(f), t.alpha, 0f)
            assertEquals(
                width * ScreenNavigationSlideFraction * (1f - eased(f)),
                t.slideX,
                0.0001f,
            )
        }
    }

    @Test
    fun ordinaryWinnerRidesOutgoingPushWithoutChangingNativeViewSize() {
        val width = 1080f
        for (f in listOf(0f, 0.5f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = false,
                targetIsPredictive = false,
                initialIsPredictive = false,
                winnerIsTarget = false,
                edgeDirection = 1,
                ordinaryDirection = 1,
                containerWidth = width,
            )
            assertEquals(1f - eased(f), t.alpha, 0f)
            assertEquals(
                -width * ScreenNavigationSlideFraction * eased(f),
                t.slideX,
                0.0001f,
            )
        }
    }

    @Test
    fun ordinaryPopReversesTheSlideDirection() {
        val width = 800f
        val forward = avatarOverlayTransform(
            fraction = 0.5f,
            transitioning = true,
            predictiveTargetPresent = false,
            targetIsPredictive = false,
            initialIsPredictive = false,
            winnerIsTarget = false,
            edgeDirection = 1,
            ordinaryDirection = 1,
            containerWidth = width,
        )
        val back = avatarOverlayTransform(
            fraction = 0.5f,
            transitioning = true,
            predictiveTargetPresent = false,
            targetIsPredictive = false,
            initialIsPredictive = false,
            winnerIsTarget = false,
            edgeDirection = 1,
            ordinaryDirection = -1,
            containerWidth = width,
        )
        assertEquals(-forward.slideX, back.slideX, 0f)
        assertEquals(forward.alpha, back.alpha, 0f)
    }

    @Test
    fun leftEdgePredictivePreviewMatchesOrdinaryBackAtEveryProgress() {
        val width = 1080f
        for (f in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            val predictive = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = true,
                targetIsPredictive = true,
                initialIsPredictive = false,
                winnerIsTarget = false,
                edgeDirection = 1,
                containerWidth = width,
            )
            val ordinaryBack = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = false,
                targetIsPredictive = false,
                initialIsPredictive = false,
                winnerIsTarget = false,
                edgeDirection = 1,
                ordinaryDirection = -1,
                containerWidth = width,
            )
            assertEquals(ordinaryBack.alpha, predictive.alpha, 0f)
            assertEquals(ordinaryBack.slideX, predictive.slideX, 0f)
        }
    }

    @Test
    fun predictiveGestureScrubsTheSameSubtleSlideAndFade() {
        val width = 1080f
        // Right-edge swipe: edgeDirection = -1 per NazoApp's mapping.
        for (f in listOf(0f, 0.5f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = true,
                targetIsPredictive = true, // gesture / commit settle branch
                initialIsPredictive = false,
                winnerIsTarget = false,
                edgeDirection = -1,
                containerWidth = width,
            )
            assertEquals(1f - eased(f), t.alpha, 0f)
            assertEquals(
                -width * ScreenNavigationSlideFraction * eased(f),
                t.slideX,
                0.0001f,
            )
        }
        // Start: full alpha, no slide (continuous with the resting screen).
        val start = avatarOverlayTransform(
            fraction = 0f, transitioning = true, predictiveTargetPresent = true,
            targetIsPredictive = true, initialIsPredictive = false,
            winnerIsTarget = false,
            edgeDirection = -1, containerWidth = width,
        )
        assertEquals(1f, start.alpha, 0f)
        assertEquals(0f, start.slideX, 0f)
        // End: fully faded, but displaced by only the shared restrained 8%.
        val end = avatarOverlayTransform(
            fraction = 1f, transitioning = true, predictiveTargetPresent = true,
            targetIsPredictive = true, initialIsPredictive = false,
            winnerIsTarget = false,
            edgeDirection = -1, containerWidth = width,
        )
        assertEquals(0f, end.alpha, 0f)
        assertEquals(-width * ScreenNavigationSlideFraction, end.slideX, 0f)
    }

    @Test
    fun cancelSettleUsesTheExactInverseSharedMotion() {
        val width = 1080f
        for (f in listOf(0f, 0.5f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = true,
                targetIsPredictive = false,
                initialIsPredictive = true, // cancel-settle branch
                winnerIsTarget = true,
                edgeDirection = 1,
                containerWidth = width,
            )
            assertEquals(eased(f), t.alpha, 0f)
            assertEquals(
                width * ScreenNavigationSlideFraction * (1f - eased(f)),
                t.slideX,
                0.0001f,
            )
        }
    }

    // ---- resolveAvatarWinnerScreen ----

    @Test
    fun winnerAtRestIsTheCurrentScreen() {
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Profile,
                targetState = Screen.Profile,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun winnerOnForwardNavigationStaysWithOutgoingScreenUntilSettle() {
        assertSame(
            Screen.Home,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Home,
                targetState = Screen.Profile,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun stackCommitBeforeAnimateToStillRetainsMeasuredOutgoingAnchor() {
        // Compose can observe the stack mutation one frame before animateTo
        // changes the seekable state's target. Profile is not measured yet.
        assertSame(
            Screen.Home,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Home,
                targetState = Screen.Home,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun ordinaryWinnerChangesOnlyAfterTransitionSettlement() {
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Profile,
                targetState = Screen.Profile,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun ordinaryBackAlsoRetainsTheOutgoingProfileAnchor() {
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Home,
                currentState = Screen.Profile,
                targetState = Screen.Home,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun winnerStaysOnTheDismissedScreenThroughGestureAndCommitSettle() {
        // Gesture: stack still shows Profile while Home is revealed behind.
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Profile,
                targetState = Screen.Home,
                predictiveTarget = Screen.Home,
            ),
        )
        // Commit settle: the stack already popped to Home, but the transition
        // is still sliding Profile out — the circle rides it until settled.
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Home,
                currentState = Screen.Profile,
                targetState = Screen.Home,
                predictiveTarget = Screen.Home,
            ),
        )
    }

    @Test
    fun winnerOnCancelSettleIsTheScreenWeNeverLeft() {
        // Segment flipped: initialState is the revealed Home, targetState is
        // Profile sliding back home — the circle belongs to Profile.
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Home,
                targetState = Screen.Profile,
                predictiveTarget = Screen.Home,
            ),
        )
    }

    @Test
    fun staleBoundsNeverBecomeVisibleForANewWinner() {
        assertFalse(
            avatarOverlayBoundsMatchWinner(
                boundsOwner = AvatarAnchorId.Home,
                winner = AvatarAnchorId.Profile,
                ready = true,
            )
        )
        assertFalse(
            avatarOverlayBoundsMatchWinner(
                boundsOwner = AvatarAnchorId.Profile,
                winner = AvatarAnchorId.Profile,
                ready = false,
            )
        )
        assertTrue(
            avatarOverlayBoundsMatchWinner(
                boundsOwner = AvatarAnchorId.Profile,
                winner = AvatarAnchorId.Profile,
                ready = true,
            )
        )
    }

    @Test
    fun persistentSurfaceActionsFollowTheCurrentAvatarAnchor() {
        val home = AvatarAnchorState(AvatarAnchorId.Home)
        val profile = AvatarAnchorState(AvatarAnchorId.Profile)
        var homeClicks = 0
        var profileClicks = 0
        var profileLongPresses = 0
        home.onClick = { homeClicks++ }
        profile.onClick = { profileClicks++ }
        profile.onLongClick = { profileLongPresses++ }

        dispatchAvatarAnchorClick(home)
        dispatchAvatarAnchorClick(profile)
        assertEquals(1, homeClicks)
        assertEquals(1, profileClicks)

        assertFalse(dispatchAvatarAnchorLongClick(home))
        assertTrue(dispatchAvatarAnchorLongClick(profile))
        assertEquals(1, profileLongPresses)
    }

}
