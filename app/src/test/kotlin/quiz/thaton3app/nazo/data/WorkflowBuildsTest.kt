package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowBuildsTest {

    @Test
    fun catalogKeepsSuccessfulRunsAndJoinsOnlyKnownApkArtifacts() {
        val runs = """
            {
              "workflow_runs": [
                {
                  "id": 101,
                  "run_number": 44,
                  "run_attempt": 1,
                  "display_title": "feat(quiz): tether explanations",
                  "head_branch": "arena/test",
                  "head_sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "created_at": "2026-11-04T10:00:00Z",
                  "status": "completed",
                  "conclusion": "success"
                },
                {
                  "id": 102,
                  "run_number": 45,
                  "run_attempt": 1,
                  "display_title": "broken build",
                  "head_branch": "arena/test",
                  "head_sha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "created_at": "2026-11-04T11:00:00Z",
                  "status": "completed",
                  "conclusion": "failure"
                }
              ]
            }
        """.trimIndent()
        val artifacts = """
            {
              "artifacts": [
                {
                  "id": 501,
                  "name": "nazo-debug-apk",
                  "size_in_bytes": 30000000,
                  "expired": false,
                  "expires_at": "2026-11-18T10:00:00Z",
                  "workflow_run": { "id": 101 }
                },
                {
                  "id": 500,
                  "name": "nazo-release-apk",
                  "size_in_bytes": 8000000,
                  "expired": false,
                  "expires_at": "2026-11-18T10:00:00Z",
                  "workflow_run": { "id": 101 }
                },
                {
                  "id": 999,
                  "name": "unrelated-report",
                  "size_in_bytes": 10,
                  "expired": false,
                  "workflow_run": { "id": 101 }
                }
              ]
            }
        """.trimIndent()

        val catalog = parseWorkflowBuildCatalog("ThatOn3Gu7/Nazo", runs, artifacts)

        assertEquals(1, catalog.size)
        val build = catalog.single()
        assertEquals(101L, build.runId)
        assertEquals("aaaaaaa", build.commitSha.take(7))
        assertEquals(
            "https://github.com/ThatOn3Gu7/Nazo/actions/runs/101",
            build.htmlUrl,
        )
        assertEquals(WORKFLOW_RELEASE_ARTIFACT, build.artifacts.first().name)
        assertEquals(500L, build.releaseArtifact()?.artifactId)
        assertEquals(501L, build.debugArtifact()?.artifactId)
        assertEquals(
            "https://api.github.com/repos/ThatOn3Gu7/Nazo/actions/artifacts/500/zip",
            build.releaseArtifact()?.downloadUrl,
        )
    }

    @Test
    fun expiredReleaseNeverBecomesAnInstallableFallback() {
        val expired = build(runId = 1L, expired = true)
        val unavailable = build(runId = 2L, withRelease = false)
        val available = build(runId = 3L, expired = false)

        assertEquals(available, newestInstallableWorkflowBuild(listOf(expired, unavailable, available)))
        assertNull(newestInstallableWorkflowBuild(listOf(expired, unavailable)))
        assertTrue(expired.releaseArtifact()?.expired == true)
    }

    @Test
    fun workflowSnapshotsCanReplaceEqualOrNewerVersionCodesButNeverDowngrade() {
        assertTrue(workflowVersionCanReplace(installedVersionCode = 14L, candidateVersionCode = 14L))
        assertTrue(workflowVersionCanReplace(installedVersionCode = 14L, candidateVersionCode = 15L))
        assertFalse(workflowVersionCanReplace(installedVersionCode = 15L, candidateVersionCode = 14L))
    }

    private fun build(
        runId: Long,
        expired: Boolean = false,
        withRelease: Boolean = true,
    ): WorkflowBuild = WorkflowBuild(
        runId = runId,
        runNumber = runId,
        runAttempt = 1,
        title = "Build $runId",
        branch = "arena/test",
        commitSha = runId.toString().padStart(40, 'a').takeLast(40),
        createdAt = "2026-11-04T10:00:00Z",
        htmlUrl = "https://example.test/$runId",
        artifacts = if (withRelease) {
            listOf(
                WorkflowBuildArtifact(
                    artifactId = runId,
                    name = WORKFLOW_RELEASE_ARTIFACT,
                    sizeBytes = 10L,
                    expired = expired,
                    expiresAt = "",
                    downloadUrl = "https://example.test/$runId.zip",
                )
            )
        } else {
            emptyList()
        },
    )
}
