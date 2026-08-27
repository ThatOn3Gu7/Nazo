package quiz.thaton3app.nazo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import quiz.thaton3app.nazo.data.backup.BackupScheduler
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.BackupRepository
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardAccent
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCardMuted
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

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
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        restoreUri = uri
        showRestoreConfirm = true
    }

    val autoBackupPath = BackupRepository.autoBackupPath(context)

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
            ScreenHeader(title = "Backup & Restore", onBackClick = onBackClick)
            Spacer(Modifier.height(20.dp))

            LastBackupCard(date = lastBackupText, summary = summaryText)

            Spacer(Modifier.height(24.dp))
            SectionLabel("MANUAL BACKUP")
            Spacer(Modifier.height(10.dp))

            SettingsCard {
                ActionRow(
                    icon = Icons.Filled.Upload,
                    title = "Create Local Backup",
                    subtitle = "Export your data as a JSON file",
                    onClick = {
                        val name = "Nazo_backup_${System.currentTimeMillis()}.json"
                        createLauncher.launch(name)
                    }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Folder,
                    title = "Restore Data",
                    subtitle = "Import a backup file from this device",
                    onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) }
                )
                RowDivider()
                ActionRow(
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

            Spacer(Modifier.height(24.dp))
            SectionLabel("AUTOMATION")
            Spacer(Modifier.height(10.dp))

            SettingsCard {
                ActionRow(
                    icon = Icons.Filled.Event,
                    title = "Auto-Backup Frequency",
                    subtitle = "How often backups are generated",
                    trailingText = freqLabel(backupPrefs.autoBackupFrequency),
                    onClick = { showFreqDialog = true }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Folder,
                    title = "Backup Location",
                    subtitle = autoBackupPath,
                    onClick = {
                        Toast.makeText(context, "Auto-backups are saved here", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showRestoreConfirm && restoreUri != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showRestoreConfirm = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(NazoSurface)
                    .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NazoError),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", color = NazoOnPrimary, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Restore backup?",
                    color = NazoTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "This will overwrite your current quiz stats, profile and settings " +
                        "with the data from the selected backup.",
                    color = NazoTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { showRestoreConfirm = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = NazoTextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NazoPrimary)
                            .clickable {
                                showRestoreConfirm = false
                                val uri = restoreUri!!
                                scope.launch {
                                    try {
                                        BackupRepository.importFromUri(context, uri)
                                        Toast.makeText(context, "Data restored", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Restore", color = NazoOnPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showFreqDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showFreqDialog = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(NazoSurface)
                    .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                    .padding(28.dp),
            ) {
                Text(
                    "Auto-Backup Frequency",
                    color = NazoTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose how often Nazo saves a backup to this device.",
                    color = NazoTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                listOf("off" to "Off", "daily" to "Daily", "weekly" to "Weekly").forEach { (value, label) ->
                    val selected = backupPrefs.autoBackupFrequency == value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                backupPrefs.autoBackupFrequency = value
                                BackupScheduler.apply(context, value)
                                showFreqDialog = false
                                Toast.makeText(context, "Auto-backup: $label", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            label,
                            color = NazoTextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = NazoPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (value != "weekly") {
                        HorizontalDivider(color = NazoBackground, thickness = 1.dp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NazoPrimary)
                        .clickable { showFreqDialog = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Close", color = NazoOnPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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
private fun LastBackupCard(date: String?, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoDarkCard)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NazoDarkCardAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsBackupRestore,
                    contentDescription = null,
                    tint = NazoOnDarkCard,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoOnDarkCardMuted,
                )
                Text(
                    text = date ?: "No backups yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoOnDarkCard,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoOnDarkCardMuted
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NazoTextPrimary,
            modifier = Modifier.size(24.dp)
        )
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatBackupDate(epoch: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(epoch))
}

private fun freqLabel(freq: String): String = when (freq) {
    "daily" -> "Daily"
    "weekly" -> "Weekly"
    else -> "Off"
}
