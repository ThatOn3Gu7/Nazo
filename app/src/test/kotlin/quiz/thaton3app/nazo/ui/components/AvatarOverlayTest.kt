package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun ordinaryNavigationIsAPureFadeOfTheIncomingScreen() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = false,
                targetIsPredictive = false,
                initialIsPredictive = false,
                winnerIsTarget = true,
                edgeDirection = 1,
                containerWidth = 1080f,
            )
            assertEquals(eased(f), t.alpha, 0f)
            assertEquals(0f, t.slideX, 0f)
        }
    }

    @Test
    fun ordinaryNavigationRidesTheExitFadeWhenWinnerIsTheOutgoingScreen() {
        // Navigation started while a settle was still running: the winner is
        // the outgoing screen and must follow fadeOut (tween 160 of a 220 ms
        // segment), not fade in.
        for (f in listOf(0f, 0.5f, 1f)) {
            val t = avatarOverlayTransform(
                fraction = f,
                transitioning = true,
                predictiveTargetPresent = false,
                targetIsPredictive = false,
                initialIsPredictive = false,
                winnerIsTarget = false,
                edgeDirection = 1,
                containerWidth = 1080f,
            )
            assertEquals(1f - eased(f * 220f / 160f), t.alpha, 0f)
            assertEquals(0f, t.slideX, 0f)
        }
    }

    @Test
    fun predictiveGestureSlidesAndFadesTheScreenBeingLeft() {
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
            // Mirror of slideOut(to +width*dir) + fadeOut, both tween 220.
            assertEquals(1f - eased(f), t.alpha, 0f)
            assertEquals(width * -1 * eased(f), t.slideX, 0f)
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
        // End: fully faded out and fully off toward the edge.
        val end = avatarOverlayTransform(
            fraction = 1f, transitioning = true, predictiveTargetPresent = true,
            targetIsPredictive = true, initialIsPredictive = false,
            winnerIsTarget = false,
            edgeDirection = -1, containerWidth = width,
        )
        assertEquals(0f, end.alpha, 0f)
        assertEquals(-width, end.slideX, 0f)
    }

    @Test
    fun cancelSettleSlidesHomeWhileFadingInOnThe180msSpec() {
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
            // Mirror of slideIn(from +width*dir, tween 220) + fadeIn(tween 180)
            // inside a 220 ms segment: the fade's local time runs at
            // f * 220 / 180 (clamped by the animation at 1).
            assertEquals(eased(f * 220f / 180f), t.alpha, 0f)
            assertEquals(width * 1 * (1f - eased(f)), t.slideX, 0f)
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
        assertSame(
            Screen.Settings,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Settings,
                currentState = Screen.Home,
                targetState = Screen.Settings,
                predictiveTarget = null,
            ),
        )
    }

    @Test
    fun winnerOnForwardNavigationIsTheIncomingScreen() {
        // currentScreen flips at push time; no predictive target involved.
        assertSame(
            Screen.Profile,
            resolveAvatarWinnerScreen(
                currentScreen = Screen.Profile,
                currentState = Screen.Home,
                targetState = Screen.Profile,
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
}
