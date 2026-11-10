package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.daily.DailyChallengeCard
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.ui.components.NazoModalSheet
import quiz.thaton3app.nazo.ui.components.NazoSheetColumn
import quiz.thaton3app.nazo.ui.components.NazoSheetHeader
import quiz.thaton3app.nazo.ui.components.nazoExpressiveCornerPercent
import quiz.thaton3app.nazo.ui.components.rememberNazoExpressivePressState
import quiz.thaton3app.nazo.ui.components.rememberNazoPillCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberSystemReduceMotion
import quiz.thaton3app.nazo.ui.components.AvatarAnchorId
import quiz.thaton3app.nazo.ui.components.ProfileAvatar
import quiz.thaton3app.nazo.ui.components.SPARKLE_METEORS
import quiz.thaton3app.nazo.ui.components.SPARKLE_TWINKLE
import quiz.thaton3app.nazo.ui.components.drawMeteorShower
import quiz.thaton3app.nazo.ui.components.drawTwinklingStars
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.mode.NazoMode
import quiz.thaton3app.nazo.ui.mode.modeIdentitySpec
import quiz.thaton3app.nazo.ui.theme.*

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    OTAKU_MASTER("Otaku Master"),
}

internal enum class HomeCountControls { Questions, Rounds, None }

internal fun homeCountControls(mode: NazoMode): HomeCountControls = when (mode) {
    NazoMode.QUIZ, NazoMode.VERSUS -> HomeCountControls.Questions
    NazoMode.GUESSING -> HomeCountControls.Rounds
    NazoMode.SURVIVAL, NazoMode.BLITZ -> HomeCountControls.None
}

