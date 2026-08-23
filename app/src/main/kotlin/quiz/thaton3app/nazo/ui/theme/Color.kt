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

// Active palette. Updated by NazoTheme; read by every screen through the accessors below.
// Reading/writing the plain State is non-composable, so it's safe from property getters.
private val _nazoColors = mutableStateOf(LightNazoColors)

internal fun setNazoColors(colors: NazoColors) {
    _nazoColors.value = colors
}

/** Returns a copy of these colors with [primary] (and accent-dependent surfaces) overridden. */
fun NazoColors.withPrimary(primary: Color): NazoColors = copy(
    primary = primary,
    badge = primary,
    darkCardAccent = primary,
)

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
