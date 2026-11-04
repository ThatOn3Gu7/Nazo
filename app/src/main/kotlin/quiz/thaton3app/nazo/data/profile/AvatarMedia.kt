package quiz.thaton3app.nazo.data.profile

/**
 * Format sniffing, size/duration policy and display-kind decisions for profile
 * pictures — the shared, pure-JVM half of animated-avatar support.
 *
 * Everything here is deliberately free of `android.*` so the rules that decide
 * "is this a supported avatar, and is it small/short enough" are unit-testable
 * on the JVM (see `AvatarMediaTest`). The Android-side probing that actually
 * decodes media lives in [ProfileImageStore.probeAnimatedAvatar].
 *
 * What is supported, and why:
 *
 *  - **JPEG / PNG** — the original static pipeline, untouched.
 *  - **GIF** — animated, decoded by coil-gif (ImageDecoder on API 28+,
 *    Movie below). Bounded by the byte cap only: frames stream, so a GIF's
 *    cost is per-frame, not its duration.
 *  - **Animated WebP** — rides along for free on API 28+ because the same
 *    coil-gif decoder handles it; below API 28 it degrades to a rejected pick
 *    with an explicit message (or a static first frame on restore from a
 *    newer device — never a crash).
 *  - **MP4** — the only video container accepted, played by a muted looping
 *    MediaPlayer. Deliberately NOT a grab-bag of containers/codecs: every
 *    additional format is another decoder path to validate on low-end phones.
 *
 * The three limits below are policy, not accident:
 *
 *  - [MAX_ANIMATED_BYTES] 8 MB — animated avatars are stored as-is (no
 *    re-encode), and the same bytes ride along in every backup as base64
 *    (~10.7 MB worst case inside the bundle). 8 MB is double the old static
 *    cap, still a comfortable share/attach size, and hard-bounds the memory a
 *    decode can ask for on a phone.
 *  - [MAX_VIDEO_DURATION_MS] 15 s — an avatar is a loop, not a movie; past
 *    ~15 s the loop restart becomes noticeable and seek/decode work grows for
 *    no benefit. A 15 s 1080p clip at typical mobile bitrates fits the 8 MB
 *    cap anyway, so the two limits agree in practice.
 *  - [MAX_ANIMATED_LONG_EDGE] 2048 px — matches the static pipeline's decode
 *    ceiling (`ProfileImageStore.decodeForEditing`), keeps a decoded frame at
 *    ≤ ~16 MB, and is far beyond what a ≤132 dp circle can ever show.
 */
internal object AvatarMedia {

    /** Hard cap for any animated avatar file, on import AND in backups. */
    const val MAX_ANIMATED_BYTES = 8L * 1024 * 1024

    /** Longest looping video we accept. See the class docs for the rationale. */
    const val MAX_VIDEO_DURATION_MS = 15_000L

    /** Longest edge (either axis) accepted for an animated picture or video. */
    const val MAX_ANIMATED_LONG_EDGE = 2048

    /**
     * Every container/format an avatar file may be. [animated] is the sniff's
     * best guess: only [Webp] is ambiguous (a static and an animated WebP
     * share magic bytes), resolved by [isAnimatedWebp].
     */
    enum class Format(
        val mime: String,
        val extension: String,
        val animated: Boolean,
    ) {
        Jpeg("image/jpeg", "jpg", false),
        Png("image/png", "png", false),
        Gif("image/gif", "gif", true),
        Webp("image/webp", "webp", false),
        Mp4("video/mp4", "mp4", true),
    }

    /** How [ProfileAvatar] should render a stored `profile_picture_uri`. */
    enum class DisplayKind {
        /** No picture: the initials avatar. */
        None,
        /** `emoji:<symbol>`: rendered as text. */
        Emoji,
        /** A stored `file://…mp4`: rendered by the shared video player. */
        Video,
        /** Everything else (static or animated) goes through Coil. */
        Image,
    }

    /**
     * Identifies the format of a picked/exported avatar from its magic bytes.
     * Returns null for anything we do not own — including HEIC/AVIF (also
     * `ftyp`-branded ISO-BMFF boxes, but images) and QuickTime `.mov`, which
     * must keep flowing down the existing static-image pipeline.
     */
    fun sniff(bytes: ByteArray): Format? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
            Format.Jpeg

        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte() ->
            Format.Png

        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte() &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte() ->
            Format.Gif

        bytes.size >= 12 &&
            ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP") ->
            Format.Webp

        bytes.size >= 12 &&
            ascii(bytes, 4, "ftyp") && !isoImageBrand(bytes) && !quickTimeBrand(bytes) ->
            Format.Mp4

