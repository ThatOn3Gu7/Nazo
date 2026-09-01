package quiz.thaton3app.nazo.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.ModelInfo
import quiz.thaton3app.nazo.data.remote.preferredDefaultModel
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.Accents
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoErrorBg
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.theme.resolveAccent
import kotlin.math.absoluteValue

// ---------------------------------------------------------------------------
// Pages. 0-2 = feature slides (reference-style doodle cards), 3 = setup
// (provider + appearance), 4 = first game. Same visual language throughout.
// ---------------------------------------------------------------------------

private data class OnboardingPage(
    val title: String,
    val body: List<Pair<String, Boolean>>,
)

private val FEATURE_PAGES = listOf(
    OnboardingPage(
        title = "Quiz\nYour Way",
        body = listOf(
            "Pick a topic, difficulty and length — then beat the clock. " to false,
            "AI-generated" to true,
            " questions online, a " to false,
            "huge local bank" to true,
            " offline." to false,
        ),
    ),
    OnboardingPage(
        title = "Guess\nthe Image",
        body = listOf(
            "A mystery image sharpens while the timer runs. " to false,
            "Name it fast" to true,
            " — the quicker you are, the " to false,
            "more you score" to true,
            "." to false,
        ),
    ),
    OnboardingPage(
        title = "Level Up\n& Track It",
        body = listOf(
            "Every game earns " to false,
            "XP" to true,
            ". Keep " to false,
            "daily streaks" to true,
            ", master your favorite anime and " to false,
            "share your stats" to true,
            "." to false,
        ),
    ),
)

private const val PAGE_COUNT = 5
private const val PAGE_SETUP = 3
private const val PAGE_FIRST_GAME = 4

private val PROVIDER_NAMES = mapOf(
    "gemini" to "Google Gemini",
    "openrouter" to "OpenRouter",
)

/** Key-verification state for the provider section. */
private sealed interface VerifyState {
    data object Idle : VerifyState
    data object Checking : VerifyState
    data class Ready(val modelName: String, val count: Int) : VerifyState
    data class Failed(val message: String) : VerifyState
}

