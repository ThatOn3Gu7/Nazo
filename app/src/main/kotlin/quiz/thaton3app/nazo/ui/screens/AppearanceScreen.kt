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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DesktopMac
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import quiz.thaton3app.nazo.ui.theme.Accents
import quiz.thaton3app.nazo.ui.theme.previewColors
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.components.CELEBRATION_STYLES
import quiz.thaton3app.nazo.ui.components.CelebrationStyle
import quiz.thaton3app.nazo.ui.components.drawCelebrationPreview
import quiz.thaton3app.nazo.ui.theme.*

// Mock data enums for the prototype state
private enum class ThemeMode(val mode: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

/** Registry of the ambient background effects (id, label, one-line blurb). */
private data class BackgroundEffect(val id: String, val label: String, val blurb: String)

private val BACKGROUND_EFFECTS = listOf(
    BackgroundEffect("none", "None", "Clean, still backdrop — no moving particles"),
    BackgroundEffect("shapes", "Floating Shapes", "Drifting geometric particles"),
    BackgroundEffect("constellation", "Constellation Web", "Twinkling stars linked by glowing lines"),
    BackgroundEffect("rain", "Digital Rain", "Falling streams of glowing drops"),
    BackgroundEffect("orbs", "Glowing Orbs", "Soft wandering gradient orbs"),
)

@OptIn(ExperimentalMaterial3Api::class)
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
    guessAutoCrop: Boolean = true,
    onGuessAutoCropChange: (Boolean) -> Unit = {},
    backgroundStyle: String = "shapes",
    onBackgroundStyleChange: (String) -> Unit = {},
    celebrationStyle: String = "burst",
    onCelebrationStyleChange: (String) -> Unit = {},
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
    var guessAutoCropChecked by remember { mutableStateOf(guessAutoCrop) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    val effectsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCelebrationSheet by remember { mutableStateOf(false) }
    val celebrationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


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
                celestial = "cycle",
                onClick = { Haptics.soft(context); onModeChange(ThemeMode.System.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.LightMode,
                title = "Light",
                subtitle = "Mint daylight surface",
                isSelected = currentMode == ThemeMode.Light.mode,
                celestial = "sun",
                onClick = { Haptics.soft(context); onModeChange(ThemeMode.Light.mode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeModeRow(
                icon = Icons.Outlined.ModeNight,
                title = "Dark",
                subtitle = "Deep forest surface",
                isSelected = currentMode == ThemeMode.Dark.mode,
                celestial = "moon",
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

            // Single entry point — the effect list (with live previews and a
            // "None" option) lives in a bottom sheet that slides up.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable {
                        Haptics.soft(context)
                        showEffectsSheet = true
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = NazoTextPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background effects",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                    )
                    Text(
                        text = "Currently: " + (BACKGROUND_EFFECTS.firstOrNull { it.id == backgroundStyle }?.label ?: "Floating Shapes"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NazoTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- CELEBRATIONS ---
            SectionHeader("CELEBRATIONS")

            // Single entry point — the confetti variants (with live previews
            // and a "None" option) live in a bottom sheet, mirroring the
            // ambient-background pattern above.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable {
                        Haptics.soft(context)
                        showCelebrationSheet = true
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Celebration,
                    contentDescription = null,
                    tint = NazoTextPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Victory confetti",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                    )
                    Text(
                        text = "Currently: " + (CELEBRATION_STYLES.firstOrNull { it.id == celebrationStyle }?.label ?: "Classic Burst"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NazoTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
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

            // --- GUESSING GAME SECTION ---
            SectionHeader("GUESSING GAME")

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
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            ) {
                LayoutToggleRow(
                    title = "Auto-crop mystery images",
                    subtitle = "Reframe each round's image on the character's face and upper body. Off shows the original",
                    isChecked = guessAutoCropChecked,
                    onCheckedChange = {
                        guessAutoCropChecked = it
                        onGuessAutoCropChange(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // --- BACKGROUND EFFECTS SHEET ---
    // Slides up from the bottom (same ModalBottomSheet pattern as the Home
    // provider switcher). Every option card plays a LIVE miniature of its
    // effect inside its own container, so the user can preview all styles at
    // once without committing. Selecting applies instantly and keeps the
    // sheet open for comparison; outside tap / back / swipe-down dismisses.
    if (showEffectsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEffectsSheet = false },
            sheetState = effectsSheetState,
            containerColor = NazoSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(NazoTextSecondary.copy(alpha = 0.3f))
                )
            }
        ) {
            // One shared frame clock drives every preview card; unbounded and
            // monotonic (same seamless-loop principle as AmbientBackground).
            // Lives only while the sheet is composed.
            val previewTime = remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) {
                val startNanos = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        previewTime.floatValue = (now - startNanos) / 30_000_000_000f
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NazoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = NazoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Background effects",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NazoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Live previews — pick a vibe, or switch effects off entirely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))

                BACKGROUND_EFFECTS.forEach { effect ->
                    EffectOptionCard(
                        effect = effect,
                        selected = backgroundStyle == effect.id,
                        time = { previewTime.floatValue },
                        onClick = {
                            Haptics.soft(context)
                            onBackgroundStyleChange(effect.id)
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // --- CELEBRATIONS SHEET ---
    // Same pattern as the background-effects sheet: every option card plays a
    // LIVE miniature of its confetti variant (a looping preview of how the
    // real game-completion celebration behaves), driven by one shared clock.
    // Selecting applies instantly and keeps the sheet open for comparison.
    if (showCelebrationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCelebrationSheet = false },
            sheetState = celebrationSheetState,
            containerColor = NazoSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(NazoTextSecondary.copy(alpha = 0.3f))
                )
            }
        ) {
            val previewTime = remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) {
                val startNanos = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        previewTime.floatValue = (now - startNanos) / 30_000_000_000f
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NazoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Celebration,
                            contentDescription = null,
                            tint = NazoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Victory confetti",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NazoTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Live previews of the end-of-game cheer — colors follow your accent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))

                CELEBRATION_STYLES.forEach { style ->
                    CelebrationOptionCard(
                        style = style,
                        selected = celebrationStyle == style.id,
                        time = { previewTime.floatValue },
                        onClick = {
                            Haptics.soft(context)
                            onCelebrationStyleChange(style.id)
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
    onClick: () -> Unit,
    celestial: String = "", // "" | "sun" | "moon" | "cycle"
) {
    val backgroundColor = if (isSelected) NazoDarkCard else NazoSurface
    val contentColor = if (isSelected) Color.White else NazoTextPrimary
    val subtitleColor = if (isSelected) Color.White.copy(alpha = 0.7f) else NazoTextSecondary

    // One-shot celestial transit: when this row BECOMES the selected theme,
    // a sun ("sun"), a moon ("moon"), or a full sun-then-moon day/night
    // cycle ("cycle") rises and falls in an arc across the row container.
    // The body enters and exits through the bottom edge (the row's clip does
    // the masking), and progress is read only in the draw phase.
    val transit = remember { Animatable(0f) }
    var wasSelected by remember { mutableStateOf(isSelected) }
    LaunchedEffect(isSelected) {
        val becameSelected = isSelected && !wasSelected
        wasSelected = isSelected
        if (celestial.isEmpty()) return@LaunchedEffect
        // ALWAYS clear any leftover mid-flight value first: rapid theme
        // switching cancels the previous run mid-animateTo, which would
        // otherwise leave the sun/moon frozen inside the deselected row.
        // This new effect instance runs right after the old one is
        // cancelled, so the reset is deterministic.
        transit.snapTo(0f)
        if (becameSelected) {
            transit.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (celestial == "cycle") 2600 else 1500,
                    easing = LinearEasing,
                ),
            )
            transit.snapTo(0f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .drawBehind {
                val p = transit.value
                if (p > 0f && p < 1f) drawCelestialTransit(celestial, p, backgroundColor)
            }
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

// ---------------------------------------------------------------------------
// Background-effects sheet: option card + live miniature effect previews
// ---------------------------------------------------------------------------

/**
 * One selectable effect in the sheet. The card's own container doubles as a
 * live preview: a Canvas painted behind the label plays a miniature version
 * of the effect, driven by the sheet's shared clock. `time` is read inside
 * the draw phase only, so the animation never recomposes the card.
 */
@Composable
private fun EffectOptionCard(
    effect: BackgroundEffect,
    selected: Boolean,
    time: () -> Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NazoSurfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawEffectPreview(effect.id, time())
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = effect.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = effect.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                    maxLines = 1,
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
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = NazoOnPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private const val TAU = (2.0 * PI).toFloat()

/**
 * One selectable confetti variant in the Celebrations sheet. Identical
 * anatomy to EffectOptionCard: the card's own Canvas plays a live looping
 * miniature of the celebration behind the label, driven by the shared clock
 * and read only in the draw phase.
 */
@Composable
private fun CelebrationOptionCard(
    style: CelebrationStyle,
    selected: Boolean,
    time: () -> Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NazoSurfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCelebrationPreview(style.id, time())
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = style.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                    maxLines = 1,
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
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = NazoOnPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}


// Constellation preview stars (fractional base positions + drift velocities).
// Top-level so nothing is allocated per frame; the position buffers below are
// reused every draw (UI-thread only, so sharing them across cards is safe).
private val PREVIEW_STAR_X = floatArrayOf(0.10f, 0.30f, 0.52f, 0.68f, 0.88f, 0.42f)
private val PREVIEW_STAR_Y = floatArrayOf(0.30f, 0.72f, 0.26f, 0.62f, 0.38f, 0.48f)
private val PREVIEW_STAR_VX = floatArrayOf(0.8f, -0.6f, 0.7f, -0.9f, 0.5f, 0.65f)
private val PREVIEW_STAR_VY = floatArrayOf(0.5f, 0.7f, -0.6f, 0.4f, -0.8f, -0.5f)
private val PREVIEW_BUF_X = FloatArray(PREVIEW_STAR_X.size)
private val PREVIEW_BUF_Y = FloatArray(PREVIEW_STAR_Y.size)

/**
 * Miniature, sped-up renditions of each ambient style, scaled to the card and
 * drawn at slightly higher alpha than the real backgrounds so they read at
 * preview size. `t` is the sheet's unbounded clock (1.0 = 30s), so — like the
 * real AmbientBackground — every preview is a seamless endless loop.
 */
private fun DrawScope.drawEffectPreview(style: String, t: Float) {
    val w = size.width
    val h = size.height
    val canvas = drawContext.canvas
    when (style) {
        "shapes" -> {
            val stroke = Stroke(width = 3f)
            // Drifting circle
            drawCircle(
                color = NazoPrimary.copy(alpha = 0.42f),
                radius = h * 0.17f,
                center = Offset(
                    (0.16f + 0.04f * sin(t * 5f * TAU)) * w,
                    (0.55f + 0.16f * cos(t * 4f * TAU)) * h,
                ),
                style = stroke,
            )
            // Rotating square
            val sq = h * 0.14f
            canvas.save()
            canvas.translate(
                (0.48f + 0.05f * cos(t * 6f * TAU + 1.3f)) * w,
                (0.42f + 0.18f * sin(t * 5f * TAU + 0.7f)) * h,
            )
            canvas.rotate(t * 2f * 360f)
            drawRect(
                color = NazoSuccess.copy(alpha = 0.38f),
                topLeft = Offset(-sq, -sq),
                size = Size(sq * 2f, sq * 2f),
                style = stroke,
            )
            canvas.restore()
            // Counter-rotating diamond
            val dm = h * 0.15f
            canvas.save()
            canvas.translate(
                (0.80f + 0.05f * sin(t * 4f * TAU + 2.4f)) * w,
                (0.50f + 0.17f * cos(t * 6f * TAU + 1.9f)) * h,
            )
            canvas.rotate(45f - t * 1.5f * 360f)
            drawRect(
                color = NazoError.copy(alpha = 0.32f),
                topLeft = Offset(-dm, -dm),
                size = Size(dm * 2f, dm * 2f),
                style = stroke,
            )
            canvas.restore()
        }

        "constellation" -> {
            val n = PREVIEW_STAR_X.size
            for (i in 0 until n) {
                PREVIEW_BUF_X[i] = (((PREVIEW_STAR_X[i] + t * PREVIEW_STAR_VX[i] * 6f) % 1f) + 1f) % 1f * w
                PREVIEW_BUF_Y[i] = (((PREVIEW_STAR_Y[i] + t * PREVIEW_STAR_VY[i] * 6f) % 1f) + 1f) % 1f * h
            }
            val maxDist = w * 0.22f
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val dist = hypot(PREVIEW_BUF_X[j] - PREVIEW_BUF_X[i], PREVIEW_BUF_Y[j] - PREVIEW_BUF_Y[i])
                    if (dist < maxDist) {
                        drawLine(
                            color = NazoPrimary.copy(alpha = (1f - dist / maxDist) * 0.5f),
                            start = Offset(PREVIEW_BUF_X[i], PREVIEW_BUF_Y[i]),
                            end = Offset(PREVIEW_BUF_X[j], PREVIEW_BUF_Y[j]),
                            strokeWidth = 1.5f,
                        )
                    }
                }
            }
            for (i in 0 until n) {
                val pulse = (sin(t * 40f + i * 1.3f) * 0.4f + 0.6f).coerceIn(0.2f, 1f)
                val c = Offset(PREVIEW_BUF_X[i], PREVIEW_BUF_Y[i])
                drawCircle(color = NazoPrimary.copy(alpha = 0.30f * pulse), radius = 7f, center = c)
                drawCircle(color = NazoPrimary.copy(alpha = 0.85f * pulse), radius = 3.5f, center = c)
            }
        }

        "rain" -> {
            val len = h * 0.45f
            for (i in 0 until 7) {
                val x = (0.07f + i * 0.14f) * w
                val speed = 6f + (i % 3) * 2.5f
                val yFrac = ((t * speed + i * 0.37f) % 1f + 1f) % 1f
                val y = yFrac * (h + len) - len
                drawLine(
                    color = NazoPrimary.copy(alpha = 0.40f),
                    start = Offset(x, y),
                    end = Offset(x, y + len),
                    strokeWidth = 2.5f,
                )
                drawCircle(
                    color = NazoSuccess.copy(alpha = 0.85f),
                    radius = 3f,
                    center = Offset(x, y + len),
                )
            }
        }

        "orbs" -> {
            for (i in 0 until 3) {
                val phase = i * 2.1f
                val cx = (0.20f + i * 0.30f + 0.06f * sin(t * 5f * TAU + phase)) * w
                val cy = (0.50f + 0.20f * cos(t * 4f * TAU + phase * 1.3f)) * h
                val color = when (i) {
                    0 -> NazoPrimary
                    1 -> Color(0xFF9C6ADE)
                    else -> Color(0xFFFFA726)
                }
                drawCircle(color = color.copy(alpha = 0.10f), radius = h * 0.55f, center = Offset(cx, cy))
                drawCircle(color = color.copy(alpha = 0.22f), radius = h * 0.32f, center = Offset(cx, cy))
            }
        }

        // "none" -> intentionally draws nothing: the plain card IS the preview.
    }
}

// ---------------------------------------------------------------------------
// Celestial transit — the theme-mode switch animation
// ---------------------------------------------------------------------------
// The body follows a ballistic arc: it rises out of the row's bottom edge on
// the left, peaks at ~20% height mid-row, and sets through the bottom edge on
// the right. Entry/exit masking is free — the row's rounded clip cuts it off.

private fun DrawScope.drawCelestialTransit(kind: String, p: Float, rowColor: Color) {
    when (kind) {
        "sun" -> drawSunTransit(p)
        "moon" -> drawMoonTransit(p, rowColor)
        // "cycle" (System Default): a miniature day/night cycle — the sun
        // crosses with its clouds, dusk falls as they dissolve, then the
        // moon follows with the stars coming out for the night shift.
        "cycle" -> if (p < 0.5f) drawSunTransit(p * 2f) else drawMoonTransit((p - 0.5f) * 2f, rowColor)
    }
}

private fun DrawScope.transitCenter(p: Float, r: Float): Offset {
    val cx = size.width * (0.06f + 0.88f * p)
    val cy = size.height + r * 2f - sin(p * PI.toFloat()) * (size.height * 0.80f + r * 2f)
    return Offset(cx, cy)
}

// Deterministic scatter for the night sky (fractions of the row size).
private val NIGHT_STAR_X = floatArrayOf(0.08f, 0.17f, 0.27f, 0.38f, 0.47f, 0.58f, 0.68f, 0.78f, 0.88f, 0.94f, 0.32f, 0.62f)
private val NIGHT_STAR_Y = floatArrayOf(0.30f, 0.68f, 0.20f, 0.55f, 0.15f, 0.62f, 0.25f, 0.58f, 0.32f, 0.70f, 0.82f, 0.85f)
private val NIGHT_STAR_R = floatArrayOf(0.045f, 0.030f, 0.050f, 0.028f, 0.055f, 0.035f, 0.048f, 0.030f, 0.042f, 0.026f, 0.032f, 0.038f)
private val NIGHT_STAR_PHASE = floatArrayOf(0.0f, 1.1f, 2.3f, 3.2f, 4.4f, 5.1f, 0.7f, 1.9f, 2.8f, 3.9f, 4.8f, 5.7f)

// Drifting daytime clouds: start x, y, scale (fractions / multiplier).
private val CLOUD_X = floatArrayOf(0.20f, 0.58f, 0.86f)
private val CLOUD_Y = floatArrayOf(0.28f, 0.64f, 0.22f)
private val CLOUD_S = floatArrayOf(1.0f, 0.78f, 0.62f)

private fun DrawScope.drawSunTransit(p: Float) {
    val r = size.height * 0.16f
    val env = sin(p * PI.toFloat()) // scenery fade-in/out envelope

    // Clouds drift slowly leftward while the sun crosses to the right.
    // Cloud 1 (index 1) is drawn AFTER the sun so it passes in front.
    drawDayCloud(0, p, env)
    drawDayCloud(2, p, env)

    val c = transitCenter(p, r)
    val gold = Color(0xFFFFC107)
    drawCircle(gold.copy(alpha = 0.20f), r * 1.9f, c)
    drawCircle(gold, r, c)
    // Slowly spinning rays
    val spin = p * PI.toFloat()
    for (i in 0 until 8) {
        val a = spin + i * (PI.toFloat() / 4f)
        drawLine(
            color = gold,
            start = Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f),
            end = Offset(c.x + cos(a) * r * 1.75f, c.y + sin(a) * r * 1.75f),
            strokeWidth = r * 0.22f,
            cap = StrokeCap.Round,
        )
    }

    drawDayCloud(1, p, env)
}

/** One puffy cloud: three lobes unioned into a single path so the
 *  translucent fill stays perfectly uniform (no darker overlap blotches). */
private fun DrawScope.drawDayCloud(i: Int, p: Float, env: Float) {
    val h = size.height
    val rc = h * 0.15f * CLOUD_S[i]
    val cx = (CLOUD_X[i] - (0.10f + i * 0.03f) * p) * size.width
    val cy = CLOUD_Y[i] * h
    val cloud = Path().apply {
        addOval(Rect(cx - rc * 1.8f, cy - rc * 0.45f, cx + rc * 1.8f, cy + rc * 0.75f))
        addOval(Rect(cx - rc * 1.35f, cy - rc * 1.05f, cx - rc * 0.05f, cy + rc * 0.25f))
        addOval(Rect(cx - rc * 0.35f, cy - rc * 1.35f, cx + rc * 1.45f, cy + rc * 0.45f))
    }
    drawPath(cloud, Color.White.copy(alpha = 0.38f * env * (1f - i * 0.12f)))
}

private fun DrawScope.drawMoonTransit(p: Float, rowColor: Color) {
    val r = size.height * 0.16f
    val env = sin(p * PI.toFloat())

    // Star field: fades in as the moon rises, each star twinkling at its
    // own rhythm; every third one gets a little sparkle cross.
    for (i in NIGHT_STAR_X.indices) {
        val sr = NIGHT_STAR_R[i] * size.height
        val sx = NIGHT_STAR_X[i] * size.width
        val sy = NIGHT_STAR_Y[i] * size.height
        val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(p * 14f + NIGHT_STAR_PHASE[i]))
        val alpha = 0.85f * env * twinkle
        drawCircle(Color.White.copy(alpha = alpha), sr, Offset(sx, sy))
        if (i % 3 == 0) {
            val arm = sr * 2.6f
            val sparkle = Color.White.copy(alpha = alpha * 0.55f)
            drawLine(sparkle, Offset(sx - arm, sy), Offset(sx + arm, sy), strokeWidth = sr * 0.4f, cap = StrokeCap.Round)
            drawLine(sparkle, Offset(sx, sy - arm), Offset(sx, sy + arm), strokeWidth = sr * 0.4f, cap = StrokeCap.Round)
        }
    }

    val c = transitCenter(p, r)
    drawCircle(Color(0xFFCDD8F2), r, c)
    // Crescent bite in the row's own colour
    drawCircle(rowColor, r * 0.82f, Offset(c.x + r * 0.52f, c.y - r * 0.40f))
}
