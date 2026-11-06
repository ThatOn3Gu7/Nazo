package quiz.thaton3app.nazo.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import quiz.thaton3app.nazo.IntroStyle
import quiz.thaton3app.nazo.R

/** Raster size for the punched-out mark. Matches the legacy 512px PNG foreground. */
private const val MARK_RASTER_PX = 512

private val IntroBackgroundLight = Color(0xFF1B4135)
private val IntroBackgroundDark = Color(0xFF0F1713)

// Slow initial swell, then rapid acceleration past the camera
private val WarpSpeedEasing = CubicBezierEasing(0.7f, 0f, 0.15f, 1f)

/**
 * Cold-start intro that continues the system splash.
 *
 * Every style works the same way underneath: the mark is *punched out* of an
 * opaque colored ground with [BlendMode.DstOut], so what animates is a hole
 * revealing the app below — never a drawn logo. That's why [mark] must be a
 * flat silhouette (see `AppIconOption.introMark`); full-color art would punch
 * out as an unreadable blob.
 *
 * @param isDark OS dark mode, used only by the default green pair.
 * @param backgroundColor overrides the green pair — passed when a custom icon is
 *   active so the intro continues the splash's color with no visible cut.
 * @param mark silhouette drawable punched out of the ground.
 * @param style which launch animation to play; see [IntroStyle].
 */
@Composable
fun IntroOverlay(
    isDark: Boolean,
    backgroundColor: Color? = null,
    mark: Int = R.drawable.ic_mark_nazo_silhouette,
    style: IntroStyle = IntroStyle.WARP,
) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    // Rasterize through the DRAWABLE pipeline, not ImageBitmap.imageResource():
    // that helper only decodes raster assets (BitmapFactory) and throws on a
    // VectorDrawable, which is what the illustrated marks are. Going via
    // ContextCompat.getDrawable().toBitmap() handles vectors and PNGs alike.
    // Rendered once per mark at a fixed size; the zoom is applied at draw time,
    // and DstOut only samples alpha, so this resolution is ample.
    val context = LocalContext.current
    val logoBitmap: ImageBitmap = remember(mark) {
        val drawable = checkNotNull(ContextCompat.getDrawable(context, mark)) {
            "Intro mark drawable $mark could not be loaded"
        }
        drawable.toBitmap(width = MARK_RASTER_PX, height = MARK_RASTER_PX).asImageBitmap()
    }

    // Shared drivers. Each style animates a different subset; unused ones simply
    // stay at their initial value, so one Canvas can serve every style.
    val zoomScale = remember { Animatable(1f) }
    val backgroundAlpha = remember { Animatable(1f) }
    // 0 -> 1 progress used by the non-warp styles for their bespoke motion.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(style) {
        delay(80)

        when (style) {
            IntroStyle.WARP -> {
                val duration = 650
                coroutineScope {
                    launch {
                        zoomScale.animateTo(
                            targetValue = 18f,
                            animationSpec = tween(duration, easing = WarpSpeedEasing),
                        )
                    }
                    launch {
                        delay(200)
                        backgroundAlpha.animateTo(0f, tween(duration - 200))
                    }
                }
            }

            IntroStyle.LANTERN_GLOW -> {
                // The lantern swells gently (as if the flame catches), then its
                // light floods outward and consumes the ground.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(820, easing = LinearEasing)) }
                    launch {
                        zoomScale.animateTo(1.18f, tween(420, easing = FastOutSlowInEasing))
                        zoomScale.animateTo(26f, tween(420, easing = WarpSpeedEasing))
                    }
                    launch {
                        delay(500)
                        backgroundAlpha.animateTo(0f, tween(340))
                    }
                }
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
            val p = progress.value
            val scale = zoomScale.value

            // The lantern sways as if hanging, damping out as the animation
            // completes so nothing jitters at the handoff. Kanji marks hold still.
            val swayX = if (style == IntroStyle.LANTERN_GLOW) {
                sin(p * 12f) * (1f - p) * size.width * 0.012f
            } else 0f

            val scaledWidth = (logoBitmap.width * scale).toInt()
            val scaledHeight = (logoBitmap.height * scale).toInt()
            val left = (size.width - scaledWidth) / 2f + swayX
            val top = (size.height - scaledHeight) / 2f

            drawImage(
                image = logoBitmap,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(scaledWidth, scaledHeight),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}
