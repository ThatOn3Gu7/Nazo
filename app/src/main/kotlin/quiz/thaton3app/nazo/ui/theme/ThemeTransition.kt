package quiz.thaton3app.nazo.ui.theme

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val AccentPaletteTransitionDurationMs = 380
internal const val ThemeRevealDurationMs = 620
internal const val ThemeRevealSystemBarSwitchFraction = 0.58f

/** One rendered frame of an app-wide theme change. */
internal data class NazoThemeTransitionFrame(
    val palette: NazoColors,
    val darkScheme: Boolean,
    val systemBarPalette: NazoColors,
    val revealSnapshot: Bitmap?,
    val revealProgress: Float,
    val revealFromRight: Boolean,
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

/** Top-corner origin: dark enters from the right and light from the left. */
internal fun themeRevealOrigin(width: Float, revealFromRight: Boolean): Offset =
    Offset(x = if (revealFromRight) width else 0f, y = 0f)

/** Radius required to reveal a full rectangular window from either top corner. */
internal fun themeRevealRadius(width: Float, height: Float, progress: Float): Float =
    hypot(width, height) * progress.coerceIn(0f, 1f)

/**
 * System-bar icons are binary, so switch them after most of the upper edge has
 * reached the target theme rather than flashing them at the start.
 */
internal fun themeRevealUsesTargetSystemBars(progress: Float): Boolean =
    progress >= ThemeRevealSystemBarSwitchFraction

/**
 * Keeps one live app composition. Accent-only changes continue to morph its
 * semantic palette. A light/dark polarity change instead captures the visible
 * window, applies the complete target palette underneath, and removes the old
 * frame with an expanding corner reveal. The bitmap is visual-only: app state,
 * media, dialogs and side effects are never duplicated.
 *
 * A target change during the reveal captures the currently displayed composite
 * and starts again from that exact frame, avoiding jumps on rapid selections.
 */
@Composable
internal fun rememberNazoThemeTransition(
    targetPalette: NazoColors,
    targetDark: Boolean,
    reduceMotion: Boolean,
    window: Window?,
): NazoThemeTransitionFrame {
    var renderedPalette by remember { mutableStateOf(targetPalette) }
    var renderedDarkScheme by remember { mutableStateOf(targetDark) }
    var renderedSystemBarPalette by remember { mutableStateOf(targetPalette) }
    var revealSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var revealFromRight by remember { mutableStateOf(targetDark) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetPalette, targetDark, reduceMotion, window) {
        if (renderedPalette == targetPalette &&
            renderedDarkScheme == targetDark &&
            revealSnapshot == null
        ) {
            renderedSystemBarPalette = targetPalette
            return@LaunchedEffect
        }

        if (reduceMotion) {
            progress.snapTo(1f)
            revealSnapshot = null
            renderedPalette = targetPalette
            renderedDarkScheme = targetDark
            renderedSystemBarPalette = targetPalette
            return@LaunchedEffect
        }

        val needsPolarityReveal = renderedDarkScheme != targetDark || revealSnapshot != null
        if (needsPolarityReveal) {
            // The previous rendered palette and any in-flight reveal remain on
            // screen while PixelCopy captures them. The target is not applied
            // until the snapshot has been installed above the live app.
            val snapshot = window?.let { captureWindowSnapshot(it) }
            if (snapshot == null) {
                // Capture failure is rare (usually a window with no buffer yet).
                // Prefer a clean immediate switch over reintroducing the full-app
                // opacity blink this reveal replaces.
                progress.snapTo(1f)
                revealSnapshot = null
                renderedPalette = targetPalette
                renderedDarkScheme = targetDark
                renderedSystemBarPalette = targetPalette
                return@LaunchedEffect
            }

            revealFromRight = targetDark
            revealSnapshot = snapshot
            progress.snapTo(0f)
            // Frame callbacks run before composition. Two ticks guarantee the
            // old-window snapshot has actually been placed before the live
            // semantic palette changes underneath it.
            withFrameNanos { }
            withFrameNanos { }

            renderedPalette = targetPalette
            renderedDarkScheme = targetDark
            // Likewise, do not open the circular hole until the target-theme
            // app has completed at least one composition behind the snapshot.
            withFrameNanos { }
            withFrameNanos { }

            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ThemeRevealDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            ) {
                if (themeRevealUsesTargetSystemBars(value)) {
                    renderedSystemBarPalette = targetPalette
                }
            }

            renderedPalette = targetPalette
            renderedDarkScheme = targetDark
            renderedSystemBarPalette = targetPalette
            revealSnapshot = null
            return@LaunchedEffect
        }

        // Accent-only: preserve the currently displayed intermediate palette
        // as the next start so fast repeated swatch taps remain continuous.
        val startPalette = renderedPalette
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = AccentPaletteTransitionDurationMs,
                easing = FastOutSlowInEasing,
            ),
        ) {
            val framePalette = interpolateNazoColors(
                start = startPalette,
                end = targetPalette,
                fraction = value,
            )
            renderedPalette = framePalette
            renderedSystemBarPalette = framePalette
        }
        renderedPalette = targetPalette
        renderedSystemBarPalette = targetPalette
    }

    return NazoThemeTransitionFrame(
        palette = renderedPalette,
        darkScheme = renderedDarkScheme,
        systemBarPalette = renderedSystemBarPalette,
        revealSnapshot = revealSnapshot,
        revealProgress = progress.value,
        revealFromRight = revealFromRight,
    )
}

/** PixelCopy includes the window's TextureView-backed media on API 26+. */
private suspend fun captureWindowSnapshot(window: Window): Bitmap? {
    repeat(2) { attempt ->
        val width = window.decorView.width
        val height = window.decorView.height
        if (width <= 0 || height <= 0) return null

        val snapshot = suspendCancellableCoroutine<Bitmap?> { continuation ->
            val bitmap = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            try {
                @Suppress("DEPRECATION")
                PixelCopy.request(
                    window,
                    bitmap,
                    { result ->
                        if (!continuation.isActive) {
                            bitmap.recycle()
                        } else if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            bitmap.recycle()
                            continuation.resume(null)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(null)
            }
        }
        if (snapshot != null) return snapshot
        if (attempt == 0) withFrameNanos { }
    }
    return null
}
