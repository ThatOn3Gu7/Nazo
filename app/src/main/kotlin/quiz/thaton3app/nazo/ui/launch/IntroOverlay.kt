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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

private val IntroBackgroundLight = Color(0xFF36A06F)
private val IntroBackgroundDark = Color(0xFF246D4C)

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
    mark: Int = R.drawable.ic_launcher_foreground,
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

            IntroStyle.TORII_PASS -> {
                // The gate grows as if you're walking under it; the ground splits
                // outward through the opening.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
                    launch {
                        zoomScale.animateTo(
                            targetValue = 22f,
                            animationSpec = tween(760, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)),
                        )
                    }
                    launch {
                        delay(430)
                        backgroundAlpha.animateTo(0f, tween(330))
                    }
                }
            }

            IntroStyle.SCROLL_UNFURL -> {
                // The scroll holds still while the ground unrolls vertically away
                // from it, then the mark itself lifts off.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(780, easing = FastOutSlowInEasing)) }
                    launch {
                        delay(240)
                        zoomScale.animateTo(14f, tween(540, easing = WarpSpeedEasing))
                    }
                    launch {
                        delay(360)
                        backgroundAlpha.animateTo(0f, tween(400))
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

            // Style-specific ground carving, drawn BEFORE the mark so the mark's
            // punch-out always wins where they overlap.
            when (style) {
                IntroStyle.TORII_PASS -> {
                    // Two panels retreat left/right, as if passing through the gate.
                    val half = size.width / 2f
                    val slide = half * p
                    // Left panel exits leftwards, right panel exits rightwards; each
                    // stays exactly half-width so they meet cleanly at p = 0.
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(-slide, 0f),
                        size = Size(half, size.height),
                        blendMode = BlendMode.DstOut,
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(half + slide, 0f),
                        size = Size(half, size.height),
                        blendMode = BlendMode.DstOut,
                    )
                }

                IntroStyle.SCROLL_UNFURL -> {
                    // The ground rolls away from the middle, top and bottom together.
                    val halfH = size.height / 2f
                    val open = halfH * p
                    // Top half rolls up, bottom half rolls down.
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(0f, -open),
                        size = Size(size.width, halfH),
                        blendMode = BlendMode.DstOut,
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(0f, halfH + open),
                        size = Size(size.width, halfH),
                        blendMode = BlendMode.DstOut,
                    )
                }

                else -> Unit
            }

            // Lantern sways slightly while it swells — a damped wobble, not a loop.
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
                blendMode = BlendMode.DstOut
            )
        }
    }
}
