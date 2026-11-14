package quiz.thaton3app.nazo.modes.guessing_game

/** Rendering policy kept pure so the API-31 compatibility boundary is testable. */
internal object BlurRevealCompatibility {
    const val NATIVE_BLUR_MIN_SDK = 31
    private const val MAX_COMPAT_CELL = 48

    enum class Renderer { PIXEL_STYLE, NATIVE_BLUR, COMPATIBILITY_BLUR }

    fun renderer(revealStyle: String, sdkInt: Int): Renderer = when {
        revealStyle == "pixel" -> Renderer.PIXEL_STYLE
        sdkInt >= NATIVE_BLUR_MIN_SDK -> Renderer.NATIVE_BLUR
        else -> Renderer.COMPATIBILITY_BLUR
    }

    /**
     * Pre-Android-12 blur substitute: downsample then smoothly upscale.
     * Radius zero is the original image; maximum blur averages roughly 48x48
     * source-pixel regions, which keeps the answer meaningfully obscured.
     */
    fun compatibilityCellSize(blurRadius: Int, maximumBlurRadius: Int): Int {
        if (blurRadius <= 0 || maximumBlurRadius <= 0) return 1
        val fraction = blurRadius.coerceAtMost(maximumBlurRadius).toFloat() / maximumBlurRadius
        return (1 + fraction * (MAX_COMPAT_CELL - 1)).toInt().coerceIn(1, MAX_COMPAT_CELL)
    }
}
