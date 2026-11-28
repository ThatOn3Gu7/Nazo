package quiz.thaton3app.nazo.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val WORKFLOW_BUILD_FILE = "PR-assemble.yml"
const val WORKFLOW_RELEASE_ARTIFACT = "nazo-release-apk"
const val WORKFLOW_DEBUG_ARTIFACT = "nazo-debug-apk"
private const val WORKFLOW_RUN_LIMIT = 30

/** One successful compile-check run that can have short-lived APK artifacts. */
data class WorkflowBuild(
    val runId: Long,
    val runNumber: Long,
    val runAttempt: Int,
    val title: String,
    val branch: String,
    val commitSha: String,
    val createdAt: String,
    val htmlUrl: String,
    val artifacts: List<WorkflowBuildArtifact>,
)

data class WorkflowBuildArtifact(
    val artifactId: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean,
    val expiresAt: String,
    val downloadUrl: String,
)

/**
 * Loads recent successful compile-checks and joins them with their artifacts.
 *
 * This is deliberately manual-only. Release/beta update scheduling remains on
 * the Releases API, while this catalog is opened explicitly by a tester.
 */
suspend fun fetchWorkflowBuilds(
    repo: String = GITHUB_REPO,
): List<WorkflowBuild> = withContext(Dispatchers.IO) {
    val encodedWorkflow = WORKFLOW_BUILD_FILE.replace("/", "%2F")
    val runsUrl = "https://api.github.com/repos/$repo/actions/workflows/" +
        "$encodedWorkflow/runs?status=success&per_page=$WORKFLOW_RUN_LIMIT"
    val artifactsUrl = "https://api.github.com/repos/$repo/actions/artifacts?per_page=100"
    val runsJson = fetchGithubJson(runsUrl)
    val artifactsJson = fetchGithubJson(artifactsUrl)
    parseWorkflowBuildCatalog(
        repo = repo,
        runsJson = runsJson,
        artifactsJson = artifactsJson,
    )
}

private fun fetchGithubJson(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        githubUpdateRequestHeaders("application/vnd.github+json").forEach { (name, value) ->
            setRequestProperty(name, value)
        }
        connectTimeout = 15_000
        readTimeout = 20_000
    }
    return try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            val remaining = connection.getHeaderField("X-RateLimit-Remaining")
            val message = if (connection.responseCode == 403 && remaining == "0") {
                "GitHub's public API limit is temporarily exhausted"
            } else {
                "GitHub returned HTTP ${connection.responseCode}"
            }
            throw IOException(message)
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** Pure parser kept separate from networking for deterministic unit coverage. */
internal fun parseWorkflowBuildCatalog(
    repo: String,
    runsJson: String,
    artifactsJson: String,
): List<WorkflowBuild> {
    val artifactsByRun = parseWorkflowArtifacts(repo, JSONObject(artifactsJson))
    val runs = JSONObject(runsJson).optJSONArray("workflow_runs") ?: JSONArray()
    return buildList {
        for (index in 0 until runs.length()) {
            val run = runs.optJSONObject(index) ?: continue
            val runId = run.optLong("id", -1L)
            if (runId <= 0L || run.optString("status") != "completed" ||
                run.optString("conclusion") != "success"
            ) {
                continue
            }
            val sha = run.optString("head_sha").trim()
            if (!sha.matches(Regex("[0-9a-fA-F]{40}"))) continue
            add(
                WorkflowBuild(
                    runId = runId,
                    runNumber = run.optLong("run_number", 0L).coerceAtLeast(0L),
                    runAttempt = run.optInt("run_attempt", 1).coerceAtLeast(1),
                    title = run.optString("display_title").trim().ifBlank { "Successful workflow" },
                    branch = run.optString("head_branch").trim().ifBlank { "Unknown branch" },
                    commitSha = sha.lowercase(),
                    createdAt = run.optString("created_at").trim(),
                    // Construct trusted destinations rather than accepting URLs from JSON.
                    htmlUrl = "https://github.com/$repo/actions/runs/$runId",
                    artifacts = artifactsByRun[runId].orEmpty().sortedBy { artifact ->
                        when (artifact.name) {
                            WORKFLOW_RELEASE_ARTIFACT -> 0
                            WORKFLOW_DEBUG_ARTIFACT -> 1
                            else -> 2
                        }
                    },
                )
            )
        }
    }
}

private fun parseWorkflowArtifacts(
    repo: String,
    response: JSONObject,
): Map<Long, List<WorkflowBuildArtifact>> {
    val artifacts = response.optJSONArray("artifacts") ?: JSONArray()
    val byRun = mutableMapOf<Long, MutableList<WorkflowBuildArtifact>>()
    for (index in 0 until artifacts.length()) {
        val artifact = artifacts.optJSONObject(index) ?: continue
        val name = artifact.optString("name").trim()
        if (name != WORKFLOW_RELEASE_ARTIFACT && name != WORKFLOW_DEBUG_ARTIFACT) continue
        val artifactId = artifact.optLong("id", -1L)
        val runId = artifact.optJSONObject("workflow_run")?.optLong("id", -1L) ?: -1L
        if (artifactId <= 0L || runId <= 0L) continue
        byRun.getOrPut(runId) { mutableListOf() }.add(
            WorkflowBuildArtifact(
                artifactId = artifactId,
                name = name,
                sizeBytes = artifact.optLong("size_in_bytes", -1L),
                expired = artifact.optBoolean("expired", false),
                expiresAt = artifact.optString("expires_at").trim(),
                downloadUrl = "https://api.github.com/repos/$repo/actions/artifacts/$artifactId/zip",
            )
        )
    }
    return byRun
}

internal fun WorkflowBuild.releaseArtifact(): WorkflowBuildArtifact? =
    artifacts.firstOrNull { it.name == WORKFLOW_RELEASE_ARTIFACT }

internal fun WorkflowBuild.debugArtifact(): WorkflowBuildArtifact? =
    artifacts.firstOrNull { it.name == WORKFLOW_DEBUG_ARTIFACT }

internal fun newestInstallableWorkflowBuild(builds: List<WorkflowBuild>): WorkflowBuild? =
    builds.firstOrNull { build -> build.releaseArtifact()?.expired == false }

internal fun workflowVersionCanReplace(installedVersionCode: Long, candidateVersionCode: Long): Boolean =
    candidateVersionCode >= installedVersionCode
