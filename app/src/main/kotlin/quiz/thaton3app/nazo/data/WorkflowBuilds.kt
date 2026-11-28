package quiz.thaton3app.nazo.data

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val WORKFLOW_BUILD_FILE = "PR-assemble.yml"
const val WORKFLOW_RELEASE_ARTIFACT = "nazo-release-apk"
const val WORKFLOW_SNAPSHOT_TAG = "workflow-snapshots"
private const val WORKFLOW_RUN_LIMIT = 30
private const val WORKFLOW_CACHE_PREFS = "nazo_workflow_build_cache"
private const val CACHE_RUNS_JSON = "runs_json"
private const val CACHE_SNAPSHOTS_JSON = "snapshots_json"
private const val CACHE_FETCHED_AT = "fetched_at"
internal const val WORKFLOW_CACHE_MAX_AGE_MS = 15L * 60L * 1_000L
private val WORKFLOW_SNAPSHOT_NAME =
    Regex("^Nazo-workflow-(\\d+)-([0-9a-fA-F]{7,40})\\.zip$")
private val COMMIT_SHA = Regex("[0-9a-fA-F]{40}")

/** One successful compile-check run and its optional public installer snapshot. */
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

data class WorkflowBuildCatalog(
    val builds: List<WorkflowBuild>,
    val fetchedAtMillis: Long,
    val fromCache: Boolean,
    val stale: Boolean,
)

/** Reads the small cached catalog synchronously so reopening About paints immediately. */
fun readCachedWorkflowBuildCatalog(
    context: Context,
    repo: String = GITHUB_REPO,
    nowMillis: Long = System.currentTimeMillis(),
): WorkflowBuildCatalog? {
    val prefs = context.applicationContext.getSharedPreferences(
        WORKFLOW_CACHE_PREFS,
        Context.MODE_PRIVATE,
    )
    val runs = prefs.getString(CACHE_RUNS_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
    val snapshots = prefs.getString(CACHE_SNAPSHOTS_JSON, null)
        ?.takeIf { it.isNotBlank() } ?: return null
    val fetchedAt = prefs.getLong(CACHE_FETCHED_AT, 0L)
    return runCatching {
        WorkflowBuildCatalog(
            builds = parseWorkflowBuildCatalog(repo, runs, snapshots),
            fetchedAtMillis = fetchedAt,
            fromCache = true,
            stale = !workflowCacheIsFresh(fetchedAt, nowMillis),
        )
    }.getOrNull()
}

/**
 * Returns a small disk-cached catalog, refreshing it at most every 15 minutes.
 *
 * One request lists 30 successful runs and one request lists the public rolling
 * snapshot release. There is never one request per run. A stale cache remains
 * usable when GitHub is temporarily unavailable or rate-limited.
 */
suspend fun loadWorkflowBuildCatalog(
    context: Context,
    forceRefresh: Boolean = false,
    repo: String = GITHUB_REPO,
): WorkflowBuildCatalog = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(WORKFLOW_CACHE_PREFS, Context.MODE_PRIVATE)
    val cachedRuns = prefs.getString(CACHE_RUNS_JSON, null)
    val cachedSnapshots = prefs.getString(CACHE_SNAPSHOTS_JSON, null)
    val cachedAt = prefs.getLong(CACHE_FETCHED_AT, 0L)
    val now = System.currentTimeMillis()
    val hasCache = !cachedRuns.isNullOrBlank() && !cachedSnapshots.isNullOrBlank()

    if (!forceRefresh && hasCache && workflowCacheIsFresh(cachedAt, now)) {
        return@withContext WorkflowBuildCatalog(
            builds = parseWorkflowBuildCatalog(repo, cachedRuns.orEmpty(), cachedSnapshots.orEmpty()),
            fetchedAtMillis = cachedAt,
            fromCache = true,
            stale = false,
        )
    }

    try {
        val encodedWorkflow = WORKFLOW_BUILD_FILE.replace("/", "%2F")
        val runsUrl = "https://api.github.com/repos/$repo/actions/workflows/" +
            "$encodedWorkflow/runs?status=success&per_page=$WORKFLOW_RUN_LIMIT"
        val snapshotsUrl = "https://api.github.com/repos/$repo/releases/tags/" +
            WORKFLOW_SNAPSHOT_TAG
        val runsJson = fetchGithubJson(runsUrl)
        // The very first workflow after this feature lands creates the rolling
        // release. Until then, an empty asset list is a valid catalog.
        val snapshotsJson = fetchGithubJson(
            url = snapshotsUrl,
            notFoundFallback = "{\"assets\":[]}",
        )
        prefs.edit()
            .putString(CACHE_RUNS_JSON, runsJson)
            .putString(CACHE_SNAPSHOTS_JSON, snapshotsJson)
            .putLong(CACHE_FETCHED_AT, now)
            .apply()
        WorkflowBuildCatalog(
            builds = parseWorkflowBuildCatalog(repo, runsJson, snapshotsJson),
            fetchedAtMillis = now,
            fromCache = false,
            stale = false,
        )
    } catch (error: Exception) {
        if (!hasCache) throw error
        WorkflowBuildCatalog(
            builds = parseWorkflowBuildCatalog(
                repo,
                cachedRuns.orEmpty(),
                cachedSnapshots.orEmpty(),
            ),
            fetchedAtMillis = cachedAt,
            fromCache = true,
            stale = true,
        )
    }
}

