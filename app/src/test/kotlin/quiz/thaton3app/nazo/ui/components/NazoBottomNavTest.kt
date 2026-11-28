package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NazoBottomNavTest {

    @Test
    fun threeButtonNavigationKeepsTheWholeAppBarOutsideSystemButtons() {
        val layers = nazoNavigationInsetLayers(
            navigationBarPx = 144,
            tappableElementPx = 144,
        )

        assertEquals(144, layers.outsideSurfacePx)
        assertEquals(0, layers.insideSurfacePx)
    }

    @Test
    fun gestureNavigationKeepsOnlyControlPaddingInsideTheSurface() {
        val layers = nazoNavigationInsetLayers(
            navigationBarPx = 72,
            tappableElementPx = 0,
        )

        assertEquals(0, layers.outsideSurfacePx)
        assertEquals(72, layers.insideSurfacePx)
    }

    @Test
    fun mixedAndInvalidOemInsetsRemainBoundedToTheNavigationBar() {
        assertEquals(
            NazoNavigationInsetLayers(outsideSurfacePx = 48, insideSurfacePx = 24),
            nazoNavigationInsetLayers(navigationBarPx = 72, tappableElementPx = 48),
        )
        assertEquals(
            NazoNavigationInsetLayers(outsideSurfacePx = 72, insideSurfacePx = 0),
            nazoNavigationInsetLayers(navigationBarPx = 72, tappableElementPx = 200),
        )
        assertEquals(
            NazoNavigationInsetLayers(outsideSurfacePx = 0, insideSurfacePx = 0),
            nazoNavigationInsetLayers(navigationBarPx = -1, tappableElementPx = -1),
        )
    }
}
