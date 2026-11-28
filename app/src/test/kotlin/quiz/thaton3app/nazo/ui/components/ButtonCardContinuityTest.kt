package quiz.thaton3app.nazo.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonCardContinuityTest {
    @Test
    fun continuityRequiresUsefulBoundsAndRespectsReducedMotion() {
        val source = Rect(left = 20f, top = 80f, right = 340f, bottom = 152f)

        assertTrue(buttonCardContinuityCanStart(source, reduceMotion = false))
        assertFalse(buttonCardContinuityCanStart(source, reduceMotion = true))
        assertFalse(buttonCardContinuityCanStart(Rect.Zero, reduceMotion = false))
        assertFalse(
            buttonCardContinuityCanStart(
                Rect(left = Float.NaN, top = 0f, right = 100f, bottom = 60f),
                reduceMotion = false,
            )
        )
    }

    @Test
    fun travellingCardInterpolatesAndClampsEveryEdge() {
        val source = Rect(left = 20f, top = 100f, right = 220f, bottom = 180f)
        val target = Rect(left = 80f, top = 30f, right = 380f, bottom = 86f)

        assertEquals(source, buttonCardContinuityRect(source, target, progress = -1f))
        assertEquals(target, buttonCardContinuityRect(source, target, progress = 2f))
        assertEquals(
            Rect(left = 50f, top = 65f, right = 300f, bottom = 133f),
            buttonCardContinuityRect(source, target, progress = 0.5f),
        )
    }
}
