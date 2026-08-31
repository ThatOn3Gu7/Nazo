package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.components.ProfileAvatar
import quiz.thaton3app.nazo.ui.theme.*

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    OTAKU_MASTER("Otaku Master"),
}

/** Which game mode the Home screen is configured to start. */
enum class NazoMode(val label: String) {
    QUIZ("Quiz"),
    GUESSING("Guessing Game"),
}

@OptIn(ExperimentalMaterial3Api::class)
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
    mode: String = NazoMode.QUIZ.name,
    onModeChange: (String) -> Unit = {},
    guessingRounds: Int = 3,
    onGuessingRoundsChange: (Int) -> Unit = {},
    onStartGuessing: (topic: String, difficulty: String, rounds: Int) -> Unit = { _, _, _ -> },
    configuredProviders: List<String> = emptyList(),
    onSelectProvider: (String) -> Unit = {},
    onManageClick: () -> Unit = {},
) {
    val difficulty = Difficulty.valueOf(difficultyName)
    val isGuessing = mode == NazoMode.GUESSING.name
    val context = LocalContext.current
    var showProviderSheet by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Smooth dismiss helper that lets the slide-down animation complete before unmounting
    fun dismissSheet(onComplete: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            // Run the side effect unconditionally so a provider selection / navigation
            // is never dropped if the hide animation is interrupted or cancelled.
            showProviderSheet = false
            onComplete()
        }
    }

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
            Spacer(Modifier.height(24.dp))
            
            HomeHeader(
                onSettingsClick = onSettingsClick,
                profileName = profileName,
                profilePictureUri = profilePictureUri,
                onProfileClick = onProfileClick,
            )
            
            Spacer(Modifier.height(18.dp))
            
            ApiKeyBadge(
                active = apiKeyActive,
                activeProvider = activeProvider,
                offline = offline,
                onClick = if (offline) null else ({ showProviderSheet = true }),
            )

            Spacer(Modifier.height(22.dp))

            SectionLabel("MODE")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = NazoMode.QUIZ.label,
                    selected = !isGuessing,
                    icon = Icons.Filled.Quiz,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isGuessing) Haptics.light(context)
                        onModeChange(NazoMode.QUIZ.name)
                    },
                )
                PillButton(
                    text = NazoMode.GUESSING.label,
                    selected = isGuessing,
                    icon = Icons.Filled.ImageSearch,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isGuessing) Haptics.light(context)
                        onModeChange(NazoMode.GUESSING.name)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (isGuessing) "Can you spot the\nmystery image?" else "Ready to test your\nanime knowledge?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = NazoTextPrimary,
            )

            Spacer(Modifier.height(24.dp))

            TopicInputCard(topic = topic, onTopicChange = onTopicChange)
            
            Spacer(Modifier.height(24.dp))
            
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
                icon = Icons.Filled.WorkspacePremium,
                onClick = {
                    if (difficulty != Difficulty.OTAKU_MASTER) Haptics.light(context)
                    onDifficultyChange(Difficulty.OTAKU_MASTER.name)
                },
            )
            
            Spacer(Modifier.height(24.dp))

            if (isGuessing) {
                SectionLabel("ROUNDS")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(1, 3, 5).forEach { count ->
                        PillButton(
                            text = if (count == 1) "1 Round" else "$count Rounds",
                            selected = guessingRounds == count,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (guessingRounds != count) Haptics.light(context)
                                onGuessingRoundsChange(count)
                            },
                        )
                    }
                }
            } else {
                SectionLabel("QUESTIONS")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10, 15).forEach { count ->
                        PillButton(
                            text = "$count Questions",
                            selected = questionCount == count,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (questionCount != count) Haptics.light(context)
                                onQuestionCountChange(count)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            GenerateButton(
                label = if (isGuessing) "Start Guessing Game" else if (offline) "Generate Quiz" else "Generate AI Quiz",
                onClick = {
                    if (isGuessing) onStartGuessing(topic, difficulty.label, guessingRounds)
                    else onStartQuiz(topic, difficulty.label, questionCount)
                },
            )
            Spacer(Modifier.height(16.dp))
        }
        
        NazoBottomNav(
            selected = NazoTab.Home,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showProviderSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProviderSheet = false },
                sheetState = sheetState,
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
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = NazoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Switch API key",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NazoTextPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NazoPrimary)
                                .clickable {
                                    dismissSheet { onManageClick() }
                                },
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
                            val icon = PROVIDER_ICONS[id] ?: Icons.Filled.Settings
                            val isSel = id == activeProvider
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSel) NazoPrimary.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable {
                                        dismissSheet { onSelectProvider(id) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) NazoPrimary.copy(alpha = 0.15f) else NazoSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "$name logo",
                                        tint = if (isSel) NazoPrimary else NazoTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    color = if (isSel) NazoPrimary else NazoTextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                )
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = NazoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = NazoTextSecondary.copy(alpha = 0.08f))
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NazoPillUnselected)
                                .clickable {
                                    dismissSheet { onManageClick() }
                                },
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                dismissSheet()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancel",
                            color = NazoTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoPillUnselected)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = "謎",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Nazo",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        ProfileAvatar(
            name = profileName,
            pictureUri = profilePictureUri,
            size = 42.dp,
            onClick = onProfileClick,
            modifier = Modifier.size(42.dp),
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
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
            .background(bg)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
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

private val PROVIDER_DISPLAY = mapOf(
    "gemini" to "Google Gemini",
    "openrouter" to "OpenRouter",
)

private val PROVIDER_ICONS = mapOf(
    "gemini" to Icons.Filled.AutoAwesome,
    "openrouter" to Icons.Filled.Api,
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoTextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
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
            .clip(RoundedCornerShape(22.dp))
            .background(NazoSurface)
            .padding(18.dp)
    ) {
        SectionLabel("TOPIC")
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (topic.isEmpty()) {
                    Text(
                        text = "Anime or theme (e.g., Jujutsu Kaisen)",
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (topic.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(NazoTextSecondary.copy(alpha = 0.2f))
                        .clickable { onTopicChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = NazoTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = NazoSurfaceVariant, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onTopicChange(suggestion)
                            focusManager.clearFocus()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
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
                        fontWeight = FontWeight.Medium
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillScale"
    )
    val pillBg by animateColorAsState(
        targetValue = if (selected) NazoPrimary else NazoPillUnselected,
        animationSpec = tween(durationMillis = 180),
        label = "pillBg",
    )
    val pillFg by animateColorAsState(
        targetValue = if (selected) NazoOnPrimary else NazoTextPrimary,
        animationSpec = tween(durationMillis = 180),
        label = "pillFg",
    )
    
    Box(
        modifier = modifier
        .scale(scale)
        .clip(RoundedCornerShape(50))
        .background(pillBg)
        .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(50))
        .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = pillFg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = pillFg,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GenerateButton(label: String, onClick: () -> Unit) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NazoPrimary)
            .clickable {
                Haptics.light(context)
                onClick()
            },
    ) {
        Icon(
            imageVector = if (label.startsWith("Start")) Icons.Filled.ImageSearch else Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = NazoOnPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = NazoOnPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

