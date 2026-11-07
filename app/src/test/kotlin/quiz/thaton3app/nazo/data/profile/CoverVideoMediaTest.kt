package quiz.thaton3app.nazo.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy contracts for custom video covers. */
class CoverVideoMediaTest {

    @Test
    fun storedVideoDispatchUsesOnlyOurMp4FileExtension() {
        assertTrue(CoverVideoMedia.isVideoUri("file:///data/user/0/nazo/profile_covers/cover_1.mp4"))
        assertTrue(CoverVideoMedia.isVideoUri("file:///data/user/0/nazo/profile_covers/cover_1.MP4"))
        for (uri in listOf(
            null,
            "",
            "file:///data/user/0/nazo/profile_covers/cover_1.jpg",
            "content://media/external/video/42",
            "file:///data/user/0/nazo/profile_covers/cover_1.webm",
        )) {
            assertFalse("for $uri", CoverVideoMedia.isVideoUri(uri))
        }
    }

    @Test
    fun fileSizeIsCappedAtTwentyFourMegabytes() {
        assertNull(CoverVideoMedia.sizeError(CoverVideoMedia.MAX_BYTES))
        val error = CoverVideoMedia.sizeError(CoverVideoMedia.MAX_BYTES + 1)
        assertNotNull(error)
        assertTrue(error!!.contains("24 MB"))
    }

    @Test
    fun loopDurationIsCappedAtFifteenSeconds() {
        assertNull(CoverVideoMedia.durationError(1L))
        assertNull(CoverVideoMedia.durationError(15_000L))
        val error = CoverVideoMedia.durationError(15_001L)
        assertNotNull(error)
        assertTrue(error!!.contains("15 seconds"))
        assertTrue(error.contains("Trim"))
    }

    @Test
    fun dimensionsAcceptWidePortraitAndSquareWithinThePixelBudget() {
        for ((width, height) in listOf(
            1024 to 480,
            1280 to 720,
            1920 to 1080,
            1080 to 1920,
            1024 to 1024,
        )) {
            assertNull("$width x $height", CoverVideoMedia.dimensionError(width, height))
        }
    }

    @Test
    fun dimensionsRejectSoftInvalidAndOverBudgetFrames() {
        assertTrue(CoverVideoMedia.dimensionError(0, 720)!!.contains("no readable"))
        assertTrue(CoverVideoMedia.dimensionError(960, 480)!!.contains("too small"))
        assertTrue(CoverVideoMedia.dimensionError(1280, 360)!!.contains("too small"))
        assertTrue(CoverVideoMedia.dimensionError(2560, 1440)!!.contains("1080p"))
        assertTrue(CoverVideoMedia.dimensionError(1440, 1440)!!.contains("1080p"))
    }

    @Test
    fun frameRateAllowsThirtyAndRejectsHighFrameRate() {
        assertNull(CoverVideoMedia.frameRateError(null)) // metadata may be absent
        assertNull(CoverVideoMedia.frameRateError(30f))
        assertNull(CoverVideoMedia.frameRateError(30.49f))
        assertTrue(CoverVideoMedia.frameRateError(30.51f)!!.contains("30 fps"))
        assertTrue(CoverVideoMedia.frameRateError(60f)!!.contains("Re-export"))
    }

    @Test
    fun onlyAvcIsTheAcceptedVideoTrackCodec() {
        assertNull(CoverVideoMedia.codecError("video/avc"))
        assertNull(CoverVideoMedia.codecError("VIDEO/AVC"))
        for (mime in listOf(null, "", "video/hevc", "video/vp9", "video/av01")) {
            assertNotNull("for $mime", CoverVideoMedia.codecError(mime))
        }
        assertEquals("video/avc", CoverVideoMedia.SUPPORTED_CODEC_MIME)
    }
}
