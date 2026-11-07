package quiz.thaton3app.nazo.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry + resolution contracts for the built-in cover collections.
 * Pure JVM: ids, kinds, palettes and the precedence rule — no Android.
 */
class CoverPresetsTest {

    // --- Registry ---------------------------------------------------------

    @Test
    fun presetIdsAreUniqueAndStable() {
        // Ids are persisted to prefs; a duplicate would make selection
        // ambiguous and a rename would orphan the owner's saved cover.
        val ids = CoverPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Spot-check the shipped ids so a "helpful" rename fails loudly.
        assertTrue(ids.containsAll(listOf(
            "photo_aurora", "photo_dusk", "photo_ocean",
            "photo_petal", "photo_geometry", "photo_dunes",
            "photo_forest", "photo_glacier", "photo_reef", "photo_cosmic",
            "photo_lavender", "photo_foliage", "photo_ice", "photo_coast",
            "gradient_aurora", "gradient_sunset", "gradient_tides",
            "gradient_grove", "gradient_ocean_current",
        )))
    }

    @Test
    fun collectionsAreSplitByKind() {
        assertEquals(14, CoverPresets.IMAGES.size)
        assertEquals(5, CoverPresets.GRADIENTS.size)
        assertEquals(CoverPresets.ALL.size, CoverPresets.IMAGES.size + CoverPresets.GRADIENTS.size)
        CoverPresets.IMAGES.forEach { preset ->
            assertEquals(CoverPresetKind.IMAGE, preset.kind)
            assertNotEquals(0, preset.imageRes)
            assertTrue(preset.gradientColors.isEmpty())
            assertTrue(preset.label.isNotBlank())
        }
        CoverPresets.GRADIENTS.forEach { preset ->
            assertEquals(CoverPresetKind.GRADIENT, preset.kind)
            assertEquals(0, preset.imageRes)
            // A gradient needs at least three stops to sweep through…
            assertTrue("gradient ${preset.id} needs ≥3 colors", preset.gradientColors.size >= 3)
            // …and every stop is an opaque 0xRRGGBB value.
            preset.gradientColors.forEach { argb ->
                assertTrue("color out of RGB range in ${preset.id}", argb in 0x0L..0xFFFFFFL)
            }
            // Dark first: every renderer starts from a deep base and builds
            // toward its luminous highlight.
            val luminance = { argb: Long ->
                0.299 * ((argb shr 16) and 0xFF) +
                    0.587 * ((argb shr 8) and 0xFF) +
                    0.114 * (argb and 0xFF)
            }
            val first = luminance(preset.gradientColors.first())
            val last = luminance(preset.gradientColors.last())
            assertTrue(
                "gradient ${preset.id} should start darker than it ends " +
                    "($first ≥ $last)",
                first < last,
            )
            assertTrue("speed must be positive for ${preset.id}", preset.gradientSpeed > 0f)
        }
    }

    @Test
    fun gradientPalettesMatchTheirDesignedPersonalities() {
        val expected = mapOf(
            "gradient_aurora" to listOf(0x0A1A2FL, 0x1FA97AL, 0x2ED8C0L, 0x8FF3E4L),
            "gradient_sunset" to listOf(0x2A0F3FL, 0xC1386BL, 0xF07B54L, 0xF7C56AL),
            "gradient_tides" to listOf(0x07203BL, 0x1573A8L, 0x3FC3D8L, 0x9BEAF4L),
            "gradient_grove" to listOf(0x0A1F14L, 0x147A4EL, 0x2FBF87L, 0xA8F0CFL),
            "gradient_ocean_current" to listOf(0x04182DL, 0x0B527FL, 0x20AFC2L, 0xB1F6EFL),
        )
        assertEquals(expected.keys, CoverPresets.GRADIENTS.map { it.id }.toSet())
        CoverPresets.GRADIENTS.forEach { preset ->
            assertEquals("palette for ${preset.id}", expected[preset.id], preset.gradientColors)
        }
    }

    @Test
    fun onlyTheSnakeLikeDeepBlueGradientUsesTheOceanRenderer() {
        val styles = CoverPresets.GRADIENTS.associate { it.id to it.gradientStyle }
        assertEquals(CoverGradientStyle.RIBBONS, styles["gradient_aurora"])
        assertEquals(CoverGradientStyle.RIBBONS, styles["gradient_sunset"])
        assertEquals(CoverGradientStyle.RIBBONS, styles["gradient_tides"])
        assertEquals(CoverGradientStyle.RIBBONS, styles["gradient_grove"])
        assertEquals(CoverGradientStyle.OCEAN_SWELL, styles["gradient_ocean_current"])
        assertEquals(2, styles.values.toSet().size)
    }

    @Test
    fun byIdLooksUpAndFailsSafe() {
        val preset = CoverPresets.byId("gradient_grove")
        assertEquals(CoverPresetKind.GRADIENT, preset?.kind)
        assertEquals("Grove", preset?.label)
        // Unknown / null ids fall back to "no preset" instead of throwing —
        // a downgrade or a removed preset can never brick the cover.
        assertNull(CoverPresets.byId(null))
        assertNull(CoverPresets.byId("photo_from_the_future"))
    }