internal fun homePracticeVisible(mode: NazoMode, practiceCount: Int): Boolean =
    mode == NazoMode.QUIZ && practiceCount > 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    apiKeyActive: Boolean,
    /** A key is stored but nothing is generation-ready (no model selected). */
    apiKeyPresentWithoutModel: Boolean = false,
    activeProvider: String? = null,
    offline: Boolean = false,
    /** Generate-button tap effect: SPARKLE_TWINKLE or SPARKLE_METEORS. */
    sparkleStyle: String = SPARKLE_TWINKLE,
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
    onStartSurvival: (topic: String, difficulty: String) -> Unit = { _, _ -> },
    onStartBlitz: (topic: String, difficulty: String) -> Unit = { _, _ -> },
    onStartVersus: (topic: String, difficulty: String, count: Int) -> Unit = { _, _, _ -> },
    configuredProviders: List<String> = emptyList(),
    onSelectProvider: (String) -> Unit = {},
    onManageClick: () -> Unit = {},
    dailyCompleted: Boolean = false,
    dailyScore: Int = 0,
    dailyBonus: Int = 0,
    onPlayDaily: () -> Unit = {},
    streakDays: Int = 0,
    bestStreakDays: Int = 0,
    lastQuizEpochDay: Long? = null,
    totalQuizzes: Int = 0,
    practiceCount: Int = 0,
    onStartPractice: () -> Unit = {},
) {
    val difficulty = Difficulty.valueOf(difficultyName)
    val nazoMode = NazoMode.entries.firstOrNull { it.name == mode } ?: NazoMode.QUIZ
    val context = LocalContext.current
    val reduceMotion = rememberSystemReduceMotion()
    var showProviderSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val modeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Smooth dismiss helpers let each slide-down finish before its sheet unmounts.
    fun dismissSheet(onComplete: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            showProviderSheet = false
            onComplete()
        }
    }

    fun dismissModeSheet(onComplete: () -> Unit = {}) {
        scope.launch {
            modeSheetState.hide()
        }.invokeOnCompletion {
            showModeSheet = false
            onComplete()
        }
    }

    val landscape = isLandscape()

    // Group 1: Profile, Badges, Daily Challenge, and Mode Selection
    val leftContent: @Composable () -> Unit = {
        Spacer(Modifier.height(16.dp))

        HomeHeader(
            onSettingsClick = onSettingsClick,
            profileName = profileName,
            profilePictureUri = profilePictureUri,
            onProfileClick = onProfileClick,
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ApiKeyBadge(
                active = apiKeyActive,
                activeProvider = activeProvider,
                offline = offline,
                keyWithoutModel = apiKeyPresentWithoutModel,
                onClick = if (offline) null else ({ showProviderSheet = true }),
            )
            Spacer(Modifier.weight(1f))
            if (streakDays >= 1) {
                StreakFlameChip(
                    streakDays = streakDays,
                    bestStreakDays = bestStreakDays,
                    lastQuizEpochDay = lastQuizEpochDay,
                    totalQuizzes = totalQuizzes,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        DailyChallengeCard(
            completed = dailyCompleted,
            lastScore = dailyScore,
            lastBonus = dailyBonus,
            onPlay = onPlayDaily,
        )

        Spacer(Modifier.height(16.dp))

        SectionLabel("MODE")
        Spacer(Modifier.height(8.dp))

        // This identity card is now the sole mode picker on Home. Tapping it
        // opens the richer bottom sheet; there is no second dropdown competing
        // with the mode's personality or repeating the same selection.
        AnimatedContent(
            targetState = nazoMode,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(90)) togetherWith fadeOut(tween(70))
                } else {
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                }
            },
            label = "modeIdentity",
        ) { selectedMode ->
            ModeIdentityCard(
                mode = selectedMode,
                onClick = {
                    Haptics.light(context)
                    showModeSheet = true
                },
            )
        }
    }

    // Group 2: Topic, Difficulty, Rounds/Questions, and the Generate Button
    val rightContent: @Composable () -> Unit = {
        // Top alignment spacer for landscape
        if (landscape) Spacer(Modifier.height(16.dp))

        TopicInputCard(topic = topic, onTopicChange = onTopicChange)

        Spacer(Modifier.height(16.dp))

        SectionLabel("DIFFICULTY")
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        PillButton(
            text = Difficulty.OTAKU_MASTER.label,
            selected = difficulty == Difficulty.OTAKU_MASTER,
            icon = Icons.Filled.WorkspacePremium,
            onClick = {
                if (difficulty != Difficulty.OTAKU_MASTER) Haptics.light(context)
                onDifficultyChange(Difficulty.OTAKU_MASTER.name)
            },
        )

        Spacer(Modifier.height(16.dp))

        // Mode-dependent controls used to be removed in one composition, which
        // made the Generate button jump when Questions/Practice disappeared.
        // Animate both the content and the container height so every sibling
        // follows the change instead of teleporting to its new position.
        AnimatedContent(
            targetState = homeCountControls(nazoMode),
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val transform = if (reduceMotion) {
                    fadeIn(tween(90)) togetherWith fadeOut(tween(70))
                } else {
                    (fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 10 })
                        .togetherWith(
                            fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                                slideOutVertically(
                                    tween(180, easing = FastOutSlowInEasing),
                                ) { -it / 10 },
                        )
                }
                transform.using(
                    SizeTransform(clip = false) { _, _ ->
                        if (reduceMotion) snap() else tween(
                            durationMillis = 240,
                            easing = FastOutSlowInEasing,
                        )
                    },
                )
            },
            label = "homeModeCountControls",
        ) { controls ->
            when (controls) {
                HomeCountControls.Rounds -> Column(Modifier.fillMaxWidth()) {
                    SectionLabel("ROUNDS")
                    Spacer(Modifier.height(8.dp))
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
                }

                HomeCountControls.Questions -> Column(Modifier.fillMaxWidth()) {
                    SectionLabel("QUESTIONS")
                    Spacer(Modifier.height(8.dp))
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

                HomeCountControls.None -> Spacer(Modifier.fillMaxWidth())
            }
        }

        AnimatedVisibility(
            visible = homePracticeVisible(nazoMode, practiceCount),
            enter = if (reduceMotion) {
                fadeIn(tween(90))
            } else {
                fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    )
            },
            exit = if (reduceMotion) {
                fadeOut(tween(70))
            } else {
                fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    )
            },
            label = "homePracticeVisibility",
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(14.dp))
                PracticeMissesButton(
                    practiceCount = practiceCount,
                    onClick = onStartPractice,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        GenerateButton(
            sparkleStyle = sparkleStyle,
            label = when (nazoMode) {
                NazoMode.GUESSING -> "Start Guessing Game"
                NazoMode.SURVIVAL -> "Start Survival Run"
                NazoMode.BLITZ -> "Start 60-Second Blitz"
                NazoMode.VERSUS -> "Start Versus Match"
                else -> if (offline) "Generate Quiz" else "Generate AI Quiz"
            },
            onClick = {
                when (nazoMode) {
                    NazoMode.GUESSING -> onStartGuessing(topic, difficulty.label, guessingRounds)
                    NazoMode.SURVIVAL -> onStartSurvival(topic, difficulty.label)
                    NazoMode.BLITZ -> onStartBlitz(topic, difficulty.label)
                    NazoMode.VERSUS -> onStartVersus(topic, difficulty.label, questionCount)
                    else -> onStartQuiz(topic, difficulty.label, questionCount)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (landscape) {
            // Two-column landscape layout.
            //
            // Each pane owns its OWN scroll state. Putting the Row inside one
            // shared verticalScroll made both columns move together, so the
            // shorter side dragged empty space past the taller one — and the
            // Row was then measured with infinite height, which defeats the
            // weight(1f) split.
            val leftScroll = rememberScrollState()
            val rightScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp) // Generous width constraint for landscape
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(leftScroll)
                        .padding(bottom = 20.dp)
                ) {
                    leftContent()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rightScroll)
                        .padding(bottom = 20.dp)
                ) {
                    rightContent()
                }
            }
        } else {
            val scrollState = rememberScrollState()
            // Render the standard single-column layout in portrait
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            ) {
                leftContent()
                // Spacer separating the sections dynamically when in portrait
                Spacer(Modifier.height(16.dp))
                rightContent()
            }
        }

        if (showModeSheet) {
            NazoModalSheet(
                onDismissRequest = { showModeSheet = false },
                sheetState = modeSheetState,
            ) {
                NazoSheetColumn {
                    NazoSheetHeader(
                        icon = Icons.Filled.SportsEsports,
                        title = "Choose a game mode",
                        subtitle = "Five ways to play, one Nazo identity",
                    )
                    Spacer(Modifier.height(16.dp))
                    NazoMode.entries.forEach { candidate ->
                        ModePickerCard(
                            mode = candidate,
                            selected = candidate == nazoMode,
                            onClick = {
                                if (candidate != nazoMode) Haptics.light(context)
                                dismissModeSheet {
                                    if (candidate != nazoMode) onModeChange(candidate.name)
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val cancelInteraction = remember { MutableInteractionSource() }
                    val cancelShape = rememberNazoRoundedCornerMorphShape(
                        interactionSource = cancelInteraction,
                        restingCorner = 12.dp,
                        pressedCorner = 6.dp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(cancelShape)
                            .clickable(
                                interactionSource = cancelInteraction,
                                indication = LocalIndication.current,
                            ) { dismissModeSheet() },
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

        if (showProviderSheet) {
            NazoModalSheet(
                onDismissRequest = { showProviderSheet = false },
                sheetState = sheetState,
            ) {
                NazoSheetColumn {
                    NazoSheetHeader(
                        icon = Icons.Filled.Key,
                        title = "Switch API key",
                        subtitle = "Choose which configured provider generates your quizzes.",
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    if (configuredProviders.isEmpty()) {
                        Text(
                            text = "You haven't set up any API keys yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        val setupInteraction = remember { MutableInteractionSource() }
                        val setupShape = rememberNazoRoundedCornerMorphShape(
                            interactionSource = setupInteraction,
                            restingCorner = 14.dp,
                            pressedCorner = 7.dp,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(setupShape)
                                .background(NazoPrimary, setupShape)
                                .clickable(
                                    interactionSource = setupInteraction,
                                    indication = LocalIndication.current,
                                ) {
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
                            val interactionSource = remember(id) { MutableInteractionSource() }
                            val indication = LocalIndication.current
                            val shape = rememberNazoRoundedCornerMorphShape(
                                interactionSource = interactionSource,
                                restingCorner = 14.dp,
                                pressedCorner = 7.dp,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(
                                        if (isSel) NazoPrimary.copy(alpha = 0.08f) else Color.Transparent,
                                        shape,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = indication,
                                    ) {
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
                        val manageInteraction = remember { MutableInteractionSource() }
                        val manageShape = rememberNazoRoundedCornerMorphShape(
                            interactionSource = manageInteraction,
                            restingCorner = 14.dp,
                            pressedCorner = 7.dp,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(manageShape)
                                .background(NazoPillUnselected, manageShape)
                                .clickable(
                                    interactionSource = manageInteraction,
                                    indication = LocalIndication.current,
                                ) {
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
                    val cancelInteraction = remember { MutableInteractionSource() }
                    val cancelShape = rememberNazoRoundedCornerMorphShape(
                        interactionSource = cancelInteraction,
                        restingCorner = 12.dp,
                        pressedCorner = 6.dp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(cancelShape)
                            .clickable(
                                interactionSource = cancelInteraction,
                                indication = LocalIndication.current,
                            ) {
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
        // Brand lockup: the same custom brush mark as the default launcher,
        // rather than a system-font approximation. Deliberately NOT pill-shaped—
        // pills on this screen mean "status or selection". The fixed bottle-
        // green tile keeps the identity stable across user-selected app accents.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF1B4135)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_mark_nazo_brand),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Nazo",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        ProfileAvatar(
            name = profileName,
            pictureUri = profilePictureUri,
            size = 42.dp,
            onClick = onProfileClick,
            anchorId = AvatarAnchorId.Home,
            modifier = Modifier.size(42.dp),
        )
    }
}

/**
 * Provider status pill.
 *
 * [active] means GENERATION-READY — a provider with both a key and a selected
 * model, i.e. exactly what the generator would use. It is not "a key exists":
 * that was the old behaviour and it lied, because a saved key with no model
 * chosen still sends the user to the "AI missing" dialog when they press
 * Generate. [keyWithoutModel] covers that gap explicitly so the pill can say
 * what is actually wrong.
 */
@Composable
private fun ApiKeyBadge(
    active: Boolean,
    activeProvider: String? = null,
    offline: Boolean = false,
    keyWithoutModel: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // Amber for the "nearly there" state: it is not an error (the key is valid
    // and saved) but it is not ready either, so neither the green nor the red
    // treatment would be honest.
    val warn = Color(0xFFFF8F00)
    val (bg, dot, text) = when {
        offline -> Triple(NazoPillUnselected, NazoTextSecondary, NazoTextSecondary)
        active -> Triple(NazoBadge, NazoPrimary, NazoPrimary)
        keyWithoutModel -> Triple(warn.copy(alpha = 0.12f), warn, warn)
        else -> Triple(NazoErrorBg, NazoError, NazoError)
    }
    val label = when {
        offline -> "Offline mode"
        active -> activeProvider?.let { PROVIDER_DISPLAY[it] ?: it } ?: "API Key active"
        keyWithoutModel -> "Choose a model"
        else -> "API Key inactive"
    }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoPillCornerMorphShape(
        interactionSource = interactionSource,
        enabled = onClick != null,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ) else Modifier
            )
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
    "opencode" to "OpenCode Zen",
)

private val PROVIDER_ICONS = mapOf(
    "gemini" to Icons.Filled.AutoAwesome,
    "openrouter" to Icons.Filled.Api,
    "opencode" to Icons.Filled.Code,
)

/**
 * Daily streak flame: a quiet chip whose flame burns hotter as the streak
 * grows — amber for days 1–2, deep orange for 3–6, red-hot (and slightly
 * bigger) from a full week. Fixed semantic colors on purpose: fire reads as
 * fire in every accent/theme.
 */
@Composable
private fun StreakFlameChip(
    streakDays: Int,
    bestStreakDays: Int,
    lastQuizEpochDay: Long?,
    totalQuizzes: Int,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val flameColor = when {
        streakDays >= 7 -> Color(0xFFE53935) // red-hot
        streakDays >= 3 -> Color(0xFFFF6D00) // deep orange
        else -> Color(0xFFFFA000)            // amber
    }
    val flameSize = when {
        streakDays >= 7 -> 24.dp
        streakDays >= 3 -> 21.dp
        else -> 18.dp
    }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoPillCornerMorphShape(interactionSource)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(flameColor.copy(alpha = 0.12f), shape)
            .border(1.dp, flameColor.copy(alpha = 0.35f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) {
                Haptics.soft(context)
                expanded = true
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Animated flame.
        //
        // The motion is deliberately irregular: three sine waves at unrelated
        // frequencies drive squash, lean and lift, so the flame never repeats
        // on an obvious beat the way a single pulse would. It is also NOT a
        // scale pulse — the Daily Challenge card already uses that pattern, and
        // two pulses on one screen read as a glitch.
        //
        // graphicsLayer is a DRAW-phase read of the animation value, so the
        // flicker never recomposes this chip (let alone HomeScreen) — it only
        // redraws the icon.
        val flicker = rememberInfiniteTransition(label = "streakFlame")
        val t by flicker.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "streakFlamePhase",
        )
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = flameColor,
            modifier = Modifier
                .size(flameSize)
                .graphicsLayer {
                    // Pivot at the base: a flame is anchored to what it burns.
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    // Vertical stretch with a matching horizontal squash, so the
                    // flame keeps its volume while it licks upward.
                    val stretch = 1f + 0.07f * sin(t)
                    scaleY = stretch
                    scaleX = 1f - 0.05f * sin(t + 0.6f)
                    // A slow lean, on a different period from the stretch.
                    rotationZ = 2.4f * sin(t * 0.73f + 1.1f)
                    // A tiny lift at yet another period keeps it from looking
                    // like it is bolted down.
                    translationY = -0.6f * size.height * 0.01f * sin(t * 1.31f)
                },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (streakDays == 1) "1-day streak" else "$streakDays-day streak",
            color = flameColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (expanded) {
        StreakDetailDialog(
            streakDays = streakDays,
            bestStreakDays = bestStreakDays,
            lastQuizEpochDay = lastQuizEpochDay,
            totalQuizzes = totalQuizzes,
            flameColor = flameColor,
            onDismiss = { expanded = false },
        )
    }
}

/**
 * The expanded streak card.
 *
 * Rendered in a [Dialog] rather than inline on Home. Expanding a chip in place
 * would reflow the page and shove the mode cards down — and in landscape, where
 * Home is already short, straight off the bottom. A dialog floats above the
 * layout, so Home never moves and no new scroll region appears.
 *
 * Landscape puts the art beside the stats instead of above them, because a
 * stacked card does not fit a ~360dp-tall window; the content is identical.
 */
@Composable
private fun StreakDetailDialog(
    streakDays: Int,
    bestStreakDays: Int,
    lastQuizEpochDay: Long?,
    totalQuizzes: Int,
    flameColor: Color,
    onDismiss: () -> Unit,
) {
    val landscape = isLandscape()
    val today = QuizStats.localEpochDay()
    val playedToday = lastQuizEpochDay == today
    // Best is stored separately and can lag the current run on the very day a
    // record is set, so show whichever is actually larger.
    val best = maxOf(bestStreakDays, streakDays)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = if (landscape) 460.dp else 340.dp)
                // A short window still gets a scroll of last resort rather than
                // clipping the dismiss button.
                .heightIn(max = if (landscape) 300.dp else 560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(NazoSurface)
                .border(1.dp, flameColor.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakArt(streakDays = streakDays, flameColor = flameColor, compact = true)
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        StreakHeadline(streakDays, flameColor, centered = false)
                        Spacer(Modifier.height(12.dp))
                        StreakFacts(best, totalQuizzes, playedToday, flameColor)
                    }
                }
            } else {
                StreakArt(streakDays = streakDays, flameColor = flameColor, compact = false)
                Spacer(Modifier.height(14.dp))
                StreakHeadline(streakDays, flameColor, centered = true)
                Spacer(Modifier.height(16.dp))
                StreakFacts(best, totalQuizzes, playedToday, flameColor)
            }

            Spacer(Modifier.height(18.dp))
            NazoSecondaryButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                muted = true,
            ) {
                Text("Close")
            }
        }
    }
}

/**
 * Decorative flame badge. Concentric rings behind a large flame, with the same
 * three-sine flicker as the chip so the two clearly belong together.
 */
@Composable
private fun StreakArt(streakDays: Int, flameColor: Color, compact: Boolean) {
    val outer = if (compact) 84.dp else 108.dp
    val flame = if (compact) 40.dp else 54.dp
    val glow = rememberInfiniteTransition(label = "streakArt")
    val t by glow.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "streakArtPhase",
    )
    Box(
        modifier = Modifier.size(outer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(flameColor.copy(alpha = 0.10f)),
        )
        Box(
            modifier = Modifier
                .size(outer * 0.72f)
                .clip(CircleShape)
                .background(flameColor.copy(alpha = 0.16f)),
        )
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = flameColor,
            modifier = Modifier
                .size(flame)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + 0.08f * sin(t)
                    scaleX = 1f - 0.05f * sin(t + 0.6f)
                    rotationZ = 2.6f * sin(t * 0.73f + 1.1f)
                },
        )
    }
}

@Composable
private fun StreakHeadline(streakDays: Int, flameColor: Color, centered: Boolean) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "$streakDays",
            color = flameColor,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (streakDays == 1) "day streak" else "days in a row",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StreakFacts(
    best: Int,
    totalQuizzes: Int,
    playedToday: Boolean,
    flameColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StreakFactRow(
            icon = Icons.Filled.EmojiEvents,
            label = "Best streak",
            value = if (best == 1) "1 day" else "$best days",
            tint = flameColor,
        )
        Spacer(Modifier.height(10.dp))
        StreakFactRow(
            icon = Icons.Filled.Quiz,
            label = "Quizzes played",
            value = "$totalQuizzes",
            tint = flameColor,
        )
        Spacer(Modifier.height(10.dp))
        StreakFactRow(
            icon = if (playedToday) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
            label = "Today",
            value = if (playedToday) "Done" else "Not yet",
            tint = if (playedToday) flameColor else NazoTextSecondary,
        )
        if (!playedToday) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Play a quiz today to keep the streak alive.",
                color = NazoTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StreakFactRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = NazoTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val MODE_ICONS = mapOf(
    NazoMode.QUIZ to Icons.Filled.Quiz,
    NazoMode.GUESSING to Icons.Filled.ImageSearch,
    NazoMode.SURVIVAL to Icons.Filled.Whatshot,
    NazoMode.BLITZ to Icons.Filled.Timer,
    NazoMode.VERSUS to Icons.Filled.Groups,
)

/** The selected identity is also Home's sole mode-selection control. */
@Composable
private fun ModeIdentityCard(
    mode: NazoMode,
    onClick: () -> Unit,
) {
    val identity = modeIdentitySpec(mode)
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 20.dp,
        pressedCorner = 10.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NazoSurface.copy(alpha = 0.72f), shape)
            .border(1.dp, NazoPrimary.copy(alpha = 0.22f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${mode.label.uppercase()}  •  ${identity.eyebrow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = identity.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = identity.cues.joinToString("  •  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                ModeSignature(mode)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CHANGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NazoPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Choose game mode",
                        tint = NazoPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Rich sheet row. [ModeSignature] is intentionally reused here so the future
 * personality animations have one obvious home instead of a second visual
 * system that can drift away from the selected card.
 */
@Composable
private fun ModePickerCard(
    mode: NazoMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val identity = modeIdentitySpec(mode)
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) NazoPrimary.copy(alpha = 0.58f)
        else NazoTextSecondary.copy(alpha = 0.12f),
        animationSpec = tween(220),
        label = "modePickerBorder",
    )
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 22.dp,
        pressedCorner = 11.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) NazoPrimary.copy(alpha = 0.08f) else NazoBackground.copy(alpha = 0.58f),
                shape,
            )
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) NazoPrimary.copy(alpha = 0.16f) else NazoSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MODE_ICONS[mode] ?: Icons.Filled.Quiz,
                    contentDescription = null,
                    tint = if (selected) NazoPrimary else NazoTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) NazoPrimary else NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = identity.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoTextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected mode",
                    tint = NazoPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = identity.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = identity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            ModeSignature(mode)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            identity.cues.forEach { cue -> ModeCuePill(cue, selected) }
        }
    }
}

@Composable
private fun ModeCuePill(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) NazoPrimary.copy(alpha = 0.12f)
                else NazoSurfaceVariant
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NazoPrimary else NazoTextSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ModeSignature(mode: NazoMode) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NazoBackground.copy(alpha = 0.88f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            NazoMode.QUIZ -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NazoPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Quiz, null, tint = NazoPrimary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(26.dp, 20.dp, 24.dp).forEachIndexed { index, width ->
                        Box(
                            Modifier
                                .width(width)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == 0) NazoPrimary
                                    else NazoTextSecondary.copy(alpha = 0.28f)
                                )
                        )
                    }
                }
            }
            NazoMode.GUESSING -> Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NazoSurfaceVariant)
                    .border(1.dp, NazoPrimary.copy(alpha = 0.36f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    color = NazoPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                )
                Icon(
                    imageVector = Icons.Filled.ImageSearch,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(13.dp),
                )
            }
            NazoMode.SURVIVAL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LIVES",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoTextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = NazoError,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            NazoMode.BLITZ -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = NazoPrimary, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(5.dp))
                Column {
                    Text("60", color = NazoTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(4) { index ->
                            Box(
                                Modifier
                                    .width((10 - index).dp)
                                    .height(2.dp)
                                    .background(NazoPrimary.copy(alpha = 1f - index * 0.16f))
                            )
                        }
                    }
                }
            }
            NazoMode.VERSUS -> Row(verticalAlignment = Alignment.CenterVertically) {
                VersusMiniPlayer("P1", active = true)
                Text(
                    text = "VS",
                    color = NazoTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                VersusMiniPlayer("P2", active = false)
            }
        }
    }
}

@Composable
private fun VersusMiniPlayer(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) NazoPrimary else NazoPillUnselected),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) NazoOnPrimary else NazoTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

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

    // Label + control, the same rhythm as DIFFICULTY and QUESTIONS below.
    // The old wrapper card put a surface around a field that already has its
    // own surface — one input wore more chrome than the mode selector, which
    // muddled both the hierarchy and the page's card rhythm.
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionLabel("TOPIC")
        Spacer(Modifier.height(8.dp))
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
                val clearInteraction = remember { MutableInteractionSource() }
                val clearShape = rememberNazoPillCornerMorphShape(
                    interactionSource = clearInteraction,
                    pressedCornerPercent = 28f,
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(clearShape)
                        .background(NazoTextSecondary.copy(alpha = 0.2f), clearShape)
                        .clickable(
                            interactionSource = clearInteraction,
                            indication = LocalIndication.current,
                        ) { onTopicChange("") },
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
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = NazoSurfaceVariant, thickness = 1.dp)
            Spacer(Modifier.height(6.dp))
            suggestions.forEach { suggestion ->
                val suggestionInteraction = remember(suggestion) { MutableInteractionSource() }
                val suggestionShape = rememberNazoRoundedCornerMorphShape(
                    interactionSource = suggestionInteraction,
                    restingCorner = 10.dp,
                    pressedCorner = 4.dp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(suggestionShape)
                        .clickable(
                            interactionSource = suggestionInteraction,
                            indication = LocalIndication.current,
                        ) {
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
    val reduceMotion = rememberSystemReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduceMotion) 1.02f else 1f,
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
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoPillCornerMorphShape(interactionSource)

    Box(
        modifier = modifier
            // Keep the existing selection-state emphasis; this is not tied to
            // pointer-down and remains distinct from press feedback.
            .scale(scale)
            .clip(shape)
            .background(pillBg, shape)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
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

/**
 * Corner-only expressive prototype. The Row keeps its original measurement and
 * full touch target; only the clipping outline and matching border interpolate.
 */
@Composable
private fun PracticeMissesButton(
    practiceCount: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val expressivePress = rememberNazoExpressivePressState(interactionSource)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val cornerPercent = nazoExpressiveCornerPercent(expressivePress.progress)
                shape = RoundedCornerShape(cornerPercent.roundToInt())
                clip = true
            }
            .drawWithContent {
                val cornerPercent = nazoExpressiveCornerPercent(expressivePress.progress)
                val radius = size.minDimension * cornerPercent / 100f
                drawRoundRect(
                    color = NazoSurface,
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawContent()

                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val borderRadius = (radius - inset).coerceAtLeast(0f)
                drawRoundRect(
                    color = NazoPrimary.copy(alpha = 0.35f),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth,
                    ),
                    cornerRadius = CornerRadius(borderRadius, borderRadius),
                    style = Stroke(width = strokeWidth),
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
            ) {
                Haptics.light(context)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.School,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Practice your misses ($practiceCount)",
            color = NazoPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GenerateButton(label: String, sparkleStyle: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberSystemReduceMotion()

    // Tap sequence: the sparkle twinkles, then navigation fires a beat later so
    // the animation is actually seen. `launching` also guards against a second
    // tap queueing a second quiz while the beat plays.
    var launching by remember { mutableStateOf(false) }
    val twinkle = remember { Animatable(0f) }

    // The button used to give no visual feedback at all — only haptics — so a
    // press was invisible. Three layers now respond to touch:
    //   1. a spring scale-down while held (the "physical" press),
    //   2. a brightening overlay + lifted elevation that track the press,
    //   3. a slow sheen that sweeps across the face, hinting it is tappable.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        // Springy on release so it feels responsive rather than sluggish.
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "generate_press_scale",
    )
    val pressOverlay by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.16f else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "generate_press_overlay",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed && !reduceMotion) 1.dp else 8.dp,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "generate_press_elevation",
    )

    // Idle sheen: a soft highlight that travels left-to-right on a long loop,
    // with a pause between sweeps so it reads as a gentle shine, not a spinner.
    val sheenX = if (reduceMotion) {
        -0.35f
    } else {
        val sheen = rememberInfiniteTransition(label = "generate_sheen")
        val animatedSheen by sheen.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = keyframes<Float> {
                    durationMillis = 4200
                    // Dwell off-screen, sweep across, then hold before repeating.
                    (-0.35f) at 0
                    (-0.35f) at 1400
                    1.35f at 3000 using LinearEasing
                    1.35f at 4200
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "generate_sheen_x",
        )
        animatedSheen
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(18.dp),
                ambientColor = NazoPrimary,
                spotColor = NazoPrimary,
            )
            .clip(RoundedCornerShape(18.dp))
            .background(NazoPrimary)
            // Sheen sits above the fill but below the label, and is skipped
            // while pressed so the two effects never overlap.
            .drawWithContent {
                drawContent()
                if (!pressed && !reduceMotion) {
                    val bandWidth = size.width * 0.38f
                    val center = size.width * sheenX
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NazoOnPrimary.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            startX = center - bandWidth / 2f,
                            endX = center + bandWidth / 2f,
                        ),
                    )
                }
            }
            // Press brightening, drawn over everything.
            .drawWithContent {
                drawContent()
                if (pressOverlay > 0f) {
                    drawRect(color = NazoOnPrimary.copy(alpha = pressOverlay))
                }
                // The shower sweeps the full button, so it is drawn here rather
                // than on the icon. Clipped by the button's rounded shape.
                if (sparkleStyle == SPARKLE_METEORS && twinkle.value > 0f) {
                    drawMeteorShower(
                        progress = twinkle.value,
                        buttonSize = size,
                        tint = NazoOnPrimary,
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                // The scale + brighten IS the feedback; a ripple on top would
                // clash with the sheen.
                indication = null,
            ) {
                if (launching) return@clickable
                launching = true
                Haptics.light(context)
                if (reduceMotion) {
                    // The action stays immediate under Remove animations; no
                    // decorative sheen, press transform, sparkle or delay.
                    onClick()
                    launching = false
                    return@clickable
                }
                scope.launch {
                    twinkle.snapTo(0f)
                    // ~420ms of twinkle, then a short beat so the animation
                    // lands before the screen changes. Deliberately brief —
                    // this is a flourish, not a loading screen.
                    // Meteors need longer to cross the button than the stars
                    // need to pulse, so the shower gets a wider window.
                    val duration = if (sparkleStyle == SPARKLE_METEORS) 720 else 460
                    twinkle.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
                    )
                    delay(70)
                    onClick()
                    // Reset so returning Home shows a clean, untwinkled icon.
                    twinkle.snapTo(0f)
                    launching = false
                }
            },
    ) {
        // "Start ..." modes keep the magnifier; the quiz modes get the
        // sparkle, which is drawn on a Canvas so each star can twinkle on its
        // own schedule (a plain Icon can only be transformed as a whole).
        val t = twinkle.value
        if (label.startsWith("Start")) {
            // Same pulse, applied to the single glyph.
            val pulse = sin(t * PI.toFloat())
            val iconScale = 1f + 0.25f * pulse
            Icon(
                imageVector = Icons.Filled.ImageSearch,
                contentDescription = null,
                tint = NazoOnPrimary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        } else {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawTwinklingStars(
                    progress = t,
                    baseColor = NazoOnPrimary,
                    boxSize = size,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = NazoOnPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
