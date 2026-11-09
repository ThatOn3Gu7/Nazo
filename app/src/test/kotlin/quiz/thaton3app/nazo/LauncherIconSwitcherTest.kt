package quiz.thaton3app.nazo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconSwitcherTest {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

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
        // Android unit-test compilation does not expose java.desktop/ImageIO.
        // Decode the tiny subset these source assets deliberately use instead:
        // non-interlaced, 8-bit RGBA PNG with standard scanline filters.
        val png = mainFile(relativePath).readBytes()
        check(png.size >= 8 && png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            "$relativePath is not a PNG"
        }
        var offset = 8
        var width = 0
        var height = 0
        val compressed = ByteArrayOutputStream()
        while (offset + 12 <= png.size) {
            val length = ByteBuffer.wrap(png, offset, 4).int
            val type = String(png, offset + 4, 4, StandardCharsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            check(dataEnd + 4 <= png.size) { "$relativePath has a truncated $type chunk" }
            when (type) {
                "IHDR" -> {
                    width = ByteBuffer.wrap(png, dataStart, 4).int
                    height = ByteBuffer.wrap(png, dataStart + 4, 4).int
                    check((png[dataStart + 8].toInt() and 0xFF) == 8) {
                        "$relativePath must remain 8-bit"
                    }
                    check((png[dataStart + 9].toInt() and 0xFF) == 6) {
                        "$relativePath must remain RGBA"
                    }
                    check((png[dataStart + 12].toInt() and 0xFF) == 0) {
                        "$relativePath must remain non-interlaced"
                    }
                }
                "IDAT" -> compressed.write(png, dataStart, length)
                "IEND" -> break
            }
            offset = dataEnd + 4 // skip CRC
        }
        check(width > 0 && height > 0) { "$relativePath has no valid IHDR" }

        val raw = InflaterInputStream(
            ByteArrayInputStream(compressed.toByteArray())
        ).use { it.readBytes() }
        val bytesPerPixel = 4
        val stride = width * bytesPerPixel
        check(raw.size == height * (stride + 1)) { "$relativePath has unexpected scanline data" }

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var rawOffset = 0
        var previous = ByteArray(stride)
        repeat(height) { y ->
            val filter = raw[rawOffset++].toInt() and 0xFF
            val row = ByteArray(stride)
            for (index in 0 until stride) {
                val source = raw[rawOffset++].toInt() and 0xFF
                val before = if (index >= bytesPerPixel) {
                    row[index - bytesPerPixel].toInt() and 0xFF
                } else {
                    0
                }
                val above = previous[index].toInt() and 0xFF
                val aboveBefore = if (index >= bytesPerPixel) {
                    previous[index - bytesPerPixel].toInt() and 0xFF
                } else {
                    0
                }
                val prediction = when (filter) {
                    0 -> 0
                    1 -> before
                    2 -> above
                    3 -> (before + above) / 2
                    4 -> paeth(before, above, aboveBefore)
                    else -> error("$relativePath uses unknown PNG filter $filter")
                }
                row[index] = ((source + prediction) and 0xFF).toByte()
            }
            for (x in 0 until width) {
                if ((row[x * bytesPerPixel + 3].toInt() and 0xFF) != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            previous = row
        }
        check(right >= left && bottom >= top) { "$relativePath has no visible pixels" }
        return AlphaBounds(left, top, right + 1, bottom + 1)
    }

    private fun paeth(left: Int, above: Int, aboveLeft: Int): Int {
        val estimate = left + above - aboveLeft
        val leftDistance = abs(estimate - left)
        val aboveDistance = abs(estimate - above)
        val aboveLeftDistance = abs(estimate - aboveLeft)
        return when {
            leftDistance <= aboveDistance && leftDistance <= aboveLeftDistance -> left
            aboveDistance <= aboveLeftDistance -> above
            else -> aboveLeft
        }
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
