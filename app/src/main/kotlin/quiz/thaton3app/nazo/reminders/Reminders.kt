package quiz.thaton3app.nazo.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.daily.DailyStore
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.data.settings.QuizStatsStore

/**
 * Streak-saver reminder (final polish pack) — OPT-IN, off by default
 * (Settings → Feedback → Daily reminder).
 *
 * A lightweight periodic worker (every 4h, no network needed — the daily
 * challenge is fully offline) that posts at most ONE notification per day,
 * only during the 17:00–22:00 local evening window, and only when today's
 * Daily Challenge hasn't been played yet. Mirrors UpdateCheckWorker's
 * permission handling and channel setup.
 */
object ReminderScheduler {

    private const val UNIQUE_WORK = "nazo_daily_reminder"
    private const val PREFS = "nazo_reminders"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            schedule(context, ExistingPeriodicWorkPolicy.UPDATE)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }

    /** Re-arms the worker on app start when the pref survived a backup/restore
     *  onto a device where the WorkManager job was never scheduled. */
    fun syncSchedule(context: Context) {
        if (isEnabled(context)) schedule(context, ExistingPeriodicWorkPolicy.KEEP)
    }

    private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            policy,
            PeriodicWorkRequestBuilder<ReminderWorker>(4, TimeUnit.HOURS).build(),
        )
    }

    internal fun markNotifiedToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong("last_notified_day", QuizStats.localEpochDay()).apply()
    }

    internal fun alreadyNotifiedToday(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("last_notified_day", -1L) == QuizStats.localEpochDay()
}

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ReminderScheduler.isEnabled(ctx)) return Result.success()
        if (DailyStore(ctx).isCompletedToday()) return Result.success()
        if (ReminderScheduler.alreadyNotifiedToday(ctx)) return Result.success()
        // Evening window only, so the 4h periodic drift never pings at odd hours.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 17 || hour >= 22) return Result.success()
        if (!canNotify()) return Result.success()

        val streak = QuizStatsStore(ctx).get().currentStreakDays
        notifyDailyPending(streak)
        ReminderScheduler.markNotifiedToday(ctx)
        return Result.success()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun notifyDailyPending(streak: Int) {
        val context = applicationContext
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Daily challenge reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (streak > 0) {
            "Keep your $streak-day streak alive — one quick round."
        } else {
            "Five quick questions — a new puzzle is up today."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("Your Daily Challenge is waiting")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "nazo_reminders"
        const val NOTIFICATION_ID = 1002
    }
}
