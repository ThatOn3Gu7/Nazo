package quiz.thaton3app.nazo.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// Nazo brand palette — sampled directly from pixel data in the approved mockups.
// Do not eyeball-adjust these; if a screen needs a color not listed here,
// sample it from the mockup the same way before adding it.
//
// The palette lives in `NazoColors`. `NazoTheme` updates the active palette (light/dark +
// accent) through `setNazoColors`, and the top-level accessors below expose it. We deliberately
// avoid a `CompositionLocal` here because these are read from plain (non-@Composable) property
// getters, which cannot invoke the composable `CompositionLocal.current`.

data class NazoColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val badge: Color,
    val pillUnselected: Color,
    val primary: Color,
    val navBar: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textPlaceholder: Color,
    val successBg: Color,
    val success: Color,
    val errorBg: Color,
    val error: Color,
    val statsCardBg: Color,
    val darkCard: Color,
    val darkCardAccent: Color,
    val darkCardTrack: Color,
    val onDarkCard: Color,
    val onDarkCardMuted: Color,
)

// Light "mint" theme (original mockup values).
val LightNazoColors = NazoColors(
    background = Color(0xFFF2FCF3),
    surface = Color(0xFFE0F5E5),
    surfaceVariant = Color(0xFFEBF9EE),
    badge = Color(0xFFDDF0E3),
    pillUnselected = Color(0xFFCDE5D4),
    primary = Color(0xFF246D4C),
    navBar = Color(0xFFE7F8EA),
    onPrimary = Color(0xFFF7FEF8),
    textPrimary = Color(0xFF173526),
    textSecondary = Color(0xFF4F6B5B),
    textPlaceholder = Color(0xFF7D9587),
    successBg = Color(0xFFC4E4D0),
    success = Color(0xFF2A8558),
    errorBg = Color(0xFFDCD7CA),
    error = Color(0xFFC9302D),
    statsCardBg = Color(0xFFD1E8D5),
    darkCard = Color(0xFF1A4331),
    darkCardAccent = Color(0xFF3B5E4D),
    darkCardTrack = Color(0xFF456758),
    onDarkCard = Color(0xFFF6FBF4),
    onDarkCardMuted = Color(0xFFA8C2B0),
)

// Real dark "deep forest" theme for the Appearance screen.
val DarkNazoColors = NazoColors(
    background = Color(0xFF0E1F18),
    surface = Color(0xFF163023),
    surfaceVariant = Color(0xFF1E3B2B),
    badge = Color(0xFF1E3B2B),
    pillUnselected = Color(0xFF24412F),
    primary = Color(0xFF36A06F),
    navBar = Color(0xFF0E1F18),
    onPrimary = Color(0xFFF7FEF8),
    textPrimary = Color(0xFFE6F2EA),
    textSecondary = Color(0xFF9DB5A6),
    textPlaceholder = Color(0xFF6E8A7A),
    successBg = Color(0xFF1E3B2B),
    success = Color(0xFF5FCF93),
    errorBg = Color(0xFF3A2326),
    error = Color(0xFFFF8A80),
    statsCardBg = Color(0xFF1A3427),
    darkCard = Color(0xFF10271B),
    darkCardAccent = Color(0xFF24412F),
    darkCardTrack = Color(0xFF2C5040),
    onDarkCard = Color(0xFFE6F2EA),
    onDarkCardMuted = Color(0xFF9DB5A6),
)

// ---- Accent themes -------------------------------------------------------
// Each accent is a *complete* light + dark palette (not just a primary color), so selecting
// it themes the whole app. Non-mint accents are derived by hue-shifting the mint base while
// preserving each role's lightness/saturation — this keeps the internal harmony (and text
// contrast) intact. Semantic colors (error/success) are intentionally left alone.

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val max = if (r > g) if (r > b) r else b else if (g > b) g else b
    val min = if (r < g) if (r < b) r else b else if (g < b) g else b
    val l = (max + min) / 2f
    var h = 0f; var s = 0f
    val d = max - min
    if (d > 0.0001f) {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            b -> (r - g) / d + 4f
            else -> 0f
        }
        h /= 6f
    }
    return Hsl(h * 360f, s, l)
}

