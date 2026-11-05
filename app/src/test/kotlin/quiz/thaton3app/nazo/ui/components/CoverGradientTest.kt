package quiz.thaton3app.nazo.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure math contracts for the ribbon gradient renderer.
 *
 * These pin down the three properties the redesign is built on:
 * determinism (same seed → same shapes, no `Random`), continuity in time
 * (ribbons undulate without snapping and colors morph without palette-index
 * jumps), and palette discipline (ring lookups always return a real blend
 * inside the stop hull).
 */
class CoverGradientTest {

    private val palette = listOf(
        Color(0xFF0A1A2F),
        Color(0xFF1FA97A),
        Color(0xFF2ED8C0),
        Color(0xFF8FF3E4),
    )

    // --- Determinism ------------------------------------------------------

    @Test
    fun layerRandIsDeterministicAndBounded() {
        for (seed in 0..3) {
            for (layer in 0 until RIBBON_COUNT) {
                for (salt in 0..20) {
                    val a = layerRand(layer, seed, salt)
                    val b = layerRand(layer, seed, salt)
                    assertEquals("seed=$seed layer=$layer salt=$salt", a, b)
                    assertTrue("out of [0,1): $a", a >= 0f && a < 1f)
                }
            }
        }
    }

    @Test
    fun layerRandDiffersAcrossLayersSeedsAndSalts() {
        // Same salt, different layer → different value (layers must not
        // move in lockstep), and the same layer under different salts
        // produces a spread rather than one repeated number.
        val layers = (0 until RIBBON_COUNT).map { layerRand(it, 0, 3) }.toSet()
        assertEquals(RIBBON_COUNT, layers.size)
        val salts = (0..20).map { layerRand(0, 0, it) }.toSet()
        assertTrue("salts should produce many distinct values", salts.size > 15)
        // Different seeds (presets) must decorrelate entirely.
        val seeds = (0..3).map { layerRand(1, it, 3) }.toSet()
        assertEquals(4, seeds.size)
    }

    @Test
    fun ribbonParamsArePureAndDistinctPerLayer() {
        val first = (0 until RIBBON_COUNT).map { ribbonParams(it, 2) }
        val second = (0 until RIBBON_COUNT).map { ribbonParams(it, 2) }
        assertEquals(first, second) // pure: same inputs, same outputs
        // No two layers share the same vertical anchor — otherwise ribbons
        // would stack as copies of each other.
        val anchors = first.map { it.baseY }.toSet()
        assertEquals(RIBBON_COUNT, anchors.size)
        first.forEach { p ->
            assertTrue("thickness in range", p.thickness in 0.05f..0.17f)
            assertTrue("alpha in range", p.alpha in 0.37f..0.69f)
            assertTrue("positive amplitude", p.amp1 > 0f && p.amp2 > 0f)
            assertTrue("detail wave is the gentler one", p.amp2 <= p.amp1)
            assertTrue("spread covers >1 stop", p.colorSpread > 1f)
        }
    }

    // --- Continuity in time ------------------------------------------------

    @Test
    fun ribbonCenterYIsContinuousInTime() {
        // Small dt must move the centerline only slightly — this is the
        // "no snapping/flickering" requirement expressed numerically.
        val p = ribbonParams(0, 1)
        val w = 1440f
        val h = 720f
        for (x in listOf(-0.1f * w, 0f, 0.37f * w, 0.8f * w, 1.1f * w)) {
            for (t in listOf(0f, 3.7f, 41.2f)) {
                val y0 = ribbonCenterY(x, w, h, t, p)
                val y1 = ribbonCenterY(x, w, h, t + 0.016f, p) // one frame
                val jump = kotlin.math.abs(y1 - y0)
                assertTrue("x=$x t=$t jump=$jump", jump < 0.02f * h)
            }
        }
    }

    @Test
    fun ribbonCenterYIsContinuousInX() {
        // The centerline is a function, not a step: adjacent samples can't
        // teleport (guards against a broken phase term).
        val p = ribbonParams(3, 0)
        val w = 1440f
        val h = 720f
        var x = -0.12f * w
        while (x < 1.12f * w) {
            val y0 = ribbonCenterY(x, w, h, 12.5f, p)
            val y1 = ribbonCenterY(x + 1f, w, h, 12.5f, p)
            assertTrue("step at x=$x", kotlin.math.abs(y1 - y0) < 0.05f * h)
            x += 24f
        }
    }

    @Test
    fun paletteColorIsContinuousAcrossTheRingWrap() {
        // The old renderer snapped between palette indices; the new one
        // must be continuous everywhere, including where pos wraps past
        // the last stop back to the first (and for negative drift).
        val n = palette.size
        for (i in 0 until n) {
            val atStop = paletteColor(palette, i.toFloat())
            val justBefore = paletteColor(palette, i.toFloat() - 0.001f)
            val justAfter = paletteColor(palette, i.toFloat() + 0.001f)
            assertTrue(colorNear(atStop, justBefore, 0.02f))
            assertTrue(colorNear(atStop, justAfter, 0.02f))
        }
        // Negative positions (negative colorRate drift) wrap to the top
        // of the ring and stay continuous there too.
        val wrapped = paletteColor(palette, -0.001f)
        val top = paletteColor(palette, 0f)
        assertTrue(colorNear(wrapped, top, 0.02f))
    }

    @Test
    fun paletteColorStaysInsideTheStopHull() {
        // Every lookup is a convex blend of neighbouring stops — it can
        // never invent a color outside the palette's per-channel bounds.
        var pos = -2f
        while (pos <= 8f) {
            val c = paletteColor(palette, pos)
            val minR = palette.minOf { it.red }
            val maxR = palette.maxOf { it.red }
            val minG = palette.minOf { it.green }
            val maxG = palette.maxOf { it.green }
            val minB = palette.minOf { it.blue }
            val maxB = palette.maxOf { it.blue }
            assertTrue("red hull at $pos", c.red in (minR - 0.001f)..(maxR + 0.001f))
            assertTrue("green hull at $pos", c.green in (minG - 0.001f)..(maxG + 0.001f))
            assertTrue("blue hull at $pos", c.blue in (minB - 0.001f)..(maxB + 0.001f))
            pos += 0.137f
        }
    }

    @Test
    fun paletteColorInterpolatesBetweenAdjacentStops() {
        // At the exact stop position it returns that stop; halfway it
        // returns Compose's perceptual midpoint (smoothstep(0.5) = 0.5),
        // not a raw arithmetic average of encoded RGB channels.
        assertTrue(colorNear(palette[0], paletteColor(palette, 0f), 0.001f))
        val mid = paletteColor(palette, 0.5f)
        val expected = androidx.compose.ui.graphics.lerp(palette[0], palette[1], 0.5f)
        assertTrue(colorNear(expected, mid, 0.001f))
        assertTrue(
            "midpoint must differ from the left stop",
            !colorNear(mid, palette[0], 0.01f),
        )
        assertTrue(
            "midpoint must differ from the right stop",
            !colorNear(mid, palette[1], 0.01f),
        )
    }

    private fun colorNear(a: Color, b: Color, tolerance: Float): Boolean =
        kotlin.math.abs(a.red - b.red) <= tolerance &&
            kotlin.math.abs(a.green - b.green) <= tolerance &&
            kotlin.math.abs(a.blue - b.blue) <= tolerance
}
