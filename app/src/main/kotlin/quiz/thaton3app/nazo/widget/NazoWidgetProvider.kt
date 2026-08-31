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
 * Home-screen widget (final polish pack): current streak + today's Daily
 * Challenge status, tap to open the app. Classic RemoteViews — zero new
 * dependencies. Data comes straight from the existing SharedPreferences
 * stores; NazoApp pings [refreshAll] whenever a game or the daily finishes,
 * and the 30-min updatePeriodMillis handles day rollover while idle.
 */
class NazoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
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
