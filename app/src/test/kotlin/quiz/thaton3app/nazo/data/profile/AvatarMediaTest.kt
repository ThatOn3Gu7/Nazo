package quiz.thaton3app.nazo.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the pure animated-avatar layer: format sniffing,
 * animated-WebP detection, display-kind dispatch, and the size/duration/
 * resolution policy messages. Everything under test is android-free by
 * construction, so these run as plain JVM tests.
 */
class AvatarMediaTest {

    // ---- fixtures --------------------------------------------------------

    private fun gifSample(): ByteArray =
        "GIF89a".toByteArray() + ByteArray(64) { it.toByte() }

    /** ISO-BMFF header: [size][ftyp][major brand][compatible brands]… */
    private fun ftypSample(brand: String): ByteArray {
        require(brand.length == 4)
        return byteArrayOf(0, 0, 0, 0x18) +
            "ftyp".toByteArray() +
            brand.toByteArray() +
            ByteArray(32) { it.toByte() }
    }

    private fun webpSample(animated: Boolean): ByteArray {
        val head = "RIFF".toByteArray() + byteArrayOf(64, 0, 0, 0) + "WEBP".toByteArray()
        return if (animated) {
            // VP8X chunk: fourcc at 12, chunk size at 16, flags byte at 20
            // (bit 1 = animation), then reserved + canvas size.
            head +
                "VP8X".toByteArray() +
                byteArrayOf(10, 0, 0, 0) +
                byteArrayOf(0x02) +
                ByteArray(3) +
                byteArrayOf(0xFF.toByte(), 0x0F) +
                byteArrayOf(0xFF.toByte(), 0x0F) +
                ByteArray(16)
        } else {
            head + "VP8 ".toByteArray() + ByteArray(32) { it.toByte() }
        }
    }

    private fun jpegSample(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(16)

    // ---- format sniffing -------------------------------------------------

    @Test
    fun sniffRecognisesEverySupportedFormat() {
        assertEquals(AvatarMedia.Format.Jpeg, AvatarMedia.sniff(jpegSample()))
        assertEquals(
            AvatarMedia.Format.Png,
            AvatarMedia.sniff(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                ) + ByteArray(8)
            ),
        )
        assertEquals(AvatarMedia.Format.Gif, AvatarMedia.sniff(gifSample()))
        assertEquals(AvatarMedia.Format.Webp, AvatarMedia.sniff(webpSample(animated = false)))
        assertEquals(AvatarMedia.Format.Mp4, AvatarMedia.sniff(ftypSample("isom")))
        assertEquals(AvatarMedia.Format.Mp4, AvatarMedia.sniff(ftypSample("mp42")))
        assertEquals(AvatarMedia.Format.Mp4, AvatarMedia.sniff(ftypSample("avc1")))
    }

