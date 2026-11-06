package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun rotationMetadataSwapsOnlyQuarterTurnDisplayAxes() {
        assertEquals(CoverVideoSize(1920, 1080), coverVideoDisplaySize(1920, 1080, 0))
        assertEquals(CoverVideoSize(1080, 1920), coverVideoDisplaySize(1920, 1080, 90))
        assertEquals(CoverVideoSize(1920, 1080), coverVideoDisplaySize(1920, 1080, 180))
        assertEquals(CoverVideoSize(1080, 1920), coverVideoDisplaySize(1920, 1080, 270))
        assertEquals(CoverVideoSize(1080, 1920), coverVideoDisplaySize(1920, 1080, -90))
        assertNull(coverVideoDisplaySize(0, 1080, 90))
    }

    @Test
    fun cropTransformPreservesProportionsForLandscapeAndPortraitVideo() {
        val landscape = requireNotNull(
            coverVideoCropScale(
                viewWidth = 400,
                viewHeight = 100,
                videoWidth = 1920,
                videoHeight = 1080,
            )
        )
        // A 16:9 clip in a 4:1 banner fills width and crops vertically.
        assertEquals(1f, landscape.x, 0.0001f)
        assertEquals(2.25f, landscape.y, 0.0001f)

        val portrait = requireNotNull(
            coverVideoCropScale(
                viewWidth = 400,
                viewHeight = 100,
                videoWidth = 1080,
                videoHeight = 1920,
            )
        )
        // A portrait recording uses the same centered fill rule without
        // distortion; only the vertical overflow is clipped by the banner.
        assertEquals(1f, portrait.x, 0.0001f)
        assertEquals(400f / 100f / (1080f / 1920f), portrait.y, 0.0001f)

        assertEquals(
            CoverVideoCropScale(1f, 1f),
            coverVideoCropScale(400, 100, 1600, 400),
        )
        assertNull(coverVideoCropScale(0, 100, 1920, 1080))
    }
}
