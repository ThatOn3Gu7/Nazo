package quiz.thaton3app.nazo.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.backup.BackupScheduler
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.BackupRepository
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val backupPrefs = remember { BackupPrefs(context) }
    val statsStore = remember { QuizStatsStore(context) }
    val profilePrefs = remember { ProfilePreferences(context) }
    val themePrefs = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()

    val lastBackupText = backupPrefs.lastBackupEpoch?.let { formatBackupDate(it) }

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

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showFreqDialog by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                BackupRepository.exportToUri(context, uri)
                backupPrefs.lastBackupEpoch = System.currentTimeMillis()
                Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (BackupRepository.validateUri(context, uri)) {
                restoreUri = uri
                showRestoreConfirm = true
            } else {
                Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
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
            
            Spacer(Modifier.height(24.dp))

            AnimatedLastBackupCard(date = lastBackupText, summary = summaryText)

            Spacer(Modifier.height(32.dp))
            
            Column {
                SectionLabel("MANUAL BACKUP")
                Spacer(Modifier.height(12.dp))

                SettingsCard {
                    AnimatedActionRow(
                        icon = Icons.Filled.Upload,
                        title = "Create Local Backup",
                        subtitle = "Export your data as a JSON file",
                        onClick = {
                            val name = "Nazo_backup_${System.currentTimeMillis()}.json"
                            createLauncher.launch(name)
                        }
                    )
                    RowDivider()
                    AnimatedActionRow(
                        icon = Icons.Filled.Folder,
                        title = "Restore Data",
                        subtitle = "Import a backup file from this device",
                        onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) }
                    )
                    RowDivider()
                    AnimatedActionRow(
                        icon = Icons.Filled.SettingsBackupRestore,
                        title = "Restore from Auto-Backup",
                        subtitle = "Use the last automatic backup on this device",
                        onClick = {
                            scope.launch {
                                try {
                                    BackupRepository.importFromPath(context, autoBackupPath)
                                    Toast.makeText(context, "Restored from auto-backup", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No auto-backup yet: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Auto-backups are saved here", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        // Fading Dialogs (Keeping the fade here since popups aren't part of the main nav graph)
        FadeDialog(
            visible = showRestoreConfirm && restoreUri != null,
            onDismiss = { showRestoreConfirm = false }
        ) {
            RestoreConfirmContent(
                onCancel = { showRestoreConfirm = false },
                onRestore = {
                    showRestoreConfirm = false
                    val uri = restoreUri!!
                    scope.launch {
                        try {
                            BackupRepository.importFromUri(context, uri)
                            Toast.makeText(context, "Data restored successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Auto-backup set to: $label", Toast.LENGTH_SHORT).show()
                },
                onClose = { showFreqDialog = false }
            )
        }
    }
}

@Composable
private fun AnimatedLastBackupCard(date: String?, summary: String) {
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
                    text = "LAST BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoOnDarkCardMuted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date ?: "No backups yet",
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
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoOnDarkCardMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AnimatedActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "press_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
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

@Composable
private fun FadeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )) {
                content()
            }
        }
    }
}

@Composable
private fun RestoreConfirmContent(onCancel: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(NazoError.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NazoError),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Restore Data?",
            color = NazoTextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This will overwrite your current quiz stats, profile, and settings with the data from the selected backup. This action cannot be undone.",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NazoSurface, contentColor = NazoTextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(NazoTextSecondary.copy(0.3f), NazoTextSecondary.copy(0.3f)))),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = onRestore,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NazoPrimary, contentColor = NazoOnPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutoBackupFreqContent(
    currentFreq: String,
    onSelect: (String, String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
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
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NazoSurface, contentColor = NazoTextPrimary),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(NazoTextSecondary.copy(0.3f), NazoTextSecondary.copy(0.3f)))),
            shape = RoundedCornerShape(16.dp)
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
        color = NazoBackground,
        thickness = 1.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
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

