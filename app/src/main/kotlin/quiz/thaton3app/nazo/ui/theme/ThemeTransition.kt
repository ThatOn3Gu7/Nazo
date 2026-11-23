package quiz.thaton3app.nazo.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal const val AccentPaletteTransitionDurationMs = 380
internal const val LightDarkPaletteTransitionDurationMs = 480

/** One rendered frame of an app-wide theme change. */
internal data class NazoThemeTransitionFrame(
    val palette: NazoColors,
    val contentAlpha: Float,
    val darkScheme: Boolean,
)

/**
 * Interpolates every palette role and re-applies the design system's contrast
 * guard to intermediate colors. Start and end values remain byte-for-byte the
 * approved palettes.
 */
internal fun interpolateNazoColors(
    start: NazoColors,
    end: NazoColors,
    fraction: Float,
): NazoColors {
    val progress = fraction.coerceIn(0f, 1f)
    if (progress == 0f) return start
    if (progress == 1f) return end

    return NazoColors(
        background = lerp(start.background, end.background, progress),
        surface = lerp(start.surface, end.surface, progress),
        surfaceVariant = lerp(start.surfaceVariant, end.surfaceVariant, progress),
        badge = lerp(start.badge, end.badge, progress),
        pillUnselected = lerp(start.pillUnselected, end.pillUnselected, progress),
        primary = lerp(start.primary, end.primary, progress),
        navBar = lerp(start.navBar, end.navBar, progress),
        onPrimary = lerp(start.onPrimary, end.onPrimary, progress),
        textPrimary = lerp(start.textPrimary, end.textPrimary, progress),
        textSecondary = lerp(start.textSecondary, end.textSecondary, progress),
        textPlaceholder = lerp(start.textPlaceholder, end.textPlaceholder, progress),
        successBg = lerp(start.successBg, end.successBg, progress),
        success = lerp(start.success, end.success, progress),
        errorBg = lerp(start.errorBg, end.errorBg, progress),
        error = lerp(start.error, end.error, progress),
        statsCardBg = lerp(start.statsCardBg, end.statsCardBg, progress),
        darkCard = lerp(start.darkCard, end.darkCard, progress),
        darkCardAccent = lerp(start.darkCardAccent, end.darkCardAccent, progress),
        darkCardTrack = lerp(start.darkCardTrack, end.darkCardTrack, progress),
        onDarkCard = lerp(start.onDarkCard, end.onDarkCard, progress),
        onDarkCardMuted = lerp(start.onDarkCardMuted, end.onDarkCardMuted, progress),
    ).enforceContrast()
}

/**
 * Dark/light changes fade the composed controls through the animated window
 * background. This hides the unavoidable foreground-polarity swap: directly
 * blending dark text into light text would briefly make it gray on a gray
 * surface. Accent-only changes keep the content fully visible.
 */
internal fun themeTransitionContentAlpha(
    progress: Float,
    startingAlpha: Float,
    lightDarkChange: Boolean,
): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = startingAlpha.coerceIn(0f, 1f)
    if (!lightDarkChange) {
        val eased = FastOutSlowInEasing.transform(p)
        return start + (1f - start) * eased
    }

    // Keep content fully hidden across the narrow interval where a single
    // foreground color cannot satisfy both the light and dark surface family.
    // The window background continues morphing underneath it.
    val fadeOutEnd = 1f / 3f
    val fadeInStart = 2f / 3f
    return when {
        p < fadeOutEnd -> start * (1f - smoothStep(p / fadeOutEnd))
        p <= fadeInStart -> 0f
        else -> smoothStep((p - fadeInStart) / (1f - fadeInStart))
    }
}

internal fun themeTransitionDurationMs(lightDarkChange: Boolean): Int =
    if (lightDarkChange) LightDarkPaletteTransitionDurationMs
    else AccentPaletteTransitionDurationMs

/** Dark system icons are preferred whenever they have at least as much contrast as white. */
internal fun shouldUseDarkSystemBarIcons(background: Color): Boolean =
    contrastRatio(ContrastNearBlack, background) >= contrastRatio(ContrastNearWhite, background)

/** Smoothstep without overshoot, useful for an opacity that must stay in [0, 1]. */
private fun smoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Keeps a single app composition alive while its palette changes underneath
 * it. A new selection interrupts from the currently rendered frame, so rapid
 * taps never jump back to an earlier palette.
 */
@Composable
internal fun rememberNazoThemeTransition(
    targetPalette: NazoColors,
    targetDark: Boolean,
    reduceMotion: Boolean,
): NazoThemeTransitionFrame {
    var renderedPalette by remember { mutableStateOf(targetPalette) }
    var renderedContentAlpha by remember { mutableFloatStateOf(1f) }
    var renderedDarkScheme by remember { mutableStateOf(targetDark) }
    var polarityTransitionInFlight by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetPalette, targetDark, reduceMotion) {
        if (renderedPalette == targetPalette &&
            renderedDarkScheme == targetDark &&
            renderedContentAlpha == 1f &&
            !polarityTransitionInFlight
        ) {
            return@LaunchedEffect
        }

        if (reduceMotion) {
            progress.snapTo(1f)
            renderedPalette = targetPalette
            renderedContentAlpha = 1f
            renderedDarkScheme = targetDark
            polarityTransitionInFlight = false
            return@LaunchedEffect
        }

        val startPalette = renderedPalette
        val startAlpha = renderedContentAlpha
        // If a mode transition is interrupted after its binary Material
        // scheme has flipped, its palette can still be midway between
        // polarities. Keep the protective fade active until a real endpoint.
        val lightDarkChange = renderedDarkScheme != targetDark || polarityTransitionInFlight
        polarityTransitionInFlight = lightDarkChange
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = themeTransitionDurationMs(lightDarkChange),
                easing = LinearEasing,
            ),
        ) {
            val easedColorProgress = FastOutSlowInEasing.transform(value)
            renderedPalette = interpolateNazoColors(
                start = startPalette,
                end = targetPalette,
                fraction = easedColorProgress,
            )
            renderedContentAlpha = themeTransitionContentAlpha(
                progress = easedColorProgress,
                startingAlpha = startAlpha,
                lightDarkChange = lightDarkChange,
            )
            // Material defaults may differ between lightColorScheme and
            // darkColorScheme. Flip that family only while content is hidden.
            if (lightDarkChange && easedColorProgress >= 0.5f) {
                renderedDarkScheme = targetDark
            }
        }

        renderedPalette = targetPalette
        renderedContentAlpha = 1f
        renderedDarkScheme = targetDark
        polarityTransitionInFlight = false
    }

    return NazoThemeTransitionFrame(
        palette = renderedPalette,
        contentAlpha = renderedContentAlpha,
        darkScheme = renderedDarkScheme,
    )
}
