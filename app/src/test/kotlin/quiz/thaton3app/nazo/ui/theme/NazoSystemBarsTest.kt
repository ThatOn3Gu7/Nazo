package quiz.thaton3app.nazo.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NazoSystemBarsTest {

    @Test
    fun threeButtonNavigationUsesAnOpaqueSystemSurface() {
        assertTrue(
            hasTappableNavigationBar(
                navigationBars = edges(bottom = 144),
                tappableElement = edges(bottom = 144),
            )
        )
    }

    @Test
    fun landscapeButtonNavigationIsDetectedOnEitherSide() {
        assertTrue(
            hasTappableNavigationBar(
                navigationBars = edges(right = 120),
                tappableElement = edges(right = 120),
            )
        )
        assertTrue(
            hasTappableNavigationBar(
                navigationBars = edges(left = 120),
                tappableElement = edges(left = 120),
            )
        )
    }

    @Test
    fun gestureNavigationKeepsItsTransparentSystemSurface() {
        assertFalse(
            hasTappableNavigationBar(
                navigationBars = edges(bottom = 72),
                tappableElement = edges(),
            )
        )
    }

    @Test
    fun unrelatedAndInvalidTappableInsetsDoNotChangeTheNavigationSurface() {
        assertFalse(
            hasTappableNavigationBar(
                navigationBars = edges(bottom = 72),
                tappableElement = edges(top = 48),
            )
        )
        assertFalse(
            hasTappableNavigationBar(
                navigationBars = edges(bottom = -1),
                tappableElement = edges(bottom = -1),
            )
        )
    }

    private fun edges(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) = NazoInsetEdges(
        leftPx = left,
        topPx = top,
        rightPx = right,
        bottomPx = bottom,
    )
}
