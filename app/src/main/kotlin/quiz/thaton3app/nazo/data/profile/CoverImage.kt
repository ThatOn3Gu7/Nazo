package quiz.thaton3app.nazo.data.profile

/**
 * Static policy for profile cover (banner) images: the resolution floor and
 * the copy for picks a cover rejects. Pure Kotlin with no Android imports,
 * so the checks run as plain JVM tests; byte-level format sniffing stays in
 * [AvatarMedia] (`isAnimatedPick` decides the animated case, which the
 * caller checks before the size floor).
 *
 * Why a floor at all: the banner is rendered with `ContentScale.Crop`, so a
 * small source never letterboxes or stretches — it UPSCALES, and a soft
 * blurry cover is the failure mode this guard exists to prevent.
 */
object CoverImage {

    /**
     * Floor in EXIF-oriented pixels. Height 480 = the tallest banner any
     * phone renders (160 dp @3x), so nothing ever scales vertically; width
     * 1024 keeps the upscale ≤ ~1.12× even on the largest phone (the widest
     * phone banner is ~1150 px). Tablets with a 1024-px source may soften to
     * ~1.47× — accepted, documented in handoff.md.
     */
    const val MIN_WIDTH = 1024
    const val MIN_HEIGHT = 480

    /** Rejected when the picked bytes sniff as GIF / animated WebP / MP4. */
    const val ANIMATED_ERROR =
        "Animated images can't be used as a cover photo. Pick a static photo."

    /**
     * Null when [width] × [height] (already EXIF-corrected by the caller)
     * is large enough for the banner; otherwise user-facing copy that names
     * the exact numbers, so "too small" is actionable rather than vague.
     */
    fun sizeError(width: Int, height: Int): String? =
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            "That image is too small for a cover photo — it needs to be at " +
                "least $MIN_WIDTH × $MIN_HEIGHT pixels. Choose a larger, wider photo."
        } else {
            null
        }
}