internal fun workflowCacheIsFresh(cachedAtMillis: Long, nowMillis: Long): Boolean =
    cachedAtMillis > 0L && nowMillis >= cachedAtMillis &&
        nowMillis - cachedAtMillis <= WORKFLOW_CACHE_MAX_AGE_MS

private fun fetchGithubJson(url: String, notFoundFallback: String? = null): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        githubUpdateRequestHeaders("application/vnd.github+json").forEach { (name, value) ->
            setRequestProperty(name, value)
        }
        connectTimeout = 15_000
        readTimeout = 20_000
    }
    return try {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND && notFoundFallback != null) {
            notFoundFallback
        } else {
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                val message = if (responseCode == 403 && remaining == "0") {
                    "GitHub's public API limit is temporarily exhausted"
                } else {
                    "GitHub returned HTTP $responseCode"
                }
                throw IOException(message)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    } finally {
        connection.disconnect()
    }
}

/** Pure parser kept separate from networking for deterministic unit coverage. */
internal fun parseWorkflowBuildCatalog(
    repo: String,
    runsJson: String,
    snapshotsJson: String,
): List<WorkflowBuild> {
    val snapshotsByRun = parsePublicSnapshots(repo, JSONObject(snapshotsJson))
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
            if (!sha.matches(COMMIT_SHA)) continue
            val snapshot = snapshotsByRun[runId]
                ?.takeIf { candidate -> sha.startsWith(candidate.shaPrefix, ignoreCase = true) }
                ?.artifact
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
                    artifacts = listOfNotNull(snapshot),
                )
            )
        }
    }
}

private data class PublicSnapshot(
    val shaPrefix: String,
    val artifact: WorkflowBuildArtifact,
)

private fun parsePublicSnapshots(
    repo: String,
    release: JSONObject,
): Map<Long, PublicSnapshot> {
    val assets = release.optJSONArray("assets") ?: JSONArray()
    val byRun = mutableMapOf<Long, PublicSnapshot>()
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val assetName = asset.optString("name").trim()
        val match = WORKFLOW_SNAPSHOT_NAME.matchEntire(assetName) ?: continue
        val runId = match.groupValues[1].toLongOrNull() ?: continue
        val shaPrefix = match.groupValues[2].lowercase()
        val assetId = asset.optLong("id", -1L)
        if (runId <= 0L || assetId <= 0L) continue
        byRun[runId] = PublicSnapshot(
            shaPrefix = shaPrefix,
            artifact = WorkflowBuildArtifact(
                artifactId = assetId,
                name = WORKFLOW_RELEASE_ARTIFACT,
                sizeBytes = asset.optLong("size", -1L),
                expired = false,
                expiresAt = "",
                downloadUrl = "https://github.com/$repo/releases/download/" +
                    "$WORKFLOW_SNAPSHOT_TAG/$assetName",
            ),
        )
    }
    return byRun
}

internal fun WorkflowBuild.releaseArtifact(): WorkflowBuildArtifact? =
    artifacts.firstOrNull { it.name == WORKFLOW_RELEASE_ARTIFACT }

internal fun newestInstallableWorkflowBuild(builds: List<WorkflowBuild>): WorkflowBuild? =
    builds.firstOrNull { build -> build.releaseArtifact()?.expired == false }

internal fun workflowVersionCanReplace(installedVersionCode: Long, candidateVersionCode: Long): Boolean =
    candidateVersionCode >= installedVersionCode
