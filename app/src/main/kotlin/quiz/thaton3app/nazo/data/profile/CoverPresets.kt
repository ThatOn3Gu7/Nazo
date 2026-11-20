package quiz.thaton3app.nazo.data.profile

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import quiz.thaton3app.nazo.R

/**
 * Built-in cover (banner) collections: premade static photos bundled in the
 * APK and premade animated-gradient designs — plus the user's own photo or
 * video, which stays a first-class option (see [resolveCoverVisual]).
 *
 * Everything here is pure registry data with stable string ids: the id is
 * what [quiz.thaton3app.nazo.data.settings.ProfilePreferences] persists, so
 * presets survive restarts and remain resolvable after an app update (an id
 * that no longer exists simply falls back to the gallery/default flow).
 *
 * The sealed [CoverVisual] is the rendering seam: the banner draws exactly
 * what the resolver picks. Custom and avatar-derived video kinds use that seam
 * without changing any stable preset id.
 */

/** Which collection a [CoverPreset] belongs to. */
enum class CoverPresetKind {
    /** A bundled static image drawn with the same crop rules as gallery picks. */
    IMAGE,

    /** A premade animated gradient drawn procedurally (no bitmaps, no video). */
    GRADIENT,

    /** A bundled, muted looping MP4 rendered by the shared cover video host. */
    VIDEO,
}

/** Which visual group owns a preset in the cover-selection sheet. */
enum class CoverPresetCollection {
    PHOTO,
    STATIC_GRADIENT,
    ANIMATED_GRADIENT,
}

/**
 * Rendering personality for procedural gradient covers. RIBBONS remains the
 * baseline style; OCEAN_SWELL powers the currently available animated preset.
 */
enum class CoverGradientStyle {
    RIBBONS,
    OCEAN_SWELL,
}

/**
 * One selectable built-in cover.
 *
 * @param id stable persisted key (e.g. `"photo_aurora"`).
 * @param label user-facing name shown in the selection sheet.
 * @param collection visual group in the cover-selection sheet.
 * @param imageRes drawable resource for [CoverPresetKind.IMAGE]; 0 otherwise.
 * @param videoRes raw MP4 resource for [CoverPresetKind.VIDEO]; 0 otherwise.
 * @param thumbnailRes optional lightweight selector preview; 0 uses [imageRes].
 * @param gradientColors opaque `0xRRGGBB` stops for [CoverPresetKind.GRADIENT]
 *   (2+ colors, dark → light for the drifting glow to pick from); 0 otherwise.
 * @param gradientSpeed relative animation pace; 1f is the base slow drift.
 * @param gradientSeed per-preset phase offset so two gradients never move
 *   in lockstep when previewed side by side.
 */
data class CoverPreset(
    val id: String,
    val kind: CoverPresetKind,
    val label: String,
    val collection: CoverPresetCollection = when (kind) {
        CoverPresetKind.IMAGE -> CoverPresetCollection.PHOTO
        CoverPresetKind.GRADIENT,
        CoverPresetKind.VIDEO -> CoverPresetCollection.ANIMATED_GRADIENT
    },
    @DrawableRes val imageRes: Int = 0,
    @RawRes val videoRes: Int = 0,
    @DrawableRes val thumbnailRes: Int = 0,
    val gradientColors: List<Long> = emptyList(),
    val gradientSpeed: Float = 1f,
    val gradientSeed: Int = 0,
    val gradientStyle: CoverGradientStyle = CoverGradientStyle.RIBBONS,
)

/** Android resource URI consumed by MediaPlayer/MediaMetadataRetriever. */
internal fun CoverPreset.videoUri(packageName: String): String? =
    videoRes.takeIf { kind == CoverPresetKind.VIDEO && it != 0 }
        ?.let { resourceId -> "android.resource://$packageName/$resourceId" }

/** The bundled collections, in sheet display order. */
object CoverPresets {

