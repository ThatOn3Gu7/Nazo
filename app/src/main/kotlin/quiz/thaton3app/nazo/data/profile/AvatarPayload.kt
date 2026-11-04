package quiz.thaton3app.nazo.data.profile

import org.json.JSONObject
import java.net.URI
import java.util.Base64

/**
 * The portable form of an accepted custom avatar inside a backup bundle.
 *
 * A backup's `stores` section carries `profile_picture_uri` as plain text, but
 * an owned avatar is a `file://` pointing INTO this app's private storage —
 * the file itself must travel with the bundle or the restored picture is
 * broken (the path is meaningless on another device, and on this one
 * [ProfileImageStore.saveAvatar] prunes old avatar files as soon as a new one
 * is saved). Schema version 2 therefore adds an optional top-level
 * `picture` object:
 *
 * ```json
 * "picture": { "mime": "image/jpeg", "data": "<base64 of the file bytes>" }
 * ```
 *
 * Everything here is pure JVM (no android.*): encode/decode with hard size
 * caps, format sniffing from magic bytes, and the two decisions — what to
 * export and what the restored URI should be — so the behaviour is unit
 * testable without Robolectric. Writing the file back to app storage is
 * [ProfileImageStore.restoreAvatar]'s job.
 */
internal object AvatarPayload {

    /**
     * Decoded-avatar cap, shared with import ([AvatarMedia.MAX_ANIMATED_BYTES]).
     *
     * Static avatars are saved at 1024 px (JPEG q92 / PNG) and land well under
     * this; animated avatars (GIF / animated WebP / MP4) are stored as-is, so
     * the cap is what bounds BOTH the import pipeline and the base64 a backup
     * has to carry (~10.7 MB worst case). Anything bigger is not one of our
     * avatar files and must not bloat every backup or get allocated on restore.
     *
     * Literal twin of [AvatarMedia.MAX_ANIMATED_BYTES] (const initializers
     * can't call `.toInt()` on it); `AvatarPayloadTest` asserts they agree.
     */
    const val MAX_AVATAR_BYTES = 8 * 1024 * 1024

    /** Base64 char cap — checked BEFORE decoding so a hostile bundle cannot make us allocate. */
    private const val MAX_AVATAR_CHARS = (MAX_AVATAR_BYTES * 4) / 3 + 16

    /** A validated avatar file. [extension] is derived from [mime] (jpg/png/gif/webp/mp4). */
    class Payload(val bytes: ByteArray, val extension: String, val mime: String) {
        override fun equals(other: Any?): Boolean =
            other is Payload &&
                bytes.contentEquals(other.bytes) &&
                extension == other.extension &&
                mime == other.mime

        override fun hashCode(): Int =
            (bytes.contentHashCode() * 31 + extension.hashCode()) * 31 + mime.hashCode()
    }

    /**
     * Avatar format from magic bytes — JPEG/PNG (what [ProfileImageStore.saveAvatar]
     * writes) plus the animated formats ([ProfileImageStore.saveAnimatedAvatar]
     * keeps as-is): GIF, animated WebP and MP4. Anything else yields null and
     * is treated as corrupt instead of being stored blindly, so a hostile or
     * mislabelled "picture" never reaches restore.
     */
    fun sniff(bytes: ByteArray): Pair<String, String>? =
        AvatarMedia.sniff(bytes)?.let { it.mime to it.extension }

    /**
     * Serializes avatar [bytes] into the bundle's `picture` object, or null
     * when they are empty, oversized, or not one of the formats above (nothing
     * worth carrying — the restore then falls back like a payload-less backup).
     */
    fun encode(bytes: ByteArray): JSONObject? {
        if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return null
        val (mime, _) = sniff(bytes) ?: return null
        return JSONObject()
            .put("mime", mime)
            .put("data", Base64.getEncoder().encodeToString(bytes))
    }

    /**
     * Parses a bundle's `picture` object back into avatar bytes.
     *
     * Lenient by design: anything wrong with the asset itself (absent, huge,
     * bad base64, wrong magic bytes, mime that disagrees with the bytes) yields
     * null — the caller keeps the rest of the restore and falls back to the
     * initials avatar for the picture. Only structural problems of the overall
     * bundle are hard errors, in [BackupRepository.parseAndValidate].
     */
    fun decode(picture: JSONObject?): Payload? {
        if (picture == null) return null
        val data = picture.optString("data", "")
        if (data.isEmpty() || data.length > MAX_AVATAR_CHARS) return null
        val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES) return null
        val (mime, extension) = sniff(bytes) ?: return null
        val declared = picture.optString("mime", "")
        if (declared.isNotEmpty() && declared != mime) return null
        return Payload(bytes, extension, mime)
    }

    /**
     * The local path of an owned `file://` avatar, or null when [value] is not
     * one (emoji:, http(s):, content:, plain text, unparseable URI).
     */
    fun localAvatarPath(value: String?): String? {
        if (value == null || !value.startsWith("file://")) return null
        return runCatching { URI(value).path }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Export side: the bundle's `picture` object for the current
     * `profile_picture_uri` value, or null when there is nothing portable to
     * carry. [readBytes] loads the avatar file (null when it is gone).
     */
    fun pictureJsonFor(prefValue: String?, readBytes: (String) -> ByteArray?): JSONObject? {
        val path = localAvatarPath(prefValue) ?: return null
        val bytes = runCatching { readBytes(path) }.getOrNull() ?: return null
        return encode(bytes)
    }

    /**
     * Restore side: what `profile_picture_uri` should be once the restore is
     * done, given the value restored from `stores` and (when a payload was
     * present) the `file://` URI of the NEW local file it was written to.
     *
     * The new file always wins, so a restored picture never depends on the
     * source device's path. Without a payload (legitimate v1 backups), a
     * `file://` value is kept only while its file exists here — same-device
     * restores keep working, anything else falls back to no picture (the
     * initials avatar) instead of a broken image. Emoji and http(s) values are
     * portable text and pass through unchanged.
     */
    fun resolveRestoredPictureUri(
        restoredValue: String?,
        newAvatarUri: String?,
        localAvatarExists: (String) -> Boolean,
    ): String? = when {
        newAvatarUri != null -> newAvatarUri
        restoredValue == null -> null
        !restoredValue.startsWith("file://") -> restoredValue
        else -> {
            val path = localAvatarPath(restoredValue)
            restoredValue.takeIf {
                path != null && runCatching { localAvatarExists(path) }.getOrDefault(false)
            }
        }
    }
}
