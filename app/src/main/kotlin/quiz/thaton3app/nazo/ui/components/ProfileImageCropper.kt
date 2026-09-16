package quiz.thaton3app.nazo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A square crop box positioned over a fully-visible image.
 *
 * The earlier version zoomed and panned the image behind a fixed circular
 * window. That was both harder to use and subtly broken, so this inverts the
 * model: the whole image stays on screen and the user drags a crop box over it,
 * which is the interaction most photo apps use.
 *
 * The box is locked to a square. The avatar is rendered in a circle, so a
 * non-square crop would have to be squashed or re-cropped on save, and the
 * result would stop matching what the user framed.
 */
class CropState(private val bitmap: Bitmap) {

    /** The crop box, in view pixels, relative to the widget's top-left. */
    var left by mutableFloatStateOf(0f)
        private set
    var top by mutableFloatStateOf(0f)
        private set
    var edge by mutableFloatStateOf(0f)
        private set

    /** Where the image is actually drawn inside the widget (letterboxed). */
    private var imageLeft = 0f
    private var imageTop = 0f
    private var imageWidth = 0f
    private var imageHeight = 0f
    private var measured = false

    /** Smallest crop box we allow, so it cannot be collapsed to nothing. */
    private val minEdge get() = min(80f, min(imageWidth, imageHeight))

    /**
     * Works out the letterboxed image rect for a ContentScale.Fit draw, then
     * seats the crop box in the middle at 80% of the short edge.
     */
    fun onCanvasMeasured(canvasWidth: Float, canvasHeight: Float) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return
        val scale = min(canvasWidth / bitmap.width, canvasHeight / bitmap.height)
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val newLeft = (canvasWidth - drawnWidth) / 2f
        val newTop = (canvasHeight - drawnHeight) / 2f

        val unchanged = measured &&
            imageWidth == drawnWidth && imageHeight == drawnHeight &&
            imageLeft == newLeft && imageTop == newTop
        if (unchanged) return

        imageLeft = newLeft
        imageTop = newTop
        imageWidth = drawnWidth
        imageHeight = drawnHeight
        measured = true
        reset()
    }

    /** Centres a fresh crop box covering most of the image. */
    fun reset() {
        if (!measured) return
        edge = min(imageWidth, imageHeight) * 0.8f
        left = imageLeft + (imageWidth - edge) / 2f
        top = imageTop + (imageHeight - edge) / 2f
    }

    /** Drags the whole box, keeping it inside the image. */
    fun move(delta: Offset) {
        if (!measured) return
        left = (left + delta.x).coerceIn(imageLeft, imageLeft + imageWidth - edge)
        top = (top + delta.y).coerceIn(imageTop, imageTop + imageHeight - edge)
    }

    /**
     * Resizes from one corner. The opposite corner stays pinned, and the box
     * stays square and inside the image.
     */
    fun resize(corner: CropCorner, delta: Offset) {
        if (!measured) return
        // Use the larger movement component so diagonal drags feel natural.
        val signed = when (corner) {
            CropCorner.TopLeft -> -(delta.x + delta.y) / 2f
            CropCorner.TopRight -> (delta.x - delta.y) / 2f
            CropCorner.BottomLeft -> (-delta.x + delta.y) / 2f
            CropCorner.BottomRight -> (delta.x + delta.y) / 2f
        }

        val right = left + edge
        val bottom = top + edge
        var newEdge = edge + signed

        // Clamp against the image bounds, anchored on the fixed corner.
        val maxEdge = when (corner) {
            CropCorner.TopLeft -> min(right - imageLeft, bottom - imageTop)
            CropCorner.TopRight -> min(imageLeft + imageWidth - left, bottom - imageTop)
            CropCorner.BottomLeft -> min(right - imageLeft, imageTop + imageHeight - top)
            CropCorner.BottomRight ->
                min(imageLeft + imageWidth - left, imageTop + imageHeight - top)
        }
        newEdge = newEdge.coerceIn(minEdge, max(minEdge, maxEdge))

        when (corner) {
            CropCorner.TopLeft -> {
                left = right - newEdge
                top = bottom - newEdge
            }
            CropCorner.TopRight -> top = bottom - newEdge
            CropCorner.BottomLeft -> left = right - newEdge
            CropCorner.BottomRight -> Unit
        }
        edge = newEdge
    }

    /** The image rect in view pixels, for drawing the dimmed surround. */
    fun imageRect(): Rect = Rect(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight)

    /**
     * Maps the crop box back to source pixels and returns a NEW bitmap.
     *
     * The source is only read, never mutated, so gallery edits stay
     * non-destructive.
     */
    fun cropToBitmap(): Bitmap? {
        if (!measured || imageWidth <= 0f) return null
        return runCatching {
            val scale = bitmap.width / imageWidth
            val srcLeft = ((left - imageLeft) * scale).roundToInt()
                .coerceIn(0, max(0, bitmap.width - 1))
            val srcTop = ((top - imageTop) * scale).roundToInt()
                .coerceIn(0, max(0, bitmap.height - 1))
            val srcEdge = (edge * scale).roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(min(bitmap.width - srcLeft, bitmap.height - srcTop))
            Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcEdge, srcEdge)
        }.getOrNull()
    }
}