@Composable
fun OnboardingScreen(
    isDark: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
    revealStyle: String,
    onRevealStyleChange: (String) -> Unit,
    backgroundStyle: String,
    onBackgroundStyleChange: (String) -> Unit,
    floatingNavBar: Boolean,
    onFloatingNavBarChange: (Boolean) -> Unit,
    iconFollowsOsTheme: Boolean,
    onIconFollowsOsThemeChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    remindersEnabled: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit,
    forceOffline: Boolean,
    onForceOfflineChange: (Boolean) -> Unit,
    onProvidersChanged: () -> Unit,
    onPlayNow: (mode: String, topic: String) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    val page = pagerState.currentPage
    val isLast = page == PAGE_COUNT - 1

    val apiKeyStore = remember { ApiKeyStore(context) }
    var providerId by remember { mutableStateOf(ApiKeyStore.PROVIDER_ORDER.first()) }
    var keyInput by remember { mutableStateOf(apiKeyStore.getKey(providerId).orEmpty()) }
    var verifyState by remember { mutableStateOf<VerifyState>(VerifyState.Idle) }
    var providerReady by remember {
        mutableStateOf(apiKeyStore.getConfiguredProviders().isNotEmpty())
    }
    var providerExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var preferencesExpanded by remember { mutableStateOf(false) }

    var gameMode by remember { mutableStateOf("QUIZ") }
    var topicInput by remember { mutableStateOf("") }
    var showProviderHint by remember { mutableStateOf(false) }

    fun verifyKey() {
        val key = keyInput.trim()
        if (key.isEmpty() || verifyState is VerifyState.Checking) return
        Haptics.light(context)
        focusManager.clearFocus()
        verifyState = VerifyState.Checking
        scope.launch {
            ApiClient.fetchModels(providerId, key)
                .onSuccess { models ->
                    if (models.isEmpty()) {
                        verifyState = VerifyState.Failed("No usable models found for this key")
                    } else {
                        apiKeyStore.saveKey(providerId, key)
                        apiKeyStore.saveModels(providerId, models)
                        val pick = pickDefaultModel(providerId, models)
                        apiKeyStore.saveModel(providerId, pick.id)
                        apiKeyStore.saveSelectedProvider(providerId)
                        providerReady = true
                        verifyState = VerifyState.Ready(pick.name.ifBlank { pick.id }, models.size)
                        onProvidersChanged()
                    }
                }
                .onFailure {
                    verifyState = VerifyState.Failed(it.message ?: "Couldn't verify the key")
                }
        }
    }

    LaunchedEffect(showProviderHint) {
        if (showProviderHint) {
            kotlinx.coroutines.delay(2600)
            showProviderHint = false
        }
    }

    BackHandler(enabled = page > 0) {
        scope.launch { pagerState.animateScrollToPage(page - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Top bar: dash progress + Skip.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Smoothly animate the row's height so when "Skip" vanishes, the 
                    // card below can gracefully glide up into the freed space.
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy, 
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .padding(start = 28.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT) { i ->
                    val dashWidth by animateDpAsState(
                        targetValue = if (i == page) 26.dp else 12.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                        label = "dashWidth",
                    )
                    val dashColor by animateColorAsState(
                        targetValue = if (i == page) NazoTextPrimary else NazoTextSecondary.copy(alpha = 0.35f),
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "dashColor"
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(4.dp)
                            .width(dashWidth)
                            .clip(CircleShape)
                            .background(dashColor),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !isLast,
                    enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)),
                ) {
                    TextButton(
                        onClick = {
                            Haptics.soft(context)
                            onFinish()
                        },
                    ) {
                        Text(
                            text = "Skip",
                            color = NazoTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp),
                pageSpacing = 10.dp,
            ) { index ->
                val pageOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                val scale = 1f - (0.05f * pageOffset.absoluteValue).coerceIn(0f, 0.05f)
                val alpha = 1f - (0.3f * pageOffset.absoluteValue).coerceIn(0f, 0.3f)

                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                ) {
                    when (index) {
                        in 0..2 -> FeatureSlide(index)
                        PAGE_SETUP -> SetupSlide(
                            isDark = isDark,
                            providerId = providerId,
                            onProviderChange = { id ->
                                if (id != providerId) {
                                    Haptics.soft(context)
                                    providerId = id
                                    keyInput = apiKeyStore.getKey(id).orEmpty()
                                    verifyState = VerifyState.Idle
                                }
                            },
                            keyInput = keyInput,
                            onKeyChange = {
                                keyInput = it
                                if (verifyState !is VerifyState.Checking) verifyState = VerifyState.Idle
                            },
                            verifyState = verifyState,
                            onVerify = { verifyKey() },
                            providerExpanded = providerExpanded,
                            onProviderToggle = {
                                Haptics.soft(context)
                                providerExpanded = !providerExpanded
                            },
                            appearanceExpanded = appearanceExpanded,
                            onAppearanceToggle = {
                                Haptics.soft(context)
                                appearanceExpanded = !appearanceExpanded
                            },
                            preferencesExpanded = preferencesExpanded,
                            onPreferencesToggle = {
                                Haptics.soft(context)
                                preferencesExpanded = !preferencesExpanded
                            },
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            accentId = accentId,
                            onAccentChange = onAccentChange,
                            revealStyle = revealStyle,
                            onRevealStyleChange = onRevealStyleChange,
                            backgroundStyle = backgroundStyle,
                            onBackgroundStyleChange = onBackgroundStyleChange,
                            floatingNavBar = floatingNavBar,
                            onFloatingNavBarChange = onFloatingNavBarChange,
                            iconFollowsOsTheme = iconFollowsOsTheme,
                            onIconFollowsOsThemeChange = onIconFollowsOsThemeChange,
                            soundEnabled = soundEnabled,
                            onSoundEnabledChange = onSoundEnabledChange,
                            remindersEnabled = remindersEnabled,
                            onRemindersEnabledChange = onRemindersEnabledChange,
                            forceOffline = forceOffline,
                            onForceOfflineChange = onForceOfflineChange,
                        )
                        else -> FirstGameSlide(
                            gameMode = gameMode,
                            onGameModeChange = { mode ->
                                if (mode == "GUESSING" && !providerReady) {
                                    Haptics.soft(context)
                                    showProviderHint = true
                                } else {
                                    Haptics.soft(context)
                                    gameMode = mode
                                }
                            },
                            providerReady = providerReady,
                            showProviderHint = showProviderHint,
                            topicInput = topicInput,
                            onTopicChange = { topicInput = it },
                            onDone = { focusManager.clearFocus() },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = page > 0,
                    enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
                    exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)),
                ) {
                    Row {
                        IconButton(
                            onClick = {
                                Haptics.soft(context)
                                scope.launch { pagerState.animateScrollToPage(page - 1) }
                            },
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(NazoSurface)
                                .border(1.dp, NazoTextSecondary.copy(alpha = 0.25f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Previous",
                                tint = NazoTextPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                val topic = topicInput.trim()
                val buttonLabel = when {
                    !isLast -> "Next"
                    topic.isNotEmpty() && gameMode == "GUESSING" -> "Play Now · Guessing"
                    topic.isNotEmpty() -> "Play Now · Quiz"
                    else -> "Start Playing"
                }
                Button(
                    onClick = {
                        Haptics.light(context)
                        when {
                            !isLast -> scope.launch { pagerState.animateScrollToPage(page + 1) }
                            topic.isNotEmpty() -> onPlayNow(gameMode, topic)
                            else -> onFinish()
                        }
                    },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NazoPrimary,
                        contentColor = NazoOnPrimary,
                    ),
                ) {
                    AnimatedContent(
                        targetState = buttonLabel,
                        transitionSpec = { 
                            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically { height -> height / 2 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically { height -> -height / 2 }) using SizeTransform(clip = false)
                        },
                        label = "nextLabel",
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun pickDefaultModel(providerId: String, models: List<ModelInfo>): ModelInfo =
    preferredDefaultModel(providerId, models) ?: models.first()

// ---------------------------------------------------------------------------
// Slide scaffolding
// ---------------------------------------------------------------------------

@Composable
private fun slideTint(index: Int): Color = when (index) {
    0 -> NazoPrimary.copy(alpha = 0.14f)
    1 -> NazoError.copy(alpha = 0.10f)
    2 -> NazoSuccess.copy(alpha = 0.13f)
    PAGE_SETUP -> NazoPrimary.copy(alpha = 0.09f)
    else -> NazoSuccess.copy(alpha = 0.10f)
}

@Composable
private fun SlideCardFrame(
    index: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(NazoSurface)
            .background(slideTint(index))
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        content()
    }
}

@Composable
private fun FeatureSlide(index: Int) {
    val page = FEATURE_PAGES[index]
    SlideCardFrame(index) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> QuizDoodle()
                1 -> GuessDoodle()
                else -> StatsDoodle()
            }
        }
        Text(
            text = buildAnnotatedString {
                page.body.forEach { (segment, bold) ->
                    if (bold) {
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, color = NazoTextPrimary)
                        ) { append(segment) }
                    } else {
                        append(segment)
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = NazoTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Slide 4: setup (AI provider + appearance), expandable sections.
// ---------------------------------------------------------------------------

@Composable
private fun SetupSlide(
    isDark: Boolean,
    providerId: String,
    onProviderChange: (String) -> Unit,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    verifyState: VerifyState,
    onVerify: () -> Unit,
    providerExpanded: Boolean,
    onProviderToggle: () -> Unit,
    appearanceExpanded: Boolean,
    onAppearanceToggle: () -> Unit,
    preferencesExpanded: Boolean,
    onPreferencesToggle: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
    revealStyle: String,
    onRevealStyleChange: (String) -> Unit,
    backgroundStyle: String,
    onBackgroundStyleChange: (String) -> Unit,
    floatingNavBar: Boolean,
    onFloatingNavBarChange: (Boolean) -> Unit,
    iconFollowsOsTheme: Boolean,
    onIconFollowsOsThemeChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    remindersEnabled: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit,
    forceOffline: Boolean,
    onForceOfflineChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // The icon preference is persisted directly (no hoisted state in NazoApp),
    // so mirror it locally for an instantly-updating switch — same pattern as
    // AppearanceScreen's toggle.
    var iconFollowsChecked by remember { mutableStateOf(iconFollowsOsTheme) }
    // Enabling the daily reminder needs POST_NOTIFICATIONS on Android 13+ —
    // identical flow to the Settings screen's toggle.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled implicitly — the worker checks before posting */ }
    SlideCardFrame(PAGE_SETUP) {
        // Encompassing the entire inner content with verticalScroll removes dead-zones,
        // letting users scroll smoothly from the title or spacing instead of mis-triggering
        // clickable elements inside the expansion.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Make It\nYours",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "All optional — everything here also lives in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ------------------ AI PROVIDER ------------------
                ExpandableSection(
                    title = "AI Provider",
                    subtitle = when {
                        verifyState is VerifyState.Ready -> "Ready — AI quizzes unlocked"
                        else -> "Unlock AI quizzes & the Guessing Game"
                    },
                    expanded = providerExpanded,
                    onToggle = onProviderToggle,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ApiKeyStore.PROVIDER_ORDER.forEach { id ->
                                SelectPill(
                                    text = PROVIDER_NAMES[id] ?: id,
                                    selected = id == providerId,
                                    onClick = { onProviderChange(id) },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = onKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = verifyState !is VerifyState.Checking,
                            singleLine = true,
                            placeholder = {
                                Text("Paste your API key…", color = NazoTextSecondary.copy(alpha = 0.7f))
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onVerify() }),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NazoPrimary,
                                unfocusedBorderColor = NazoTextSecondary.copy(alpha = 0.3f),
                                focusedTextColor = NazoTextPrimary,
                                unfocusedTextColor = NazoTextPrimary,
                                cursorColor = NazoPrimary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                disabledTextColor = NazoTextSecondary,
                                disabledBorderColor = NazoTextSecondary.copy(alpha = 0.2f),
                            ),
                        )

                        Button(
                            onClick = onVerify,
                            enabled = keyInput.isNotBlank() && verifyState !is VerifyState.Checking,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NazoPrimary,
                                contentColor = NazoOnPrimary,
                                disabledContainerColor = NazoPrimary.copy(alpha = 0.4f),
                                disabledContentColor = NazoOnPrimary.copy(alpha = 0.7f),
                            ),
                        ) {
                            AnimatedContent(
                                targetState = verifyState,
                                transitionSpec = { 
                                    (fadeIn(tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.95f)) togetherWith 
                                    (fadeOut(tween(140)) + scaleOut(targetScale = 1.05f)) using SizeTransform(clip = false) 
                                },
                                label = "verifyBtn",
                            ) { state ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (state) {
                                        is VerifyState.Checking -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = NazoOnPrimary,
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Checking key…", fontWeight = FontWeight.Bold)
                                        }
                                        is VerifyState.Ready -> {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Saved & ready", fontWeight = FontWeight.Bold)
                                        }
                                        else -> Text("Verify & Save", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = verifyState is VerifyState.Ready,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
                            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)),
                        ) {
                            val ready = verifyState as? VerifyState.Ready
                            Text(
                                text = "Using ${ready?.modelName ?: ""} · ${ready?.count ?: 0} models available — change anytime in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NazoSuccess,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        AnimatedVisibility(
                            visible = verifyState is VerifyState.Failed,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
                            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)),
                        ) {
                            val failed = verifyState as? VerifyState.Failed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NazoErrorBg)
                                    .border(1.dp, NazoError, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = failed?.message ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NazoError,
                                )
                            }
                        }

                        Text(
                            text = "No key? No problem — Quiz Mode works fully offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NazoTextSecondary,
                        )
                    }
                }

                // ------------------ APPEARANCE ------------------
                ExpandableSection(
                    title = "Appearance",
                    subtitle = "Theme, accent, background, layout & icon",
                    expanded = appearanceExpanded,
                    onToggle = onAppearanceToggle,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionLabel("THEME")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
                                SelectPill(
                                    text = label,
                                    selected = themeMode == id,
                                    onClick = {
                                        Haptics.soft(context)
                                        onThemeModeChange(id)
                                    },
                                )
                            }
                        }

                        SectionLabel("ACCENT")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Accents.chunked(5).forEach { accentRow ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    accentRow.forEach { accent ->
                                        val selected = accent.id == accentId
                                        val ringAlpha by animateFloatAsState(
                                            targetValue = if (selected) 1f else 0f,
                                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                                            label = "accentRing",
                                        )
                                        val ringScale by animateFloatAsState(
                                            targetValue = if (selected) 1f else 0.8f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "accentRingScale"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .graphicsLayer {
                                                    scaleX = ringScale
                                                    scaleY = ringScale
                                                }
                                                .border(
                                                    2.5.dp,
                                                    NazoTextPrimary.copy(alpha = ringAlpha),
                                                    CircleShape,
                                                )
                                                .padding(5.dp)
                                                .clip(CircleShape)
                                                .background(resolveAccent(accent.id, isDark).primary)
                                                .clickable {
                                                    Haptics.soft(context)
                                                    onAccentChange(accent.id)
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        SectionLabel("GUESSING REVEAL")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("pixel" to "Pixelate", "blur" to "Blur").forEach { (id, label) ->
                                SelectPill(
                                    text = label,
                                    selected = revealStyle == id,
                                    onClick = {
                                        Haptics.soft(context)
                                        onRevealStyleChange(id)
                                    },
                                )
                            }
                        }

                        SectionLabel("AMBIENT BACKGROUND")
                        // 2x2 static grid — NO horizontalScroll rows inside the
                        // vertically-scrolling setup card (gesture conflict,
                        // see the accent-swatch fix).
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "shapes" to "Shapes",
                                "constellation" to "Constellation",
                                "rain" to "Digital Rain",
                                "orbs" to "Glowing Orbs",
                            ).chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { (id, label) ->
                                        SelectPill(
                                            text = label,
                                            selected = backgroundStyle == id,
                                            onClick = {
                                                Haptics.soft(context)
                                                onBackgroundStyleChange(id)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        SectionLabel("LAYOUT & ICON")
                        SetupToggleRow(
                            title = "Floating navigation bar",
                            subtitle = "Elevated pill with the background showing around it",
                            checked = floatingNavBar,
                            onCheckedChange = onFloatingNavBarChange,
                        )
                        SetupToggleRow(
                            title = "Match icon to system theme",
                            subtitle = "Launcher icon follows your device's light/dark mode",
                            checked = iconFollowsChecked,
                            onCheckedChange = {
                                iconFollowsChecked = it
                                onIconFollowsOsThemeChange(it)
                            },
                        )
                    }
                }

                // ------------------ PREFERENCES ------------------
                ExpandableSection(
                    title = "Preferences",
                    subtitle = "Sound, daily reminder & offline mode",
                    expanded = preferencesExpanded,
                    onToggle = onPreferencesToggle,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SetupToggleRow(
                            title = "Sound effects",
                            subtitle = "Soft chimes for answers, results and new records",
                            checked = soundEnabled,
                            onCheckedChange = onSoundEnabledChange,
                        )
                        SetupToggleRow(
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
                        SetupToggleRow(
                            title = "Offline mode",
                            subtitle = "Use the local question library only — no API calls",
                            checked = forceOffline,
                            onCheckedChange = onForceOfflineChange,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Slide 5: first game — topic + mode, launched straight from the tour.
// ---------------------------------------------------------------------------

@Composable
private fun FirstGameSlide(
    gameMode: String,
    onGameModeChange: (String) -> Unit,
    providerReady: Boolean,
    showProviderHint: Boolean,
    topicInput: String,
    onTopicChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val suggestions = remember {
        val all = LocalQuestionBank.suggestions()
        val preferred = listOf(
            "Naruto", "One Piece", "Attack on Titan", "Dragon Ball",
            "Demon Slayer", "Jujutsu Kaisen", "My Hero Academia", "Death Note",
        )
        (preferred.filter { p -> all.any { it.equals(p, ignoreCase = true) } } + all)
            .distinct()
            .take(8)
    }

    SlideCardFrame(PAGE_FIRST_GAME) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Your First\nGame",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Pick a topic and jump right in — or just start exploring from Home.",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("MODE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectPill(
                        text = "Quiz",
                        selected = gameMode == "QUIZ",
                        onClick = { onGameModeChange("QUIZ") },
                    )
                    SelectPill(
                        text = if (providerReady) "Guessing Game" else "Guessing Game 🔒",
                        selected = gameMode == "GUESSING",
                        enabledLook = providerReady,
                        onClick = { onGameModeChange("GUESSING") },
                    )
                }
                AnimatedVisibility(
                    visible = showProviderHint,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)),
                ) {
                    Text(
                        text = "The Guessing Game needs an AI provider — set one up on the previous step.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoError,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                SectionLabel("TOPIC")
                OutlinedTextField(
                    value = topicInput,
                    onValueChange = onTopicChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("e.g. Naruto…", color = NazoTextSecondary.copy(alpha = 0.7f))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NazoPrimary,
                        unfocusedBorderColor = NazoTextSecondary.copy(alpha = 0.3f),
                        focusedTextColor = NazoTextPrimary,
                        unfocusedTextColor = NazoTextPrimary,
                        cursorColor = NazoPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        SelectPill(
                            text = suggestion,
                            selected = topicInput.equals(suggestion, ignoreCase = true),
                            onClick = {
                                Haptics.soft(context)
                                onTopicChange(suggestion)
                            },
                        )
                    }
                }

                AnimatedContent(
                    targetState = gameMode,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 40)) + slideInVertically { height -> height / 2 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically { height -> -height / 2 }) using SizeTransform(clip = false)
                    },
                    label = "modeNote",
                ) { mode ->
                    Text(
                        text = if (mode == "GUESSING") {
                            "3 rounds · Medium difficulty · scored by speed"
                        } else {
                            "5 questions · Medium difficulty · beat the clock"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared setup widgets
// ---------------------------------------------------------------------------

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = NazoTextSecondary,
                modifier = Modifier.size(24.dp).rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)),
        ) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SelectPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabledLook: Boolean = true,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.10f),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) NazoOnPrimary else NazoTextPrimary,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "pillFg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabledLook) fg else fg.copy(alpha = 0.45f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
        color = NazoTextSecondary,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Compact switch row for the setup slide — same behavior as the Settings /
 * Appearance toggle rows (row tap or switch flip, soft haptic), sized for the
 * onboarding card.
 */
@Composable
private fun SetupToggleRow(
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NazoTextSecondary.copy(alpha = 0.08f))
            .clickable { trigger(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = trigger,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NazoOnPrimary,
                checkedTrackColor = NazoPrimary,
                uncheckedThumbColor = NazoOnPrimary,
                uncheckedTrackColor = NazoTextSecondary.copy(alpha = 0.30f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Hand-drawn doodle illustrations (unchanged from the reference redesign).
// ---------------------------------------------------------------------------

@Composable
private fun QuizDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        Column(
            modifier = Modifier
                .size(116.dp, 132.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp, y = 14.dp)
                .rotate(9f)
                .clip(RoundedCornerShape(18.dp))
                .background(ink)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (i == 1) NazoPrimary else paper.copy(alpha = 0.45f)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (i == 1) 54.dp else 40.dp)
                            .clip(CircleShape)
                            .background(paper.copy(alpha = if (i == 1) 0.95f else 0.45f)),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(112.dp, 138.dp)
                .align(Alignment.CenterStart)
                .offset(x = 14.dp, y = (-8).dp)
                .rotate(-8f)
                .clip(RoundedCornerShape(18.dp))
                .background(paper)
                .border(3.dp, ink, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "?", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = ink)
        }
        DoodleArrow(size = 54.dp, color = ink, modifier = Modifier.align(Alignment.TopCenter).offset(x = 8.dp))
        DoodleSparkle(size = 22.dp, color = ink, modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 22.dp))
        DoodleSparkle(size = 14.dp, color = ink, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = (-2).dp))
    }
}

@Composable
private fun GuessDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    val accent = NazoPrimary
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        Box(
            modifier = Modifier
                .size(158.dp, 122.dp)
                .align(Alignment.TopCenter)
                .offset(x = (-14).dp, y = 10.dp)
                .rotate(-5f)
                .clip(RoundedCornerShape(18.dp))
                .background(ink),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val cols = 6
                val rows = 4
                val cw = size.width / cols
                val ch = size.height / rows
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val shade = when ((r * 3 + c * 5) % 4) {
                            0 -> 0.12f
                            1 -> 0.28f
                            2 -> 0.48f
                            else -> 0.70f
                        }
                        drawRect(
                            color = paper.copy(alpha = shade),
                            topLeft = Offset(c * cw + 1.5f, r * ch + 1.5f),
                            size = Size(cw - 3f, ch - 3f),
                        )
                    }
                }
            }
        }
        Canvas(
            modifier = Modifier.size(44.dp).align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp),
        ) {
            drawLine(
                color = ink,
                start = Offset(size.width * 0.15f, size.height * 0.15f),
                end = Offset(size.width * 0.9f, size.height * 0.9f),
                strokeWidth = size.width * 0.22f,
                cap = StrokeCap.Round,
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-30).dp, y = (-26).dp)
                .clip(CircleShape)
                .background(paper)
                .border(4.dp, ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "謎", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        DoodleSparkle(size = 20.dp, color = ink, modifier = Modifier.align(Alignment.BottomStart).offset(x = 14.dp, y = (-18).dp))
        DoodleSparkle(size = 13.dp, color = ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 2.dp))
    }
}

