package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRestoreStatusTest {

    @Test
    fun instantAutoBackupReadKeepsStatusVisibleForFullMinimum() {
        assertEquals(2_000L, remainingAutoBackupReadStatusMillis(elapsedMillis = 0L))
    }

    @Test
    fun quickAutoBackupReadWaitsOnlyForRemainingMinimum() {
        assertEquals(1_250L, remainingAutoBackupReadStatusMillis(elapsedMillis = 750L))
    }

    @Test
    fun slowAutoBackupReadAddsNoDelay() {
        assertEquals(0L, remainingAutoBackupReadStatusMillis(elapsedMillis = 2_500L))
    }

    @Test
    fun invalidNegativeElapsedTimeCannotExtendTheMinimum() {
        assertEquals(2_000L, remainingAutoBackupReadStatusMillis(elapsedMillis = -1L))
    }
}
