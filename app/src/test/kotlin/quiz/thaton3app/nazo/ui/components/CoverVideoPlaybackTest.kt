package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Playback-gate contracts for the independent profile-cover player. */
class CoverVideoPlaybackTest {

    @Test
    fun playbackNeedsPreparationVisibleSurfaceAndStartedLifecycle() {
        assertTrue(
            coverVideoShouldPlay(
                prepared = true,
                lifecycleStarted = true,
                playbackAllowed = true,
                hasSurface = true,
            )
        )
    }

    @Test
    fun everyMissingGatePausesInsteadOfRecreatingThePlayer() {
        val valid = booleanArrayOf(true, true, true, true)
        for (blockedIndex in valid.indices) {
            val gates = valid.copyOf().also { it[blockedIndex] = false }
            assertFalse(
                "gate $blockedIndex should pause",
                coverVideoShouldPlay(
                    prepared = gates[0],
                    lifecycleStarted = gates[1],
                    playbackAllowed = gates[2],
                    hasSurface = gates[3],
                )
            )
        }
    }
}
