package quiz.thaton3app.nazo.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import quiz.thaton3app.nazo.ui.components.LocalNazoReduceMotion

// Theme now reacts to the user's Appearance-screen choices: a real dark "deep forest"
// theme, plus a selectable accent that applies a full palette. Dynamic color (Material You)
// is intentionally NOT wired up: the brief is to match the mockup's fixed palette.

/**
 * The visually applied mode can intentionally trail the saved target for a few
 * frames while PixelCopy secures the reveal's source image. Appearance uses
 * this value so its swatches and celestial selectors change underneath the
 * wave instead of flickering before capture.
 */
internal data class NazoThemeVisualState(
    val darkTheme: Boolean,
    val themeMode: String,
    val modeChangePending: Boolean,
    val isProvided: Boolean,
)

internal val LocalNazoThemeVisualState = staticCompositionLocalOf {
    NazoThemeVisualState(
        darkTheme = false,
        themeMode = "",
        modeChangePending = false,
        isProvided = false,
    )
}

/** Insets kept as plain values so navigation-mode classification is unit-testable. */
internal data class NazoInsetEdges(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
)

/**
 * Whether Android has placed tappable system controls in its navigation area.
 *
 * Intersecting by edge matters because [WindowInsets.tappableElement] may also
 * describe system UI outside the navigation bar. Gesture navigation has a
 * navigation inset but no tappable overlap, so it remains transparent.
 */
internal fun hasTappableNavigationBar(
    navigationBars: NazoInsetEdges,
    tappableElement: NazoInsetEdges,
): Boolean =
    (navigationBars.leftPx > 0 && tappableElement.leftPx > 0) ||
        (navigationBars.topPx > 0 && tappableElement.topPx > 0) ||
        (navigationBars.rightPx > 0 && tappableElement.rightPx > 0) ||
        (navigationBars.bottomPx > 0 && tappableElement.bottomPx > 0)

@Composable
fun NazoTheme(
    darkTheme: Boolean,
    accentId: String,
    themeMode: String = if (darkTheme) "dark" else "light",
    reduceMotion: Boolean = false,
    highContrast: Boolean = false,
    textScaleMultiplier: Float = 1f,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val window = if (!view.isInEditMode) (view.context as? Activity)?.window else null
    val targetPalette = resolveAccent(accentId, darkTheme, highContrast)
    val themeFrame = rememberNazoThemeTransition(
        targetPalette = targetPalette,
        targetDark = darkTheme,
        targetThemeMode = themeMode,
        reduceMotion = reduceMotion,
        window = window,
    )
    val palette = themeFrame.palette
    // Publish the rendered (possibly intermediate) palette so every custom
    // `NazoXxx` accessor and Material component advances in the same frame.
    setNazoColors(palette)

    val colorScheme = if (themeFrame.darkScheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            secondaryContainer = palette.pillUnselected,
            onSecondaryContainer = palette.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textSecondary,
            secondaryContainer = palette.pillUnselected,
            onSecondaryContainer = palette.textPrimary,
        )
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationInsets = WindowInsets.navigationBars
    val tappableInsets = WindowInsets.tappableElement
    val navigationBarIsTappable = hasTappableNavigationBar(
        navigationBars = NazoInsetEdges(
            leftPx = navigationInsets.getLeft(density, layoutDirection),
            topPx = navigationInsets.getTop(density),
            rightPx = navigationInsets.getRight(density, layoutDirection),
            bottomPx = navigationInsets.getBottom(density),
        ),
        tappableElement = NazoInsetEdges(
            leftPx = tappableInsets.getLeft(density, layoutDirection),
            topPx = tappableInsets.getTop(density),
            rightPx = tappableInsets.getRight(density, layoutDirection),
            bottomPx = tappableInsets.getBottom(density),
        ),
    )

    if (window != null) {
        SideEffect {
            // Edge-to-edge: let the app draw full-bleed (behind the status bar and into the
            // display cutout) so the startup popup's blur/scrim can reach the entire top,
            // including the camera cutout and status bar. Screens already apply system-bar
            // padding so their controls stay clear. The gesture-navigation background may
            // still show through; tappable system-button navigation gets an opaque surface.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // System-bar colour setters are deprecated on API 35+, but remain necessary on
            // the older Android versions Nazo supports (including Android 10).
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            // Keep the gesture hint edge-to-edge, but give three-button navigation a fully
            // opaque themed surface so app content cannot show through behind Android's
            // Back, Home, and Recents controls.
            @Suppress("DEPRECATION")
            window.navigationBarColor = if (navigationBarIsTappable) {
                themeFrame.systemBarPalette.navBar.toArgb()
            } else {
                Color.Transparent.toArgb()
            }
            // Keep the Activity's own window background in sync with the in-app theme.
            // This prevents the white system window from bleeding through during the
            // AnimatedContent crossfade (especially in forced-dark mode) — see handoff log.
            window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
            val insets = WindowCompat.getInsetsController(window, view)
            // Android's icon style is a boolean rather than an animatable color.
            // Choose the higher-contrast polarity from the currently rendered
            // backdrop so the flip happens safely during the palette morph.
            insets.isAppearanceLightStatusBars =
                shouldUseDarkSystemBarIcons(themeFrame.systemBarPalette.background)
            val navigationBackdrop = if (navigationBarIsTappable) {
                themeFrame.systemBarPalette.navBar
            } else {
                themeFrame.systemBarPalette.background
            }
            insets.isAppearanceLightNavigationBars =
                shouldUseDarkSystemBarIcons(navigationBackdrop)
        }
    }

    val scaledDensity = remember(density.density, density.fontScale, textScaleMultiplier) {
        Density(
            density = density.density,
            fontScale = density.fontScale * textScaleMultiplier.coerceIn(1f, 1.5f),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NazoTypography,
    ) {
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalNazoReduceMotion provides reduceMotion,
            LocalNazoThemeVisualState provides NazoThemeVisualState(
                darkTheme = themeFrame.darkScheme,
                themeMode = themeFrame.themeMode,
                modeChangePending = themeFrame.modeChangePending,
                isProvided = true,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
                themeFrame.revealSnapshot?.let { snapshot ->
                    ThemeRevealOverlay(
                        snapshot = snapshot,
                        progress = themeFrame.revealProgress,
                        revealFromRight = themeFrame.revealFromRight,
                    )
                }
            }
        }
    }
}

/**
 * Draws the captured old window above the one live app tree, then clears an
 * expanding circle so the complete target theme is progressively exposed.
 */
@Composable
private fun ThemeRevealOverlay(
    snapshot: Bitmap,
    progress: Float,
    revealFromRight: Boolean,
) {
    val image = remember(snapshot) { snapshot.asImageBitmap() }
    // Do not eagerly recycle a bitmap that reached a Compose draw layer: the
    // renderer may still hold it for the preceding frame. Once this short-lived
    // overlay is removed, ordinary Bitmap GC safely releases its pixel buffer.

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // BlendMode.Clear must affect only this bitmap layer, never the
                // newly themed live app underneath it.
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        if (snapshot.isRecycled) return@Canvas
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(snapshot.width, snapshot.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
        val origin = themeRevealOrigin(size.width, revealFromRight)
        val radius = themeRevealRadius(size.width, size.height, progress)
        if (radius > 0f) {
            drawCircle(
                color = Color.Transparent,
                radius = radius + 1f,
                center = origin,
                blendMode = BlendMode.Clear,
            )
        }
    }
}
