package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowBuildBrowserTest {

    @Test
    fun buildEntriesUseOneFastBoundedStagger() {
        assertEquals(0, workflowBuildEntryDelayMillis(-1))
        assertEquals(0, workflowBuildEntryDelayMillis(0))
        assertEquals(24, workflowBuildEntryDelayMillis(1))
        assertEquals(120, workflowBuildEntryDelayMillis(5))
        assertEquals(120, workflowBuildEntryDelayMillis(30))
    }

    @Test
    fun installVisualPhaseChangesOnlyWhenTheSelectedRunsPhaseChanges() {
        assertEquals(
            WorkflowInstallVisualPhase.DownloadAction,
            workflowInstallVisualPhase(
                WorkflowInstallState.Idle,
                runId = 10L,
                releaseAvailable = true,
            ),
        )
        assertEquals(
            WorkflowInstallVisualPhase.Unavailable,
            workflowInstallVisualPhase(
                WorkflowInstallState.Idle,
                runId = 10L,
                releaseAvailable = false,
            ),
        )

        val downloading = WorkflowInstallState.Downloading(
            runId = 10L,
            downloaded = 50L,
            total = 100L,
        )
        assertEquals(
            WorkflowInstallVisualPhase.Downloading,
            workflowInstallVisualPhase(downloading, runId = 10L, releaseAvailable = true),
        )
        assertEquals(
            WorkflowInstallVisualPhase.DownloadAction,
            workflowInstallVisualPhase(downloading, runId = 11L, releaseAvailable = true),
        )

        assertEquals(
            WorkflowInstallVisualPhase.Verifying,
            workflowInstallVisualPhase(
                WorkflowInstallState.Verifying(runId = 10L),
                runId = 10L,
                releaseAvailable = true,
            ),
        )
        assertEquals(
            WorkflowInstallVisualPhase.Ready,
            workflowInstallVisualPhase(
                WorkflowInstallState.Ready(
                    runId = 10L,
                    versionName = "snapshot",
                    versionCode = 15L,
                ),
                runId = 10L,
                releaseAvailable = true,
            ),
        )
        assertEquals(
            WorkflowInstallVisualPhase.Failed,
            workflowInstallVisualPhase(
                WorkflowInstallState.Failed(runId = 10L, message = "failed"),
                runId = 10L,
                releaseAvailable = true,
            ),
        )
    }
}
