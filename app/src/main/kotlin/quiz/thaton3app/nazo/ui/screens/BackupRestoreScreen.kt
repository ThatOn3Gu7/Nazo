package quiz.thaton3app.nazo.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import quiz.thaton3app.nazo.ui.components.NazoPrimaryButton
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.NazoDangerButton
import quiz.thaton3app.nazo.ui.components.NazoAdaptiveDialog
import quiz.thaton3app.nazo.ui.components.isLandscape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import quiz.thaton3app.nazo.ui.components.rememberRetained
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.backup.BackupScheduler
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.BackupRepository
import quiz.thaton3app.nazo.data.settings.toLastBackup
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Presentation state for the long-running backup/restore operations. Replaces
 * the transient Toasts: progress is visible while it runs, and success/failure
 * stays on screen next to the actions it came from. Purely visual — the
 * operations themselves are unchanged.
 */
private sealed interface OpStatus {
    val label: String
    data class Busy(override val label: String) : OpStatus
    data class Done(override val label: String) : OpStatus
    data class Failed(override val label: String) : OpStatus
}

@Composable
fun BackupRestoreScreen(
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    /**
     * Called only after a restore has been durably applied. The app root uses
     * this to re-read every piece of hoisted state (theme, accent, profile,
     * sound, statistics, question history, practice deck) so the whole UI
     * reflects the restored backup without an app restart.
     */
    onRestored: () -> Unit = {},
) {
    val context = LocalContext.current
    val backupPrefs = remember { BackupPrefs(context) }
    val statsStore = remember { QuizStatsStore(context) }
    val profilePrefs = remember { ProfilePreferences(context) }
    val themePrefs = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()

    // Held in state, not read straight from prefs: a plain read only re-runs when
    // something else recomposes the screen, which is why a fresh backup used to
    // need an app restart before its timestamp appeared.
    var lastBackup by remember { mutableStateOf(backupPrefs.lastBackup) }

    // The auto-backup worker writes while we are backgrounded, so re-read the
    // cached record whenever the screen comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lastBackup = backupPrefs.lastBackup
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val stats = statsStore.get()
    val summaryParts = mutableListOf<String>()
    if (stats.totalQuizzes > 0) {
        summaryParts += "${stats.totalQuizzes} ${if (stats.totalQuizzes == 1) "quiz" else "quizzes"}"
    }
    if (stats.currentStreakDays > 0) {
        summaryParts += "${stats.currentStreakDays}-day streak"
    }
    summaryParts += "${themePrefs.accent} theme"
    if (profilePrefs.username.isNotBlank()) {
        summaryParts += "profile \"${profilePrefs.username}\""
    }
    val summaryText = if (summaryParts.isEmpty()) {
        "No quiz data yet — your settings will still be backed up."
    } else {
        summaryParts.joinToString(" · ")
    }

    // Rotating swaps the whole layout subtree (full-screen in portrait,
    // master/detail in landscape), which discards plain `remember` and used to
    // make an open Backup or Restore preview vanish. rememberRetained keeps the
    // value in a store held above that swap, so the dialog stays open and
    // simply re-renders in the other orientation's form -- a centred card in
    // portrait, the drag sheet in landscape. See RetainedState.kt.
    //
    // The preview payloads are retained alongside their flag so the dialog can
    // never come back without its contents.
    var showRestoreConfirm by rememberRetained("BackupRestore.showRestoreConfirm") { false }
    var restoreUri by rememberRetained<Uri?>("BackupRestore.restoreUri") { null }
    var showFreqDialog by rememberRetained("BackupRestore.showFreqDialog") { false }

    // Contents preview state. Categories always come from the real bundle:
    // live SharedPreferences for an export, the parsed file for a restore.
    var backupPreview by rememberRetained<List<BackupRepository.BackupCategory>>(
        "BackupRestore.backupPreview"
    ) { emptyList() }
    var showBackupPreview by rememberRetained("BackupRestore.showBackupPreview") { false }
    var restorePreview by rememberRetained<List<BackupRepository.BackupCategory>>(
        "BackupRestore.restorePreview"
    ) { emptyList() }
    // Set when the pending restore is the on-device auto-backup rather than a picked file.
    var restoreFromAuto by rememberRetained("BackupRestore.restoreFromAuto") { false }

    // Operation status banner (see OpStatus). Plain remember: a rotation
    // mid-operation already cancels the work (its scope dies), so the banner
    // must not survive claiming "Backing up…" forever.
    var opStatus by remember { mutableStateOf<OpStatus?>(null) }
    LaunchedEffect(opStatus) {
        // Success notes fade on their own; failures stay until dismissed or
        // until the next operation replaces them.
        if (opStatus is OpStatus.Done) {
            kotlinx.coroutines.delay(3500)
            opStatus = null
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            opStatus = OpStatus.Busy("Backing up…")
            try {
                val receipt = BackupRepository.exportToUri(context, uri)
                val record = receipt.toLastBackup()
                backupPrefs.lastBackup = record
                // Update the card in the same breath as the write — no restart.
                lastBackup = record
                opStatus = OpStatus.Done("Backup saved successfully")
            } catch (e: CancellationException) {
                // The screen went away mid-write; never dress cancellation up
                // as a failure.
                throw e
            } catch (e: Exception) {
                opStatus = OpStatus.Failed("Backup failed: ${e.message}")
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            opStatus = OpStatus.Busy("Reading backup file…")
            // inspectUri runs the same parseAndValidate gate as validateUri, so an
            // invalid file is still rejected before anything is shown or written.
            val contents = BackupRepository.inspectUri(context, uri)
            if (contents != null) {
                restoreUri = uri
                restoreFromAuto = false
                restorePreview = contents
                opStatus = null
                showRestoreConfirm = true
            } else {
                opStatus = OpStatus.Failed("Invalid backup file — that file isn't a Nazo backup.")
            }
        }
    }

    val autoBackupPath = BackupRepository.autoBackupPath(context)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            ScreenHeader(title = "Backup & Restore", onBackClick = onBackClick)

            // Live operation status: progress while it runs, then a success or
            // failure note in place of the old transient Toasts.
            AnimatedVisibility(
                visible = opStatus != null,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(140)),
            ) {
                StatusBanner(
                    status = opStatus,
                    onDismiss = { opStatus = null },
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedLastBackupCard(last = lastBackup, fallbackSummary = summaryText)

            Spacer(Modifier.height(32.dp))
            
            Column {
                SectionLabel("MANUAL BACKUP")
                Spacer(Modifier.height(12.dp))

                SettingsCard {
                    AnimatedActionRow(
                        icon = Icons.Filled.Upload,
                        title = "Create Local Backup",
                        subtitle = "Export your data as a JSON file",
                        enabled = opStatus !is OpStatus.Busy,
                        onClick = {
                            // summarizeLocal reads every store off the main
                            // thread now; the preview opens when it is ready.
                            scope.launch {
                                opStatus = OpStatus.Busy("Preparing backup…")
                                try {
                                    backupPreview = BackupRepository.summarizeLocal(context)
                                    opStatus = null
                                    showBackupPreview = true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    opStatus = OpStatus.Failed("Backup failed: ${e.message}")
                                }
                            }
                        }
                    )
                    RowDivider()
                    AnimatedActionRow(
                        icon = Icons.Filled.Folder,
                        title = "Restore Data",
                        subtitle = "Import a backup file from this device",
                        enabled = opStatus !is OpStatus.Busy,
                        onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) }
                    )
                    RowDivider()
                    AnimatedActionRow(
                        icon = Icons.Filled.SettingsBackupRestore,
                        title = "Restore from Auto-Backup",
                        subtitle = "Use the last automatic backup on this device",
                        enabled = opStatus !is OpStatus.Busy,
                        onClick = {
                            scope.launch {
                                opStatus = OpStatus.Busy("Reading auto-backup…")
                                val contents = BackupRepository.inspectPath(context, autoBackupPath)
                                if (contents != null) {
                                    restoreUri = null
                                    restoreFromAuto = true
                                    restorePreview = contents
                                    opStatus = null
                                    showRestoreConfirm = true
                                } else {
                                    opStatus = OpStatus.Failed("No usable auto-backup yet")
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Column {
                SectionLabel("AUTOMATION")
                Spacer(Modifier.height(12.dp))

                SettingsCard {
                    AnimatedActionRow(
                        icon = Icons.Filled.Event,
                        title = "Auto-Backup Frequency",
                        subtitle = "How often backups are generated",
                        trailingText = freqLabel(backupPrefs.autoBackupFrequency),
                        onClick = { showFreqDialog = true }
                    )
                    RowDivider()
                    AnimatedActionRow(
                        icon = Icons.Filled.CloudDone,
                        title = "Backup Location",
                        subtitle = autoBackupPath,
                        onClick = {
                            opStatus = OpStatus.Done("Auto-backups are saved in the folder shown above")
                        }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        // Fading Dialogs (Keeping the fade here since popups aren't part of the main nav graph)
        FadeDialog(
            visible = showBackupPreview,
            onDismiss = { showBackupPreview = false },
            dismissible = false,
        ) {
            if (backupPreview.none { it.isProgress }) {
                // Fresh install: only default preference stores exist. Backing
                // that up would produce a file that restores nothing, so offer
                // no confirm button at all rather than a misleading success.
                NothingToBackUpContent(onClose = { showBackupPreview = false })
            } else {
            BackupContentsContent(
                title = "Ready to Back Up",
                message = "Everything below will be written into a single JSON file. " +
                    "All of it is included — nothing is optional.",
                accent = NazoPrimary,
                icon = Icons.Filled.Upload,
                categories = backupPreview,
                confirmLabel = "Back Up",
                onCancel = { showBackupPreview = false },
                onConfirm = {
                    showBackupPreview = false
                    val name = "Nazo_backup_${System.currentTimeMillis()}.json"
                    createLauncher.launch(name)
                }
            )
            }
        }

        FadeDialog(
            visible = showRestoreConfirm,
            onDismiss = { showRestoreConfirm = false },
            dismissible = false,
        ) {
            BackupContentsContent(
                title = "Restore Data?",
                message = "This backup contains the data below. Restoring overwrites your " +
                    "current stats, profile and settings. This cannot be undone.",
                accent = NazoError,
                icon = Icons.Filled.Warning,
                categories = restorePreview,
                confirmLabel = "Restore",
                // Overwriting data is irreversible — the destructive action
                // wears the fixed danger red, not the accent.
                destructive = true,
                onCancel = { showRestoreConfirm = false },
                onConfirm = {
                    showRestoreConfirm = false
                    val uri = restoreUri
                    val fromAuto = restoreFromAuto
                    scope.launch {
                        opStatus = OpStatus.Busy("Restoring…")
                        try {
                            if (fromAuto) {
                                BackupRepository.importFromPath(context, autoBackupPath)
                                // importFrom* only returns after every store has
                                // been committed; refresh the app state first, then
                                // tell the user it is done.
                                onRestored()
                                opStatus = OpStatus.Done("Restored from auto-backup")
                            } else if (uri != null) {
                                BackupRepository.importFromUri(context, uri)
                                onRestored()
                                opStatus = OpStatus.Done("Data restored successfully")
                            } else {
                                opStatus = OpStatus.Failed("Restore failed: no backup selected")
                            }
                        } catch (e: CancellationException) {
                            // Leaving the screen mid-restore cancels the work;
                            // that is not a restore failure.
                            throw e
                        } catch (e: Exception) {
                            opStatus = OpStatus.Failed("Restore failed: ${e.message}")
                        }
                    }
                }
            )
        }

        FadeDialog(
            visible = showFreqDialog,
            onDismiss = { showFreqDialog = false }
        ) {
            AutoBackupFreqContent(
                currentFreq = backupPrefs.autoBackupFrequency,
                onSelect = { value, label ->
                    backupPrefs.autoBackupFrequency = value
                    BackupScheduler.apply(context, value)
                    showFreqDialog = false
                    opStatus = OpStatus.Done("Auto-backup set to: $label")
                },
                onClose = { showFreqDialog = false }
            )
        }
    }
}

/**
 * Last Backup card. Works for manual and automatic backups alike: everything it
 * shows comes from [BackupPrefs.LastBackup], which is written when the backup is
 * created. The file is never reopened to measure it.
 *
 * [fallbackSummary] is the old "what you'd be backing up" line, still used
 * before any backup exists.
 */
@Composable
private fun AnimatedLastBackupCard(last: BackupPrefs.LastBackup?, fallbackSummary: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "VaultAnimation")
    
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NazoDarkCard, NazoDarkCard.copy(alpha = 0.85f))
                )
            )
            .border(
                width = 1.dp,
                color = NazoDarkCardAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(NazoDarkCardAccent.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier
                    .fillMaxSize()) {
                    drawCircle(
                        color = NazoOnDarkCard.copy(alpha = 0.15f),
                        radius = size.minDimension / 2.2f
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(NazoDarkCardAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SettingsBackupRestore,
                        contentDescription = null,
                        tint = NazoOnDarkCard,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(18.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (last?.automatic == true) "LAST BACKUP · AUTOMATIC" else "LAST BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoOnDarkCardMuted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = last?.let { formatBackupDate(it.epoch) } ?: "No backups yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = NazoOnDarkCard,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        
        Spacer(Modifier.height(18.dp))
        
        Surface(
            color = Color.Black.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (last == null) {
                Text(
                    text = fallbackSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoOnDarkCardMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    lineHeight = 20.sp
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "${last.records} ${if (last.records == 1) "record" else "records"}" +
                            " · ${formatBytes(last.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoOnDarkCard,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = last.categories.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoOnDarkCardMuted,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "press_scale"
    )
    // While an operation is running the rows dim and stop accepting taps, so
    // one backup/restore can never be launched on top of another.
    val contentAlpha = if (enabled) 1f else 0.45f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(contentAlpha)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NazoPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NazoPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
                lineHeight = 18.sp
            )
        }
        
        Spacer(Modifier.width(8.dp))

        if (trailingText != null) {
            Surface(
                color = NazoPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelLarge,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = NazoTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Inline fading overlay used for this screen's dialogs.
 *
 * [dismissible] = false makes it modal in the strict sense: the scrim swallows
 * taps instead of closing, and a [BackHandler] eats the back press / back
 * gesture while it is on screen. That is used for the backup and restore
 * confirmations, where an accidental swipe should not silently abandon — or
 * worse, ambiguously continue — a destructive operation. The only way out is
 * the explicit Cancel button.
 */
@Composable
private fun FadeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit
) {
    // Delegates to the shared adaptive dialog: a centred card in portrait, and
    // in landscape the same bottom drag sheet the app-icon picker uses, which
    // spans the full width and dims the whole screen. A centred card in a
    // ~360 dp-tall window had to scroll between two wide bands of scrim.
    NazoAdaptiveDialog(
        visible = visible,
        onDismiss = onDismiss,
        dismissible = dismissible,
        content = content,
    )
}

/**
 * Shown instead of the backup confirmation when there is nothing worth saving:
 * no quizzes played, no records, no practice deck, no profile. Only default
 * preferences exist at that point, so a backup would restore nothing. Rather
 * than write a file and report success, the flow stops here with an explanation
 * and no confirm button.
 */
@Composable
private fun NothingToBackUpContent(onClose: () -> Unit) {
    val landscape = isLandscape()
    Column(
        modifier = Modifier
            .then(
                // The bottom sheet already provides the surface in landscape.
                if (landscape) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(NazoSurface)
                        .border(
                            1.dp,
                            NazoTextSecondary.copy(alpha = 0.15f),
                            RoundedCornerShape(32.dp),
                        )
                }
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NazoTextSecondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Inbox,
                contentDescription = null,
                tint = NazoTextSecondary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Nothing to Back Up Yet",
            color = NazoTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You haven't played any quizzes or set up a profile, so a backup " +
                "would only contain default settings. Play a round or two and " +
                "come back — then there'll be real progress worth saving.",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        NazoPrimaryButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text("Got it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Shared informational dialog used both before a manual backup is written and
 * before a validated restore overwrites data. The category list is always real
 * bundle content supplied by the caller; rows are read-only on purpose — the
 * user cannot pick and choose, this only makes the operation feel deliberate.
 */
@Composable
private fun BackupContentsContent(
    title: String,
    message: String,
    accent: Color,
    icon: ImageVector,
    categories: List<BackupRepository.BackupCategory>,
    confirmLabel: String,
    /**
     * Irreversible overwrite (restore) — the confirm action wears the fixed
     * danger red instead of the theme accent, per the button vocabulary.
     */
    destructive: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Drives the staggered reveal once, when the dialog content first composes.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val landscape = isLandscape()
    Column(
        modifier = Modifier
            .then(
                // In landscape this renders inside a bottom sheet, which
                // already draws the surface, so the card drops its own
                // background, border and width cap and simply fills it.
                if (landscape) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(NazoSurface)
                        .border(
                            1.dp,
                            NazoTextSecondary.copy(alpha = 0.15f),
                            RoundedCornerShape(32.dp),
                        )
                }
            )
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            color = NazoTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NazoBackground,
                border = BorderStroke(1.dp, NazoTextSecondary.copy(alpha = 0.1f)),
            ) {
                Column(
                    modifier = Modifier
                        // Long bundles stay scrollable instead of pushing the
                        // buttons off-screen. In landscape the sheet itself is
                        // already height-capped and scrolls, so the inner list
                        // stays small to avoid two competing scroll regions.
                        .heightIn(max = if (isLandscape()) 120.dp else 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    categories.forEachIndexed { index, category ->
                        BackupCategoryRow(
                            category = category,
                            accent = accent,
                            revealed = revealed,
                            index = index,
                        )
                        if (index < categories.lastIndex) {
                            HorizontalDivider(
                                color = NazoTextSecondary.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NazoSecondaryButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(54.dp),
                muted = true,
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Restore overwrites data irreversibly, so its confirm wears the
            // fixed danger red; the backup preview keeps the theme accent.
            if (destructive) {
                NazoDangerButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Text(confirmLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                NazoPrimaryButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Text(confirmLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** One category line, sliding up and fading in a beat after the one above it. */
@Composable
private fun BackupCategoryRow(
    category: BackupRepository.BackupCategory,
    accent: Color,
    revealed: Boolean,
    index: Int,
) {
    val delay = (index * 55).coerceAtMost(440)
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 320, delayMillis = delay, easing = EaseOutCubic),
        label = "category_reveal_$index"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 14.dp.toPx()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = accent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "${category.entries}",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AutoBackupFreqContent(
    currentFreq: String,
    onSelect: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val landscape = isLandscape()
    Column(
        modifier = Modifier
            .then(
                // The bottom sheet already provides the surface in landscape.
                if (landscape) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(NazoSurface)
                        .border(
                            1.dp,
                            NazoTextSecondary.copy(alpha = 0.15f),
                            RoundedCornerShape(32.dp),
                        )
                }
            )
            .padding(28.dp),
    ) {
        Text(
            "Auto-Backup",
            color = NazoTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose how often Nazo saves a backup automatically to this device.",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NazoBackground,
            border = BorderStroke(1.dp, NazoTextSecondary.copy(alpha = 0.1f))
        ) {
            Column {
                listOf("off" to "Off", "daily" to "Daily", "weekly" to "Weekly").forEachIndexed { index, (value, label) ->
                    val selected = currentFreq == value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value, label) }
                            .padding(vertical = 16.dp, horizontal = 20.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (selected) NazoPrimary else NazoTextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.CheckCircle, 
                                contentDescription = null, 
                                tint = NazoPrimary, 
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(2.dp, NazoTextSecondary.copy(alpha = 0.3f), CircleShape)
                            )
                        }
                    }
                    if (index < 2) {
                        HorizontalDivider(color = NazoTextSecondary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        NazoSecondaryButton(
            onClick = onClose,
            muted = true,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NazoSurface)
                .border(1.dp, NazoTextSecondary.copy(alpha = 0.1f), CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NazoTextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 12.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = NazoTextSecondary.copy(alpha = 0.12f),
        thickness = 1.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/**
 * Status chip pinned under the header: spinner while the operation runs,
 * success (which clears itself), and failure — tap the failure chip to
 * dismiss it. This replaces the transient Toasts so the outcome is readable
 * in every orientation.
 */
@Composable
private fun StatusBanner(status: OpStatus?, onDismiss: () -> Unit) {
    if (status == null) return
    val container = when (status) {
        is OpStatus.Busy -> NazoPrimary.copy(alpha = 0.16f)
        is OpStatus.Done -> NazoSuccessBg
        is OpStatus.Failed -> NazoErrorBg
    }
    val content = when (status) {
        is OpStatus.Busy -> NazoTextPrimary
        is OpStatus.Done -> NazoSuccess
        is OpStatus.Failed -> NazoError
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(enabled = status is OpStatus.Failed, onClick = onDismiss)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
        when (status) {
            is OpStatus.Busy -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = NazoPrimary,
                strokeWidth = 2.dp,
            )
            is OpStatus.Done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            is OpStatus.Failed -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (status is OpStatus.Failed) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss message",
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Sizes are tiny JSON, so B / KB / MB with one decimal is plenty. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
}

private fun formatBackupDate(epoch: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(epoch))
}

private fun freqLabel(freq: String): String = when (freq) {
    "daily" -> "Daily"
    "weekly" -> "Weekly"
    else -> "Off"
}

