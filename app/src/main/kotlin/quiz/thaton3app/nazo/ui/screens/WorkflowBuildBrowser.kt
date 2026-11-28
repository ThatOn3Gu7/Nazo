package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import quiz.thaton3app.nazo.data.WorkflowBuild
import quiz.thaton3app.nazo.data.WorkflowBuildArtifact
import quiz.thaton3app.nazo.data.WorkflowBuildCatalog
import quiz.thaton3app.nazo.data.newestInstallableWorkflowBuild
import quiz.thaton3app.nazo.data.releaseArtifact
import quiz.thaton3app.nazo.ui.components.NazoPrimaryButton
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.NazoSheetHeader
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.components.sheetContentMaxHeight
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal sealed interface WorkflowCatalogState {
    data object Idle : WorkflowCatalogState
    data object Loading : WorkflowCatalogState
    data class Loaded(val catalog: WorkflowBuildCatalog) : WorkflowCatalogState
    data class Failed(val message: String) : WorkflowCatalogState
}

internal sealed interface WorkflowInstallState {
    data object Idle : WorkflowInstallState
    data class Downloading(
        val runId: Long,
        val downloaded: Long,
        val total: Long,
    ) : WorkflowInstallState
    data class Verifying(val runId: Long) : WorkflowInstallState
    data class Ready(
        val runId: Long,
        val versionName: String,
        val versionCode: Long,
    ) : WorkflowInstallState
    data class Failed(val runId: Long, val message: String) : WorkflowInstallState
}

@Composable
internal fun WorkflowBuildBrowserContent(
    catalogState: WorkflowCatalogState,
    installState: WorkflowInstallState,
    installedWorkflowSha: String,
    selectedRunId: Long?,
    onSelectRun: (Long) -> Unit,
    onRefresh: () -> Unit,
    onDownload: (WorkflowBuild, WorkflowBuildArtifact) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallAgain: () -> Unit,
    onOpenWorkflow: (String) -> Unit,
    onUseReleaseUpdater: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetContentMaxHeight())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        NazoSheetHeader(
            icon = Icons.Filled.Science,
            title = "Workflow builds",
            subtitle = "Install a successful GitHub Actions snapshot",
        )
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NazoPrimary.copy(alpha = 0.10f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Tester snapshots may be less stable. Only signed release APKs are " +
                        "mirrored publicly; Nazo verifies the package, signing certificate, and " +
                        "version code before opening Android's installer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextPrimary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (catalogState) {
            WorkflowCatalogState.Idle,
            WorkflowCatalogState.Loading -> WorkflowCatalogLoading()

            is WorkflowCatalogState.Failed -> WorkflowCatalogFailure(
                message = catalogState.message,
                onRefresh = onRefresh,
                onUseReleaseUpdater = onUseReleaseUpdater,
            )

            is WorkflowCatalogState.Loaded -> {
                val catalog = catalogState.catalog
                val builds = catalog.builds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Successful runs",
                            style = MaterialTheme.typography.titleMedium,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when {
                                catalog.stale ->
                                    "${builds.size} cached runs · GitHub temporarily unavailable"
                                catalog.fromCache ->
                                    "${builds.size} cached runs · checked " +
                                        formatWorkflowCacheTime(catalog.fetchedAtMillis)
                                else -> "${builds.size} runs · just refreshed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (catalog.stale) NazoError else NazoTextSecondary,
                        )
                    }
                    NazoSecondaryButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (builds.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = NazoSurfaceVariant,
                    ) {
                        Text(
                            text = "No successful workflow runs were returned by GitHub.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                        )
                    }
                } else {
                    val newestAvailable = newestInstallableWorkflowBuild(builds)
                    builds.forEach { build ->
                        WorkflowBuildCard(
                            build = build,
                            selected = build.runId == selectedRunId,
                            installed = installedWorkflowSha.equals(
                                build.commitSha,
                                ignoreCase = true,
                            ),
                            installState = installState,
                            newestAvailable = newestAvailable,
                            onSelect = { onSelectRun(build.runId) },
                            onDownload = { artifact -> onDownload(build, artifact) },
                            onCancelDownload = onCancelDownload,
                            onInstallAgain = onInstallAgain,
                            onSelectFallback = { fallback -> onSelectRun(fallback.runId) },
                            onOpenWorkflow = { onOpenWorkflow(build.htmlUrl) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nazo's public installer mirror keeps the newest 30 signed snapshots. " +
                        "Original Actions archives still require GitHub sign-in, so Nazo never " +
                        "stores an account token. A pruned snapshot cannot be recovered without " +
                        "rebuilding its commit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                NazoSecondaryButton(
                    onClick = onUseReleaseUpdater,
                    modifier = Modifier.fillMaxWidth(),
                    muted = true,
                ) {
                    Text("Use beta or stable updater")
                }
            }
        }
    }
}

