package quiz.thaton3app.nazo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pure WCAG contrast math for the design system.
 *
 * The palette is built so every text role clears 4.5:1 and every control fill
 * or shared border clears 3:1 against the surfaces it actually sits on, in
 * every accent and both modes (see [enforceContrast] in Color.kt and
 * NazoContrastTest). Everything here is plain math on [Color] — no Android,
 * no composition — so the unit tests can hold the palette to the same rules
 * the builder applies.
 *
 * Thresholds follow WCAG 2.1: 4.5:1 for normal text (1.4.3), 3:1 for large
 * text and for meaningful non-text UI components (1.4.11). Disabled controls
 * are exempt from WCAG, but we still hold their labels to 3:1 so a greyed-out
 * button is readable as "off", not unreadable as a style.
 */

/** Near-white label color used across the app's filled buttons (mockup value). */
internal val ContrastNearWhite = Color(0xFFF7FEF8)

/** Near-black label color (the Mono dark theme's own on-color). */
internal val ContrastNearBlack = Color(0xFF121212)

/** Disabled button fill: the accent at this alpha over the page background. */
internal const val DisabledContainerAlpha = 0.4f

/**
 * Shared border alpha for outlined buttons. Composited over the page
 * background this reads as a 3:1 control boundary in every accent and both
 * modes (the empirical floor is 0.87; 0.9 keeps a small margin).
 */
internal const val OutlineBorderAlpha = 0.9f

/**
 * WCAG relative luminance of [color], ignoring alpha (palette colors are
 * opaque; semi-transparent roles are blended first via [blendOver]).
 */
internal fun relativeLuminance(color: Color): Float {
    fun ch(v: Float): Float =
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow24()
    val r = ch(color.red)
    val g = ch(color.green)
    val b = ch(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun Float.pow24(): Float {
    // x^2.4 for x >= 0; pow(2.4) via Math.pow is fine for our value range.
    return Math.pow(this.toDouble(), 2.4).toFloat()
}

/** WCAG contrast ratio between two opaque colors: (L1 + 0.05) / (L2 + 0.05). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** Composites [fg] at [alpha] over opaque [bg] — how semi-transparent roles actually render. */
internal fun blendOver(fg: Color, alpha: Float, bg: Color): Color = Color(
    red = fg.red * alpha + bg.red * (1f - alpha),
    green = fg.green * alpha + bg.green * (1f - alpha),
    blue = fg.blue * alpha + bg.blue * (1f - alpha),
    alpha = 1f,
)

/**
 * The label color for a filled button: the app's near-white unless that fails
 * [minContrast] on [fill], in which case near-black. Bright accent fills in
 * the dark themes (gold, lime, …) cannot carry white text at 4.5:1 — a dark
 * label on the same fill can. [preferred] is returned untouched when it
 * already passes, so palettes that read fine today keep their exact colors.
 */
internal fun readableTextOn(
    fill: Color,
    minContrast: Float,
    preferred: Color = ContrastNearWhite,
): Color = when {
    contrastRatio(preferred, fill) >= minContrast -> preferred
    contrastRatio(ContrastNearWhite, fill) >= minContrast -> ContrastNearWhite
    contrastRatio(ContrastNearBlack, fill) >= minContrast -> ContrastNearBlack
    // Mid-tone fill that neither label reaches: keep whichever reads better.
    contrastRatio(ContrastNearWhite, fill) >= contrastRatio(ContrastNearBlack, fill) ->
        ContrastNearWhite
    else -> ContrastNearBlack
}

/**
 * Disabled-button fill recipe: the accent at [DisabledContainerAlpha] over the
 * page background — still visibly "off", but a defined surface instead of an
 * accidental one.
 */
internal fun disabledContainer(accent: Color, background: Color): Color =
    blendOver(accent, DisabledContainerAlpha, background)

/**
 * Disabled-button label recipe: adaptive like [readableTextOn], but held to
 * the softer 3:1 "still legible" bar.
 */
internal fun disabledContent(container: Color): Color =
    readableTextOn(container, minContrast = 3f)

/**
 * The outlined button's border as rendered: accent at [OutlineBorderAlpha]
 * over the page background. Tested for 3:1 against that background so the
 * button's edge is perceivable as a control boundary (WCAG 1.4.11).
 */
internal fun outlineBorderComposite(accent: Color, background: Color): Color =
    blendOver(accent, OutlineBorderAlpha, background)
