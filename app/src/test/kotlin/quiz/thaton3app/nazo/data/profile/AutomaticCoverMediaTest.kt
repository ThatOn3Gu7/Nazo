package quiz.thaton3app.nazo.data.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCoverMediaTest {

    @Test
    fun sourceClassificationKeepsAutomaticScopeExplicit() {
        assertEquals(AutomaticCoverMedia.SourceKind.NONE, AutomaticCoverMedia.sourceKind(null))
        assertEquals(AutomaticCoverMedia.SourceKind.NONE, AutomaticCoverMedia.sourceKind("emoji:🌊"))
        assertEquals(
            AutomaticCoverMedia.SourceKind.STATIC_IMAGE,
            AutomaticCoverMedia.sourceKind("file:///profile/avatar_1.jpg"),
        )
        assertEquals(
            AutomaticCoverMedia.SourceKind.VIDEO,
            AutomaticCoverMedia.sourceKind("file:///profile/avatar_2.mp4"),
        )
        assertEquals(
            AutomaticCoverMedia.SourceKind.UNSUPPORTED_ANIMATION,
            AutomaticCoverMedia.sourceKind("file:///profile/avatar_3.gif"),
        )
        assertEquals(
            AutomaticCoverMedia.SourceKind.UNSUPPORTED_ANIMATION,
            AutomaticCoverMedia.sourceKind("file:///profile/avatar_4.webp"),
        )
    }

    @Test
    fun videoOutputIsEvenBoundedWideAndNeverUpscalesNormalSources() {
        assertEquals(960 to 540, AutomaticCoverMedia.videoOutputSize(1920, 1080, 0))
        assertEquals(960 to 540, AutomaticCoverMedia.videoOutputSize(1080, 1920, 0))
        assertEquals(960 to 540, AutomaticCoverMedia.videoOutputSize(1920, 1080, 90))
        assertEquals(640 to 360, AutomaticCoverMedia.videoOutputSize(640, 480, 0))
        assertEquals(480 to 270, AutomaticCoverMedia.videoOutputSize(480, 480, 0))
        assertNull(AutomaticCoverMedia.videoOutputSize(0, 720, 0))
        for ((width, height) in listOf(960 to 540, 640 to 360, 480 to 270)) {
            assertEquals(0, width % 2)
            assertEquals(0, height % 2)
            assertTrue(width <= AutomaticCoverMedia.VIDEO_MAX_WIDTH)
            assertTrue(height <= AutomaticCoverMedia.VIDEO_MAX_HEIGHT)
        }
    }

    @Test
    fun dominantPaletteIsOpaqueDistinctAndDeterministic() {
        val pixels = IntArray(100) { index ->
            when {
                index < 50 -> 0xFF1A78C2.toInt()
                index < 80 -> 0xFFE0684A.toInt()
                else -> 0xFF2DA66F.toInt()
            }
        }
        val first = AutomaticCoverMedia.dominantPalette(pixels)
        val second = AutomaticCoverMedia.dominantPalette(pixels)
        assertArrayEquals(first, second)
        assertEquals(3, first.size)
        first.forEach { assertEquals(0xFF, it ushr 24) }
        assertNotEquals(first[0], first[1])
    }

    @Test
    fun blurKeepsFlatColourAndSpreadsAnImpulse() {
        val flat = IntArray(25) { 0xFF336699.toInt() }
        assertArrayEquals(
            flat,
            AutomaticCoverMedia.blurOpaque(flat, width = 5, height = 5, radius = 1),
        )

        val impulse = IntArray(25) { 0xFF000000.toInt() }
        impulse[12] = 0xFFFFFFFF.toInt()
        val blurred = AutomaticCoverMedia.blurOpaque(
            impulse,
            width = 5,
            height = 5,
            radius = 1,
            passes = 1,
        )
        val centre = blurred[12] and 0xFF
        val neighbour = blurred[11] and 0xFF
        assertTrue(centre in 1..254)
        assertTrue(neighbour > 0)
    }
}
