package quiz.thaton3app.nazo.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.BackupRepository
import quiz.thaton3app.nazo.data.settings.toLastBackup

/**
 * Writes the current data bundle to the app-external auto-backup file and stamps
 * the last-backup time. Scheduled (daily/weekly) via [BackupScheduler].
 */
class BackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val receipt = BackupRepository.exportToPath(
                applicationContext,
                BackupRepository.autoBackupPath(applicationContext),
            )
            // Cache what was written, measured during the write, so the Last
            // Backup card can describe an automatic backup without reopening it.
            BackupPrefs(applicationContext).lastBackup = receipt.toLastBackup()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