private fun Hsl.toColor(): Color {
    val h = h / 360f
    val r: Float; val g: Float; val b: Float
    if (s <= 0.0001f) {
        val v = (l * 255f).toInt().coerceIn(0, 255)
        return Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v)
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun hue2c(t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    r = hue2c(h + 1f / 3f)
    g = hue2c(h)
    b = hue2c(h - 1f / 3f)
    val r8 = (r * 255f).toInt().coerceIn(0, 255)
    val g8 = (g * 255f).toInt().coerceIn(0, 255)
    val b8 = (b * 255f).toInt().coerceIn(0, 255)
    return Color(0xFF000000.toInt() or (r8 shl 16) or (g8 shl 8) or b8)
}

private val REFERENCE_HUE = LightNazoColors.primary.toHsl().h

private fun NazoColors.recolorToHue(targetHue: Float): NazoColors {
    val delta = targetHue - REFERENCE_HUE
    fun Color.shift(): Color {
        val hsl = toHsl()
        val m = (hsl.h + delta) % 360f
        val newHue = if (m < 0f) m + 360f else m
        return Hsl(newHue, hsl.s, hsl.l).toColor()
    }
    return copy(
        background = background.shift(),
        surface = surface.shift(),
        surfaceVariant = surfaceVariant.shift(),
        badge = badge.shift(),
        pillUnselected = pillUnselected.shift(),
        primary = primary.shift(),
        navBar = navBar.shift(),
        onPrimary = onPrimary,
        textPrimary = textPrimary.shift(),
        textSecondary = textSecondary.shift(),
        textPlaceholder = textPlaceholder.shift(),
        successBg = successBg,
        success = success,
        errorBg = errorBg,
        error = error,
        statsCardBg = statsCardBg.shift(),
        darkCard = darkCard.shift(),
        darkCardAccent = darkCardAccent.shift(),
        darkCardTrack = darkCardTrack.shift(),
        onDarkCard = onDarkCard,
        onDarkCardMuted = onDarkCardMuted.shift(),
    )
}

data class Accent(
    val id: String,
    val label: String,
    val light: NazoColors,
    val dark: NazoColors,
)

val Accents: List<Accent> = listOf(
    Accent("mint", "Mint Green", LightNazoColors, DarkNazoColors),
    Accent("rose", "Rose", LightNazoColors.recolorToHue(345f), DarkNazoColors.recolorToHue(345f)),
    Accent("indigo", "Indigo", LightNazoColors.recolorToHue(230f), DarkNazoColors.recolorToHue(230f)),
    Accent("bronze", "Bronze", LightNazoColors.recolorToHue(35f), DarkNazoColors.recolorToHue(35f)),
    Accent("slate", "Slate", LightNazoColors.recolorToHue(200f), DarkNazoColors.recolorToHue(200f)),
)

fun resolveAccent(id: String, dark: Boolean): NazoColors =
    (Accents.firstOrNull { it.id == id } ?: Accents.first()).let { if (dark) it.dark else it.light }

fun previewColors(id: String, dark: Boolean): List<Color> {
    val p = resolveAccent(id, dark)
    return listOf(p.primary, p.surface, p.background, p.textPrimary)
}

// Active palette. Updated by NazoTheme; read by every screen through the accessors below.
// Reading/writing the plain State is non-composable, so it's safe from property getters.
private val _nazoColors = mutableStateOf(LightNazoColors)

internal fun setNazoColors(colors: NazoColors) {
    _nazoColors.value = colors
}

// Top-level accessors — existing screen code keeps using `NazoBackground`, etc.
val NazoBackground: Color get() = _nazoColors.value.background
val NazoSurface: Color get() = _nazoColors.value.surface
val NazoSurfaceVariant: Color get() = _nazoColors.value.surfaceVariant
val NazoBadge: Color get() = _nazoColors.value.badge
val NazoPillUnselected: Color get() = _nazoColors.value.pillUnselected
val NazoPrimary: Color get() = _nazoColors.value.primary
val NazoNavBar: Color get() = _nazoColors.value.navBar
val NazoOnPrimary: Color get() = _nazoColors.value.onPrimary
val NazoTextPrimary: Color get() = _nazoColors.value.textPrimary
val NazoTextSecondary: Color get() = _nazoColors.value.textSecondary
val NazoTextPlaceholder: Color get() = _nazoColors.value.textPlaceholder
val NazoSuccessBg: Color get() = _nazoColors.value.successBg
val NazoSuccess: Color get() = _nazoColors.value.success
val NazoErrorBg: Color get() = _nazoColors.value.errorBg
val NazoError: Color get() = _nazoColors.value.error
val NazoStatsCardBg: Color get() = _nazoColors.value.statsCardBg
val NazoDarkCard: Color get() = _nazoColors.value.darkCard
val NazoDarkCardAccent: Color get() = _nazoColors.value.darkCardAccent
val NazoDarkCardTrack: Color get() = _nazoColors.value.darkCardTrack
val NazoOnDarkCard: Color get() = _nazoColors.value.onDarkCard
val NazoOnDarkCardMuted: Color get() = _nazoColors.value.onDarkCardMuted
