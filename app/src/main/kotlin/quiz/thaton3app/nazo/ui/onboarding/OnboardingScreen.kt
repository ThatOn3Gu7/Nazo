package quiz.thaton3app.nazo.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.ModelInfo
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

/**
 * First-launch onboarding, v3: the reference-style tour is now a full setup
 * wizard. Slides 1-3 present the app; slide 4 sets up an AI provider (key →
 * verify → models fetched + best model auto-picked) and appearance (theme /
 * accent / reveal style — applied LIVE so the whole tour recolors); slide 5
 * lets the user pick a topic and launch straight into their first game (Quiz
 * offline or with AI; Guessing once a provider is ready). Everything is
 * optional and skippable. All expand/collapse/state changes are animated
 * (expandVertically/shrinkVertically + fades + animateContentSize + color
 * crossfades) — motion polish is a hard requirement from the owner.
 */
@Composable
fun OnboardingScreen(
    isDark: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
    revealStyle: String,
    onRevealStyleChange: (String) -> Unit,
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

    // --- Setup state, hoisted here so it survives page swipes (the pager
    // disposes far-away pages). ---
    val apiKeyStore = remember { ApiKeyStore(context) }
    var providerId by remember { mutableStateOf(ApiKeyStore.PROVIDER_ORDER.first()) }
    var keyInput by remember { mutableStateOf(apiKeyStore.getKey(providerId).orEmpty()) }
    var verifyState by remember { mutableStateOf<VerifyState>(VerifyState.Idle) }
    var providerReady by remember {
        mutableStateOf(apiKeyStore.getConfiguredProviders().isNotEmpty())
    }
    var providerExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }

    // --- First-game state. ---
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

    // Auto-hide the "needs a provider" hint.
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
            .background(NazoBackground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
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
                    .padding(start = 28.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT) { i ->
                    val dashWidth by animateDpAsState(
                        targetValue = if (i == page) 26.dp else 12.dp,
                        animationSpec = tween(240),
                        label = "dash",
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(4.dp)
                            .width(dashWidth)
                            .clip(CircleShape)
                            .background(
                                if (i == page) NazoTextPrimary
                                else NazoTextSecondary.copy(alpha = 0.35f)
                            ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !isLast,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
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
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        accentId = accentId,
                        onAccentChange = onAccentChange,
                        revealStyle = revealStyle,
                        onRevealStyleChange = onRevealStyleChange,
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

            // Bottom bar: full-width button on slide 1 (no arrow); back arrow
            // slides in from slide 2 on. On the last slide the button becomes
            // "Play Now" once a topic is set, else "Start Playing" (= skip).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = page > 0,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkHorizontally(tween(220)) + fadeOut(tween(160)),
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
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
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

private fun pickDefaultModel(providerId: String, models: List<ModelInfo>): ModelInfo = when (providerId) {
    "gemini" -> models.firstOrNull { it.id.contains("flash", ignoreCase = true) } ?: models.first()
    "openrouter" -> models.firstOrNull { it.isFree } ?: models.first()
    else -> models.first()
}

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
    // ColumnScope receiver so children can use weight(1f) — a plain
    // `() -> Unit` lambda breaks `weight` resolution (CI-caught).
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
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
    revealStyle: String,
    onRevealStyleChange: (String) -> Unit,
) {
    val context = LocalContext.current
    SlideCardFrame(PAGE_SETUP) {
        Text(
            text = "Make It\nYours",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 46.sp),
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Both optional — everything here also lives in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
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
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
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

                    // Success caption — fades/expands in under the button.
                    AnimatedVisibility(
                        visible = verifyState is VerifyState.Ready,
                        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
                    ) {
                        val ready = verifyState as? VerifyState.Ready
                        Text(
                            text = "Using ${ready?.modelName ?: ""} · ${ready?.count ?: 0} models available — change anytime in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NazoSuccess,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Error pill — plain fade, like the provider screen's.
                    AnimatedVisibility(
                        visible = verifyState is VerifyState.Failed,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(160)),
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
                subtitle = "Theme, accent color & reveal style",
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Accents.forEach { accent ->
                            val selected = accent.id == accentId
                            val ringAlpha by animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = tween(200),
                                label = "accentRing",
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
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
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
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
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
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

            // What the first game will be — crossfades when the mode changes.
            AnimatedContent(
                targetState = gameMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
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

// ---------------------------------------------------------------------------
// Shared setup widgets
// ---------------------------------------------------------------------------

/** Rounded expandable section card: animated chevron, expand/collapse reveal,
 * and animateContentSize so the card height glides instead of snapping. */
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
        animationSpec = tween(240),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .animateContentSize(animationSpec = tween(260)),
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
            enter = expandVertically(tween(240)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
        ) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
            }
        }
    }
}

/** Selection pill with animated background/text colors. */
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
        animationSpec = tween(180),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) NazoOnPrimary else NazoTextPrimary,
        animationSpec = tween(180),
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

// ---------------------------------------------------------------------------
// Hand-drawn doodle illustrations (unchanged from the reference redesign).
// ---------------------------------------------------------------------------

/** Slide 1: a question card + an answer sheet, sparkles and a curly arrow. */
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

/** Slide 2: a pixelated mystery card with a magnifier revealing the 謎. */
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

/** Slide 3: a rising bar chart card, an XP badge and a victory arrow. */
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

/** Four-point hand-drawn sparkle (the reference's little asterisks). */
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

/** A curly hand-drawn arrow, swooping up-right. */
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
