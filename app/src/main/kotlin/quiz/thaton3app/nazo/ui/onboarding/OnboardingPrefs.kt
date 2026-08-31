package quiz.thaton3app.nazo.ui.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * First-launch flag for the onboarding overlay. Its own tiny store
 * ("nazo_onboarding") so the onboarding feature stays fully self-contained
 * in this package (nothing added to ThemePreferences). Included in backups
 * is NOT desired: a restored device should re-show onboarding only if the
 * app is genuinely fresh, and BackupRepository only reads its known stores,
 * so this one is naturally excluded.
 */
class OnboardingPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_onboarding", Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean("completed", false)
        set(value) = prefs.edit().putBoolean("completed", value).apply()
}
