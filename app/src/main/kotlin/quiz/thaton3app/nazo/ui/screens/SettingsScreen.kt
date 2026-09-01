package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.BuildConfig
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.*

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// No backend wiring yet — the on*Click callbacks are no-ops until each destination
// screen exists, per the incremental build plan.
@Composable
fun SettingsScreen(
    scrollState: ScrollState = rememberScrollState(),
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onOpenAiProvider: () -> Unit = {},
    onOpenStatistics: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenBackupRestore: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    forceOffline: Boolean = false,
    onForceOfflineChange: (Boolean) -> Unit = {},
    soundEnabled: Boolean = false,
    onSoundEnabledChange: (Boolean) -> Unit = {},
    remindersEnabled: Boolean = false,
    onRemindersEnabledChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            
            SettingsHeader(
                onBackClick = onBackClick
            )
            
            Spacer(Modifier.height(28.dp))
            
            SectionLabel("AI & ENGINE")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.VpnKey,
                    title = "AI Provider & API Keys",
                    subtitle = "Configure Gemini, OpenRouter, and model settings",
                    onClick = onOpenAiProvider,
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            SectionLabel("MODE")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsSwitchRow(
                    icon = Icons.Filled.SignalWifiOff,
                    title = "Offline mode",
                    subtitle = "Use the local question library only — no API calls",
                    checked = forceOffline,
                    onCheckedChange = onForceOfflineChange,
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            SectionLabel("FEEDBACK")
            Spacer(Modifier.height(8.dp))
            // Enabling the daily reminder needs POST_NOTIFICATIONS on Android 13+.
            // The toggle turns on either way; the worker independently re-checks the
            // permission before posting, so a later grant/revoke just works.
            val context = LocalContext.current
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* result handled implicitly — the worker checks before posting */ }
            SettingsCard {
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Sound effects",
                    subtitle = "Soft chimes for answers, results and new records",
                    checked = soundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Daily reminder",
                    subtitle = "One evening nudge when today's challenge is unplayed",
                    checked = remindersEnabled,
                    onCheckedChange = { v ->
                        if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onRemindersEnabledChange(v)
                    },
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            SectionLabel("GENERAL")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.BarChart,
                    title = "Statistics",
                    subtitle = "Total questions answered, accuracy rate, and streaks",
                    onClick = onOpenStatistics,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = "Appearance",
                    subtitle = "Theme options, green accent shades",
                    onClick = onOpenAppearance,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Backup,
                    title = "Backup & Restore",
                    subtitle = "Export or import your quiz history and custom settings",
                    onClick = onOpenBackupRestore,
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            SectionLabel("INFO")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "About",
                    subtitle = "App version ${BuildConfig.VERSION_NAME} & credits",
                    onClick = onOpenAbout,
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsHeader(
    onBackClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NazoSurface),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = NazoTextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Text(
            text = "Settings", 
            style = MaterialTheme.typography.titleLarge, 
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold
        )
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
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                .size(42.dp)
                .clip(CircleShape)
                .background(NazoPillUnselected),
            contentAlignment = Alignment.Center,
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
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = NazoTextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val trigger: (Boolean) -> Unit = { value ->
        Haptics.soft(context)
        onCheckedChange(value)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(NazoPillUnselected),
            contentAlignment = Alignment.Center,
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
        Switch(checked = checked, onCheckedChange = trigger)
    }
}