        else -> null
    }

    /**
     * True when the first bytes are an ISO-BMFF box whose major brand is a
     * still image (HEIC/HEIF/AVIF), not a video. Those share `ftyp` with MP4
     * and must not be routed into the video pipeline — today they decode as
     * static images exactly as before.
     */
    private fun isoImageBrand(bytes: ByteArray): Boolean {
        val brand = brand(bytes) ?: return false
        return brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis")
    }

    /** QuickTime `.mov` uses `ftyp` with the `qt  ` brand; we only accept MP4. */
    private fun quickTimeBrand(bytes: ByteArray): Boolean = brand(bytes) == "qt  "

    private fun brand(bytes: ByteArray): String? =
        if (bytes.size >= 12) asciiString(bytes, 8, 4) else null

    /**
     * True when a WebP carries the VP8X animation flag (bit 1 of the VP8X
     * payload flags byte at offset 20). Static WebPs have no VP8X chunk at
     * all, so they answer false.
     */
    fun isAnimatedWebp(bytes: ByteArray): Boolean =
        bytes.size >= 21 &&
            ascii(bytes, 12, "VP8X") &&
            (bytes[20].toInt() and 0x02) != 0

    /**
     * True when a picked file should bypass the static crop/re-encode pipeline
     * and be kept as-is (GIF, MP4, or an animated WebP). Static WebPs are
     * NOT animated picks — they keep today's crop-to-JPG behaviour.
     */
    fun isAnimatedPick(head: ByteArray): Boolean = when (sniff(head)) {
        Format.Gif, Format.Mp4 -> true
        Format.Webp -> isAnimatedWebp(head)
        else -> false
    }

    /**
     * How a stored `profile_picture_uri` should be displayed. Dispatches on
     * the URI shape first (emoji/video are ours to name) and only then on the
     * file extension, so no disk read is needed on the composition path.
     * Every write path names files by [Format.extension], including backup
     * restores, which is what makes the extension trustworthy.
     */
    fun displayKind(uri: String?): DisplayKind = when {
        uri.isNullOrBlank() -> DisplayKind.None
        uri.startsWith("emoji:") -> DisplayKind.Emoji
        uri.startsWith("file://") ->
            if (uri.substringAfterLast('/', "").substringAfterLast('.', "")
                    .lowercase() == Format.Mp4.extension
            ) DisplayKind.Video else DisplayKind.Image
        else -> DisplayKind.Image
    }

    // -----------------------------------------------------------------------
    // Policy checks — each returns a user-facing message, or null when OK.
    // -----------------------------------------------------------------------

    fun sizeError(sizeBytes: Long): String? =
        if (sizeBytes > MAX_ANIMATED_BYTES) {
            "That file is too large for a profile picture (8 MB maximum). " +
                "Try a shorter clip or a smaller file."
        } else null

    fun durationError(durationMs: Long): String? =
        if (durationMs > MAX_VIDEO_DURATION_MS) {
            "That video is too long — avatars loop best at 15 seconds or " +
                "less. Trim the clip and try again."
        } else null

    fun dimensionError(width: Int, height: Int): String? =
        if (maxOf(width, height) > MAX_ANIMATED_LONG_EDGE) {
            "That file's resolution is too high for a profile picture " +
                "(2048 px maximum). Downscale it and try again."
        } else null

    /**
     * True when the URI's file extension names an animated image we store
     * (`.gif` / `.webp`). Extension-based like [displayKind] — every persisted
     * value was written by us with [Format.extension], so the name is
     * trustworthy without touching disk.
     *
     * The UI uses this to tell "this avatar animates" from "this is a plain
     * photo" on the composition path (e.g. to delay a loading placeholder so
     * navigation doesn't flash the fallback avatar).
     */
    fun isAnimatedImageUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val ext = uri.substringAfterLast('/').substringAfterLast('.').lowercase()
        return ext == Format.Gif.extension || ext == Format.Webp.extension
    }

    /**
     * True for a video file extension we deliberately do NOT accept (by name).
     * The bytes decide what actually plays — MP4 only — but knowing the name
     * lets the picker explain "pick an MP4" instead of a generic image error.
     */
    fun isUnsupportedVideoName(name: String): Boolean =
        name.lowercase() in NON_MP4_VIDEO_EXTENSIONS

    private val NON_MP4_VIDEO_EXTENSIONS = setOf(
        "mov", "mkv", "webm", "avi", "3gp", "3g2", "flv", "ts", "m4v", "wmv", "mpeg", "mpg",
    )

    /**
     * A short, human heading for the top of the preview dialog, derived from
     * the full validation message shown in the preview area. The dialog shows
     * BOTH — heading on top, full explanation inside the preview — so they
     * must never repeat each other verbatim.
     *
     * Unknown messages (future wording, wrapped causes) get a safe generic
     * heading; the full message inside the preview is always the detail.
     */
    fun failureHeading(message: String): String = when {
        message.contains("too large") -> "File too large"
        message.contains("too long") -> "Video too long"
        message.contains("resolution") -> "Resolution too high"
        message.contains("video format") -> "Unsupported video format"
        message.contains("Animated WebP") -> "Animated WebP needs Android 9+"
        message.contains("couldn't be played") -> "Video couldn't be played"
        message.contains("couldn't be read", ignoreCase = true) ||
            message.contains("couldn't read", ignoreCase = true) -> {
            if (message.contains("video", ignoreCase = true)) "Video couldn't be read"
            else "File couldn't be read"
        }
        message.contains("is empty") -> "Empty file"
        message.contains("isn't a format") -> "Unsupported format"
        message.contains("couldn't be used") -> "Couldn't use this file"
        else -> "Couldn't use this file"
    }

    /** Shown when the bytes are not any format we accept. */
    fun unsupportedFormatError(isVideoLike: Boolean): String =
        if (isVideoLike) {
            "That video format isn't supported. Pick an MP4 video, or use a " +
                "GIF or photo instead."
        } else {
            "That file isn't a format we can use. Pick a photo, GIF, " +
                "animated WebP or MP4 video."
        }

    // -----------------------------------------------------------------------

    private fun ascii(bytes: ByteArray, offset: Int, expected: String): Boolean {
        if (bytes.size < offset + expected.length) return false
        for (i in expected.indices) {
            if (bytes[offset + i] != expected[i].code.toByte()) return false
        }
        return true
    }

    private fun asciiString(bytes: ByteArray, offset: Int, length: Int): String? {
        if (bytes.size < offset + length) return null
        return buildString(length) { for (i in 0 until length) append(bytes[offset + i].toInt().toChar()) }
    }
}