    val ALL: List<CoverPreset> = listOf(
        // --- Static photos (bundled in res/drawable-nodpi, offline) ---
        // Originals are 1280x640 JPEG (first six) and 1440x720 WebP (the
        // eight added later) - all at or above the gallery floor
        // (1024x480), so a preset never upscales more than the user's own
        // photos would. See PRESET_IMAGES.md for provenance.
        CoverPreset(
            id = "photo_aurora",
            kind = CoverPresetKind.IMAGE,
            label = "Aurora ridge",
            imageRes = R.drawable.cover_preset_aurora,
        ),
        CoverPreset(
            id = "photo_dusk",
            kind = CoverPresetKind.IMAGE,
            label = "Dusk peaks",
            imageRes = R.drawable.cover_preset_dusk,
        ),
        CoverPreset(
            id = "photo_ocean",
            kind = CoverPresetKind.IMAGE,
            label = "Ocean haze",
            imageRes = R.drawable.cover_preset_ocean,
        ),
        CoverPreset(
            id = "photo_petal",
            kind = CoverPresetKind.IMAGE,
            label = "Petal bokeh",
            imageRes = R.drawable.cover_preset_petal,
        ),
        CoverPreset(
            id = "photo_geometry",
            kind = CoverPresetKind.IMAGE,
            label = "Geometry",
            imageRes = R.drawable.cover_preset_geometry,
        ),
        CoverPreset(
            id = "photo_dunes",
            kind = CoverPresetKind.IMAGE,
            label = "Warm dunes",
            imageRes = R.drawable.cover_preset_dunes,
        ),
        CoverPreset(
            id = "photo_forest",
            kind = CoverPresetKind.IMAGE,
            label = "Misty forest",
            imageRes = R.drawable.cover_preset_forest,
        ),
        CoverPreset(
            id = "photo_glacier",
            kind = CoverPresetKind.IMAGE,
            label = "Glacier lake",
            imageRes = R.drawable.cover_preset_glacier,
        ),
        CoverPreset(
            id = "photo_reef",
            kind = CoverPresetKind.IMAGE,
            label = "Coral reef",
            imageRes = R.drawable.cover_preset_reef,
        ),
        CoverPreset(
            id = "photo_cosmic",
            kind = CoverPresetKind.IMAGE,
            label = "Cosmic night",
            imageRes = R.drawable.cover_preset_cosmic,
        ),
        CoverPreset(
            id = "photo_lavender",
            kind = CoverPresetKind.IMAGE,
            label = "Lavender skies",
            imageRes = R.drawable.cover_preset_lavender,
        ),
        CoverPreset(
            id = "photo_foliage",
            kind = CoverPresetKind.IMAGE,
            label = "Emerald foliage",
            imageRes = R.drawable.cover_preset_foliage,
        ),
        CoverPreset(
            id = "photo_ice",
            kind = CoverPresetKind.IMAGE,
            label = "Frozen world",
            imageRes = R.drawable.cover_preset_ice,
        ),
        CoverPreset(
            id = "photo_coast",
            kind = CoverPresetKind.IMAGE,
            label = "Coastal escape",
            imageRes = R.drawable.cover_preset_coast,
        ),

        // --- Static gradient artwork from Nazo-Assets ---------------------
        CoverPreset(
            id = "gradient_static_emerald_tide",
            kind = CoverPresetKind.IMAGE,
            label = "Emerald Tide",
            collection = CoverPresetCollection.STATIC_GRADIENT,
            imageRes = R.drawable.cover_gradient_emerald_tide,
            thumbnailRes = R.drawable.cover_gradient_emerald_tide_thumb,
        ),
        CoverPreset(
            id = "gradient_static_frosted_blush",
            kind = CoverPresetKind.IMAGE,
            label = "Frosted Blush",
            collection = CoverPresetCollection.STATIC_GRADIENT,
            imageRes = R.drawable.cover_gradient_frosted_blush,
            thumbnailRes = R.drawable.cover_gradient_frosted_blush_thumb,
        ),
        CoverPreset(
            id = "gradient_static_golden_hour",
            kind = CoverPresetKind.IMAGE,
            label = "Golden Hour",
            collection = CoverPresetCollection.STATIC_GRADIENT,
            imageRes = R.drawable.cover_gradient_golden_hour,
            thumbnailRes = R.drawable.cover_gradient_golden_hour_thumb,
        ),

        // --- Animated gradients (procedural, lifecycle-aware rendering) ---
        // The sole current animation: deep-blue, flowing snake-like waves.
        CoverPreset(
            id = "gradient_ocean_current",
            kind = CoverPresetKind.GRADIENT,
            label = "Ocean current",
            gradientColors = listOf(0x04182D, 0x0B527F, 0x20AFC2, 0xB1F6EF),
            gradientSpeed = 0.68f,
            gradientSeed = 5,
            gradientStyle = CoverGradientStyle.OCEAN_SWELL,
        ),

        // Nazo-Assets MP4 loops. Static thumbnails keep the selector cheap;
        // only the selected tile and the real banner request a live decoder.
        CoverPreset(
            id = "gradient_video_blue_sky",
            kind = CoverPresetKind.VIDEO,
            label = "Blue Sky",
            videoRes = R.raw.cover_gradient_blue_sky,
            thumbnailRes = R.drawable.cover_gradient_blue_sky_thumb,
        ),
        CoverPreset(
            id = "gradient_video_emerald_night",
            kind = CoverPresetKind.VIDEO,
            label = "Emerald Night",
            videoRes = R.raw.cover_gradient_emerald_night,
            thumbnailRes = R.drawable.cover_gradient_emerald_night_thumb,
        ),
        CoverPreset(
            id = "gradient_video_glassy_wisteria",
            kind = CoverPresetKind.VIDEO,
            label = "Glassy Wisteria",
            videoRes = R.raw.cover_gradient_glassy_wisteria,
            thumbnailRes = R.drawable.cover_gradient_glassy_wisteria_thumb,
        ),
        CoverPreset(
            id = "gradient_video_flower_god",
            kind = CoverPresetKind.VIDEO,
            label = "Flower God",
            videoRes = R.raw.cover_gradient_flower_god,
            thumbnailRes = R.drawable.cover_gradient_flower_god_thumb,
        ),
        CoverPreset(
            id = "gradient_video_itachi",
            kind = CoverPresetKind.VIDEO,
            label = "Itachi",
            videoRes = R.raw.cover_gradient_itachi,
            thumbnailRes = R.drawable.cover_gradient_itachi_thumb,
        ),
        CoverPreset(
            id = "gradient_video_leaf_ninja",
            kind = CoverPresetKind.VIDEO,
            label = "Leaf Ninja",
            videoRes = R.raw.cover_gradient_leaf_ninja,
            thumbnailRes = R.drawable.cover_gradient_leaf_ninja_thumb,
        ),
        CoverPreset(
            id = "gradient_video_otaku",
            kind = CoverPresetKind.VIDEO,
            label = "Otaku",
            videoRes = R.raw.cover_gradient_otaku,
            thumbnailRes = R.drawable.cover_gradient_otaku_thumb,
        ),
        CoverPreset(
            id = "gradient_video_soul_king",
            kind = CoverPresetKind.VIDEO,
            label = "Soul King",
            videoRes = R.raw.cover_gradient_soul_king,
            thumbnailRes = R.drawable.cover_gradient_soul_king_thumb,
        ),
        CoverPreset(
            id = "gradient_video_untitled_blend",
            kind = CoverPresetKind.VIDEO,
            label = "Untitled Blend",
            videoRes = R.raw.cover_gradient_untitled_blend,
            thumbnailRes = R.drawable.cover_gradient_untitled_blend_thumb,
        ),
        CoverPreset(
            id = "gradient_video_wang",
            kind = CoverPresetKind.VIDEO,
            label = "King (王)",
            videoRes = R.raw.cover_gradient_wang,
            thumbnailRes = R.drawable.cover_gradient_wang_thumb,
        ),
    )

