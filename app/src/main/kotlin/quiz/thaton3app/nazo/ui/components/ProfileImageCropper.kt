package quiz.thaton3app.nazo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The state of a square crop: how the image is panned and zoomed inside the
 * crop window.
 *
 * Kept outside the composable so the caller can hold it across a preview/edit
 * round trip, and so [cropToBitmap] can run without touching the UI.
 */
class CropState(private val bitmap: Bitmap) {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    /** Viewport edge in px, set once the crop window has been measured. */
    var viewport: Float = 0f
        private set

    /**
     * The scale at which the image exactly fills the square. Everything is
     * expressed as a multiple of this so the image can never be zoomed out far
     * enough to show empty space inside the crop window.
     */
    private var baseScale: Float = 1f

    fun onViewportMeasured(size: Float) {
        if (size <= 0f || viewport == size) return
        viewport = size
        baseScale = size / min(bitmap.width, bitmap.height).toFloat()
        clamp()
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        clamp()
    }

    fun onGesture(pan: Offset, zoom: Float) {
        scale = (scale * zoom).coerceIn(1f, 6f)
        offsetX += pan.x
        offsetY += pan.y
        clamp()
    }

    /**
     * Keeps the image covering the crop window.
     *
     * Without this the user can drag the photo half out of frame and crop a
     * band of blank canvas, which then gets saved as their avatar.
     */
    private fun clamp() {
        if (viewport <= 0f) return
        val drawnWidth = bitmap.width * baseScale * scale
        val drawnHeight = bitmap.height * baseScale * scale
        val maxX = max(0f, (drawnWidth - viewport) / 2f)
        val maxY = max(0f, (drawnHeight - viewport) / 2f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    fun effectiveScale(): Float = baseScale * scale

    /**
     * Renders the visible square to a NEW bitmap.
     *
     * Reads from the source and allocates a fresh output — the input bitmap is
     * never mutated, which is what makes gallery editing non-destructive even
     * before the file-level guarantees in ProfileImageStore.
     */
    fun cropToBitmap(): Bitmap? {
        if (viewport <= 0f) return null
        return runCatching {
            val effective = effectiveScale()
            // Top-left of the crop window, in source-image coordinates.
            val srcLeft = (bitmap.width / 2f) - (offsetX / effective) -
                (viewport / 2f / effective)
            val srcTop = (bitmap.height / 2f) - (offsetY / effective) -
                (viewport / 2f / effective)
            val srcEdge = viewport / effective

            val left = srcLeft.roundToInt().coerceIn(0, max(0, bitmap.width - 1))
            val top = srcTop.roundToInt().coerceIn(0, max(0, bitmap.height - 1))
            val edge = srcEdge.roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(min(bitmap.width - left, bitmap.height - top))

            Bitmap.createBitmap(bitmap, left, top, edge, edge)
        }.getOrNull()
    }
}

@Composable
fun rememberCropState(bitmap: Bitmap): CropState =
    remember(bitmap) { CropState(bitmap) }

/**
 * A square pinch-to-zoom / drag-to-pan cropper with a circular guide.
 *
 * Deliberately hand-rolled rather than pulling in a crop library: the app only
 * needs a fixed square, and the common libraries each ship their own Activity,
 * theme and Material version, which would fight the app's styling — the brief
 * asks to stay inside the existing UI system.
 */
@Composable
fun ProfileImageCropper(
    bitmap: Bitmap,
    state: CropState,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        state.onViewportMeasured(viewportPx)

        Image(
            bitmap = image,
            contentDescription = "Crop preview",
            contentScale = ContentScale.None,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The image is drawn at its natural pixel size, so the
                    // source-px -> viewport mapping is applied here.
                    val effective = state.effectiveScale()
                    scaleX = effective
                    scaleY = effective
                    translationX = state.offsetX
                    translationY = state.offsetY
                }
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        state.onGesture(pan, zoom)
                    }
                },
        )

        // Circular guide: the avatar always renders in a circle, so the user
        // needs to see which part survives that mask.
        //
        // graphicsLayer comes BEFORE drawWithContent so the offscreen layer
        // wraps the drawing. BlendMode.Clear punches a hole in the layer it
        // composites into, so without an explicit offscreen layer it would
        // clear straight through the window background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // Scrim everything, then punch the circle back out.
                    drawRect(color = Color.Black.copy(alpha = 0.45f))
                    drawCircle(
                        color = Color.Black,
                        radius = radius,
                        center = center,
                        blendMode = BlendMode.Clear,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        )
    }
}
