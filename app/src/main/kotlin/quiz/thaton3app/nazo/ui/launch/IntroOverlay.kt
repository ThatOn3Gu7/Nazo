package quiz.thaton3app.nazo.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.R

// The exact launcher-icon / splash background greens (see res/values/colors.xml
// and values-night/colors.xml) so the system splash hands off to this overlay
// with zero visible seam. Chosen by the APP's theme mode (isDark from NazoApp),
// not the OS, so post-splash the tile matches what the app will actually show.
private val IntroBackgroundLight = Color(0xFF36A06F)
private val IntroBackgroundDark = Color(0xFF246D4C)

/**
 * Branded cold-start intro: a full-screen brand tile with the 謎 launcher glyph
 * that plays ONCE per cold start, layered over the already-composed app (nothing
 * underneath is delayed). Timeline (~1.5s total):
 *   hold 250ms → logo scale 0.9→1.0 over 450ms (FastOutSlowInEasing)
 *   → hold 450ms → whole overlay fades out over 400ms → removed from the tree.
 *
 * While visible it is an input barrier: a pointerInput loop consumes every
 * pointer change so taps can't leak to the UI below (the loop is a suspend
 * effect, so it self-cancels the moment the overlay leaves composition).
 * Warm starts don't replay it — the composition (and the dismissed flag)
 * survive while the activity is alive.
 */
@Composable
fun IntroOverlay(isDark: Boolean) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val logoScale = remember { Animatable(0.9f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(250)
        logoScale.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        delay(450)
        overlayAlpha.animateTo(0f, tween(durationMillis = 400))
        dismissed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // graphicsLayer BEFORE background so the fade applies to the whole
            // overlay (tile + logo) as one layer.
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(if (isDark) IntroBackgroundDark else IntroBackgroundLight)
            .pointerInput(Unit) {
                // Input barrier while the intro is showing.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 200dp = the system splash screen's icon box, so the handoff from the
        // splash icon to this logo is seamless (same asset, same size, same bg).
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
        )
    }
}
