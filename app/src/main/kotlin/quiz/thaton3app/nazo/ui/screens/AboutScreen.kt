package quiz.thaton3app.nazo.ui.screens

import androidx.compose.ui.platform.LocalContext
import quiz.thaton3app.nazo.ui.components.NazoModalSheet
import quiz.thaton3app.nazo.ui.components.rememberHapticBack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.GITHUB_REPO
import quiz.thaton3app.nazo.data.UpdateDownloader
import quiz.thaton3app.nazo.data.UpdateFrequency
import quiz.thaton3app.nazo.data.UpdatePrefs
import quiz.thaton3app.nazo.data.UpdateScheduler
import quiz.thaton3app.nazo.data.currentVersionName
import quiz.thaton3app.nazo.data.fetchLatestRelease
import quiz.thaton3app.nazo.data.isNewerVersion
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FEEDBACK_EMAIL = "socialzoneop@gmail.com"

/** 17.3-style megabyte formatting for the download progress line. */
private fun formatMb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1048576.0)

private sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    object Error : UpdateState
    data class Available(
        val tag: String,
        val htmlUrl: String,
        val releaseNotes: String,
        val directApkUrl: String?,
        val apkSizeBytes: Long = -1L,
    ) : UpdateState
}

/** In-sheet APK download progress (the in-app replacement for DownloadManager). */
private sealed interface ApkDownloadState {
    object Idle : ApkDownloadState
    data class Running(val downloaded: Long, val total: Long) : ApkDownloadState
    object Done : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = remember(packageInfo) { packageInfo?.versionName ?: "3.0" }
    val versionCodeStr = remember(packageInfo) {
        packageInfo?.let { info ->
            PackageInfoCompat.getLongVersionCode(info).toString()
        } ?: "1"
    }
    val installDateStr = remember(packageInfo) {
        packageInfo?.firstInstallTime?.let { time ->
            if (time > 0) {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(time))
            } else "Unknown"
        } ?: "Unknown"
    }

    var showDev by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var checkLabel by remember { mutableStateOf("Check Now") }
    var frequency by remember { mutableStateOf(UpdatePrefs(context).updateFrequency) }
    var downloadState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by the OS prompt */ }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startDownload(apkUrl: String, expectedSize: Long) {
        if (downloadState is ApkDownloadState.Running) return
        downloadState = ApkDownloadState.Running(0L, expectedSize)
        downloadJob = scope.launch {
            try {
                UpdateDownloader.downloadApk(context, apkUrl) { downloaded, total ->
                    downloadState = ApkDownloadState.Running(
                        downloaded = downloaded,
                        // Fall back to the release asset's size when the
                        // server didn't send Content-Length.
                        total = if (total > 0) total else expectedSize,
                    )
                }
                downloadState = ApkDownloadState.Done
                // Hand straight to the system installer; the sheet keeps an
                // Install button for when the user dismisses that prompt.
                UpdateDownloader.install(context)
            } catch (ce: CancellationException) {
                downloadState = ApkDownloadState.Idle
                throw ce
            } catch (e: Exception) {
                downloadState = ApkDownloadState.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun onOpenBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun checkForUpdates() {
        scope.launch {
            updateState = UpdateState.Checking
            delay(1200) // brief delay so the "Checking" state is visible
            val latest = fetchLatestRelease(GITHUB_REPO)
            if (latest == null) {
                updateState = UpdateState.Error
                checkLabel = "Retry"
                return@launch
            }
            val current = currentVersionName(context)
            if (current == null) {
                updateState = UpdateState.UpToDate
                checkLabel = "Check Again"
                return@launch
            }
            updateState = if (isNewerVersion(latest.tag, current)) {
                UpdateState.Available(latest.tag, latest.htmlUrl, latest.body, latest.apkUrl, latest.apkSizeBytes)
            } else {
                checkLabel = "Check Again"
                UpdateState.UpToDate
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            ScreenHeader(title = "About", onBackClick = onBackClick)
            Spacer(Modifier.height(20.dp))

            HeroCard(versionName = versionName, versionCode = versionCodeStr)

            Spacer(Modifier.height(24.dp))
            SectionLabel("SUPPORT & SOURCE")
            Spacer(Modifier.height(10.dp))

            SettingsCard {
                ActionRow(
                    icon = Icons.Filled.Sync,
                    title = "Updates & Settings",
                    subtitle = "Check for updates from GitHub",
                    onClick = {
                        showUpdate = true
                        requestNotificationPermission()
                        if (updateState is UpdateState.Idle) checkForUpdates()
                    }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "Send Feedback",
                    subtitle = "Report issues or share ideas",
                    onClick = { sendFeedback(context) }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub Repository",
                    subtitle = "View source code",
                    onClick = { onOpenBrowser("https://github.com/$GITHUB_REPO") }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.PersonOutline,
                    title = "About the Developer",
                    subtitle = "Story & projects",
                    onClick = { showDev = true }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Balance,
                    title = "Licenses",
                    subtitle = "Open-source libraries",
                    onClick = { showLicenses = true }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Event,
                    title = "Installed Date",
                    subtitle = "First launch of the app",
                    trailingText = installDateStr,
                    onClick = { /* Non-clickable stat row */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Tag,
                    title = "Version code",
                    subtitle = "This build's version code",
                    trailingText = versionCodeStr,
                    onClick = { /* Non-clickable stat row */ }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showUpdate) {
        NazoModalSheet(
            onDismissRequest = { showUpdate = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            UpdateMenuContent(
                state = updateState,
                downloadState = downloadState,
                currentVersion = versionName,
                checkLabel = checkLabel,
                onCheckForUpdates = { checkForUpdates() },
                onStartDownload = { apkUrl, size -> startDownload(apkUrl, size) },
                onCancelDownload = { cancelDownload() },
                onInstall = { UpdateDownloader.install(context) },
                onOpenBrowser = { url -> onOpenBrowser(url) },
                frequency = frequency,
                onFrequencyChange = { freq ->
                    frequency = freq
                    UpdatePrefs(context).updateFrequency = freq
                    UpdateScheduler.apply(context, freq)
                }
            )
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            icon = { Icon(Icons.Filled.Balance, contentDescription = null, tint = NazoPrimary) },
            title = { Text("Open-source Licenses", color = NazoTextPrimary) },
            text = {
                val licenses = listOf(
                    "Android & Jetpack Compose — Apache-2.0",
                    "Material 3 — Apache-2.0",
                    "AndroidX Core KTX — Apache-2.0",
                    "AndroidX Activity Compose — Apache-2.0",
                    "AndroidX Lifecycle — Apache-2.0",
                    "AndroidX WorkManager — Apache-2.0",
                    "Material Icons Extended — Apache-2.0",
                    "Coil (image loading) — Apache-2.0",
                    "Kotlin stdlib — Apache-2.0",
                    "Local data stored via Android SharedPreferences (framework)",
                )
                LazyColumn {
                    items(licenses.size) { index ->
                        Text(
                            text = "• ${licenses[index]}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("Close", color = NazoPrimary) }
            },
        )
    }

    if (showDev) {
        AboutDevDialog(onDismiss = { showDev = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateMenuContent(
    state: UpdateState,
    downloadState: ApkDownloadState,
    currentVersion: String,
    checkLabel: String,
    onCheckForUpdates: () -> Unit,
    onStartDownload: (String, Long) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    frequency: UpdateFrequency,
    onFrequencyChange: (UpdateFrequency) -> Unit,
) {
    val frequencyLabels = mapOf(
        UpdateFrequency.EVERY_LAUNCH to "Every Launch",
        UpdateFrequency.WEEKLY to "Weekly",
        UpdateFrequency.BI_WEEKLY to "Bi-weekly",
        UpdateFrequency.NEVER to "Never",
    )
    var showFrequencyDropdown by remember { mutableStateOf(false) }

    val appContext = LocalContext.current.applicationContext
    var apkFilesToClean by remember { mutableStateOf<List<File>>(emptyList()) }
    var showCleanupConfirm by remember { mutableStateOf(false) }

    fun promptApkCleanup() {
        val found = UpdateDownloader.findApkFiles(appContext)
        if (found.isEmpty()) {
            Toast.makeText(appContext, "No APK files to clean up", Toast.LENGTH_SHORT).show()
        } else {
            apkFilesToClean = found
            showCleanupConfirm = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "App Updates",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NazoTextPrimary,
        )

        Spacer(Modifier.height(16.dp))

        // Status Card with smooth height expansion
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = NazoSurfaceVariant.copy(alpha = 0.6f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Crossfade status header content smoothly
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + expandVertically()) togetherWith
                            (fadeOut(animationSpec = tween(200)) + shrinkVertically())
                    },
                    label = "status_transition",
                ) { targetState ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (targetState) {
                                is UpdateState.Checking -> Icons.Filled.Sync
                                is UpdateState.Available -> Icons.Filled.NewReleases
                                is UpdateState.UpToDate -> Icons.Filled.CheckCircle
                                is UpdateState.Error -> Icons.Filled.Error
                                is UpdateState.Idle -> Icons.Filled.Info
                            },
                            contentDescription = null,
                            tint = if (targetState is UpdateState.Error) {
                                NazoError
                            } else {
                                NazoPrimary
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = when (targetState) {
                                is UpdateState.Checking -> "Checking GitHub..."
                                is UpdateState.Available -> "Version ${targetState.tag} is available!"
                                is UpdateState.UpToDate -> "Nazo is up to date."
                                is UpdateState.Error -> "Failed to check for updates."
                                is UpdateState.Idle -> "Ready to check."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (targetState is UpdateState.Error) {
                                NazoError
                            } else {
                                NazoTextPrimary
                            },
                        )
                    }
                }
                // Animated expand/collapse for release notes section
                AnimatedVisibility(
                    visible = state is UpdateState.Available,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val availableState = state as? UpdateState.Available
                    if (availableState != null) {
                        Column {
                            Spacer(Modifier.height(16.dp))

                            // Current → New version comparison.
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = NazoSurfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = "Current",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NazoTextSecondary,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = NazoBackground,
                                        ) {
                                            Text(
                                                text = "v${currentVersion.removePrefix("v")}",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NazoTextPrimary,
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = NazoPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = "New",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NazoTextSecondary,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = NazoPrimary,
                                        ) {
                                            Text(
                                                text = "v${availableState.tag.removePrefix("v")}",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NazoOnPrimary,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                "What's New:",
                                style = MaterialTheme.typography.labelLarge,
                                color = NazoTextSecondary,
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .padding(top = 8.dp),
                                shape = MaterialTheme.shapes.small,
                                color = NazoSurfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = availableState.releaseNotes.ifBlank { "No release notes provided." },
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NazoTextSecondary,
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            when (downloadState) {
                                is ApkDownloadState.Running -> {
                                    // Live in-sheet progress: bar + MB counter + percent.
                                    val total = downloadState.total
                                    val fraction = if (total > 0) {
                                        (downloadState.downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    } else null
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.5.dp,
                                            color = NazoPrimary,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Downloading…",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NazoTextPrimary,
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    if (fraction != null) {
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = NazoPrimary,
                                            trackColor = NazoSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${formatMb(downloadState.downloaded)} / ${formatMb(total)} MB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NazoTextSecondary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${(fraction * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NazoPrimary,
                                            )
                                        }
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = NazoPrimary,
                                            trackColor = NazoSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "${formatMb(downloadState.downloaded)} MB so far",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NazoTextSecondary,
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = onCancelDownload,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Cancel", color = NazoPrimary)
                                    }
                                }

                                is ApkDownloadState.Done -> {
                                    Button(
                                        onClick = onInstall,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NazoPrimary,
                                            contentColor = NazoOnPrimary,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Filled.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Install Update")
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Download complete — the installer should have opened. Tap again if you dismissed it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NazoTextSecondary,
                                    )
                                }

                                else -> {
                                    if (downloadState is ApkDownloadState.Failed) {
                                        Text(
                                            text = "Download failed: ${downloadState.message}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NazoError,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    if (availableState.directApkUrl != null) {
                                        Button(
                                            onClick = {
                                                onStartDownload(
                                                    availableState.directApkUrl,
                                                    availableState.apkSizeBytes,
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NazoPrimary,
                                                contentColor = NazoOnPrimary,
                                            ),
                                        ) {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (downloadState is ApkDownloadState.Failed) "Retry Download" else "Download & Install")
                                        }
                                    } else {
                                        Button(
                                            onClick = { onOpenBrowser(availableState.htmlUrl) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NazoPrimary,
                                                contentColor = NazoOnPrimary,
                                            ),
                                        ) {
                                            Text("Download Manually")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(onClick = { onOpenBrowser(availableState.htmlUrl) }) {
                                            Text("View on GitHub", color = NazoPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Animated visibility for Check Now / Retry / Check Again button
                AnimatedVisibility(
                    visible = state !is UpdateState.Checking && state !is UpdateState.Available,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { promptApkCleanup() }) {
                                Text(
                                    "Clean up APKs",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = NazoTextSecondary,
                                )
                            }
                            Button(
                                onClick = onCheckForUpdates,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NazoPrimary,
                                    contentColor = NazoOnPrimary,
                                ),
                            ) {
                                Text(checkLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = NazoBackground)
        Spacer(Modifier.height(16.dp))

        // Preferences Section
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NazoTextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        // Auto-check frequency dropdown
        ExposedDropdownMenuBox(
            expanded = showFrequencyDropdown,
            onExpandedChange = { showFrequencyDropdown = !showFrequencyDropdown },
        ) {
            OutlinedTextField(
                value = frequencyLabels[frequency] ?: "Weekly",
                onValueChange = {},
                readOnly = true,
                label = { Text("Auto-check frequency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFrequencyDropdown) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NazoTextPrimary,
                    unfocusedTextColor = NazoTextPrimary,
                    focusedBorderColor = NazoPrimary,
                    unfocusedBorderColor = NazoTextSecondary,
                    focusedLabelColor = NazoTextSecondary,
                    unfocusedLabelColor = NazoTextSecondary,
                    cursorColor = NazoPrimary,
                ),
            )
            ExposedDropdownMenu(
                expanded = showFrequencyDropdown,
                onDismissRequest = { showFrequencyDropdown = false },
            ) {
                UpdateFrequency.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(freqLabel(option), color = NazoTextPrimary) },
                        onClick = {
                            showFrequencyDropdown = false
                            onFrequencyChange(option)
                        },
                    )
                }
            }
        }
    }

    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("Clean up APK files?", color = NazoTextPrimary) },
            text = {
                val totalBytes = apkFilesToClean.sumOf { it.length() }
                Column {
                    Text(
                        "Found ${apkFilesToClean.size} APK file(s) totaling ${formatBytes(totalBytes)}:",
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    apkFilesToClean.forEach { file ->
                        Text("• ${file.name}", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = UpdateDownloader.deleteApkFiles(apkFilesToClean)
                    showCleanupConfirm = false
                    Toast.makeText(
                        appContext,
                        if (deleted == apkFilesToClean.size) "Deleted $deleted APK file(s)"
                        else "Deleted $deleted of ${apkFilesToClean.size} APK file(s)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Text("Delete", color = NazoPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupConfirm = false }) { Text("Cancel", color = NazoTextSecondary) }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

private fun freqLabel(frequency: UpdateFrequency): String = when (frequency) {
    UpdateFrequency.EVERY_LAUNCH -> "Every Launch"
    UpdateFrequency.WEEKLY -> "Weekly"
    UpdateFrequency.BI_WEEKLY -> "Bi-weekly"
    UpdateFrequency.NEVER -> "Never"
}

// region Helpers

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun sendFeedback(context: android.content.Context) {
    val pkgInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = pkgInfo?.versionName ?: "?"
    val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) }

    val info = buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Architecture: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("App: Nazo $versionName (code $versionCode)")
        appendLine()
        appendLine("--- Write your feedback after this line ---")
        appendLine()
    }

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Nazo Feedback")
        putExtra(Intent.EXTRA_TEXT, info)
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No email app installed", Toast.LENGTH_SHORT).show()
    }
}

// endregion

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NazoTextPrimary)
    }
}

@Composable
private fun HeroCard(versionName: String, versionCode: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NazoPillUnselected),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "謎", // Nazo (Mystery/Puzzle)
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp),
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Nazo",
            style = MaterialTheme.typography.headlineMedium,
            color = NazoTextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSurfaceVariant)
                .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Version $versionName (code $versionCode)",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "An anime quiz companion that turns any series, arc or theme into AI-generated questions — with your own API keys, stored securely on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoTextSecondary,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = NazoBackground,
        thickness = 2.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NazoTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AboutDevDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = NazoPrimary) },
        title = { Text("About the Developer", color = NazoTextPrimary) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                item {
                    Text("The Story", style = MaterialTheme.typography.titleMedium, color = NazoPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nazo started as a learning project. I built it because I wanted to learn how to code, and an anime quiz app felt like the perfect first app — simple to start, but with enough real pieces (a local question bank, a UI, and an AI integration) to actually learn from. It grew into the app you're using now: a friendly place to test how well you really know your favorite series.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("About Me", style = MaterialTheme.typography.titleMedium, color = NazoPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hi, I'm Sahil R. — also known as ThatOn3Gu7. I'm a developer who likes to learn by building, and I spend a lot of time in the terminal. When I'm not tinkering with Android apps like this one, I'm usually shipping command-line tools or breaking things on purpose to see how they work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("My Projects", style = MaterialTheme.typography.titleMedium, color = NazoPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "• ProjectR — a modular Bash terminal setup assistant that installs, inspects, and backs up 240+ tools across Linux, macOS, and Termux.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• UtilityKit — a toolbox of 65 standalone Bash utilities (files, network, git, and more) behind one interactive dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Connect", style = MaterialTheme.typography.titleMedium, color = NazoPrimary)
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    DevLink("GitHub", "ThatOn3Gu7") {
                        openUrl(context, "https://github.com/ThatOn3Gu7")
                    }
                    DevLink("Email", "socialzoneop@gmail.com") {
                        openUrl(context, "mailto:socialzoneop@gmail.com")
                    }
                    DevLink("Instagram", "@thaton3gu7") {
                        openUrl(context, "https://instagram.com/thaton3gu7")
                    }
                    DevLink("TikTok", "@thaton3gu7") {
                        openUrl(context, "https://tiktok.com/@thaton3gu7")
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Credits", style = MaterialTheme.typography.titleMedium, color = NazoPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nazo is built with Jetpack Compose and Kotlin, with a local question bank and an optional AI provider for fresh questions. Thanks to the open-source community that makes projects like this possible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Third-party services",
                        style = MaterialTheme.typography.titleSmall,
                        color = NazoPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Optional AI question generation is powered by third-party providers — " +
                            "Google Gemini, OpenRouter, " +
                            "OpenCode Zen. Remote images (e.g. profile pictures) are " +
                            "loaded with Coil. Update checks use the GitHub API, and network " +
                            "connectivity is verified via Google's service. These services are not " +
                            "affiliated with or endorsed by Nazo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NazoPrimary) }
        },
    )
}

@Composable
private fun DevLink(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(90.dp),
            color = NazoTextPrimary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}
