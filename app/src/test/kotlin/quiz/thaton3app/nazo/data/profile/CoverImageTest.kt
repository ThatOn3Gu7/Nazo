package quiz.thaton3app.nazo.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cover-image policy: the resolution floor (the "no tiny images"
 * guard — Crop upscale softness is the failure it prevents) and the
 * animated-pick rejection copy. Pure JVM, no Android types.
 */
class CoverImageTest {

    @Test
    fun imagesAtOrAboveTheFloorAreAccepted() {
        // Exactly at the floor passes — the floor is inclusive.
        assertNull(CoverImage.sizeError(CoverImage.MIN_WIDTH, CoverImage.MIN_HEIGHT))
        // Typical camera photo and a wide-but-short-enough panorama.
        assertNull(CoverImage.sizeError(4000, 3000))
        assertNull(CoverImage.sizeError(3000, 900))
        // Huge square sources pass too (heavy top/bottom crop is a framing
        // choice, not a resolution problem).
        assertNull(CoverImage.sizeError(2000, 2000))
    }

    @Test
    fun imagesBelowTheFloorAreRejectedWithActionableCopy() {
        // One pixel short on either axis fails.
        val tooNarrow = CoverImage.sizeError(CoverImage.MIN_WIDTH - 1, CoverImage.MIN_HEIGHT)
        assertNotNull(tooNarrow)
        assertEquals(true, tooNarrow!!.contains("1024"))

        val tooShort = CoverImage.sizeError(CoverImage.MIN_WIDTH, CoverImage.MIN_HEIGHT - 1)
        assertNotNull(tooShort)
        assertEquals(true, tooShort!!.contains("480"))

        // A classic small photo (800×600) is refused outright.
        assertNotNull(CoverImage.sizeError(800, 600))
    }

    @Test
    fun animatedPickCopyNamesTheFix() {
        // The rejection must say what kind of image IS acceptable, not just
        // what was refused — "static" is the actionable word.
        assertEquals(true, CoverImage.ANIMATED_ERROR.contains("static"))
        assertEquals(true, CoverImage.ANIMATED_ERROR.contains("cover"))
    }
}
