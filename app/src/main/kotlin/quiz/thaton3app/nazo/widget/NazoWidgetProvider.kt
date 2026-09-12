package quiz.thaton3app.nazo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.daily.DailyChallenge
import quiz.thaton3app.nazo.daily.DailyStore
import quiz.thaton3app.nazo.data.settings.QuizStatsStore

/**
 * Home-screen widget: current streak + today's Daily Challenge status, tap to
 * open the app. Classic RemoteViews — zero new dependencies. Data comes
 * straight from the existing SharedPreferences stores; NazoApp pings
 * [refreshAll] whenever a game or the daily finishes, and the periodic update
 * handles day rollover while idle.
 *
 * ## Behaviour after "Clear data" (platform limitation — read before changing)
 *
 * The launcher caches the last [RemoteViews] it was handed and keeps drawing
 * them. When the user clears Nazo's data the stores empty, but nothing tells
 * the widget, so it can keep showing a stale streak.
 *
 * Android does **not** deliver `ACTION_PACKAGE_DATA_CLEARED` to the package
 * whose own data was cleared — it goes to *other* apps. A self-targeted
 * receiver for it will never fire, so do not "fix" this by adding one.
 * Clearing data also cancels the widget's scheduled `updatePeriodMillis`
 * alarm, so even the periodic refresh stops until something re-arms it.
 *
 * The supported recovery paths, all of which are wired up:
 *
 *  - **App launch** — `MainActivity.onCreate` calls [refreshAll]. This is the
 *    earliest code of ours that can run after a clear, and it repaints from the
 *    now-empty stores.
 *  - **`onEnabled` / `onUpdate`** — a widget that is added, or re-added, or
 *    updated by the host renders from current storage; there is no cached state
 *    inside the provider itself.
 *  - **Periodic update** — re-armed by the system once the app runs again.
 *
 * [render] reads the stores on every call and never caches, so a cleared state
 * naturally produces the zero-streak / "Daily Challenge ready" default.
 *
 * What is genuinely NOT possible: repainting the widget at the instant data is
 * cleared while the app never runs again. No supported callback exists for
 * that, and OEM launchers vary in how aggressively they refresh. The window is
 * "until the next app launch or host update", which the above minimises.
 */
class NazoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    /**
     * First widget placed. Rendering here means a widget added right after a
     * data clear shows the fresh default immediately, rather than waiting for
     * the first periodic update.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context)
    }

    /**
     * Sent after the host restores widgets from a backup, where the restored
     * ids refer to state that may no longer exist. Re-render so restored
     * widgets never show another install's numbers.
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        refreshAll(context)
    }

    companion object {

        /** No-op when no widgets are placed — safe to call after every game. */
        fun refreshAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, NazoWidgetProvider::class.java)
                )
                ids.forEach { render(context, manager, it) }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val stats = QuizStatsStore(context).get()
            val daily = DailyStore(context)
            val done = daily.isCompletedToday()

            val views = RemoteViews(context.packageName, R.layout.widget_nazo)
            val streak = stats.currentStreakDays
            views.setTextViewText(
                R.id.widget_streak,
                if (streak > 0) "\uD83D\uDD25 $streak-day streak" else "Start a streak today",
            )
            views.setTextViewText(
                R.id.widget_daily,
                if (done) {
                    "Daily cleared ${daily.lastScore()}/${DailyChallenge.QUESTION_COUNT} ✓"
                } else {
                    "Daily Challenge ready →"
                },
            )
            views.setTextColor(
                R.id.widget_daily,
                if (done) Color.parseColor("#7FD8A4") else Color.parseColor("#A8B8AE"),
            )
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
