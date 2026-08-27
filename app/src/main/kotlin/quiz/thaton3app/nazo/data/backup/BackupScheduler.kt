package quiz.thaton3app.nazo.data.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Mirrors [quiz.thaton3app.nazo.data.UpdateScheduler]: keeps a single unique
 * periodic backup work enqueued according to the chosen frequency. "off" cancels it.
 */
object BackupScheduler {

    private const val UNIQUE_WORK = "nazo_backup"

    fun apply(context: Context, frequency: String) {
        val wm = WorkManager.getInstance(context)
        when (frequency) {
            "daily" -> wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build(),
            )
            "weekly" -> wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
            )
            else -> wm.cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