@Composable
private fun WorkflowCatalogLoading() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NazoSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = NazoPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Loading successful runs…",
                    style = MaterialTheme.typography.titleSmall,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Checking workflow and artifact availability",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun WorkflowCatalogFailure(
    message: String,
    onRefresh: () -> Unit,
    onUseReleaseUpdater: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NazoError.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = NazoError)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Workflow builds unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoError,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NazoPrimaryButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("Retry")
                }
                NazoSecondaryButton(
                    onClick = onUseReleaseUpdater,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Releases")
                }
            }
        }
    }
}

@Composable
private fun WorkflowBuildCard(
    build: WorkflowBuild,
    selected: Boolean,
    installed: Boolean,
    installState: WorkflowInstallState,
    newestAvailable: WorkflowBuild?,
    onSelect: () -> Unit,
    onDownload: (WorkflowBuildArtifact) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallAgain: () -> Unit,
    onSelectFallback: (WorkflowBuild) -> Unit,
    onOpenWorkflow: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 18.dp,
        pressedCorner = 10.dp,
    )
    val releaseArtifact = build.releaseArtifact()
    val releaseAvailable = releaseArtifact != null && !releaseArtifact.expired
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) NazoPrimary.copy(alpha = 0.08f) else NazoSurface, shape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onSelect,
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NazoBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (installed) Icons.Filled.CheckCircle else Icons.Filled.Code,
                    contentDescription = null,
                    tint = if (installed) NazoPrimary else NazoTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = build.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${build.branch} · ${build.commitSha.take(7)} · run #${build.runNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatWorkflowTime(build.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoTextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            WorkflowAvailabilityBadge(
                label = when {
                    installed -> "INSTALLED"
                    releaseAvailable -> "AVAILABLE"
                    else -> "UNAVAILABLE"
                },
                positive = installed || releaseAvailable,
            )
        }

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = NazoTextSecondary.copy(alpha = 0.12f))
                Spacer(Modifier.height(14.dp))
                WorkflowArtifactDetails(
                    build = build,
                    releaseArtifact = releaseArtifact,
                    installState = installState,
                    newestAvailable = newestAvailable,
                    installed = installed,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onInstallAgain = onInstallAgain,
                    onSelectFallback = onSelectFallback,
                    onOpenWorkflow = onOpenWorkflow,
                )
            }
        }
    }
}

