package quiz.thaton3app.nazo.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FEEDBACK_EMAIL = "socialzoneop@gmail.com"

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
    ) : UpdateState
}

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
    val versionName = remember(packageInfo) { packageInfo?.versionName ?: "1.0.0" }
    val versionCodeStr = remember(packageInfo) {
        packageInfo?.let { info ->
            if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode.toString()
            } else {
                info.versionCode.toString()
            }
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
    var freqExpanded by remember { mutableStateOf(false) }

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

    fun onDownload(apkUrl: String) {
        requestNotificationPermission()
        if (UpdateDownloader.enqueue(context, apkUrl)) {
            Toast.makeText(context, "Download started — check your notifications", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show()
        }
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
                UpdateState.Available(latest.tag, latest.htmlUrl, latest.body, latest.apkUrl)
            } else {
                checkLabel = "Check Again"
                UpdateState.UpToDate
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
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
        NazoBottomNav(selected = NazoTab.Settings, onHomeClick = onHomeClick, onSettingsClick = onBackClick)
    }

    if (showUpdate) {
        AlertDialog(
            onDismissRequest = { showUpdate = false },
            title = { Text("App Updates", color = NazoTextPrimary) },
            text = {
                Column {
                    when (val state = updateState) {
                        is UpdateState.Checking -> Text("Checking GitHub...", color = NazoTextSecondary)
                        is UpdateState.Available -> {
                            Text(
                                "Version ${state.tag} is available!",
                                color = NazoTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Release Notes:",
                                style = MaterialTheme.typography.labelLarge,
                                color = NazoTextSecondary,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .padding(top = 8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = state.releaseNotes.ifBlank { "No release notes provided." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NazoTextSecondary,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onOpenBrowser(state.htmlUrl) }) {
                                    Text("View on GitHub", color = NazoPrimary)
                                }
                                Spacer(Modifier.weight(1f))
                                if (state.directApkUrl != null) {
                                    TextButton(onClick = { onDownload(state.directApkUrl) }) {
                                        Text("Update Now", color = NazoPrimary)
                                    }
                                } else {
                                    TextButton(onClick = { onOpenBrowser(state.htmlUrl) }) {
                                        Text("Download Manually", color = NazoPrimary)
                                    }
                                }
                            }
                        }
                        is UpdateState.UpToDate -> Text("You're on the latest version.", color = NazoTextSecondary)
                        is UpdateState.Error -> Text("Couldn't check for updates.", color = NazoTextSecondary)
                        is UpdateState.Idle -> Text("Ready to check for updates.", color = NazoTextSecondary)
                    }

                    if (updateState !is UpdateState.Checking && updateState !is UpdateState.Available) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = {
                                val found = UpdateDownloader.findApkFiles(context)
                                val deleted = UpdateDownloader.deleteApkFiles(found)
                                Toast.makeText(
                                    context,
                                    if (found.isEmpty()) "No APK files to clean up"
                                    else "Deleted $deleted APK file(s)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Text("Clean up APKs", color = NazoTextSecondary)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { checkForUpdates() }) {
                                Text(checkLabel, color = NazoPrimary)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = NazoBackground)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Auto-check frequency",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        TextButton(onClick = { freqExpanded = true }) {
                            Text(freqLabel(frequency), color = NazoPrimary)
                        }
                        DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                            UpdateFrequency.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(freqLabel(option)) },
                                    onClick = {
                                        freqExpanded = false
                                        frequency = option
                                        UpdatePrefs(context).updateFrequency = option
                                        UpdateScheduler.apply(context, option)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUpdate = false }) { Text("Close", color = NazoTextSecondary) }
            },
        )
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
    val versionCode = if (Build.VERSION.SDK_INT >= 28) {
        pkgInfo?.longVersionCode
    } else {
        pkgInfo?.versionCode?.toLong()
    }

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
            onClick = onBackClick,
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
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Nazo",
            style = MaterialTheme.typography.headlineMedium,
            color = NazoTextPrimary
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Version $versionName (code $versionCode)",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextPrimary
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "An anime quiz companion that turns any series, arc or theme into AI-generated questions — with your own API keys, stored securely on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoTextSecondary,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = NazoBackground,
        thickness = 2.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
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
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NazoTextPrimary,
                modifier = Modifier.size(20.dp)
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
                color = NazoTextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
