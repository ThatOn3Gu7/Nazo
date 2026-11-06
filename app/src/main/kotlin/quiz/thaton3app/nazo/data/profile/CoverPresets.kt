package quiz.thaton3app.nazo.data.profile

import androidx.annotation.DrawableRes
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
 * what the resolver picks. Custom video uses that seam without changing any
 * preset ids; a later avatar-derived phase can add another resolver branch.
 */

/** Which collection a [CoverPreset] belongs to. */
enum class CoverPresetKind {
    /** A bundled static image drawn with the same crop rules as gallery picks. */
    IMAGE,

    /** A premade animated gradient drawn procedurally (no bitmaps, no video). */
    GRADIENT,

    // A future bundled-video collection would add VIDEO here; custom videos
    // are CoverVisual.Video and therefore do not alter the preset registry.
}

/**
 * Rendering personality for procedural gradient covers. RIBBONS is the
 * original renderer and remains the default for the four shipped presets.
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
 * @param imageRes drawable resource for [CoverPresetKind.IMAGE]; 0 otherwise.
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
    @DrawableRes val imageRes: Int = 0,
    val gradientColors: List<Long> = emptyList(),
    val gradientSpeed: Float = 1f,
    val gradientSeed: Int = 0,
    val gradientStyle: CoverGradientStyle = CoverGradientStyle.RIBBONS,
)

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

        // --- Animated gradients (procedural, lifecycle-aware rendering) ---
        // Four stops each, dark → light: the renderer interpolates
        // continuously around this ring (never indexes it discretely).
        CoverPreset(
            id = "gradient_aurora",
            kind = CoverPresetKind.GRADIENT,
            label = "Aurora",
            gradientColors = listOf(0x0A1A2F, 0x1FA97A, 0x2ED8C0, 0x8FF3E4),
            gradientSpeed = 0.9f,
            gradientSeed = 0,
        ),
        CoverPreset(
            id = "gradient_sunset",
            kind = CoverPresetKind.GRADIENT,
            label = "Sunset",
            gradientColors = listOf(0x2A0F3F, 0xC1386B, 0xF07B54, 0xF7C56A),
            gradientSpeed = 1.0f,
            gradientSeed = 1,
        ),
        CoverPreset(
            id = "gradient_tides",
            kind = CoverPresetKind.GRADIENT,
            label = "Tides",
            gradientColors = listOf(0x07203B, 0x1573A8, 0x3FC3D8, 0x9BEAF4),
            gradientSpeed = 0.8f,
            gradientSeed = 2,
        ),
        CoverPreset(
            id = "gradient_grove",
            kind = CoverPresetKind.GRADIENT,
            label = "Grove",
            gradientColors = listOf(0x0A1F14, 0x147A4E, 0x2FBF87, 0xA8F0CF),
            gradientSpeed = 1.1f,
            gradientSeed = 3,
        ),

        // The sole additional animation: deep-blue, flowing snake-like waves.
        CoverPreset(
            id = "gradient_ocean_current",
            kind = CoverPresetKind.GRADIENT,
            label = "Ocean current",
            gradientColors = listOf(0x04182D, 0x0B527F, 0x20AFC2, 0xB1F6EF),
            gradientSpeed = 0.68f,
            gradientSeed = 5,
            gradientStyle = CoverGradientStyle.OCEAN_SWELL,
        ),
    )

    /** Static photo presets, in sheet order. */
    val IMAGES: List<CoverPreset> = ALL.filter { it.kind == CoverPresetKind.IMAGE }

    /** Animated gradient presets, in sheet order. */
    val GRADIENTS: List<CoverPreset> = ALL.filter { it.kind == CoverPresetKind.GRADIENT }

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
 * banner rendering — [resolveCoverVisual] maps the two persisted values
 * (gallery uri + preset id) onto it.
 */
sealed interface CoverVisual {
    /** Nothing selected: the banner's placeholder gradient shows. */
    data object Default : CoverVisual

    /** The user's own gallery photo (existing Phase-2 flow, `file://`). */
    data class Gallery(val uri: String) : CoverVisual

    /** The user's validated, app-owned MP4 cover (`file://`). */
    data class Video(val uri: String) : CoverVisual

    /** A bundled preset (static image or animated gradient). */
    data class Preset(val preset: CoverPreset) : CoverVisual
}

/**
 * Pure precedence rule: **preset > custom media > default**.
 *
 * - Choosing a preset only writes the preset id — the custom-media uri is
 *   never touched, so "switch back to mine" is just clearing the preset id
 *   ([CoverVisual.Gallery] or [CoverVisual.Video] returns without re-picking).
 * - With no preset, a custom photo renders exactly as before and an app-owned
 *   `.mp4` dispatches to dedicated cover playback.
 * - An unknown preset id (removed in an update, corrupt prefs) falls back
 *   down the chain instead of showing a broken cover.
 */
fun resolveCoverVisual(galleryUri: String?, presetId: String?): CoverVisual {
    val preset = CoverPresets.byId(presetId)
    return when {
        preset != null -> CoverVisual.Preset(preset)
        galleryUri.isNullOrBlank() -> CoverVisual.Default
        CoverVideoMedia.isVideoUri(galleryUri) -> CoverVisual.Video(galleryUri)
        else -> CoverVisual.Gallery(galleryUri)
    }
}
