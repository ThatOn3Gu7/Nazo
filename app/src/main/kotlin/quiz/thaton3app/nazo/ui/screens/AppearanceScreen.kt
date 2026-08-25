package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DesktopMac
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.*

// Mock data enums for the prototype state
private enum class ThemeMode(val mode: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}
private enum class AccentColor(val accentName: String, val color: Color, val label: String) {
    Mint("mint", Color(0xFF246D4C), "Mint Green"),
    Rose("rose", Color(0xFFC05C72), "Rose"),
    Indigo("indigo", Color(0xFF324888), "Indigo"),
    Bronze("bronze", Color(0xFFAD7931), "Bronze"),
    Slate("slate", Color(0xFF4C5E57), "Slate"),
}

@Composable
fun AppearanceScreen(
    currentMode: String = "system",
    currentAccent: String = "mint",
    onModeChange: (String) -> Unit = {},
    onAccentChange: (String) -> Unit = {},
    iconFollowsOsTheme: Boolean = true,
    onIconFollowsOsThemeChange: (Boolean) -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    var compactViewChecked by remember { mutableStateOf(true) }
    var cardStyleChecked by remember { mutableStateOf(false) }
    var iconFollowsOsThemeChecked by remember { mutableStateOf(iconFollowsOsTheme) }

    Scaffold(
        containerColor = NazoBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = rememberHapticBack(onBackClick),
                    modifier = Modifier
                        .background(NazoSurface, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = NazoTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    color = NazoTextPrimary
                )
            }
        },
        bottomBar = {
            NazoBottomNav(
                selected = NazoTab.Settings,
                onHomeClick = onHomeClick,
                onSettingsClick = onBackClick,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            
            // --- THEME MODE SECTION ---
            SectionHeader("THEME MODE")
            
            ThemeModeRow(
                icon = Icons.Outlined.DesktopMac,
                title = "System Default",
                subtitle = "Follow device setting",
                isSelected = currentMode == ThemeMode.System.mode,
                onClick = { onModeChange(ThemeMode.System.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.LightMode,
                title = "Light",
                subtitle = "Mint daylight surface",
                isSelected = currentMode == ThemeMode.Light.mode,
                onClick = { onModeChange(ThemeMode.Light.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.ModeNight,
                title = "Dark",
                subtitle = "Deep forest surface",
                isSelected = currentMode == ThemeMode.Dark.mode,
                onClick = { onModeChange(ThemeMode.Dark.mode) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- COLOR ACCENTS SECTION ---
            SectionHeader("COLOR ACCENTS")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccentColor.entries.forEach { accent ->
                        ColorAccentCircle(
                            color = accent.color,
                            isSelected = currentAccent == accent.accentName,
                            onClick = { onAccentChange(accent.accentName) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = buildAnnotatedString {
                        append("Material You accents recolor cards, chips and progress rings.\nCurrently using ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(AccentColor.entries.firstOrNull { it.accentName == currentAccent }?.label ?: "Mint Green")
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- APP ICON SECTION ---
            SectionHeader("APP ICON")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
            ) {
                LayoutToggleRow(
                    title = "Match icon to system theme",
                    subtitle = "Switch the launcher icon between light and dark with your device theme",
                    isChecked = iconFollowsOsThemeChecked,
                    onCheckedChange = {
                        iconFollowsOsThemeChecked = it
                        onIconFollowsOsThemeChange(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- LAYOUT SECTION ---
            SectionHeader("LAYOUT")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
            ) {
                LayoutToggleRow(
                    title = "Compact List View",
                    subtitle = "Denser rows for long quiz histories",
                    isChecked = compactViewChecked,
                    onCheckedChange = { compactViewChecked = it }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = NazoBackground, 
                    thickness = 2.dp
                )
                
                LayoutToggleRow(
                    title = "Card Style",
                    subtitle = "Elevated tonal cards with 24dp corners",
                    isChecked = cardStyleChecked,
                    onCheckedChange = { cardStyleChecked = it }
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp)) // bottom padding before nav
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoTextSecondary,
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun ThemeModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) NazoDarkCard else NazoSurface
    val contentColor = if (isSelected) Color.White else NazoTextPrimary
    val subtitleColor = if (isSelected) Color.White.copy(alpha = 0.7f) else NazoTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor
            )
        }

        // Radio indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (isSelected) Color.Transparent else NazoPillUnselected,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = NazoDarkCard,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorAccentCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            // Add a subtle border if selected, matching the mockup's inner padding look
            .then(
                if (isSelected) Modifier.border(2.dp, NazoTextPrimary, CircleShape) 
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected Accent",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LayoutToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val trigger: (Boolean) -> Unit = { value ->
        Haptics.soft(context)
        onCheckedChange(value)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { trigger(!isChecked) }
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = trigger,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NazoDarkCard,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = NazoPillUnselected,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
