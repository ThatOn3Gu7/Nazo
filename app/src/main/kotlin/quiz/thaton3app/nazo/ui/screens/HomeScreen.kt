package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoBadge
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPlaceholder
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoErrorBg
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.ProfileAvatar

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    OTAKU_MASTER("Otaku Master"),
}

// UI-only for now — no API/backend wiring yet, per the incremental build plan.
@Composable
fun HomeScreen(
    apiKeyActive: Boolean,
    activeProvider: String? = null,
    offline: Boolean = false,
    onSettingsClick: () -> Unit = {},
    profileName: String = "",
    profilePictureUri: String? = null,
    onProfileClick: () -> Unit = {},
    onStartQuiz: (topic: String, difficulty: String, count: Int) -> Unit,
    topic: String = "",
    difficultyName: String = Difficulty.MEDIUM.name,
    questionCount: Int = 5,
    onTopicChange: (String) -> Unit = {},
    onDifficultyChange: (String) -> Unit = {},
    onQuestionCountChange: (Int) -> Unit = {},
    configuredProviders: List<String> = emptyList(),
    onSelectProvider: (String) -> Unit = {},
    onManageClick: () -> Unit = {},
) {
    // Selection state is hoisted to NazoApp (the always-composed root) so it
    // survives navigating between screens; NazoApp persists it with
    // rememberSaveable. Difficulty arrives as its enum name and is re-derived here.
    val difficulty = Difficulty.valueOf(difficultyName)
    val context = LocalContext.current
    var showProviderSheet by remember { mutableStateOf(false) }
    // Selection state is hoisted to NazoApp (the always-composed root) so it
    // survives navigating between screens; NazoApp persists it with
    // rememberSaveable. Difficulty arrives as its enum name and is re-derived here.
    val difficulty = Difficulty.valueOf(difficultyName)
    val context = LocalContext.current

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
                .padding(bottom = 96.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            HomeHeader(
                onSettingsClick = onSettingsClick,
                profileName = profileName,
                profilePictureUri = profilePictureUri,
                onProfileClick = onProfileClick,
            )
            Spacer(Modifier.height(16.dp))
            ApiKeyBadge(
                active = apiKeyActive,
                activeProvider = activeProvider,
                offline = offline,
                onClick = if (offline) null else ({ showProviderSheet = true }),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Ready to test your\nanime knowledge?",
                style = MaterialTheme.typography.headlineMedium,
                color = NazoTextPrimary,
            )
            Spacer(Modifier.height(24.dp))
            TopicInputCard(topic = topic, onTopicChange = onTopicChange)
            Spacer(Modifier.height(20.dp))
            SectionLabel("DIFFICULTY")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD).forEach { level ->
                    PillButton(
                        text = level.label,
                        selected = difficulty == level,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (difficulty != level) Haptics.light(context)
                            onDifficultyChange(level.name)
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = Difficulty.OTAKU_MASTER.label,
                selected = difficulty == Difficulty.OTAKU_MASTER,
                onClick = {
                    if (difficulty != Difficulty.OTAKU_MASTER) Haptics.light(context)
                    onDifficultyChange(Difficulty.OTAKU_MASTER.name)
                },
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel("QUESTIONS")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5, 10, 15).forEach { count ->
                    PillButton(
                        text = count.toString(),
                        selected = questionCount == count,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (questionCount != count) Haptics.light(context)
                            onQuestionCountChange(count)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            GenerateButton(
                offline = offline,
                onClick = { onStartQuiz(topic, difficulty.label, questionCount) },
            )
            Spacer(Modifier.height(16.dp))
        }
        NazoBottomNav(
            selected = NazoTab.Home,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (showProviderSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showProviderSheet = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(NazoSurface)
                        .padding(20.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(
                        text = "Switch API key",
                        style = MaterialTheme.typography.titleLarge,
                        color = NazoTextPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Choose which configured provider generates your quizzes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (configuredProviders.isEmpty()) {
                        Text(
                            text = "You haven't set up any API keys yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NazoPrimary)
                                .clickable { onManageClick(); showProviderSheet = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Set up API keys",
                                color = NazoOnPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        configuredProviders.forEach { id ->
                            val name = PROVIDER_DISPLAY[id] ?: id
                            val isSel = id == activeProvider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectProvider(id)
                                        showProviderSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) NazoPrimary else NazoTextSecondary.copy(alpha = 0.3f)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    color = if (isSel) NazoPrimary else NazoTextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                            HorizontalDivider(color = NazoTextSecondary.copy(alpha = 0.12f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NazoPillUnselected)
                                .clickable { onManageClick(); showProviderSheet = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Manage keys in settings",
                                color = NazoTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { showProviderSheet = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            color = NazoTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onSettingsClick: () -> Unit,
    profileName: String,
    profilePictureUri: String?,
    onProfileClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Logo + title wrapped in a pill (same visual language as the API-key / offline
        // badges). Both sit on the same horizontal line (the inner Row is center-aligned)
        // so the kanji and the "Nazo" text read as one unit instead of being misaligned.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoPillUnselected)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = "謎",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Nazo",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                color = NazoTextPrimary,
            )
        }
        Spacer(Modifier.weight(1f))
        ProfileAvatar(
            name = profileName,
            pictureUri = profilePictureUri,
            size = 40.dp,
            onClick = onProfileClick,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun ApiKeyBadge(
    active: Boolean,
    activeProvider: String? = null,
    offline: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (bg, dot, text) = if (offline) {
        Triple(NazoPillUnselected, NazoTextSecondary, NazoTextSecondary)
    } else {
        Triple(
            if (active) NazoBadge else NazoErrorBg,
            if (active) NazoPrimary else NazoError,
            if (active) NazoPrimary else NazoError,
        )
    }
    val label = when {
        offline -> "Offline mode"
        active -> activeProvider?.let { PROVIDER_DISPLAY[it] ?: it } ?: "API Key active"
        else -> "API Key inactive"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = text,
        )
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = text,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Friendly names for the active-provider pill (id -> display name).
private val PROVIDER_DISPLAY = mapOf(
    "gemini" to "Google Gemini",
    "openrouter" to "OpenRouter",
)

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = NazoTextSecondary)
}

@Composable
private fun TopicInputCard(topic: String, onTopicChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val suggestions = remember(topic) {
        if (topic.isBlank()) emptyList()
        else LocalQuestionBank.suggestions().filter { it.contains(topic, ignoreCase = true) }.take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
            .padding(16.dp)
    ) {
        SectionLabel("TOPIC")
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            if (topic.isEmpty()) {
                Text(
                    text = "Enter Anime or Theme (e.g., Jujutsu Kaisen, 90s Mecha, Shonen Villains)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPlaceholder,
                )
            }
            BasicTextField(
                value = topic,
                onValueChange = onTopicChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = NazoTextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = NazoSurfaceVariant, thickness = 1.dp)
            Spacer(Modifier.height(6.dp))
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onTopicChange(suggestion)
                            focusManager.clearFocus()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = NazoPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pillBg = animateColorAsState(
        targetValue = if (selected) NazoPrimary else NazoPillUnselected,
        animationSpec = tween(durationMillis = 160),
        label = "pillBg",
    ).value
    val pillFg = animateColorAsState(
        targetValue = if (selected) NazoOnPrimary else NazoTextPrimary,
        animationSpec = tween(durationMillis = 160),
        label = "pillFg",
    ).value
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = pillFg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun GenerateButton(offline: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NazoPrimary)
            .clickable {
                Haptics.light(context)
                onClick()
            },
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = NazoOnPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (offline) "Generate Quiz" else "Generate AI Quiz",
            style = MaterialTheme.typography.bodyLarge,
            color = NazoOnPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
