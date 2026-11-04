package quiz.thaton3app.nazo.data.profile

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.settings.BackupRepository
import java.io.File
import java.util.Base64

/**
 * Profile-picture portability contract tests (backup schema version 2):
 *
 *  - an accepted custom avatar is exported as the bundle's `picture` payload
 *    and restored to a NEW local file whose URI replaces the stored path;
 *  - missing, corrupt or oversized image data falls back safely instead of
 *    failing the restore;
 *  - legitimate version-1 backups (no payload at all) still restore.
 *
 * Export decisions, restore decisions and the file/prune mechanics are all
 * exercised here; the Context-bound glue (ProfileImageStore.restoreAvatar)
 * is a two-line wrapper around writeAvatarFile.
 */
class AvatarPayloadTest {

    // ---- fixtures --------------------------------------------------------

    /** JPEG-magic bytes + filler: enough for sniffing/transport tests. */
    private fun jpegSample(size: Int = 256): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
            ByteArray(size) { it.toByte() }

    private fun pngSample(size: Int = 256): ByteArray =
        byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        ) + ByteArray(size) { it.toByte() }

    /** GIF magic bytes + filler: enough for sniffing/transport tests. */
    private fun gifSample(size: Int = 256): ByteArray =
        "GIF89a".toByteArray() + ByteArray(size) { it.toByte() }

    /** ISO-BMFF `ftyp` header (major brand `isom`) + filler: an MP4 sample. */
    private fun mp4Sample(size: Int = 256): ByteArray =
        byteArrayOf(0, 0, 0, 0x18) + "ftyp".toByteArray() + "isom".toByteArray() +
            ByteArray(size) { it.toByte() }

    private fun bundleWith(picture: JSONObject?, version: Int = 2): String {
        val root = JSONObject()
            .put("version", version)
            .put("createdAt", 1728000000000L)
            .put("stores", JSONObject().put("nazo_profile", JSONObject().put(
                "profile_picture_uri",
                JSONObject().put("t", "string").put("v", "file:///data/user/0/nazo/files/profile/avatar_old.jpg"),
            )))
        if (picture != null) root.put("picture", picture)
        return root.toString()
    }

    // ---- format sniffing / encode / decode -------------------------------

    @Test
    fun sniffRecognisesJpegPngAndAnimatedFormats() {
        assertEquals("image/jpeg" to "jpg", AvatarPayload.sniff(jpegSample()))
        assertEquals("image/png" to "png", AvatarPayload.sniff(pngSample()))
        assertEquals("image/gif" to "gif", AvatarPayload.sniff(gifSample()))
        assertEquals("video/mp4" to "mp4", AvatarPayload.sniff(mp4Sample()))
        assertNull(AvatarPayload.sniff(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(AvatarPayload.sniff(ByteArray(0)))
    }

    @Test
    fun encodeDecodeRoundTripsAvatarBytes() {
        for (sample in listOf(jpegSample(), pngSample(), gifSample(), mp4Sample())) {
            val json = AvatarPayload.encode(sample)!!
            val payload = AvatarPayload.decode(json)!!
            assertTrue(sample.contentEquals(payload.bytes))
            assertEquals(AvatarPayload.sniff(sample)!!.first, payload.mime)
            assertEquals(AvatarPayload.sniff(sample)!!.second, payload.extension)
        }
    }

    @Test
    fun encodeRejectsEmptyOversizedAndUnrecognisedBytes() {
        assertNull(AvatarPayload.encode(ByteArray(0)))
        assertNull(AvatarPayload.encode(ByteArray(AvatarPayload.MAX_AVATAR_BYTES + 1) { 0xFF.toByte() }))
        assertNull(AvatarPayload.encode(byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9, 9)))
    }

    @Test
    fun decodeRejectsCorruptOrHostilePictureObjects() {
        // Not base64 at all.
        assertNull(AvatarPayload.decode(JSONObject().put("data", "!!! not base64 !!!")))
        // Well-formed base64 of bytes that are not an image.
        assertNull(
            AvatarPayload.decode(
                JSONObject().put("data", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
            )
        )
        // Magic bytes disagree with the declared mime.
        assertNull(
            AvatarPayload.decode(
                JSONObject()
                    .put("mime", "image/png")
                    .put("data", Base64.getEncoder().encodeToString(jpegSample()))
            )
        )
        // Absent payload and absurdly long payload both fail safe.
        assertNull(AvatarPayload.decode(null))
        assertNull(AvatarPayload.decode(JSONObject()))
        assertNull(AvatarPayload.decode(JSONObject().put("data", "A".repeat(6_000_000))))
    }

    // ---- export decision -------------------------------------------------

    @Test
    fun exportCarriesTheOwnedAvatarFile() {
        val bytes = jpegSample()
        val json = AvatarPayload.pictureJsonFor("file:///data/user/0/nazo/files/profile/avatar_1.jpg") {
            assertEquals("/data/user/0/nazo/files/profile/avatar_1.jpg", it)
            bytes
        }
        assertNotNull(json)
        assertTrue(bytes.contentEquals(AvatarPayload.decode(json!!)!!.bytes))
    }

    @Test
    fun exportCarriesAnAnimatedAvatarFile() {
        // A GIF avatar is owned bytes like any other — the bundle must carry
        // it verbatim, or a backup/restore silently loses the animation.
        val bytes = gifSample()
        val json = AvatarPayload.pictureJsonFor("file:///data/user/0/nazo/files/profile/avatar_9.gif") { bytes }
        assertNotNull(json)
        val payload = AvatarPayload.decode(json!!)!!
        assertTrue(bytes.contentEquals(payload.bytes))
        assertEquals("gif", payload.extension)
        assertEquals("image/gif", payload.mime)
    }

    @Test
    fun exportSkipsNonFilePicturesMissingFilesAndOversizedFiles() {
        // content:// (gallery), emoji and remote pictures carry no file to pack.
        assertNull(AvatarPayload.pictureJsonFor("content://media/external/images/42") { jpegSample() })
        assertNull(AvatarPayload.pictureJsonFor("emoji:cherry_blossom") { jpegSample() })
        assertNull(AvatarPayload.pictureJsonFor("https://example.com/me.png") { jpegSample() })
        assertNull(AvatarPayload.pictureJsonFor(null) { jpegSample() })
        // The avatar file is gone (or unreadable): nothing to carry.
        assertNull(AvatarPayload.pictureJsonFor("file:///data/user/0/nazo/files/profile/avatar_x.jpg") { null })
        assertNull(
            AvatarPayload.pictureJsonFor("file:///data/user/0/nazo/files/profile/avatar_x.jpg") {
                throw java.io.FileNotFoundException()
            }
        )
        // Sensible backup sizes: oversized files are not packed.
        assertNull(
            AvatarPayload.pictureJsonFor("file:///data/user/0/nazo/files/profile/avatar_big.png") {
                ByteArray(AvatarPayload.MAX_AVATAR_BYTES + 1)
            }
        )
    }

    // ---- restore decision (missing-image fallback) -----------------------

    @Test
    fun restoredPictureUsesTheNewLocalFileNeverTheSourcePath() {
        assertEquals(
            "file:///data/user/0/nazo/files/profile/avatar_new.jpg",
            AvatarPayload.resolveRestoredPictureUri(
                restoredValue = "file:///data/user/0/OTHER/files/profile/avatar_old.jpg",
                newAvatarUri = "file:///data/user/0/nazo/files/profile/avatar_new.jpg",
                localAvatarExists = { false },
            ),
        )
    }

    @Test
    fun legacyV1RestoreKeepsThePictureOnlyWhileItsFileStillExists() {
        // Same-device restore of a v1 backup: the file survived (no newer
        // avatar was saved) and the old value keeps working.
        assertEquals(
            "file:///data/user/0/nazo/files/profile/avatar_old.jpg",
            AvatarPayload.resolveRestoredPictureUri(
                restoredValue = "file:///data/user/0/nazo/files/profile/avatar_old.jpg",
                newAvatarUri = null,
                localAvatarExists = { it == "/data/user/0/nazo/files/profile/avatar_old.jpg" },
            ),
        )
        // Cross-device restore of a v1 backup: no file, no payload → fall back
        // to no picture (the initials avatar) instead of a broken image.
        assertNull(
            AvatarPayload.resolveRestoredPictureUri(
                restoredValue = "file:///data/user/0/OTHER/files/profile/avatar_old.jpg",
                newAvatarUri = null,
                localAvatarExists = { false },
            ),
        )
    }

    @Test
    fun portablePictureValuesPassThroughUnchanged() {
        for (value in listOf("emoji:cherry_blossom", "https://example.com/me.png")) {
            assertEquals(
                value,
                AvatarPayload.resolveRestoredPictureUri(value, null) { false },
            )
        }
        assertNull(AvatarPayload.resolveRestoredPictureUri(null, null) { false })
    }

    // ---- file mechanics: new file + preserved cleanup behaviour ----------

    @Test
    fun writeAvatarFileWritesNewAvatarAndPrunesOldOnesButNeverDrafts() {
        val root = File.createTempFile("nazo_av", "").apply { delete(); mkdirs() }
        try {
            val avatars = File(root, "profile").apply { mkdirs() }
            val drafts = File(root, "profile_drafts").apply { mkdirs() }
            val old = File(avatars, "avatar_1.jpg").apply { writeText("old") }
            val draft = File(drafts, "draft_1").apply { writeText("draft") }

            val bytes = jpegSample()
            val written = ProfileImageStore.writeAvatarFile(avatars, bytes, "jpg")

            assertTrue(written.exists())
            assertTrue(bytes.contentEquals(written.readBytes()))
            assertTrue(written.name.startsWith("avatar_"))
            assertTrue(written.name.endsWith(".jpg"))
            // Same cleanup rule as saveAvatar: only the newest avatar remains.
            assertFalse(old.exists())
            // Drafts live elsewhere and are untouched.
            assertTrue(draft.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writeAvatarFileAcceptsAnimatedExtensions() {
        val root = File.createTempFile("nazo_av_anim", "").apply { delete(); mkdirs() }
        try {
            val avatars = File(root, "profile").apply { mkdirs() }
            val old = File(avatars, "avatar_1.jpg").apply { writeText("old") }

            val bytes = mp4Sample()
            val written = ProfileImageStore.writeAvatarFile(avatars, bytes, "mp4")

            assertTrue(written.exists())
            assertTrue(written.name.endsWith(".mp4"))
            assertTrue(bytes.contentEquals(written.readBytes()))
            // The prune rule is format-agnostic: only the newest avatar remains.
            assertFalse(old.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    // ---- schema / parse integration -------------------------------------

    @Test
    fun parseCarriesThePicturePayloadForV2Bundles() {
        val parsed = BackupRepository.parseAndValidate(bundleWith(AvatarPayload.encode(pngSample())))
        assertNotNull(parsed.picture)
        assertEquals("png", parsed.picture!!.extension)
        assertEquals("file:///data/user/0/nazo/files/profile/avatar_old.jpg",
            parsed.stores.getValue("nazo_profile").getValue("profile_picture_uri").second)
    }

    @Test
    fun parseCarriesAnAnimatedPicturePayload() {
        val parsed = BackupRepository.parseAndValidate(bundleWith(AvatarPayload.encode(mp4Sample())))
        assertNotNull(parsed.picture)
        assertEquals("mp4", parsed.picture!!.extension)
        assertEquals("video/mp4", parsed.picture!!.mime)
    }

    @Test
    fun prePictureV1BundlesStillParseWithoutAPayload() {
        val parsed = BackupRepository.parseAndValidate(bundleWith(picture = null, version = 1))
        assertNull(parsed.picture)
        assertTrue(parsed.stores.containsKey("nazo_profile"))
    }

    @Test
    fun corruptPictureDataDoesNotBreakTheRestOfTheRestore() {
        val parsed = BackupRepository.parseAndValidate(
            bundleWith(JSONObject().put("data", "!!! not base64 !!!"))
        )
        assertNull(parsed.picture)
        // The stores — including the picture URI — still restore.
        assertTrue(parsed.stores.containsKey("nazo_profile"))
    }

    @Test
    fun fullRestoreChainPutsThePictureAtAWholeNewPath() {
        val sourceBytes = pngSample(512)
        val parsed = BackupRepository.parseAndValidate(bundleWith(AvatarPayload.encode(sourceBytes)))
        val payload = parsed.picture!!
        val restoredValue = parsed.stores.getValue("nazo_profile")
            .getValue("profile_picture_uri").second as String

        val targetDir = File.createTempFile("nazo_av2", "").apply { delete(); mkdirs() }
        try {
            val newFile = ProfileImageStore.writeAvatarFile(targetDir, payload.bytes, payload.extension)
            val resolved = AvatarPayload.resolveRestoredPictureUri(
                restoredValue,
                "file://${newFile.absolutePath}",
            ) { File(it).exists() }

            // The stored URI is the NEW local file, not the source device's path.
            assertEquals("file://${newFile.absolutePath}", resolved)
            assertFalse(resolved == restoredValue)
            assertTrue(sourceBytes.contentEquals(File(resolved!!.removePrefix("file://")).readBytes()))
        } finally {
            targetDir.deleteRecursively()
        }
    }
}