@Composable
private fun StatsDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    val accent = NazoPrimary
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        Box(
            modifier = Modifier
                .size(152.dp, 126.dp)
                .align(Alignment.Center)
                .offset(x = 12.dp, y = (-8).dp)
                .rotate(6f)
                .clip(RoundedCornerShape(18.dp))
                .background(paper)
                .border(3.dp, ink, RoundedCornerShape(18.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
                val barWidth = size.width * 0.18f
                val gap = (size.width - barWidth * 3f) / 2f
                val heights = listOf(0.45f, 0.68f, 1.0f)
                heights.forEachIndexed { i, hFrac ->
                    val h = size.height * hFrac
                    drawRoundRect(
                        color = if (i == 2) accent else ink.copy(alpha = 0.75f),
                        topLeft = Offset(i * (barWidth + gap), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.BottomStart)
                .offset(x = 22.dp, y = (-6).dp)
                .rotate(-10f)
                .clip(CircleShape)
                .background(ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "XP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = paper)
        }
        DoodleArrow(size = 52.dp, color = ink, modifier = Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = 6.dp))
        DoodleSparkle(size = 20.dp, color = ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 14.dp))
        DoodleSparkle(size = 13.dp, color = ink, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = (-14).dp))
    }
}

@Composable
private fun DoodleSparkle(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.16f
        drawLine(color, Offset(s / 2f, 0f), Offset(s / 2f, s * 0.30f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s / 2f, s * 0.70f), Offset(s / 2f, s), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, s / 2f), Offset(s * 0.30f, s / 2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s * 0.70f, s / 2f), Offset(s, s / 2f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DoodleArrow(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.09f
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.88f)
            quadraticBezierTo(w * 0.05f, h * 0.30f, w * 0.78f, h * 0.22f)
        }
        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.58f, h * 0.10f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.62f, h * 0.42f), strokeWidth, StrokeCap.Round)
    }
}

