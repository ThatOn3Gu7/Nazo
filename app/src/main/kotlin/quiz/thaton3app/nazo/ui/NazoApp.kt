package quiz.thaton3app.nazo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.Connectivity
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.OfflineWarningDialog
import quiz.thaton3app.nazo.ui.components.FloatingParticlesBackground
import quiz.thaton3app.nazo.ui.components.StartupMode
import quiz.thaton3app.nazo.ui.screens.*
import quiz.thaton3app.nazo.ui.theme.NazoTheme

// Every destination in the app. Wrapping this in AnimatedContent gives us a single,
// uniform fade-in / fade-out transition between ALL screens (Roadmap #2).
sealed interface Screen {
    data object Home : Screen
    data object Profile : Screen
    data object Settings : Screen
    data object AiProvider : Screen
    data object Statistics : Screen
    data object Appearance : Screen
    data object BackupRestore : Screen
    data object About : Screen
    data object Quiz : Screen
    data object Results : Screen
    data object Loading : Screen
    data object Review : Screen
}

@Composable
fun NazoApp() {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val themePrefs = remember { ThemePreferences(context) }
    val statsStore = remember { QuizStatsStore(context.applicationContext) }
    var quizStats by remember { mutableStateOf(statsStore.get()) }
    val profilePrefs = remember { ProfilePreferences(context) }
    var profileName by remember { mutableStateOf(profilePrefs.username) }
    var profilePictureUri by remember { mutableStateOf(profilePrefs.profilePictureUri) }

    // Offline / online mode. `forceOffline` is the manual Settings switch and is
    // SESSION-ONLY (never persisted) — when the app is killed and reopened the network
    // scan fires again and the user gets the prompt fresh. `detectedOffline` comes from
    // the startup connectivity probe. `startupDialogMode` drives the one-time startup
    // popup (OFFLINE requires acknowledgement; ONLINE is informational).
    var forceOffline by remember { mutableStateOf(false) }
    var detectedOffline by remember { mutableStateOf(false) }
    var startupDialogMode by remember { mutableStateOf<StartupMode?>(null) }
    val isOfflineMode = forceOffline || detectedOffline

    LaunchedEffect(Unit) {
        detectedOffline = !Connectivity.isOnline(context)
        startupDialogMode = if (detectedOffline) StartupMode.OFFLINE else StartupMode.ONLINE
    }

    var themeMode by remember { mutableStateOf(themePrefs.mode) }
    var accentName by remember { mutableStateOf(themePrefs.accent) }

    // Launcher-icon theme sync now happens silently when the app is backgrounded
    // (see MainActivity.onStop); no in-app prompt is shown. The Appearance toggle
    // (iconFollowsOsTheme) just persists the preference.

    // Home-screen quiz preset (topic / difficulty / count) is hoisted to this
    // always-composed root so the user's last selection survives navigating into a
    // quiz and back. rememberSaveable keeps it across process death too. (A
    // rememberSaveable placed *inside* HomeScreen did not restore under the
    // AnimatedContent swap, so the source of truth lives here.)
    var homeTopic by rememberSaveable { mutableStateOf("") }
    var homeDifficultyName by rememberSaveable { mutableStateOf(Difficulty.MEDIUM.name) }
    var homeQuestionCount by rememberSaveable { mutableStateOf(5) }

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // Navigation + quiz session state (single source of truth for the whole app).
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    // Tracks where the Statistics screen was opened from so its back arrow returns
    // to the correct place (Profile vs Settings).
    var statisticsSource by remember { mutableStateOf<Screen>(Screen.Settings) }
    // Tracks where the Settings screen was opened from so its back arrow returns to the
    // correct place (Profile vs Home), e.g. Profile -> Settings -> back -> Profile.
    var settingsSource by remember { mutableStateOf<Screen>(Screen.Home) }
    var questions by remember { mutableStateOf(emptyList<Question>()) }
    var userAnswers by remember { mutableStateOf<List<String?>>(emptyList()) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var quizDifficulty by remember { mutableStateOf("Medium") }
    var quizStartedAt by remember { mutableStateOf(0L) }

    val scope = rememberCoroutineScope()

    fun startQuiz(topic: String, difficulty: String, count: Int) {
        // Offline mode: skip any API attempt and go straight to the local bank
        // (stats still record normally in `answer`).
        if (isOfflineMode) {
            questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
            userAnswers = emptyList()
            currentQuestionIndex = 0
            score = 0
            currentScreen = Screen.Quiz
            return
        }
        quizDifficulty = difficulty
        quizStartedAt = System.currentTimeMillis()
        val provider = apiKeyStore.getActiveProvider()
        val key = provider?.let { apiKeyStore.getKey(it) }
        val model = provider?.let { apiKeyStore.getModel(it) }.orEmpty()

        if (provider != null && !key.isNullOrBlank() && model.isNotBlank()) {
            currentScreen = Screen.Loading
            scope.launch {
                ApiClient.generateQuiz(provider, key, model, topic, difficulty, count)
                    .onSuccess { qs ->
                        questions = if (qs.isNotEmpty()) qs.map { it.withShuffledOptions() }
                        else LocalQuestionBank.getQuestions(count, topic, difficulty)
                        userAnswers = emptyList()
                        currentQuestionIndex = 0
                        score = 0
                        currentScreen = Screen.Quiz
                    }
                    .onFailure {
                        questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
                        userAnswers = emptyList()
                        currentQuestionIndex = 0
                        score = 0
                        currentScreen = Screen.Quiz
                    }
            }
        } else {
            questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
            userAnswers = emptyList()
            currentQuestionIndex = 0
            score = 0
            currentScreen = Screen.Quiz
        }
    }

    fun answer(isCorrect: Boolean, selected: String?) {
        userAnswers = userAnswers + selected
        if (isCorrect) score++
        if (currentQuestionIndex < questions.lastIndex) {
            currentQuestionIndex++
        } else {
            // Quiz finished — fold the result into the persisted stats.
            val finishedQuestions = questions
            val finishedAnswers = userAnswers
            val finishedDifficulty = quizDifficulty
            scope.launch {
                statsStore.record(finishedDifficulty, finishedQuestions, finishedAnswers)
                quizStats = statsStore.get()
            }
            currentScreen = Screen.Results
        }
    }

    NazoTheme(darkTheme = isDark, accentId = accentName) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Base color + animated floating particles live OUTSIDE AnimatedContent so the
            // animation never resets on screen changes. Screens render transparent on top,
            // letting the particles drift behind their content.
            Box(modifier = Modifier.fillMaxSize().background(NazoBackground))
            FloatingParticlesBackground(modifier = Modifier.fillMaxSize())
            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (startupDialogMode != null) Modifier.blur(16.dp) else Modifier),
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "nazoScreenTransition",
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(
                        apiKeyActive = apiKeyStore.hasAnyActiveKey(),
                        offline = isOfflineMode,
                        onSettingsClick = {
                            settingsSource = Screen.Home
                            currentScreen = Screen.Settings
                        },
                        profileName = profileName,
                        profilePictureUri = profilePictureUri,
                        onProfileClick = { currentScreen = Screen.Profile },
                        onStartQuiz = { topic, difficulty, count -> startQuiz(topic, difficulty, count) },
                        topic = homeTopic,
                        difficultyName = homeDifficultyName,
                        questionCount = homeQuestionCount,
                        onTopicChange = { homeTopic = it },
                        onDifficultyChange = { homeDifficultyName = it },
                        onQuestionCountChange = { homeQuestionCount = it },
                    )

                    Screen.Settings -> SettingsScreen(
                        onBackClick = { currentScreen = settingsSource },
                        onHomeClick = { currentScreen = Screen.Home },
                        onOpenAiProvider = { currentScreen = Screen.AiProvider },
                        onOpenStatistics = {
                            statisticsSource = Screen.Settings
                            currentScreen = Screen.Statistics
                        },
                        onOpenAppearance = { currentScreen = Screen.Appearance },
                        onOpenBackupRestore = { currentScreen = Screen.BackupRestore },
                        onOpenAbout = { currentScreen = Screen.About },
                    forceOffline = forceOffline,
                    onForceOfflineChange = { v -> forceOffline = v },
                )

                    Screen.Profile -> ProfileScreen(
                        username = profileName,
                        profilePictureUri = profilePictureUri,
                        quizStats = quizStats,
                        onBack = { currentScreen = Screen.Home },
                        onUsernameChange = { name ->
                            profileName = name
                            profilePrefs.username = name
                        },
                        onProfilePictureChange = { uri ->
                            profilePictureUri = uri
                            profilePrefs.profilePictureUri = uri
                        },
                        onNavigateToStatistics = {
                            statisticsSource = Screen.Profile
                            currentScreen = Screen.Statistics
                        },
                        onNavigateToSettings = {
                            settingsSource = Screen.Profile
                            currentScreen = Screen.Settings
                        },
                    )

                    Screen.AiProvider -> AiProviderScreen(
                        apiKeyStore = apiKeyStore,
                        onBackClick = { currentScreen = Screen.Settings },
                        onHomeClick = { currentScreen = Screen.Home },
                        onSaved = { currentScreen = Screen.Settings },
                    )

                    Screen.Statistics -> StatisticsScreen(
                        stats = quizStats,
                        onBackClick = { currentScreen = statisticsSource },
                        onHomeClick = { currentScreen = Screen.Home },
                    )

                    Screen.Appearance -> AppearanceScreen(
                        currentMode = themeMode,
                        currentAccent = accentName,
                        onModeChange = { themeMode = it; themePrefs.mode = it },
                        onAccentChange = { accentName = it; themePrefs.accent = it },
                        iconFollowsOsTheme = themePrefs.iconFollowsOsTheme,
                        onIconFollowsOsThemeChange = { enabled ->
                            // Just persist the preference; the actual swap happens silently
                            // when the app is backgrounded (MainActivity.onStop).
                            themePrefs.iconFollowsOsTheme = enabled
                        },
                        onBackClick = { currentScreen = Screen.Settings },
                        onHomeClick = { currentScreen = Screen.Home },
                    )

                    Screen.BackupRestore -> BackupRestoreScreen(
                        onBackClick = { currentScreen = Screen.Settings },
                        onHomeClick = { currentScreen = Screen.Home },
                    )

                    Screen.About -> AboutScreen(
                        onBackClick = { currentScreen = Screen.Settings },
                        onHomeClick = { currentScreen = Screen.Home },
                    )

                    Screen.Quiz -> ActiveQuizScreen(
                        question = questions[currentQuestionIndex],
                        currentQuestionIndex = currentQuestionIndex,
                        totalQuestions = questions.size,
                        difficulty = quizDifficulty,
                        onNextQuestion = { isCorrect, selected -> answer(isCorrect, selected) },
                        onCloseClick = { currentScreen = Screen.Home },
                    )

                    Screen.Results -> QuizCompleteScreen(
                        score = score,
                        totalQuestions = questions.size,
                        timeSpent = formatElapsed(quizStartedAt),
                        difficulty = quizDifficulty,
                        onPlayAnother = { currentScreen = Screen.Home },
                        onReviewAnswers = { currentScreen = Screen.Review },
                        onSettingsClick = {
                            settingsSource = Screen.Home
                            currentScreen = Screen.Settings
                        },
                    )

                    Screen.Loading -> LoadingScreen(
                        onHomeClick = { currentScreen = Screen.Home },
                        onSettingsClick = {
                            settingsSource = Screen.Home
                            currentScreen = Screen.Settings
                        },
                    )

                    Screen.Review -> ReviewAnswersScreen(
                        questions = questions,
                        userAnswers = userAnswers,
                        onBackClick = { currentScreen = Screen.Results },
                        onHomeClick = { currentScreen = Screen.Home },
                    )
                }
            }

            if (startupDialogMode != null) {
                OfflineWarningDialog(
                    mode = startupDialogMode!!,
                    onGoOffline = {
                        forceOffline = true
                        startupDialogMode = null
                    },
                    onContinue = { startupDialogMode = null },
                )
            }
        }
    }
}

private fun formatElapsed(startedAt: Long): String {
    if (startedAt == 0L) return "0m 0s"
    val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0)
    return "${secs / 60}m ${secs % 60}s"
}


