package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.settings.AccessibilityTextScale
import quiz.thaton3app.nazo.data.settings.TimerAssist
import quiz.thaton3app.nazo.sound.SoundTheme
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.components.rememberNazoPillCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/** One player-facing home for layout, visual, motion, timing, vibration, and audio assists. */
@Composable
fun AccessibilityScreen(
    reduceMotion: Boolean,
    systemReduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit,
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
    textScale: AccessibilityTextScale,
    onTextScaleChange: (AccessibilityTextScale) -> Unit,
    timerAssist: TimerAssist,
    onTimerAssistChange: (TimerAssist) -> Unit,
    classicHomeLayout: Boolean,
    onClassicHomeLayoutChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsEnabledChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    soundTheme: SoundTheme,
    onSoundThemeChange: (SoundTheme) -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = if (isLandscape()) 24.dp else 96.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        AccessibilityHeader(onBackClick)
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NazoSurface)
                .border(1.dp, NazoPrimary.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Filled.AccessibilityNew)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Make Nazo comfortable for you",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Changes apply immediately. Android's font size and Remove animations settings are always respected too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
            }
        }

        AccessibilitySection("LAYOUT") {
            AccessibilitySwitchRow(
                icon = Icons.Filled.Home,
                title = "Classic Home layout",
                subtitle = "Restore the familiar scrolling Home with Challenge Packs and Collections on the page",
                checked = classicHomeLayout,
                onCheckedChange = onClassicHomeLayoutChange,
            )
        }

        AccessibilitySection("VISION") {
            AccessibilitySwitchRow(
                icon = Icons.Filled.Contrast,
                title = "High contrast",
                subtitle = "Raise text to 7:1 and controls to 4.5:1 where the palette allows",
                checked = highContrast,
                onCheckedChange = onHighContrastChange,
            )
            AccessibilityDivider()
            ChoiceHeading(Icons.Filled.TextFields, "Text size", "Added on top of Android's selected font size")
            AccessibilityTextScale.values().forEach { option ->
                AccessibilityChoice(
                    title = option.label,
                    subtitle = option.description,
                    selected = textScale == option,
                    onClick = {
                        Haptics.soft(context)
                        onTextScaleChange(option)
                    },
                )
            }
        }

        AccessibilitySection("MOTION") {
            AccessibilitySwitchRow(
                icon = Icons.Filled.Animation,
                title = "Reduce motion",
                subtitle = if (systemReduceMotion) {
                    "Android Remove animations is active; Nazo is already reducing motion"
                } else {
                    "Remove decorative movement, staged entrances, and animated navigation distance"
                },
                checked = reduceMotion,
                onCheckedChange = onReduceMotionChange,
            )
        }

        AccessibilitySection("TIMING") {
            ChoiceHeading(Icons.Filled.Timer, "Timer assist", "All question modes and Guessing; Blitz keeps its 60-second rule")
            TimerAssist.values().forEach { option ->
                AccessibilityChoice(
                    title = option.label,
                    subtitle = option.description,
                    selected = timerAssist == option,
                    onClick = {
                        Haptics.soft(context)
                        onTimerAssistChange(option)
                    },
                )
            }
        }

        AccessibilitySection("FEEDBACK") {
            AccessibilitySwitchRow(
                icon = Icons.Filled.TouchApp,
                title = "Haptic feedback",
                subtitle = "Vibration for taps, answers, and countdown warnings",
                checked = hapticsEnabled,
                onCheckedChange = onHapticsEnabledChange,
            )
            AccessibilityDivider()
            AccessibilitySwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Sound effects",
                subtitle = "Audio cues for answers, results, and new records",
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
            )
            AccessibilityDivider()
            ChoiceHeading(
                Icons.Filled.MusicNote,
                "Sound style",
                if (soundEnabled) "Select a style to preview it" else "Preview a style without enabling all sounds",
            )
            SoundTheme.values().forEach { option ->
                AccessibilityChoice(
                    title = option.label,
                    subtitle = option.blurb,
                    selected = soundTheme == option,
                    onClick = {
                        Haptics.soft(context)
                        onSoundThemeChange(option)
                        Sounds.preview(context, option)
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccessibilityHeader(onBackClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoPillCornerMorphShape(
        interactionSource = interactionSource,
        pressedCornerPercent = 28f,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            interactionSource = interactionSource,
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(NazoSurface, shape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = NazoTextPrimary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = "Accessibility studio",
                style = MaterialTheme.typography.titleLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Vision, motion, timing, and feedback",
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
            )
        }
    }
}

@Composable
private fun AccessibilitySection(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = NazoPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun AccessibilitySwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 0.dp,
        pressedCorner = 12.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onValueChange = { value ->
                    Haptics.soft(context)
                    onCheckedChange(value)
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NazoTextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceHeading(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NazoTextSecondary)
        }
    }
}

@Composable
private fun AccessibilityChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 16.dp,
        pressedCorner = 8.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(if (selected) NazoSurfaceVariant else Color.Transparent, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = NazoTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NazoTextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) NazoPrimary else Color.Transparent)
                .border(2.dp, if (selected) Color.Transparent else NazoTextSecondary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = NazoOnPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(NazoPillUnselected),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = NazoPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun AccessibilityDivider() {
    HorizontalDivider(
        color = NazoTextSecondary.copy(alpha = 0.12f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
