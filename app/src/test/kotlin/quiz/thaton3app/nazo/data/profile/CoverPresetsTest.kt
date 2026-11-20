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
            "gradient_static_emerald_tide", "gradient_static_frosted_blush",
            "gradient_static_golden_hour", "gradient_ocean_current",
            "gradient_video_blue_sky", "gradient_video_emerald_night",
            "gradient_video_glassy_wisteria", "gradient_video_flower_god",
            "gradient_video_itachi", "gradient_video_leaf_ninja",
            "gradient_video_otaku", "gradient_video_soul_king",
            "gradient_video_untitled_blend", "gradient_video_wang",
        )))
    }

    @Test
    fun collectionsAreSplitByPresentationGroupAndMediaKind() {
        assertEquals(14, CoverPresets.IMAGES.size)
        assertEquals(3, CoverPresets.STATIC_GRADIENTS.size)
        assertEquals(11, CoverPresets.GRADIENTS.size)
        assertEquals(
            CoverPresets.ALL.size,
            CoverPresets.IMAGES.size +
                CoverPresets.STATIC_GRADIENTS.size + CoverPresets.GRADIENTS.size,
        )
        CoverPresets.IMAGES.forEach { preset ->
            assertEquals(CoverPresetCollection.PHOTO, preset.collection)
            assertEquals(CoverPresetKind.IMAGE, preset.kind)
            assertNotEquals(0, preset.imageRes)
        }
        CoverPresets.STATIC_GRADIENTS.forEach { preset ->
            assertEquals(CoverPresetCollection.STATIC_GRADIENT, preset.collection)
            assertEquals(CoverPresetKind.IMAGE, preset.kind)
            assertNotEquals(0, preset.imageRes)
            assertNotEquals(0, preset.thumbnailRes)
            assertEquals(0, preset.videoRes)
        }

        val procedural = CoverPresets.GRADIENTS.filter { it.kind == CoverPresetKind.GRADIENT }
        val videos = CoverPresets.GRADIENTS.filter { it.kind == CoverPresetKind.VIDEO }
        assertEquals(1, procedural.size)
        assertEquals(10, videos.size)
        videos.forEach { preset ->
            assertEquals(CoverPresetCollection.ANIMATED_GRADIENT, preset.collection)
            assertNotEquals(0, preset.videoRes)
            assertNotEquals(0, preset.thumbnailRes)
            assertEquals(0, preset.imageRes)
            assertTrue(preset.gradientColors.isEmpty())
            assertTrue(preset.label.isNotBlank())
        }

        procedural.forEach { preset ->
            assertEquals(0, preset.imageRes)
            assertEquals(0, preset.videoRes)
            // A procedural gradient needs at least three stops to sweep through…
            assertTrue("gradient ${preset.id} needs ≥3 colors", preset.gradientColors.size >= 3)
            // …and every stop is an opaque 0xRRGGBB value.
            preset.gradientColors.forEach { argb ->
                assertTrue("color out of RGB range in ${preset.id}", argb in 0x0L..0xFFFFFFL)
            }
            val luminance = { argb: Long ->
                0.299 * ((argb shr 16) and 0xFF) +
                    0.587 * ((argb shr 8) and 0xFF) +
                    0.114 * (argb and 0xFF)
            }
            assertTrue(
                "gradient ${preset.id} should start darker than it ends",
                luminance(preset.gradientColors.first()) <
                    luminance(preset.gradientColors.last()),
            )
            assertTrue("speed must be positive for ${preset.id}", preset.gradientSpeed > 0f)
        }
    }

    @Test
    fun gradientPalettesMatchTheirDesignedPersonalities() {
        val expected = mapOf(
            "gradient_ocean_current" to listOf(0x04182DL, 0x0B527FL, 0x20AFC2L, 0xB1F6EFL),
        )
        val procedural = CoverPresets.GRADIENTS.filter {
            it.kind == CoverPresetKind.GRADIENT
        }
        assertEquals(expected.keys, procedural.map { it.id }.toSet())
        procedural.forEach { preset ->
            assertEquals("palette for ${preset.id}", expected[preset.id], preset.gradientColors)
        }
    }

    @Test
    fun oceanCurrentUsesTheOceanRenderer() {
        val preset = CoverPresets.GRADIENTS.single {
            it.kind == CoverPresetKind.GRADIENT
        }
        assertEquals("gradient_ocean_current", preset.id)
        assertEquals(CoverGradientStyle.OCEAN_SWELL, preset.gradientStyle)
    }

    @Test
    fun allThirteenNazoAssetsGradientsAreAccountedForWithoutDuplicates() {
        val imported = CoverPresets.STATIC_GRADIENTS + CoverPresets.GRADIENTS.filter {
            it.kind == CoverPresetKind.VIDEO
        }
        val expectedIds = setOf(
            "gradient_static_emerald_tide",
            "gradient_static_frosted_blush",
            "gradient_static_golden_hour",
            "gradient_video_blue_sky",
            "gradient_video_emerald_night",
            "gradient_video_glassy_wisteria",
            "gradient_video_flower_god",
            "gradient_video_itachi",
            "gradient_video_leaf_ninja",
            "gradient_video_otaku",
            "gradient_video_soul_king",
            "gradient_video_untitled_blend",
            "gradient_video_wang",
        )
        assertEquals(expectedIds, imported.map { it.id }.toSet())
        assertEquals(13, imported.size)
        assertEquals(imported.size, imported.map { it.label }.toSet().size)

        CoverPresets.STATIC_GRADIENTS.forEach { preset ->
            assertNull(preset.videoUri("quiz.thaton3app.nazo"))
        }

        val videos = imported.filter { it.kind == CoverPresetKind.VIDEO }
        assertEquals(videos.size, videos.map { it.videoRes }.toSet().size)
        assertEquals(videos.size, videos.map { it.thumbnailRes }.toSet().size)
        videos.forEach { preset ->
            assertEquals(
                "android.resource://quiz.thaton3app.nazo/${preset.videoRes}",
                preset.videoUri("quiz.thaton3app.nazo"),
            )
        }
    }

    @Test
    fun byIdLooksUpAndFailsSafe() {
        val preset = CoverPresets.byId("gradient_ocean_current")
        assertEquals(CoverPresetKind.GRADIENT, preset?.kind)
        assertEquals("Ocean current", preset?.label)
        // Unknown, retired, and null ids fall back to "no preset" instead of
        // throwing — stale preferences can never brick the cover.
        assertNull(CoverPresets.byId("gradient_grove"))
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
    fun bundledVideoGradientResolvesWithoutConsumingCustomMedia() {
        val visual = resolveCoverVisual(
            galleryUri = "file:///covers/mine.jpg",
            presetId = "gradient_video_blue_sky",
        )
        assertTrue(visual is CoverVisual.Preset)
        visual as CoverVisual.Preset
        assertEquals(CoverPresetKind.VIDEO, visual.preset.kind)
        assertTrue(visual.preset.videoUri("quiz.thaton3app.nazo")!!.startsWith("android.resource://"))
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
        val visual = resolveCoverVisual(galleryUri = uri, presetId = "gradient_ocean_current")
        assertTrue(visual is CoverVisual.Preset)
        assertEquals("gradient_ocean_current", (visual as CoverVisual.Preset).preset.id)
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
            presetId = "gradient_ocean_current",
            automaticEnabled = true,
            automaticArtifact = null,
        )
        assertTrue(visual is CoverVisual.Preset)
        assertEquals("gradient_ocean_current", (visual as CoverVisual.Preset).preset.id)
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
