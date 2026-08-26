package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Local profile: the user's display name and selected profile picture.
 * `profilePictureUri` is either null (initials), "emoji:<symbol>", a remote
 * image URL, or a content:// gallery URI.
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

    private companion object {
        const val KEY_USERNAME = "profile_username"
        const val KEY_PICTURE = "profile_picture_uri"
    }
}
