package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Local profile: the user's display name and selected profile picture.
 * `profilePictureUri` is either null (initials), "emoji:<symbol>", a remote
 * image URL, a content:// gallery URI, or a `file://` into this app's own
 * avatar storage — the latter holds whatever the pick pipeline saved: a
 * cropped photo (jpg/png) or an animated avatar kept as-is (gif/webp/mp4).
 */
class ProfilePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_profile", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var profilePictureUri: String?
        get() = prefs.getString(KEY_PICTURE, null)
        set(value) = prefs.edit().putString(KEY_PICTURE, value).apply()

    /**
     * Profile cover banner image: null (no stored photo) or a `file://`
     * into this app's own cover storage. Gallery picks only — the value is
     * always a file we own, never a content:// (permissions lapse) and never
     * a network URL. Kept independent of [coverPresetId]: choosing a built-in
     * preset never touches this, so the custom photo survives switching.
     */
    var coverPictureUri: String?
        get() = prefs.getString(KEY_COVER, null)
        set(value) = prefs.edit().putString(KEY_COVER, value).apply()

    /**
     * Selected built-in cover preset id (`photo_*` / `gradient_*`), or null
     * for the gallery/default flow. Resolution order lives in
     * `resolveCoverVisual` (preset > gallery > placeholder); an unknown id
     * (e.g. after a downgrade) simply falls back instead of breaking.
     */
    var coverPresetId: String?
        get() = prefs.getString(KEY_COVER_PRESET, null)
        set(value) = prefs.edit().putString(KEY_COVER_PRESET, value).apply()

    private companion object {
        const val KEY_USERNAME = "profile_username"
        const val KEY_PICTURE = "profile_picture_uri"
        const val KEY_COVER = "profile_cover_uri"
        const val KEY_COVER_PRESET = "profile_cover_preset"
    }
}
