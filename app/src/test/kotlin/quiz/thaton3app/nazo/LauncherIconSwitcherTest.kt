package quiz.thaton3app.nazo

import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconSwitcherTest {

    private data class AlphaBounds(
        val left: Int,
        val top: Int,
        val rightExclusive: Int,
        val bottomExclusive: Int,
    ) {
        val width: Int get() = rightExclusive - left
        val height: Int get() = bottomExclusive - top
        /** Doubled center avoids floating-point comparisons for half pixels. */
        val centerX2: Int get() = left + rightExclusive
        val centerY2: Int get() = top + bottomExclusive
    }

    private fun mainFile(relativePath: String): File = sequenceOf(
        File("src/main/$relativePath"),
        File("app/src/main/$relativePath"),
    ).firstOrNull { it.isFile }
        ?: error("Missing app/src/main/$relativePath")

    private fun alphaBounds(relativePath: String): AlphaBounds {
        val image = requireNotNull(ImageIO.read(mainFile(relativePath)))
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y).ushr(24) and 0xFF
                if (alpha != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        check(right >= left && bottom >= top) { "$relativePath has no visible pixels" }
        return AlphaBounds(left, top, right + 1, bottom + 1)
    }

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
    fun kanjiLauncherVariantsShareTheDefaultOpticalFootprint() {
        val defaultForeground = alphaBounds(
            "res/drawable-nodpi/ic_launcher_foreground_nazo.png"
        )
        listOf("sakura", "midnight", "ocean").forEach { variant ->
            val bounds = alphaBounds(
                "res/drawable-nodpi/ic_launcher_foreground_$variant.png"
            )
            assertTrue(
                "$variant foreground width should match Sumi Ember",
                abs(bounds.width - defaultForeground.width) <= 2,
            )
            assertTrue(
                "$variant foreground height should match Sumi Ember",
                abs(bounds.height - defaultForeground.height) <= 1,
            )
            assertTrue(
                "$variant foreground should keep Sumi Ember's horizontal center",
                abs(bounds.centerX2 - defaultForeground.centerX2) <= 1,
            )
            assertTrue(
                "$variant foreground should keep Sumi Ember's vertical center",
                abs(bounds.centerY2 - defaultForeground.centerY2) <= 1,
            )
        }

        // Android themed icons and the intro punch-out use the flat silhouette;
        // keep that path optically identical too, not only the color artwork.
        val defaultSilhouette = alphaBounds(
            "res/drawable-nodpi/ic_mark_nazo_silhouette.png"
        )
        val variantSilhouette = alphaBounds(
            "res/drawable-nodpi/ic_mark_nazo_variant_silhouette.png"
        )
        assertTrue(abs(variantSilhouette.width - defaultSilhouette.width) <= 2)
        assertTrue(abs(variantSilhouette.height - defaultSilhouette.height) <= 1)
        assertTrue(abs(variantSilhouette.centerX2 - defaultSilhouette.centerX2) <= 1)
        assertTrue(abs(variantSilhouette.centerY2 - defaultSilhouette.centerY2) <= 1)
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
    fun everyRealLauncherActivityHandlesRotationWithoutRecreatingNazo() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(mainFile("AndroidManifest.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val activities = document.getElementsByTagName("activity")
        val configByName = buildMap {
            for (index in 0 until activities.length) {
                val activity = activities.item(index)
                val attributes = activity.attributes
                val name = attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue
                    ?: continue
                val config = attributes.getNamedItemNS(androidNamespace, "configChanges")?.nodeValue
                put(name.removePrefix("."), config)
            }
        }
        val requiredConfigChanges = setOf(
            "orientation",
            "screenSize",
            "smallestScreenSize",
            "screenLayout",
            "keyboardHidden",
        )
        val realLauncherActivities = buildSet {
            add("MainActivity")
            addAll(LauncherIconSwitcher.OPTIONS.filterNot { it.themed }.map { it.alias })
            addAll(LauncherIconSwitcher.RETIRED_ALIASES)
        }

        realLauncherActivities.forEach { activity ->
            val config = configByName[activity]
            assertTrue(
                "$activity must declare configChanges; manifest attributes are not inherited",
                config != null,
            )
            assertEquals(
                "$activity must use MainActivity's complete rotation contract",
                requiredConfigChanges,
                requireNotNull(config).split('|').toSet(),
            )
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
