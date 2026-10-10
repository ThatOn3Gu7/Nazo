package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.BuildConfig
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.NazoModalSheet
import quiz.thaton3app.nazo.ui.components.NazoSheetColumn
import quiz.thaton3app.nazo.ui.components.rememberRetained
import quiz.thaton3app.nazo.sound.SoundTheme
import quiz.thaton3app.nazo.sound.Sounds
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
// OptIn: rememberModalBottomSheetState for the Sound style chooser, the same
// Material 3 sheet API the Appearance screen already uses.
@OptIn(ExperimentalMaterial3Api::class)
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
    soundTheme: SoundTheme = SoundTheme.DEFAULT,
    onSoundThemeChange: (SoundTheme) -> Unit = {},
    remindersEnabled: Boolean = false,
    onRemindersEnabledChange: (Boolean) -> Unit = {},
) {
    // Retained so the chooser survives a rotation like every other sheet.
    var showSoundStyleSheet by rememberRetained("Settings.showSoundStyleSheet") { false }
    val soundSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Box (not Column) so the content can scroll UNDER the bottom nav, which is
    // drawn by NazoApp on top of this screen. The bottom padding below reserves
    // room for it so the last row is always reachable.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                // Portrait reserves room for the overlaid bottom nav bar.
                // Landscape moves that bar to a RIGHT-EDGE RAIL, so the same
                // 96.dp became dead space and the INFO section could be
                // scrolled up into the middle of the screen. A small inset is
                // all landscape needs.
                .padding(bottom = if (isLandscape()) 16.dp else 96.dp)
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
                    subtitle = "Configure Gemini, OpenRouter, OpenCode, and model settings",
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
                // Same SettingsRow component as every other "opens a chooser"
                // row, so the card keeps its existing look; the subtitle just
                // reports the current choice the way Appearance rows do.
                SettingsRow(
                    icon = Icons.Filled.MusicNote,
                    title = "Sound style",
                    subtitle = "${soundTheme.label} — ${soundTheme.blurb}",
                    onClick = { showSoundStyleSheet = true },
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

    // Same NazoModalSheet + option-card pattern Appearance already uses for
    // "pick a style", so this introduces no new design language.
    if (showSoundStyleSheet) {
        val sheetContext = LocalContext.current
        NazoModalSheet(
            onDismissRequest = { showSoundStyleSheet = false },
            sheetState = soundSheetState,
        ) {
            NazoSheetColumn {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NazoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = NazoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sound style",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NazoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (soundEnabled) {
                        "Tap a style to hear it. It applies to every sound in the app."
                    } else {
                        "Turn on Sound effects above to hear these."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))

                SoundTheme.values().forEach { theme ->
                    SoundStyleOptionCard(
                        theme = theme,
                        selected = soundTheme == theme,
                        onClick = {
                            Haptics.soft(sheetContext)
                            // Persist first, then audition, so the preview is
                            // the voice that was just selected.
                            onSoundThemeChange(theme)
                            Sounds.preview(sheetContext)
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SoundStyleOptionCard(
    theme: SoundTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NazoSurfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.label,
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = theme.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) NazoPrimary else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (selected) Color.Transparent else NazoTextSecondary.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = NazoOnPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
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