    // --- Resolution precedence --------------------------------------------

    @Test
    fun presetWinsOverGallery() {
        val visual = resolveCoverVisual(galleryUri = "file:///covers/mine.jpg", presetId = "photo_ocean")
        assertTrue(visual is CoverVisual.Preset)
        assertEquals("photo_ocean", (visual as CoverVisual.Preset).preset.id)
    }

    @Test
    fun galleryShowsWhenNoPreset() {
        val visual = resolveCoverVisual(galleryUri = "file:///covers/mine.jpg", presetId = null)
        assertTrue(visual is CoverVisual.Gallery)
        assertEquals("file:///covers/mine.jpg", (visual as CoverVisual.Gallery).uri)
    }

    @Test
    fun appOwnedMp4DispatchesToVideoWhenNoPreset() {
        val uri = "file:///data/user/0/nazo/files/profile_covers/cover_42.mp4"
        val visual = resolveCoverVisual(galleryUri = uri, presetId = null)
        assertTrue(visual is CoverVisual.Video)
        assertEquals(uri, (visual as CoverVisual.Video).uri)
    }

    @Test
    fun presetStillWinsOverStoredVideo() {
        val uri = "file:///data/user/0/nazo/files/profile_covers/cover_42.mp4"
        val visual = resolveCoverVisual(galleryUri = uri, presetId = "gradient_aurora")
        assertTrue(visual is CoverVisual.Preset)
        assertEquals("gradient_aurora", (visual as CoverVisual.Preset).preset.id)
    }

    @Test
    fun readyAutomaticImageWinsWithoutDestroyingManualSelection() {
        val artifact = AutomaticCoverArtifact(
            sourceUri = "file:///profile/avatar.jpg",
            uri = "file:///profile_auto_covers/auto.jpg",
            kind = AutomaticCoverKind.IMAGE,
        )
        val visual = resolveCoverVisual(
            galleryUri = "file:///covers/manual.mp4",
            presetId = "gradient_ocean_current",
            automaticEnabled = true,
            automaticArtifact = artifact,
        )
        assertTrue(visual is CoverVisual.AutomaticImage)
        assertEquals(artifact.uri, (visual as CoverVisual.AutomaticImage).uri)

        // The same untouched inputs resolve straight back to the preset when
        // automatic mode is disabled.
        val manual = resolveCoverVisual(
            galleryUri = "file:///covers/manual.mp4",
            presetId = "gradient_ocean_current",
            automaticEnabled = false,
            automaticArtifact = artifact,
        )
        assertTrue(manual is CoverVisual.Preset)
    }

    @Test
    fun automaticVideoCarriesAvatarSyncSource() {
        val artifact = AutomaticCoverArtifact(
            sourceUri = "file:///profile/avatar.mp4",
            uri = "file:///profile_auto_covers/auto.mp4",
            kind = AutomaticCoverKind.VIDEO,
        )
        val visual = resolveCoverVisual(
            galleryUri = null,
            presetId = null,
            automaticEnabled = true,
            automaticArtifact = artifact,
        )
        assertTrue(visual is CoverVisual.AutomaticVideo)
        visual as CoverVisual.AutomaticVideo
        assertEquals(artifact.uri, visual.uri)
        assertEquals(artifact.sourceUri, visual.avatarUri)
    }

    @Test
    fun unavailableAutomaticFallsBackToPreservedManualCover() {
        val visual = resolveCoverVisual(
            galleryUri = "file:///covers/manual.jpg",
            presetId = "gradient_grove",
            automaticEnabled = true,
            automaticArtifact = null,
        )
        assertTrue(visual is CoverVisual.Preset)
        assertEquals("gradient_grove", (visual as CoverVisual.Preset).preset.id)
    }

    @Test
    fun defaultWhenNothingSelected() {
        assertTrue(resolveCoverVisual(galleryUri = null, presetId = null) is CoverVisual.Default)
        // Blank uri (defensive) behaves like none.
        assertTrue(resolveCoverVisual(galleryUri = "", presetId = null) is CoverVisual.Default)
    }

    @Test
    fun unknownPresetIdFallsBackDownTheChain() {
        // Corrupt/forward prefs: preset lookup misses → gallery still shows.
        val visual = resolveCoverVisual(galleryUri = "file:///covers/mine.jpg", presetId = "retired_preset")
        assertTrue(visual is CoverVisual.Gallery)
        // …or the placeholder when there is no photo either.
        assertTrue(resolveCoverVisual(galleryUri = null, presetId = "retired_preset") is CoverVisual.Default)
    }

    @Test
    fun switchingToAPresetNeverConsumesTheGalleryUri() {
        // The whole "switch without losing the custom image" promise: the
        // resolver is pure, so the SAME uri resolves back to Gallery the
        // moment the preset id clears — no re-pick, no file touched.
        val uri = "file:///covers/keeper.jpg"
        assertTrue(resolveCoverVisual(uri, "photo_dusk") is CoverVisual.Preset)
        val backToCustom = resolveCoverVisual(uri, null)
        assertTrue(backToCustom is CoverVisual.Gallery)
        assertEquals(uri, (backToCustom as CoverVisual.Gallery).uri)
    }
}
