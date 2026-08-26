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
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
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
    var navBarFloating by remember { mutableStateOf(themePrefs.floatingNavBar) }

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
    // Navigation back-stack: `navigationStack` is the source of truth for where we are and
    // where "back" should return to. Both the system back (gesture / hardware button) and the
    // in-app back arrows pop this stack, so a user always returns to the screen they came from.
    val navigationStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = navigationStack.last()
    var backPressedOnce by remember { mutableStateOf(false) }

    fun navigate(screen: Screen) {
        navigationStack.add(screen)
    }
    fun replace(screen: Screen) {
        if (navigationStack.isNotEmpty()) navigationStack[navigationStack.lastIndex] = screen
    }
    fun goBack() {
        if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex)
    }
    fun goHome() {
        navigationStack.clear()
        navigationStack.add(Screen.Home)
    }

    // Leaving Home cancels the "press back again to exit" flag; it also auto-resets after 2s.
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.Home) backPressedOnce = false
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000)
            backPressedOnce = false
        }
    }

    val activity = context as? Activity
    BackHandler(enabled = true) {
        if (startupDialogMode != null) {
            startupDialogMode = null
            return@BackHandler
        }
        if (navigationStack.size > 1) {
            goBack()
        } else {
            if (backPressedOnce) {
                activity?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }
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
            navigate(Screen.Quiz)
            return
        }
        quizDifficulty = difficulty
        quizStartedAt = System.currentTimeMillis()
        val provider = apiKeyStore.getActiveProvider()
        val key = provider?.let { apiKeyStore.getKey(it) }
        val model = provider?.let { apiKeyStore.getModel(it) }.orEmpty()

        if (provider != null && !key.isNullOrBlank() && model.isNotBlank()) {
            navigate(Screen.Loading)
            scope.launch {
                ApiClient.generateQuiz(provider, key, model, topic, difficulty, count)
                    .onSuccess { qs ->
                        questions = if (qs.isNotEmpty()) qs.map { it.withShuffledOptions() }
                        else LocalQuestionBank.getQuestions(count, topic, difficulty)
                        userAnswers = emptyList()
                        currentQuestionIndex = 0
                        score = 0
                        replace(Screen.Quiz)
                    }
                    .onFailure {
                        questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
                        userAnswers = emptyList()
                        currentQuestionIndex = 0
                        score = 0
                        replace(Screen.Quiz)
                    }
            }
        } else {
            questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
            userAnswers = emptyList()
            currentQuestionIndex = 0
            score = 0
            navigate(Screen.Quiz)
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
            navigate(Screen.Results)
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
                        onSettingsClick = { navigate(Screen.Settings) },
                        profileName = profileName,
                        profilePictureUri = profilePictureUri,
                        onProfileClick = { navigate(Screen.Profile) },
                        onStartQuiz = { topic, difficulty, count -> startQuiz(topic, difficulty, count) },
                        topic = homeTopic,
                        difficultyName = homeDifficultyName,
                        questionCount = homeQuestionCount,
                        onTopicChange = { homeTopic = it },
                        onDifficultyChange = { homeDifficultyName = it },
                        onQuestionCountChange = { homeQuestionCount = it },
                    )

                    Screen.Settings -> SettingsScreen(
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                        onOpenAiProvider = { navigate(Screen.AiProvider) },
                        onOpenStatistics = { navigate(Screen.Statistics) },
                        onOpenAppearance = { navigate(Screen.Appearance) },
                        onOpenBackupRestore = { navigate(Screen.BackupRestore) },
                        onOpenAbout = { navigate(Screen.About) },
                    forceOffline = forceOffline,
                    onForceOfflineChange = { v -> forceOffline = v },
                )

                    Screen.Profile -> ProfileScreen(
                        username = profileName,
                        profilePictureUri = profilePictureUri,
                        quizStats = quizStats,
                        onBack = { goBack() },
                        onUsernameChange = { name ->
                            profileName = name
                            profilePrefs.username = name
                        },
                        onProfilePictureChange = { uri ->
                            profilePictureUri = uri
                            profilePrefs.profilePictureUri = uri
                        },
                        onNavigateToStatistics = { navigate(Screen.Statistics) },
                        onNavigateToSettings = { navigate(Screen.Settings) },
                    )

                    Screen.AiProvider -> AiProviderScreen(
                        apiKeyStore = apiKeyStore,
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                        onSaved = { goBack() },
                    )

                    Screen.Statistics -> StatisticsScreen(
                        stats = quizStats,
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                    )

                    Screen.Appearance -> AppearanceScreen(
                        currentMode = themeMode,
                        currentAccent = accentName,
                        onModeChange = { themeMode = it; themePrefs.mode = it },
                        onAccentChange = { accentName = it; themePrefs.accent = it },
                        floatingNavBar = navBarFloating,
                        onFloatingNavBarChange = {
                            navBarFloating = it
                            themePrefs.floatingNavBar = it
                        },
                        iconFollowsOsTheme = themePrefs.iconFollowsOsTheme,
                        onIconFollowsOsThemeChange = { enabled ->
                            // Just persist the preference; the actual swap happens silently
                            // when the app is backgrounded (MainActivity.onStop).
                            themePrefs.iconFollowsOsTheme = enabled
                        },
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                    )

                    Screen.BackupRestore -> BackupRestoreScreen(
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                    )

                    Screen.About -> AboutScreen(
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                    )

                    Screen.Quiz -> ActiveQuizScreen(
                        question = questions[currentQuestionIndex],
                        currentQuestionIndex = currentQuestionIndex,
                        totalQuestions = questions.size,
                        difficulty = quizDifficulty,
                        onNextQuestion = { isCorrect, selected -> answer(isCorrect, selected) },
                        onCloseClick = { goHome() },
                    )

                    Screen.Results -> QuizCompleteScreen(
                        score = score,
                        totalQuestions = questions.size,
                        timeSpent = formatElapsed(quizStartedAt),
                        difficulty = quizDifficulty,
                        onPlayAnother = { goHome() },
                        onReviewAnswers = { navigate(Screen.Review) },
                        onSettingsClick = { navigate(Screen.Settings) },
                    )

                    Screen.Loading -> LoadingScreen(
                        onHomeClick = { goHome() },
                        onSettingsClick = { navigate(Screen.Settings) },
                    )

                    Screen.Review -> ReviewAnswersScreen(
                        questions = questions,
                        userAnswers = userAnswers,
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
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


