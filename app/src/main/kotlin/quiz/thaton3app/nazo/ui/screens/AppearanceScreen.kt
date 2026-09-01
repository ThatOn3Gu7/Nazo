package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DesktopMac
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import quiz.thaton3app.nazo.ui.theme.Accents
import quiz.thaton3app.nazo.ui.theme.previewColors
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
@Composable
fun AppearanceScreen(
    currentMode: String = "system",
    currentAccent: String = "mint",
    onModeChange: (String) -> Unit = {},
    onAccentChange: (String) -> Unit = {},
    iconFollowsOsTheme: Boolean = true,
    onIconFollowsOsThemeChange: (Boolean) -> Unit = {},
    floatingNavBar: Boolean = false,
    onFloatingNavBarChange: (Boolean) -> Unit = {},
    revealStyle: String = "pixel",
    onRevealStyleChange: (String) -> Unit = {},
    backgroundStyle: String = "shapes",
    onBackgroundStyleChange: (String) -> Unit = {},
    touchRipples: Boolean = true,
    onTouchRipplesChange: (Boolean) -> Unit = {},
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    val context = LocalContext.current

    val isDark = when (currentMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    var iconFollowsOsThemeChecked by remember { mutableStateOf(iconFollowsOsTheme) }
    var floatingNavBarChecked by remember { mutableStateOf(floatingNavBar) }
    var touchRipplesChecked by remember { mutableStateOf(touchRipples) }

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
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = rememberHapticBack(onBackClick),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    color = NazoTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            
            // --- THEME MODE SECTION ---
            SectionHeader("THEME MODE")
            
            ThemeModeRow(
                icon = Icons.Outlined.DesktopMac,
                title = "System Default",
                subtitle = "Follow device setting",
                isSelected = currentMode == ThemeMode.System.mode,
                onClick = { Haptics.soft(context); onModeChange(ThemeMode.System.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.LightMode,
                title = "Light",
                subtitle = "Mint daylight surface",
                isSelected = currentMode == ThemeMode.Light.mode,
                onClick = { Haptics.soft(context); onModeChange(ThemeMode.Light.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.ModeNight,
                title = "Dark",
                subtitle = "Deep forest surface",
                isSelected = currentMode == ThemeMode.Dark.mode,
                onClick = { Haptics.soft(context); onModeChange(ThemeMode.Dark.mode) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- COLOR ACCENTS SECTION ---
            SectionHeader("COLOR ACCENTS")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val defaultAccent = Accents.firstOrNull { it.id == "mint" } ?: Accents.first()
                    ColorAccentCircle(
                        previewColors = previewColors(defaultAccent.id, isDark),
                        isSelected = currentAccent == defaultAccent.id,
                        onClick = { Haptics.soft(context); onAccentChange(defaultAccent.id) }
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .height(32.dp)
                            .align(Alignment.CenterVertically),
                        color = NazoTextSecondary.copy(alpha = 0.25f)
                    )
                    Accents.filter { it.id != "mint" }.forEach { accent ->
                        ColorAccentCircle(
                            previewColors = previewColors(accent.id, isDark),
                            isSelected = currentAccent == accent.id,
                            onClick = { Haptics.soft(context); onAccentChange(accent.id) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = buildAnnotatedString {
                        append("Each accent themes the whole app — background, cards, toggles and text.\nCurrently using ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(Accents.firstOrNull { it.id == currentAccent }?.label ?: "Mint Green")
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
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
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

            // --- AMBIENT BACKGROUND ---
            SectionHeader("AMBIENT BACKGROUND")

            ThemeModeRow(
                icon = Icons.Outlined.BlurOn,
                title = "Floating Shapes",
                subtitle = "Drifting geometric particles across the backdrop",
                isSelected = backgroundStyle == "shapes",
                onClick = { Haptics.soft(context); onBackgroundStyleChange("shapes") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Constellation Web",
                subtitle = "Twinkling star nodes connected by glowing proximity web lines",
                isSelected = backgroundStyle == "constellation",
                onClick = { Haptics.soft(context); onBackgroundStyleChange("constellation") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.Speed,
                title = "Digital Rain",
                subtitle = "Subtle vertical falling streams of glowing drops",
                isSelected = backgroundStyle == "rain",
                onClick = { Haptics.soft(context); onBackgroundStyleChange("rain") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.Lightbulb,
                title = "Glowing Orbs",
                subtitle = "Soft wandering radial gradient orbs that pulse and drift",
                isSelected = backgroundStyle == "orbs",
                onClick = { Haptics.soft(context); onBackgroundStyleChange("orbs") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            ) {
                LayoutToggleRow(
                    title = "Interactive touch bursts",
                    subtitle = "Spawn glowing ripple rings and sparkle bursts wherever you tap",
                    isChecked = touchRipplesChecked,
                    onCheckedChange = {
                        touchRipplesChecked = it
                        onTouchRipplesChange(it)
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
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            ) {
                LayoutToggleRow(
                    title = "Floating navigation bar",
                    subtitle = "Elevated pill that lets the background show around it",
                    isChecked = floatingNavBarChecked,
                    onCheckedChange = {
                        floatingNavBarChecked = it
                        onFloatingNavBarChange(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- GUESSING REVEAL SECTION ---
            SectionHeader("GUESSING REVEAL")

            ThemeModeRow(
                icon = Icons.Outlined.BlurOn,
                title = "Blur",
                subtitle = "The mystery image starts foggy and sharpens as time runs out",
                isSelected = revealStyle == "blur",
                onClick = { Haptics.soft(context); onRevealStyleChange("blur") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.GridView,
                title = "Pixelate",
                subtitle = "Starts as a blocky mosaic and resolves into the image",
                isSelected = revealStyle == "pixel",
                onClick = { Haptics.soft(context); onRevealStyleChange("pixel") }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(40.dp))
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
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
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
    previewColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, NazoTextPrimary, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val slice = 360f / previewColors.size
            previewColors.forEachIndexed { i, c ->
                drawArc(
                    color = c,
                    startAngle = i * slice,
                    sweepAngle = slice,
                    useCenter = true,
                    topLeft = Offset(0f, 0f),
                    size = Size(diameter, diameter),
                )
            }
        }
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
