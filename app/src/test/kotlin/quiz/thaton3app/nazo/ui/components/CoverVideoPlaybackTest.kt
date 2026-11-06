package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
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
    fun avatarSyncPositionWrapsToTheDerivedLoop() {
        assertEquals(2_250, normalizeCoverSyncPosition(12_250, 10_000))
        assertEquals(0, normalizeCoverSyncPosition(-100, 10_000))
        assertEquals(450, normalizeCoverSyncPosition(450, 0))
    }

    @Test
    fun modalPreviewOutranksBannerAndNewerTargetWinsEqualPriority() {
        assertTrue(
            coverVideoTargetOutranks(
                candidatePriority = CoverVideoPriority.SHEET_PREVIEW,
                candidateSequence = 1,
                activePriority = CoverVideoPriority.BANNER,
                activeSequence = 99,
            )
        )
        assertFalse(
            coverVideoTargetOutranks(
                candidatePriority = CoverVideoPriority.BANNER,
                candidateSequence = 100,
                activePriority = CoverVideoPriority.DIALOG_PREVIEW,
                activeSequence = 1,
            )
        )
        assertTrue(
            coverVideoTargetOutranks(
                candidatePriority = CoverVideoPriority.SHEET_PREVIEW,
                candidateSequence = 2,
                activePriority = CoverVideoPriority.SHEET_PREVIEW,
                activeSequence = 1,
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
