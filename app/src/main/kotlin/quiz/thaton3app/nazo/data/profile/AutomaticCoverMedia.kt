package quiz.thaton3app.nazo.data.profile

import kotlin.math.roundToInt

/** Pure policy and image math for avatar-derived automatic cover backgrounds. */
internal object AutomaticCoverMedia {
    const val STATIC_WIDTH = 1024
    const val STATIC_HEIGHT = 576
    const val VIDEO_MAX_WIDTH = 960
    const val VIDEO_MAX_HEIGHT = 540
    const val VIDEO_FRAME_RATE = 24f
    const val VIDEO_BLUR_SIGMA = 18f

    enum class SourceKind {
        NONE,
        STATIC_IMAGE,
        VIDEO,
        UNSUPPORTED_ANIMATION,
    }

    fun sourceKind(uri: String?): SourceKind = when {
        uri.isNullOrBlank() || uri.startsWith("emoji:") -> SourceKind.NONE
        AvatarMedia.displayKind(uri) == AvatarMedia.DisplayKind.Video -> SourceKind.VIDEO
        AvatarMedia.isAnimatedImageUri(uri) -> SourceKind.UNSUPPORTED_ANIMATION
        else -> SourceKind.STATIC_IMAGE
    }

    /**
     * A 16:9 encode no larger than 960x540 and never wider than the source's
     * useful centre-crop. Even dimensions keep AVC encoders happy. Tiny legacy
     * clips get a conservative 160x90 floor rather than an invalid codec size.
     */
    fun videoOutputSize(
        encodedWidth: Int,
        encodedHeight: Int,
        rotationDegrees: Int,
    ): Pair<Int, Int>? {
        if (encodedWidth <= 0 || encodedHeight <= 0) return null
        val rotated = rotationDegrees.mod(180) != 0
        val displayWidth = if (rotated) encodedHeight else encodedWidth
        val displayHeight = if (rotated) encodedWidth else encodedHeight
        val usefulWidth = minOf(displayWidth.toFloat(), displayHeight * (16f / 9f))
        val width = even(minOf(VIDEO_MAX_WIDTH.toFloat(), usefulWidth).roundToInt())
            .coerceAtLeast(160)
        val height = even((width * 9f / 16f).roundToInt())
            .coerceAtLeast(90)
            .coerceAtMost(VIDEO_MAX_HEIGHT)
        return width to height
    }

    private fun even(value: Int): Int = value and 1.inv()

    /**
     * Three opaque representative colours from a small sample. Quantising to
     * 4 bits/channel makes the histogram cheap while a saturation-weighted
     * score avoids a tiny grey majority washing out a colourful avatar.
     */
    fun dominantPalette(pixels: IntArray): IntArray {
        data class Bucket(var count: Int = 0, var red: Long = 0, var green: Long = 0, var blue: Long = 0)

        val buckets = HashMap<Int, Bucket>()
        for (pixel in pixels) {
            if ((pixel ushr 24) < 96) continue
            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            val key = (red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
        }
        if (buckets.isEmpty()) {
            return intArrayOf(0xFF17233A.toInt(), 0xFF315B70.toInt(), 0xFF6D8A92.toInt())
        }

        data class Candidate(val color: Int, val score: Double, val count: Int)
        val candidates = buckets.values.map { bucket ->
            val red = (bucket.red / bucket.count).toInt()
            val green = (bucket.green / bucket.count).toInt()
            val blue = (bucket.blue / bucket.count).toInt()
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            val saturation = if (max == 0) 0.0 else (max - min).toDouble() / max
            Candidate(
                color = opaque(red, green, blue),
                score = bucket.count * (0.7 + saturation * 0.9),
                count = bucket.count,
            )
        }.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.count }.thenBy { it.color })

        val selected = ArrayList<Int>(3)
        for (candidate in candidates) {
            if (selected.all { distanceSquared(it, candidate.color) >= 46 * 46 }) {
                selected += candidate.color
                if (selected.size == 3) break
            }
        }
        if (selected.isEmpty()) selected += candidates.first().color
        while (selected.size < 3) {
            val base = selected.first()
            selected += if (selected.size == 1) adjust(base, 1.28f) else adjust(base, 0.68f)
        }
        return selected.toIntArray()
    }

    /** Three-pass separable box blur, intended for the tiny 160x90 work image. */
    fun blurOpaque(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int = 3,
    ): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height)
        if (radius <= 0 || passes <= 0) return pixels.copyOf()
        var current = pixels.copyOf()
        repeat(passes) {
            val horizontal = IntArray(current.size)
            val window = radius * 2 + 1
            for (y in 0 until height) {
                var red = 0L
                var green = 0L
                var blue = 0L
                for (offset in -radius..radius) {
                    val pixel = current[y * width + offset.coerceIn(0, width - 1)]
                    red += pixel ushr 16 and 0xFF
                    green += pixel ushr 8 and 0xFF
                    blue += pixel and 0xFF
                }
                for (x in 0 until width) {
                    horizontal[y * width + x] = opaque(
                        (red / window).toInt(),
                        (green / window).toInt(),
                        (blue / window).toInt(),
                    )
                    val leaving = current[y * width + (x - radius).coerceIn(0, width - 1)]
                    val entering = current[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                    red += (entering ushr 16 and 0xFF) - (leaving ushr 16 and 0xFF)
                    green += (entering ushr 8 and 0xFF) - (leaving ushr 8 and 0xFF)
                    blue += (entering and 0xFF) - (leaving and 0xFF)
                }
            }

            val vertical = IntArray(current.size)
            for (x in 0 until width) {
                var red = 0L
                var green = 0L
                var blue = 0L
                for (offset in -radius..radius) {
                    val pixel = horizontal[offset.coerceIn(0, height - 1) * width + x]
                    red += pixel ushr 16 and 0xFF
                    green += pixel ushr 8 and 0xFF
                    blue += pixel and 0xFF
                }
                for (y in 0 until height) {
                    vertical[y * width + x] = opaque(
                        (red / window).toInt(),
                        (green / window).toInt(),
                        (blue / window).toInt(),
                    )
                    val leaving = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                    val entering = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                    red += (entering ushr 16 and 0xFF) - (leaving ushr 16 and 0xFF)
                    green += (entering ushr 8 and 0xFF) - (leaving ushr 8 and 0xFF)
                    blue += (entering and 0xFF) - (leaving and 0xFF)
                }
            }
            current = vertical
        }
        return current
    }

    private fun opaque(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

    private fun distanceSquared(first: Int, second: Int): Int {
        val red = (first ushr 16 and 0xFF) - (second ushr 16 and 0xFF)
        val green = (first ushr 8 and 0xFF) - (second ushr 8 and 0xFF)
        val blue = (first and 0xFF) - (second and 0xFF)
        return red * red + green * green + blue * blue
    }

    private fun adjust(color: Int, factor: Float): Int = opaque(
        ((color ushr 16 and 0xFF) * factor).roundToInt(),
        ((color ushr 8 and 0xFF) * factor).roundToInt(),
        ((color and 0xFF) * factor).roundToInt(),
    )
}

enum class AutomaticCoverKind { IMAGE, VIDEO }

data class AutomaticCoverArtifact(
    val sourceUri: String,
    val uri: String,
    val kind: AutomaticCoverKind,
)

sealed interface AutomaticCoverState {
    data object Idle : AutomaticCoverState
    data class Preparing(val sourceUri: String) : AutomaticCoverState
    data class Ready(val artifact: AutomaticCoverArtifact) : AutomaticCoverState
    data class Unavailable(val sourceUri: String?, val message: String) : AutomaticCoverState
}