    /** Bundled static-photo presets, in sheet order. */
    val IMAGES: List<CoverPreset> = ALL.filter {
        it.collection == CoverPresetCollection.PHOTO
    }

    /** Bundled static-gradient JPEGs, in sheet order. */
    val STATIC_GRADIENTS: List<CoverPreset> = ALL.filter {
        it.collection == CoverPresetCollection.STATIC_GRADIENT
    }

    /** Procedural and MP4 animated gradients, in sheet order. */
    val GRADIENTS: List<CoverPreset> = ALL.filter {
        it.collection == CoverPresetCollection.ANIMATED_GRADIENT
    }

    /**
     * Lookup by persisted id. Unknown / null ids return null, which the
     * resolver treats as "no preset" — a downgrade or a removed preset can
     * never crash or brick the cover.
     */
    fun byId(id: String?): CoverPreset? =
        id?.let { wanted -> ALL.firstOrNull { it.id == wanted } }
}

/**
 * What the cover banner should draw right now. The only source of truth for
 * banner rendering — [resolveCoverVisual] maps the persisted automatic flag,
 * generated artifact, gallery URI and preset id onto it.
 */
sealed interface CoverVisual {
    /** Nothing selected: the banner's placeholder gradient shows. */
    data object Default : CoverVisual

    /** The user's own gallery photo (existing Phase-2 flow, `file://`). */
    data class Gallery(val uri: String) : CoverVisual

    /** The user's validated, app-owned MP4 cover (`file://`). */
    data class Video(val uri: String) : CoverVisual

    /** One-time generated static background following the current avatar. */
    data class AutomaticImage(val uri: String) : CoverVisual

    /** Low-resolution blurred MP4 following and initially synced to the avatar. */
    data class AutomaticVideo(
        val uri: String,
        val avatarUri: String,
    ) : CoverVisual

    /** A bundled preset (static image, procedural gradient, or MP4 loop). */
    data class Preset(val preset: CoverPreset) : CoverVisual
}

/**
 * Pure precedence rule: **ready automatic > preset > custom media > default**.
 *
 * Automatic mode is a separate selection flag. Its generated artifact wins
 * only when preparation for the current avatar succeeded; while preparing or
 * unavailable, the preserved manual selection remains visible instead of a
 * broken/empty cover. Choosing manual content clears the flag in the caller,
 * never by mutating the stored manual values here.
 */
internal fun resolveCoverVisual(
    galleryUri: String?,
    presetId: String?,
    automaticEnabled: Boolean = false,
    automaticArtifact: AutomaticCoverArtifact? = null,
): CoverVisual {
    if (automaticEnabled && automaticArtifact != null) {
        return when (automaticArtifact.kind) {
            AutomaticCoverKind.IMAGE -> CoverVisual.AutomaticImage(automaticArtifact.uri)
            AutomaticCoverKind.VIDEO -> CoverVisual.AutomaticVideo(
                uri = automaticArtifact.uri,
                avatarUri = automaticArtifact.sourceUri,
            )
        }
    }
    val preset = CoverPresets.byId(presetId)
    return when {
        preset != null -> CoverVisual.Preset(preset)
        galleryUri.isNullOrBlank() -> CoverVisual.Default
        CoverVideoMedia.isVideoUri(galleryUri) -> CoverVisual.Video(galleryUri)
        else -> CoverVisual.Gallery(galleryUri)
    }
}
