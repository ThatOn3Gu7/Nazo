package quiz.thaton3app.nazo.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.BackupRepository

/**
 * Writes the current data bundle to the app-external auto-backup file and stamps
 * the last-backup time. Scheduled (daily/weekly) via [BackupScheduler].
 */
class BackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            BackupRepository.exportToPath(
                applicationContext,
                BackupRepository.autoBackupPath(applicationContext),
            )
            BackupPrefs(applicationContext).lastBackupEpoch = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
