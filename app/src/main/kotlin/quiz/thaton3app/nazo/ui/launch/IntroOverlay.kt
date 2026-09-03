package quiz.thaton3app.nazo.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.R

private val IntroBackgroundLight = Color(0xFF36A06F)
private val IntroBackgroundDark = Color(0xFF246D4C)

// Slow initial swell, then rapid acceleration past the camera
private val WarpSpeedEasing = CubicBezierEasing(0.7f, 0f, 0.15f, 1f)

/**
 * @param backgroundColor overrides the default green pair — passed when the user
 *   picked a custom app icon, so the intro continues the system splash's color
 *   instead of cutting from (say) pink to green mid-animation.
 */
@Composable
fun IntroOverlay(isDark: Boolean, backgroundColor: Color? = null) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val logoBitmap = ImageBitmap.imageResource(R.drawable.ic_launcher_foreground)

    val zoomScale = remember { Animatable(1f) }
    val backgroundAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(80)
        val duration = 650

        // coroutineScope allows running launch block animations concurrently safely
        coroutineScope {
            launch {
                zoomScale.animateTo(
                    targetValue = 18f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = WarpSpeedEasing
                    )
                )
            }

            launch {
                delay(200)
                backgroundAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = duration - 200)
                )
            }
        }

        dismissed = true
    }

    val bgColor = backgroundColor
        ?: if (isDark) IntroBackgroundDark else IntroBackgroundLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { 
                compositingStrategy = CompositingStrategy.Offscreen 
                alpha = backgroundAlpha.value
            }
            .background(bgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = zoomScale.value
            val scaledWidth = (logoBitmap.width * scale).toInt()
            val scaledHeight = (logoBitmap.height * scale).toInt()

            val left = (size.width - scaledWidth) / 2f
            val top = (size.height - scaledHeight) / 2f

            drawImage(
                image = logoBitmap,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(scaledWidth, scaledHeight),
                blendMode = BlendMode.DstOut
            )
        }
    }
}