enum class CropCorner { TopLeft, TopRight, BottomLeft, BottomRight }

@Composable
fun rememberCropState(bitmap: Bitmap): CropState = remember(bitmap) { CropState(bitmap) }

/**
 * Square crop box with draggable corners over a fully-visible image.
 */
@Composable
fun ProfileImageCropper(
    bitmap: Bitmap,
    state: CropState,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    // Generous touch target: the drawn handle is small, but anywhere within
    // this radius of a corner grabs it.
    val handleTouchPx = with(density) { 32.dp.toPx() }
    val handleDrawPx = with(density) { 18.dp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f)),
    ) {
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        state.onCanvasMeasured(canvasW, canvasH)

        // The whole image, always fully visible. ContentScale.Fit letterboxes
        // it; CropState mirrors that same maths to place the box.
        Image(
            bitmap = image,
            contentDescription = "Image being cropped",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    var active: CropCorner? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            active = nearestCorner(
                                start,
                                state.left,
                                state.top,
                                state.edge,
                                handleTouchPx,
                            )
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, drag ->
                        change.consume()
                        val corner = active
                        if (corner != null) state.resize(corner, drag) else state.move(drag)
                    }
                }
                .drawBehind {
                    val box = Rect(
                        state.left,
                        state.top,
                        state.left + state.edge,
                        state.top + state.edge,
                    )
                    val img = state.imageRect()

                    // Dim everything outside the crop box, in four bands. This
                    // avoids BlendMode.Clear, which needs an offscreen layer
                    // and is easy to get wrong.
                    val shade = Color.Black.copy(alpha = 0.55f)
                    drawRect(
                        shade,
                        topLeft = Offset(img.left, img.top),
                        size = Size(img.width, box.top - img.top),
                    )
                    drawRect(
                        shade,
                        topLeft = Offset(img.left, box.bottom),
                        size = Size(img.width, img.bottom - box.bottom),
                    )
                    drawRect(
                        shade,
                        topLeft = Offset(img.left, box.top),
                        size = Size(box.left - img.left, box.height),
                    )
                    drawRect(
                        shade,
                        topLeft = Offset(box.right, box.top),
                        size = Size(img.right - box.right, box.height),
                    )

                    // Box outline.
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(box.left, box.top),
                        size = Size(box.width, box.height),
                        style = Stroke(width = strokePx),
                    )

                    // Rule-of-thirds guides.
                    val third = box.width / 3f
                    val guide = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        drawLine(
                            guide,
                            Offset(box.left + third * i, box.top),
                            Offset(box.left + third * i, box.bottom),
                            strokeWidth = strokePx / 2f,
                        )
                        drawLine(
                            guide,
                            Offset(box.left, box.top + third * i),
                            Offset(box.right, box.top + third * i),
                            strokeWidth = strokePx / 2f,
                        )
                    }

                    // Corner handles.
                    val corners = listOf(
                        Offset(box.left, box.top),
                        Offset(box.right, box.top),
                        Offset(box.left, box.bottom),
                        Offset(box.right, box.bottom),
                    )
                    corners.forEach { c ->
                        drawCircle(
                            color = Color.White,
                            radius = handleDrawPx / 2f,
                            center = c,
                        )
                    }

                    // Circle preview: the avatar is masked to a circle, so show
                    // which part of the crop actually survives.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = box.width / 2f,
                        center = box.center,
                        style = Stroke(width = strokePx / 2f),
                    )
                },
        )
    }
}

/** Returns the corner within [radius] of [point], or null to mean "move". */
private fun nearestCorner(
    point: Offset,
    left: Float,
    top: Float,
    edge: Float,
    radius: Float,
): CropCorner? {
    val candidates = listOf(
        CropCorner.TopLeft to Offset(left, top),
        CropCorner.TopRight to Offset(left + edge, top),
        CropCorner.BottomLeft to Offset(left, top + edge),
        CropCorner.BottomRight to Offset(left + edge, top + edge),
    )
    var best: CropCorner? = null
    var bestDistance = radius
    candidates.forEach { (corner, position) ->
        val distance = (point - position).getDistance()
        if (distance <= bestDistance) {
            bestDistance = distance
            best = corner
        }
    }
    return best
}