@Composable
private fun WorkflowAvailabilityBadge(label: String, positive: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (positive) NazoPrimary.copy(alpha = 0.14f) else NazoError.copy(alpha = 0.10f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (positive) NazoPrimary else NazoError,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WorkflowArtifactDetails(
    build: WorkflowBuild,
    releaseArtifact: WorkflowBuildArtifact?,
    installState: WorkflowInstallState,
    newestAvailable: WorkflowBuild?,
    installed: Boolean,
    onDownload: (WorkflowBuildArtifact) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallAgain: () -> Unit,
    onSelectFallback: (WorkflowBuild) -> Unit,
    onOpenWorkflow: () -> Unit,
) {
    Text(
        text = "Artifacts",
        style = MaterialTheme.typography.labelLarge,
        color = NazoTextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))

    if (releaseArtifact != null) {
        ArtifactSummary(
            title = "Signed release snapshot",
            subtitle = "${formatWorkflowBytes(releaseArtifact.sizeBytes)} · public installer mirror",
            compatible = !releaseArtifact.expired,
        )
    } else {
        ArtifactSummary(
            title = "Public snapshot unavailable",
            subtitle = "This predates the mirror or has been pruned from its newest 30 builds",
            compatible = false,
        )
    }

    Spacer(Modifier.height(12.dp))
    val stateForRun = when (installState) {
        is WorkflowInstallState.Downloading -> installState.runId == build.runId
        is WorkflowInstallState.Verifying -> installState.runId == build.runId
        is WorkflowInstallState.Ready -> installState.runId == build.runId
        is WorkflowInstallState.Failed -> installState.runId == build.runId
        WorkflowInstallState.Idle -> false
    }

    if (stateForRun) {
        when (installState) {
            is WorkflowInstallState.Downloading -> {
                val fraction = if (installState.total > 0L) {
                    (installState.downloaded.toFloat() / installState.total).coerceIn(0f, 1f)
                } else {
                    null
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = NazoPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Downloading artifact…", color = NazoTextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = NazoPrimary,
                        trackColor = NazoSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatWorkflowBytes(installState.downloaded)} / " +
                            formatWorkflowBytes(installState.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NazoPrimary,
                        trackColor = NazoSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                NazoSecondaryButton(
                    onClick = onCancelDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }

            is WorkflowInstallState.Verifying -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = NazoPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Verifying package and signature…",
                        color = NazoTextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is WorkflowInstallState.Ready -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = NazoPrimary.copy(alpha = 0.10f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Verified Nazo ${installState.versionName} " +
                                "(code ${installState.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Android's installer should be open. You can launch it again below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NazoTextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                NazoPrimaryButton(
                    onClick = onInstallAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Installer Again")
                }
            }

            is WorkflowInstallState.Failed -> {
                Text(
                    text = installState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoError,
                )
                Spacer(Modifier.height(8.dp))
                if (releaseArtifact?.expired == false) {
                    NazoPrimaryButton(
                        onClick = { onDownload(releaseArtifact) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry Release APK")
                    }
                }
            }

            WorkflowInstallState.Idle -> Unit
        }
    } else if (releaseArtifact?.expired == false) {
        NazoPrimaryButton(
            onClick = { onDownload(releaseArtifact) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (installed) "Reinstall This Snapshot" else "Download, Verify & Install")
        }
    } else {
        Text(
            text = "The exact snapshot is no longer downloadable.",
            style = MaterialTheme.typography.bodySmall,
            color = NazoError,
        )
        if (newestAvailable != null && newestAvailable.runId != build.runId) {
            Spacer(Modifier.height(8.dp))
            NazoPrimaryButton(
                onClick = { onSelectFallback(newestAvailable) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Select Newest Available · ${newestAvailable.commitSha.take(7)}")
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    NazoSecondaryButton(
        onClick = onOpenWorkflow,
        modifier = Modifier.fillMaxWidth(),
        muted = true,
    ) {
        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text("View Workflow on GitHub")
    }

    if (releaseArtifact == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "The original Actions archive may still exist, but GitHub returns HTTP 401 " +
                "without an authenticated account. Nazo intentionally does not request one.",
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
        )
    }
}

@Composable
private fun ArtifactSummary(
    title: String,
    subtitle: String,
    compatible: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NazoBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (compatible) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (compatible) NazoPrimary else NazoTextSecondary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatWorkflowBytes(bytes: Long): String = when {
    bytes < 0L -> "Unknown size"
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

internal fun formatWorkflowCacheTime(timestampMillis: Long): String = runCatching {
    val output = SimpleDateFormat("HH:mm", Locale.getDefault())
    output.format(java.util.Date(timestampMillis))
}.getOrElse { "earlier" }

internal fun formatWorkflowTime(raw: String): String {
    if (raw.isBlank()) return "Unknown time"
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val output = SimpleDateFormat("MMM d, yyyy · HH:mm 'UTC'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        output.format(requireNotNull(parser.parse(raw)))
    }.getOrElse { raw }
}
