package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.ui.components.rememberHapticBack

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoDarkCard
import quiz.thaton3app.nazo.ui.theme.NazoDarkCardAccent
import quiz.thaton3app.nazo.ui.theme.NazoOnDarkCard
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
    // Empty state: null means no backup has ever been made on this device.
    val lastBackupDate: String? = null 

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
            
            LastBackupCard(date = lastBackupDate)
            
            Spacer(Modifier.height(24.dp))
            SectionLabel("MANUAL BACKUP")
            Spacer(Modifier.height(10.dp))
            
            SettingsCard {
                ActionRow(
                    icon = Icons.Filled.Upload,
                    title = "Create Local Backup",
                    subtitle = "Export your data as a JSON file",
                    onClick = { /* TODO: Launch export intent */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Folder,
                    title = "Restore Data",
                    subtitle = "Import a backup file from this device",
                    onClick = { /* TODO: Launch import intent */ }
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
                    trailingText = "Daily",
                    onClick = { /* TODO: Show frequency dialog */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Folder,
                    title = "Backup Location",
                    subtitle = "/storage/emulated/0/Nazo",
                    onClick = { /* TODO: Show location picker */ }
                )
            }
            
            Spacer(Modifier.height(32.dp))
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
private fun LastBackupCard(date: String?) {
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
                contentAlignment = Alignment.Center,
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
            text = if (date != null) {
                "42 quizzes, 6 provider profiles and all custom settings are included in the export bundle."
            } else {
                "Your quiz history and custom settings are currently not backed up. Create a manual backup below."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NazoOnDarkCardMuted
            // Removed the crashing lineHeight calculation here!
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
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
