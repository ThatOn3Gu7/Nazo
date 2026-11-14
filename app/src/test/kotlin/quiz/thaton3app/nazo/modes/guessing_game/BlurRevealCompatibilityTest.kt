package quiz.thaton3app.nazo.modes.guessing_game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurRevealCompatibilityTest {

    @Test
    fun blurUsesCompatibilityRendererBeforeAndroid12() {
        for (sdk in 26..30) {
            assertEquals(
                BlurRevealCompatibility.Renderer.COMPATIBILITY_BLUR,
                BlurRevealCompatibility.renderer("blur", sdk),
            )
        }
    }

    @Test
    fun blurUsesNativeRendererFromAndroid12() {
        assertEquals(
            BlurRevealCompatibility.Renderer.NATIVE_BLUR,
            BlurRevealCompatibility.renderer("blur", 31),
        )
        assertEquals(
            BlurRevealCompatibility.Renderer.NATIVE_BLUR,
            BlurRevealCompatibility.renderer("blur", 36),
        )
    }

    @Test
    fun explicitPixelStyleIsUnchangedAcrossApiLevels() {
        assertEquals(
            BlurRevealCompatibility.Renderer.PIXEL_STYLE,
            BlurRevealCompatibility.renderer("pixel", 26),
        )
        assertEquals(
            BlurRevealCompatibility.Renderer.PIXEL_STYLE,
            BlurRevealCompatibility.renderer("pixel", 36),
        )
    }

    @Test
    fun compatibilityObscurationStartsStrongAndRevealsMonotonically() {
        val cells = (28 downTo 0).map {
            BlurRevealCompatibility.compatibilityCellSize(it, maximumBlurRadius = 28)
        }

        assertTrue("start must average broad source regions", cells.first() >= 40)
        assertEquals(1, cells.last())
        assertTrue(cells.zipWithNext().all { (current, next) -> next <= current })
    }
}
