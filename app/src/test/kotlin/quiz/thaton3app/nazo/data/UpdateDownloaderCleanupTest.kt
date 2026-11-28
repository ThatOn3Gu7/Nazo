package quiz.thaton3app.nazo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloaderCleanupTest {

    @Test
    fun installerApkIsCleanedOnlyAfterAndroidAdvancesTheInstalledPackage() {
        assertTrue(
            shouldCleanPendingInstaller(
                baselineUpdateTime = 1_000L,
                currentUpdateTime = 1_001L,
            )
        )
        assertFalse(
            shouldCleanPendingInstaller(
                baselineUpdateTime = 1_000L,
                currentUpdateTime = 1_000L,
            )
        )
        assertFalse(
            shouldCleanPendingInstaller(
                baselineUpdateTime = 1_000L,
                currentUpdateTime = 999L,
            )
        )
        assertFalse(
            shouldCleanPendingInstaller(
                baselineUpdateTime = -1L,
                currentUpdateTime = 1_001L,
            )
        )
        assertFalse(
            shouldCleanPendingInstaller(
                baselineUpdateTime = 1_000L,
                currentUpdateTime = -1L,
            )
        )
    }
}
