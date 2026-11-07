package quiz.thaton3app.nazo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconSwitcherTest {

    @Test
    fun defaultPairUsesSumiArtworkAndMatchingGrounds() {
        val light = LauncherIconSwitcher.option(LauncherIconSwitcher.ID_LIGHT)
        val dark = LauncherIconSwitcher.option(LauncherIconSwitcher.ID_DARK)

        listOf(light, dark).forEach { option ->
            assertTrue(option.themed)
            assertEquals(R.drawable.ic_mark_nazo_silhouette, option.introMark)
            assertEquals(R.drawable.ic_launcher_foreground_nazo, option.previewMark)
        }
        assertEquals(0xFF1B4135, light.startColor)
        assertEquals(light.startColor, light.endColor)
        assertEquals(0xFF0F1713, dark.startColor)
        assertEquals(dark.startColor, dark.endColor)
    }

    @Test
    fun colorThemesUseTheirOwnTexturedArtwork() {
        val expectedMarks = mapOf(
            "sakura" to R.drawable.ic_launcher_foreground_sakura,
            "midnight" to R.drawable.ic_launcher_foreground_midnight,
            "ocean" to R.drawable.ic_launcher_foreground_ocean,
        )

        expectedMarks.forEach { (id, mark) ->
            val option = LauncherIconSwitcher.option(id)
            assertFalse(option.themed)
            assertEquals(R.drawable.ic_mark_nazo_variant_silhouette, option.introMark)
            assertEquals(mark, option.previewMark)
        }
    }

    @Test
    fun lanternStaysUntouchedWhileWeakInitialIconsAreRetired() {
        val lantern = LauncherIconSwitcher.option("lantern")
        assertEquals(R.drawable.ic_mark_lantern_silhouette, lantern.introMark)
        assertEquals(R.drawable.ic_mark_lantern, lantern.previewMark)

        assertFalse(
            LauncherIconSwitcher.OPTIONS.any { it.id == "nbrush" || it.id == "npixel" },
        )
        assertEquals(
            setOf("LauncherBrush", "LauncherPixel"),
            LauncherIconSwitcher.RETIRED_ALIASES,
        )
        listOf("nbrush", "npixel").forEach { retiredId ->
            assertEquals(LauncherIconSwitcher.ID_LIGHT, LauncherIconSwitcher.option(retiredId).id)
        }
    }

    @Test
    fun unknownSelectionStillFallsBackToDefaultIdentity() {
        assertEquals(
            LauncherIconSwitcher.ID_LIGHT,
            LauncherIconSwitcher.option("missing").id,
        )
    }
}
