package quiz.thaton3app.nazo.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Theme now reacts to the user's Appearance-screen choices: a real dark "deep forest"
// theme, plus a selectable accent that applies a full palette. Dynamic color (Material You)
// is intentionally NOT wired up: the brief is to match the mockup's fixed palette.

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
    content: @Composable () -> Unit,
) {
    val palette = resolveAccent(accentId, darkTheme)
    // Publish the active palette so every screen's `NazoXxx` accessor reflects it.
    setNazoColors(palette)

    val colorScheme = if (darkTheme) {
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
                palette.navBar.toArgb()
            } else {
                Color.Transparent.toArgb()
            }
            // Keep the Activity's own window background in sync with the in-app theme.
            // This prevents the white system window from bleeding through during the
            // AnimatedContent crossfade (especially in forced-dark mode) — see handoff log.
            window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NazoTypography,
        content = content,
    )
}