    @Test
    fun sniffRejectsUnknownEmptyAndLookalikeBytes() {
        assertNull(AvatarMedia.sniff(ByteArray(0)))
        assertNull(AvatarMedia.sniff(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        // GIF magic with the wrong trailer.
        assertNull(AvatarMedia.sniff("GIF80a".toByteArray() + ByteArray(8)))
        // ISO-BMFF but a still-image brand: must stay on the static pipeline.
        assertNull(AvatarMedia.sniff(ftypSample("heic")))
        assertNull(AvatarMedia.sniff(ftypSample("avif")))
        // QuickTime .mov shares ftyp with MP4 and is deliberately unsupported.
        assertNull(AvatarMedia.sniff(ftypSample("qt  ")))
        // RIFF that is not WEBP (e.g. a WAV).
        assertNull(
            AvatarMedia.sniff(
                "RIFF".toByteArray() + byteArrayOf(64, 0, 0, 0) + "WAVE".toByteArray() + ByteArray(8)
            ),
        )
    }

    // ---- animated WebP detection ----------------------------------------

    @Test
    fun animatedWebpIsDetectedFromTheVp8xFlag() {
        assertTrue(AvatarMedia.isAnimatedWebp(webpSample(animated = true)))
        assertFalse(AvatarMedia.isAnimatedWebp(webpSample(animated = false)))
        // No VP8X chunk at all (baseline VP8) and truncated bytes: not animated.
        assertFalse(AvatarMedia.isAnimatedWebp("RIFF".toByteArray() + ByteArray(4)))
        assertFalse(AvatarMedia.isAnimatedWebp(ByteArray(0)))
    }

    @Test
    fun animatedPickIncludesGifMp4AndAnimatedWebpOnly() {
        assertTrue(AvatarMedia.isAnimatedPick(gifSample()))
        assertTrue(AvatarMedia.isAnimatedPick(ftypSample("isom")))
        assertTrue(AvatarMedia.isAnimatedPick(webpSample(animated = true)))
        // Static WebP keeps the existing crop/re-encode path.
        assertFalse(AvatarMedia.isAnimatedPick(webpSample(animated = false)))
        assertFalse(AvatarMedia.isAnimatedPick(jpegSample()))
        assertFalse(AvatarMedia.isAnimatedPick(ByteArray(0)))
    }

    // ---- display-kind dispatch ------------------------------------------

    @Test
    fun displayKindDispatchesOnStoredUriShape() {
        assertEquals(AvatarMedia.DisplayKind.None, AvatarMedia.displayKind(null))
        assertEquals(AvatarMedia.DisplayKind.None, AvatarMedia.displayKind(""))
        assertEquals(AvatarMedia.DisplayKind.Emoji, AvatarMedia.displayKind("emoji:🌸"))
        assertEquals(
            AvatarMedia.DisplayKind.Video,
            AvatarMedia.displayKind("file:///data/user/0/nazo/files/profile/avatar_1.mp4"),
        )
        // Extension match is case-insensitive; only mp4 is a video.
        assertEquals(
            AvatarMedia.DisplayKind.Video,
            AvatarMedia.displayKind("file:///data/user/0/nazo/files/profile/avatar_1.MP4"),
        )
        for (uri in listOf(
            "file:///data/user/0/nazo/files/profile/avatar_1.jpg",
            "file:///data/user/0/nazo/files/profile/avatar_1.gif",
            "file:///data/user/0/nazo/files/profile/avatar_1.webp",
            "file:///data/user/0/nazo/files/profile/avatar_1",
            "https://example.com/me.png",
            "content://media/external/images/42",
        )) {
            assertEquals("for $uri", AvatarMedia.DisplayKind.Image, AvatarMedia.displayKind(uri))
        }
    }

    // ---- policy ----------------------------------------------------------

    @Test
    fun sizePolicyCapsAnimatedAvatarsAtEightMegabytes() {
        assertNull(AvatarMedia.sizeError(AvatarMedia.MAX_ANIMATED_BYTES))
        val message = AvatarMedia.sizeError(AvatarMedia.MAX_ANIMATED_BYTES + 1)
        assertNotNull(message)
        assertTrue(message!!.contains("8 MB"))
        assertNull(AvatarMedia.sizeError(0L))
    }

    @Test
    fun durationPolicyCapsVideoLoopsAtFifteenSeconds() {
        assertNull(AvatarMedia.durationError(15_000L))
        assertNull(AvatarMedia.durationError(1L))
        val message = AvatarMedia.durationError(15_001L)!!
        assertTrue(message.contains("15 seconds"))
        assertTrue(message.contains("Trim"))
    }

    @Test
    fun resolutionPolicyCapsLongEdgeAt2048Pixels() {
        assertNull(AvatarMedia.dimensionError(2048, 1080))
        assertNull(AvatarMedia.dimensionError(512, 2048))
        assertTrue(AvatarMedia.dimensionError(2049, 1000)!!.contains("2048"))
        assertTrue(AvatarMedia.dimensionError(100, 3000)!!.contains("resolution"))
    }

    @Test
    fun unsupportedFormatMessagePointsAtTheSupportedSet() {
        val video = AvatarMedia.unsupportedFormatError(isVideoLike = true)
        assertTrue(video.contains("MP4"))
        val other = AvatarMedia.unsupportedFormatError(isVideoLike = false)
        assertTrue(other.contains("GIF"))
        assertTrue(other.contains("MP4"))
    }

    // ---- shared constants ------------------------------------------------

    @Test
    fun backupCapMatchesImportCapSoEveryValidAvatarIsBackupable() {
        assertEquals(AvatarMedia.MAX_ANIMATED_BYTES.toInt(), AvatarPayload.MAX_AVATAR_BYTES)
    }

    // ---- container policy: bytes decide, never the file name -------------

    @Test
    fun matroskaAndWebmBytesAreNotSniffedAsVideoAndNamesNeverMatter() {
        // Real WebM/MKV files start with the EBML magic — recognised as
        // "not ours", so they flow to the static pipeline and get the
        // unsupported-video message instead of ever reaching playback.
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(16)
        assertNull(AvatarMedia.sniff(ebml))
        assertFalse(AvatarMedia.isAnimatedPick(ebml))

        // Renamed copies: an MP4 whose NAME says .mkv still sniffs as MP4 —
        // only bytes are read, and the save path rewrites our own .mp4 name.
        // (This is exactly why renamed MP4 test files "worked" as MKV/WebM.)
        assertEquals(AvatarMedia.Format.Mp4, AvatarMedia.sniff(ftypSample("isom")))
        assertTrue(AvatarMedia.isAnimatedPick(ftypSample("isom")))
    }

    @Test
    fun unsupportedVideoNamesCoverEveryNonMp4Container() {
        for (name in listOf("mkv", "webm", "mov", "avi", "3gp", "wmv", "MPG", "WebM")) {
            assertTrue("for $name", AvatarMedia.isUnsupportedVideoName(name))
        }
        assertFalse(AvatarMedia.isUnsupportedVideoName("mp4"))
        assertFalse(AvatarMedia.isUnsupportedVideoName("jpg"))
        assertFalse(AvatarMedia.isUnsupportedVideoName(""))
    }

    // ---- animated-image URI detection (placeholder gating) ---------------

    @Test
    fun animatedImageUriDetectsGifAndWebpNames() {
        assertTrue(AvatarMedia.isAnimatedImageUri("file:///data/user/0/nazo/files/profile/a_1.gif"))
        assertTrue(AvatarMedia.isAnimatedImageUri("file:///data/user/0/nazo/files/profile/a_1.GIF"))
        assertTrue(AvatarMedia.isAnimatedImageUri("file:///data/user/0/nazo/files/profile/a_1.webp"))
        assertTrue(AvatarMedia.isAnimatedImageUri("https://example.com/pic.gif"))
        for (uri in listOf(
            "file:///data/user/0/nazo/files/profile/a_1.mp4",
            "file:///data/user/0/nazo/files/profile/a_1.jpg",
            "file:///data/user/0/nazo/files/profile/a_1",
            "emoji:cherry_blossom",
            "content://media/external/images/42",
            "",
        )) {
            assertFalse("for $uri", AvatarMedia.isAnimatedImageUri(uri))
        }
        assertFalse(AvatarMedia.isAnimatedImageUri(null))
    }

    // ---- dialog headings --------------------------------------------------

    @Test
    fun failureHeadingsAreShortAndMatchEveryValidationMessage() {
        // Policy messages (the same strings the dialog shows in full).
        assertEquals(
            "File too large",
            AvatarMedia.failureHeading(AvatarMedia.sizeError(AvatarMedia.MAX_ANIMATED_BYTES + 1)!!),
        )
        assertEquals(
            "Video too long",
            AvatarMedia.failureHeading(AvatarMedia.durationError(AvatarMedia.MAX_VIDEO_DURATION_MS + 1)!!),
        )
        assertEquals(
            "Resolution too high",
            AvatarMedia.failureHeading(AvatarMedia.dimensionError(2049, 100)!!),
        )
        assertEquals(
            "Unsupported video format",
            AvatarMedia.failureHeading(AvatarMedia.unsupportedFormatError(isVideoLike = true)),
        )
        assertEquals(
            "Unsupported format",
            AvatarMedia.failureHeading(AvatarMedia.unsupportedFormatError(isVideoLike = false)),
        )
        // Probe/store messages (kept as literals so the mapping survives any
        // wording change in one place without silently breaking the other).
        assertEquals(
            "Video couldn't be read",
            AvatarMedia.failureHeading("That video couldn't be read. It may be corrupted or empty."),
        )
        assertEquals(
            "Video couldn't be played",
            AvatarMedia.failureHeading("That video couldn't be played on this device. Try re-exporting it as an MP4."),
        )
        assertEquals(
            "File couldn't be read",
            AvatarMedia.failureHeading("That file couldn't be read. Check that it still exists and try again."),
        )
        assertEquals("Empty file", AvatarMedia.failureHeading("That file is empty."))
        assertEquals(
            "Animated WebP needs Android 9+",
            AvatarMedia.failureHeading("Animated WebP needs Android 9 or newer. Use a GIF or MP4 instead."),
        )
        // Unknown wording still yields a usable heading.
        assertEquals("Couldn't use this file", AvatarMedia.failureHeading("Something entirely new."))
    }
}
