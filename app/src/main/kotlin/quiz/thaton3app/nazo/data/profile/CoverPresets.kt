package quiz.thaton3app.nazo.data.profile

import androidx.annotation.DrawableRes
import quiz.thaton3app.nazo.R

/**
 * Built-in cover (banner) collections: premade static photos bundled in the
 * APK and premade animated-gradient designs — plus the user's own gallery
 * image, which stays a first-class option (see [resolveCoverVisual]).
 *
 * Everything here is pure registry data with stable string ids: the id is
 * what [quiz.thaton3app.nazo.data.settings.ProfilePreferences] persists, so
 * presets survive restarts and remain resolvable after an app update (an id
 * that no longer exists simply falls back to the gallery/default flow).
 *
 * The sealed [CoverVisual] is the rendering seam: the banner draws exactly
 * what the resolver picks, and later phases (video covers, avatar-derived
 * backgrounds) add a kind + a resolver branch + a banner case without
 * touching the selection flow or the persisted keys.
 */

/** Which collection a [CoverPreset] belongs to. */
enum class CoverPresetKind {
    /** A bundled static image drawn with the same crop rules as gallery picks. */
    IMAGE,

    /** A premade animated gradient drawn procedurally (no bitmaps, no video). */
    GRADIENT,

    // Future phases: VIDEO, AVATAR_DERIVED — new kinds force explicit
    // handling at each dispatch point (resolver, banner, selection sheet).
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
)

/** The bundled collections, in sheet display order. */
object CoverPresets {

    val ALL: List<CoverPreset> = listOf(
        // --- Static photos (bundled in res/drawable-nodpi, offline) ---
        // Each is 1280×640 JPEG — above the gallery floor (1024×480), so a
        // preset never upscales more than the user's own photos would.
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

        // --- Animated gradients (procedural, lifecycle-aware rendering) ---
        CoverPreset(
            id = "gradient_aurora",
            kind = CoverPresetKind.GRADIENT,
            label = "Aurora",
            gradientColors = listOf(0x0B1E3A, 0x1B6B6B, 0x7BD8C8),
            gradientSpeed = 0.9f,
            gradientSeed = 0,
        ),
        CoverPreset(
            id = "gradient_sunset",
            kind = CoverPresetKind.GRADIENT,
            label = "Sunset",
            gradientColors = listOf(0x3A0C3F, 0xC13B5C, 0xF2A65A),
            gradientSpeed = 1.0f,
            gradientSeed = 1,
        ),
        CoverPreset(
            id = "gradient_tides",
            kind = CoverPresetKind.GRADIENT,
            label = "Tides",
            gradientColors = listOf(0x08203E, 0x1C5D99, 0x57C4E8),
            gradientSpeed = 0.8f,
            gradientSeed = 2,
        ),
        CoverPreset(
            id = "gradient_grove",
            kind = CoverPresetKind.GRADIENT,
            label = "Grove",
            gradientColors = listOf(0x0E1F18, 0x246D4C, 0x7BD8A0),
            gradientSpeed = 1.1f,
            gradientSeed = 3,
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

    /** A bundled preset (static image or animated gradient). */
    data class Preset(val preset: CoverPreset) : CoverVisual
}

/**
 * Pure precedence rule: **preset > gallery > default**.
 *
 * - Choosing a preset only writes the preset id — the gallery uri is never
 *   touched, so "switch back to my photo" is just clearing the preset id
 *   ([CoverVisual.Gallery] returns without re-picking anything).
 * - With no preset, the existing custom photo renders exactly as before.
 * - An unknown preset id (removed in an update, corrupt prefs) falls back
 *   down the chain instead of showing a broken cover.
 */
fun resolveCoverVisual(galleryUri: String?, presetId: String?): CoverVisual {
    val preset = CoverPresets.byId(presetId)
    return when {
        preset != null -> CoverVisual.Preset(preset)
        !galleryUri.isNullOrBlank() -> CoverVisual.Gallery(galleryUri)
        else -> CoverVisual.Default
    }
}
