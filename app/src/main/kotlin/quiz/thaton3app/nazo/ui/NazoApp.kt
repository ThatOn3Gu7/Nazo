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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.Connectivity
import quiz.thaton3app.nazo.data.backup.BackupScheduler
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.records.RecordsStore
import quiz.thaton3app.nazo.daily.DailyChallenge
import quiz.thaton3app.nazo.daily.DailyStore
import quiz.thaton3app.nazo.achievements.AchievementEngine
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.OfflineWarningDialog
import quiz.thaton3app.nazo.ui.components.FloatingParticlesBackground
import quiz.thaton3app.nazo.ui.components.StartupMode
import quiz.thaton3app.nazo.ui.launch.IntroOverlay
import quiz.thaton3app.nazo.ui.onboarding.OnboardingPrefs
import quiz.thaton3app.nazo.ui.onboarding.OnboardingScreen
import quiz.thaton3app.nazo.ui.screens.*
import quiz.thaton3app.nazo.ui.theme.NazoTheme
import quiz.thaton3app.nazo.data.remote.QuizCache
import quiz.thaton3app.nazo.ui.screens.GenerationState
import quiz.thaton3app.nazo.modes.guessing_game.GuessApiClient
import quiz.thaton3app.nazo.modes.guessing_game.GuessImageFetcher
import quiz.thaton3app.nazo.modes.guessing_game.GuessPhase
import quiz.thaton3app.nazo.modes.guessing_game.GuessRoundResult
import quiz.thaton3app.nazo.modes.guessing_game.GuessScoring
import quiz.thaton3app.nazo.modes.guessing_game.GuessingPlayScreen
import quiz.thaton3app.nazo.modes.guessing_game.GuessingResultsScreen
import quiz.thaton3app.nazo.reminders.ReminderScheduler
import quiz.thaton3app.nazo.widget.NazoWidgetProvider

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
    data object GuessingGame : Screen
    data object GuessingResults : Screen
}

private data class GenerationRequest(
    val topic: String,
    val difficulty: String,
    val count: Int,
    val provider: String,
    val key: String,
    val model: String,
)

@Composable
fun NazoApp(launchDailyChallenge: Boolean = false) {
    val context = LocalContext.current
    val apiKeyStore = remember { ApiKeyStore(context) }
    val themePrefs = remember { ThemePreferences(context) }
    val statsStore = remember { QuizStatsStore(context.applicationContext) }
    var quizStats by remember { mutableStateOf(statsStore.get()) }
    // Personal bests (Phase 4): separate tiny store; previous best is captured
    // right before a finished run is submitted so the results screens can show
    // either the "New Record!" badge or the standing best as a caption.
    val recordsStore = remember { RecordsStore(context.applicationContext) }
    var quizPrevBest by remember { mutableIntStateOf(-1) }
    var quizNewRecord by remember { mutableStateOf(false) }
    var guessPrevBest by remember { mutableIntStateOf(-1) }
    var guessNewRecord by remember { mutableStateOf(false) }
    // Daily challenge (Phase 5): flag marks the in-flight quiz as today's
    // daily; the earned bonus is surfaced on the results screen.
    val dailyStore = remember { DailyStore(context.applicationContext) }
    var isDailyQuiz by remember { mutableStateOf(false) }
    var lastDailyBonus by remember { mutableIntStateOf(0) }
    // Sound effects (Phase 7): opt-in, persisted in the Sounds store.
    var soundEnabled by remember { mutableStateOf(Sounds.isEnabled(context)) }
    // Daily reminder notification (final polish pack): opt-in, evening-only.
    var remindersEnabled by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    val profilePrefs = remember { ProfilePreferences(context) }
    var profileName by remember { mutableStateOf(profilePrefs.username) }
    var profilePictureUri by remember { mutableStateOf(profilePrefs.profilePictureUri) }
    val backupPrefs = remember { BackupPrefs(context) }

    // First-launch onboarding tour (ui/onboarding). Shown as an overlay above
    // the app until completed/skipped, then never again (persisted flag).
    val onboardingPrefs = remember { OnboardingPrefs(context) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.completed) }

    LaunchedEffect(Unit) {
        BackupScheduler.apply(context, backupPrefs.autoBackupFrequency)
        // Re-arm the reminder worker if the pref survived a backup/restore
        // onto a device where the WorkManager job was never scheduled.
        ReminderScheduler.syncSchedule(context.applicationContext)
    }

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
        // Only block with a popup when offline — the "you're online" notice is no longer needed.
        startupDialogMode = if (detectedOffline) StartupMode.OFFLINE else null
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

    // ---- Guessing Game (modes/guessing_game) ----
    // Home-screen mode preset is hoisted here too (like the quiz presets) so the
    // user's last selection survives navigating away and back.
    // Last game mode played/selected is pre-selected on launch (falls back
    // to Quiz for anything stored that isn't a valid mode name).
    var homeMode by rememberSaveable {
        mutableStateOf(
            NazoMode.entries.firstOrNull { it.name == themePrefs.lastMode }?.name
                ?: NazoMode.QUIZ.name
        )
    }
    var guessRounds by rememberSaveable { mutableIntStateOf(3) }
    var guessTopic by remember { mutableStateOf("") }
    var guessDifficulty by remember { mutableStateOf("Medium") }
    var guessPhase by remember { mutableStateOf<GuessPhase>(GuessPhase.Idle) }
    // "blur" | "pixel" — the guessing game's image reveal style (Appearance).
    var guessRevealStyle by remember { mutableStateOf(themePrefs.guessRevealStyle) }
    var guessRound by remember { mutableIntStateOf(1) }
    var guessTotalRounds by remember { mutableIntStateOf(3) }
    var guessScore by remember { mutableIntStateOf(0) }
    var guessResults by remember { mutableStateOf<List<GuessRoundResult>>(emptyList()) }
    // Outcome of the CURRENT round (null until the player answers or time runs
    // out) — drives the in-place reveal on the play screen.
    var guessRoundResult by remember { mutableStateOf<GuessRoundResult?>(null) }
    // Targets already played this game, so the AI keeps picking something new.
    var guessAvoidTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    // In-flight round generation (AI call + image fetch) — cancelled on quit
    // so a stale round can never write into a newer game's state.
    var guessJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    var generationState by remember { mutableStateOf<GenerationState>(GenerationState.Idle) }
    var generationRequest by remember { mutableStateOf<GenerationRequest?>(null) }
    var aiGenerated by remember { mutableStateOf(false) }
    // Cache keys (provider:model:topic:difficulty:count) for AI question-sets that have already
    // been ANSWERED this session. If a regeneration hits a cache entry that's in here, we bypass
    // the cache and pull a fresh set from the API — so "Generate AI quiz" again doesn't repeat
    // questions the user just did. Session-only (in-memory), so a relaunch starts fresh anyway.
    var answeredKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun runLocal(topic: String, difficulty: String, count: Int) {
        questions = LocalQuestionBank.getQuestions(count, topic, difficulty)
        userAnswers = emptyList()
        currentQuestionIndex = 0
        score = 0
        aiGenerated = false
        generationState = GenerationState.Idle
        if (navigationStack.last() == Screen.Loading) replace(Screen.Quiz) else navigate(Screen.Quiz)
    }

    fun launchGeneration(req: GenerationRequest) {
        val cacheKey = QuizCache.key(req.provider, req.model, req.topic, req.difficulty, req.count)
        // Skip the cache when this exact set was already answered this session — otherwise the
        // user keeps getting the same questions. Fall through to a fresh API call instead.
        if (cacheKey !in answeredKeys) {
            QuizCache.get(cacheKey)?.let { cached ->
                questions = cached.map { it.withShuffledOptions() }
                userAnswers = emptyList()
                currentQuestionIndex = 0
                score = 0
                aiGenerated = true
                generationState = GenerationState.Idle
                if (navigationStack.last() == Screen.Loading) replace(Screen.Quiz) else navigate(Screen.Quiz)
                return
            }
        }
        generationState = GenerationState.Loading("${req.provider} • ${req.model}")
        if (navigationStack.last() != Screen.Loading) navigate(Screen.Loading)
        scope.launch {
            ApiClient.generateQuiz(req.provider, req.key, req.model, req.topic, req.difficulty, req.count)
                .onSuccess { qs ->
                    if (qs.isEmpty()) {
                        runLocal(req.topic, req.difficulty, req.count)
                        return@onSuccess
                    }
                    QuizCache.put(cacheKey, qs)
                    questions = qs.map { it.withShuffledOptions() }
                    userAnswers = emptyList()
                    currentQuestionIndex = 0
                    score = 0
                    aiGenerated = true
                    generationState = GenerationState.Idle
                    replace(Screen.Quiz)
                }
                .onFailure { e ->
                    val msg = e.message ?: "Something went wrong."
                    generationState = GenerationState.Error(
                        message = msg,
                        isModelError = msg.contains("model", ignoreCase = true),
                    )
                }
        }
    }

    fun startQuiz(topic: String, difficulty: String, count: Int) {
        themePrefs.lastMode = NazoMode.QUIZ.name
        isDailyQuiz = false
        // Set BEFORE the offline early-return: this branch used to skip both
        // assignments, so offline quizzes inherited the PREVIOUS game's
        // difficulty (wrong timer/hints/stats/records) and a stale start time.
        quizDifficulty = difficulty
        quizStartedAt = System.currentTimeMillis()
        // Offline mode: skip any API attempt and go straight to the local bank
        // (stats still record normally in `answer`).
        if (isOfflineMode) {
            runLocal(topic, difficulty, count)
            return
        }
        val provider = apiKeyStore.getSelectedProvider() ?: apiKeyStore.getActiveProvider()
        val key = provider?.let { apiKeyStore.getKey(it) }
        val model = provider?.let { apiKeyStore.getModel(it) }.orEmpty()

        if (provider != null && !key.isNullOrBlank() && model.isNotBlank()) {
            val req = GenerationRequest(topic, difficulty, count, provider, key, model)
            generationRequest = req
            launchGeneration(req)
        } else {
            runLocal(topic, difficulty, count)
        }
    }

    /**
     * Daily Challenge (Phase 5): a fixed, date-seeded 5-question set from the
     * local bank — bypasses AI generation entirely, so it works offline and
     * with no provider. Runs through the normal quiz flow (timer, stats,
     * streak, records under the "Daily" difficulty); completion is detected
     * in [answer] and banks the bonus XP.
     */
    fun startDailyChallenge() {
        questions = DailyChallenge.questionsForToday()
        userAnswers = emptyList()
        currentQuestionIndex = 0
        score = 0
        aiGenerated = false
        generationState = GenerationState.Idle
        quizDifficulty = "Daily"
        isDailyQuiz = true
        quizStartedAt = System.currentTimeMillis()
        navigate(Screen.Quiz)
    }

    // Launcher-shortcut deep link ("Daily" on app-icon long-press): jump straight
    // into today's challenge once on launch — but never over the first-run
    // onboarding, and not when today's daily is already cleared (Home then shows
    // the completed card as usual).
    LaunchedEffect(Unit) {
        if (launchDailyChallenge && !showOnboarding && !dailyStore.isCompletedToday()) {
            startDailyChallenge()
        }
    }

    fun answer(isCorrect: Boolean, selected: String?) {
        userAnswers = userAnswers + selected
        if (isCorrect) score++
        if (currentQuestionIndex < questions.lastIndex) {
            currentQuestionIndex++
        } else {
            // Mark this AI question-set as "already answered this session" so a later
            // regeneration with the same params bypasses the cache and fetches fresh ones.
            if (aiGenerated) {
                generationRequest?.let {
                    answeredKeys = answeredKeys + QuizCache.key(
                        it.provider, it.model, it.topic, it.difficulty, it.count,
                    )
                }
            }
            // Quiz finished — fold the result into the persisted stats.
            val finishedQuestions = questions
            val finishedAnswers = userAnswers
            val finishedDifficulty = quizDifficulty
            scope.launch {
                statsStore.record(finishedDifficulty, finishedQuestions, finishedAnswers)
                quizStats = statsStore.get()
                // Home-screen widget shows streak + daily status — keep it live.
                NazoWidgetProvider.refreshAll(context.applicationContext)
            }
            // Personal best check (previous best captured first for the caption).
            quizPrevBest = recordsStore.quizBestPercent(finishedDifficulty)
            quizNewRecord = recordsStore.submitQuiz(finishedDifficulty, score, finishedQuestions.size)
            // Daily challenge completion banks its bonus XP (0 for normal quizzes).
            lastDailyBonus = if (isDailyQuiz) {
                dailyStore.recordCompletion(score, finishedQuestions.size)
            } else {
                0
            }
            navigate(Screen.Results)
        }
    }

    // ---- Guessing Game orchestration (all UI lives in modes/guessing_game) ----

    fun prepareGuessRound() {
        if (isOfflineMode) {
            guessPhase = GuessPhase.Error(
                "Guessing Game needs an internet connection to fetch the answer set and the mystery image.",
                isOffline = true,
            )
            return
        }
        val provider = apiKeyStore.getSelectedProvider() ?: apiKeyStore.getActiveProvider()
        val key = provider?.let { apiKeyStore.getKey(it) }
        val model = provider?.let { apiKeyStore.getModel(it) }.orEmpty()
        if (provider == null || key.isNullOrBlank() || model.isBlank()) {
            guessPhase = GuessPhase.Error(
                "Set up an AI provider key and model first (Settings → AI & Model Configuration), then start a guessing game.",
                isOffline = false,
            )
            return
        }
        guessRoundResult = null
        guessPhase = GuessPhase.Preparing(guessRound)
        val startedRound = guessRound
        guessJob?.cancel()
        guessJob = scope.launch {
            GuessApiClient.generateGuessRound(
                provider, key, model, guessTopic, guessDifficulty, guessAvoidTargets,
            )
                .onSuccess { payload ->
                    // A cancelled / stale job (quit, or a newer round started)
                    // must not write into a different round's state.
                    if (guessRound != startedRound) return@onSuccess
                    // Image URL is best-effort: null just means the play screen
                    // shows its drawn placeholder instead of a fetched image.
                    val url = GuessImageFetcher.fetchImageUrl(
                        payload.targetEntity, payload.aliases, payload.imageQuery,
                    )
                    // Teach the next round not to repeat this round's target/aliases.
                    guessAvoidTargets = guessAvoidTargets + payload.displayAnswer() + payload.aliases
                    guessPhase = GuessPhase.Playing(payload, url)
                }
                .onFailure { e ->
                    if (guessRound != startedRound) return@onFailure
                    val msg = e.message ?: "Something went wrong."
                    guessPhase = GuessPhase.Error(msg, isOffline = false)
                }
        }
    }

    fun startGuessing(topic: String, difficulty: String, rounds: Int) {
        themePrefs.lastMode = NazoMode.GUESSING.name
        guessTopic = topic
        guessDifficulty = difficulty
        guessTotalRounds = rounds.coerceIn(1, 15)
        guessRound = 1
        guessScore = 0
        guessResults = emptyList()
        guessRoundResult = null
        guessAvoidTargets = emptyList()
        if (navigationStack.last() != Screen.GuessingGame) navigate(Screen.GuessingGame)
        prepareGuessRound()
    }

    fun guessRoundComplete(correct: Boolean, answerText: String?, remainingMs: Long) {
        val payload = (guessPhase as? GuessPhase.Playing)?.payload ?: return
        if (guessRoundResult != null) return // one shot per round
        val durationMs = GuessScoring.durationMsFor(guessDifficulty)
        val frac = if (durationMs > 0) (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val points = if (correct) {
            GuessScoring.pointsFor(GuessScoring.specFor(guessDifficulty).basePoints, frac)
        } else {
            0
        }
        val result = GuessRoundResult(
            round = guessRound,
            target = payload.displayAnswer(),
            aliases = payload.aliases,
            imageQuery = payload.imageQuery,
            correct = correct,
            answerText = answerText,
            points = points,
            remainingFraction = frac,
        )
        guessRoundResult = result
        guessScore += points
        guessResults = guessResults + result
    }

    fun guessNext() {
        val last = guessResults.lastOrNull() ?: return
        // All rounds are always played — a miss reveals the answer and moves
        // on; the game ends once the last round is finished.
        val finished = last.round >= guessTotalRounds
        if (finished) {
            // Game complete — fold the result into the shared stats, exactly
            // like a finished quiz (level/XP, streak, difficulty, topics).
            val finishedDifficulty = guessDifficulty
            val finishedTopic = guessTopic
            val finishedAnswered = guessResults.size
            val finishedCorrect = guessResults.count { it.correct }
            scope.launch {
                statsStore.recordGuessing(finishedDifficulty, finishedTopic, finishedAnswered, finishedCorrect)
                quizStats = statsStore.get()
                // Home-screen widget shows streak + daily status — keep it live.
                NazoWidgetProvider.refreshAll(context.applicationContext)
            }
            // Personal best check (previous best captured first for the caption).
            guessPrevBest = recordsStore.guessBestPoints(finishedDifficulty, guessTotalRounds)
            guessNewRecord = recordsStore.submitGuess(finishedDifficulty, guessTotalRounds, guessScore)
            guessPhase = GuessPhase.Idle
            replace(Screen.GuessingResults)
        } else {
            guessRound++
            prepareGuessRound()
        }
    }

    var selectedProvider by remember { mutableStateOf(apiKeyStore.getSelectedProvider()) }
    val activeProvider = selectedProvider ?: apiKeyStore.getActiveProvider()
    val configuredProviders = apiKeyStore.getConfiguredProviders()

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
                        activeProvider = activeProvider,
                        offline = isOfflineMode,
                        configuredProviders = configuredProviders,
                        onSelectProvider = { id ->
                            apiKeyStore.saveSelectedProvider(id)
                            selectedProvider = id
                        },
                        onManageClick = { navigate(Screen.AiProvider) },
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
                        mode = homeMode,
                        onModeChange = { homeMode = it; themePrefs.lastMode = it },
                        guessingRounds = guessRounds,
                        onGuessingRoundsChange = { guessRounds = it },
                        onStartGuessing = { topic, difficulty, rounds -> startGuessing(topic, difficulty, rounds) },
                        dailyCompleted = dailyStore.isCompletedToday(),
                        dailyScore = dailyStore.lastScore(),
                        dailyBonus = dailyStore.lastBonus(),
                        onPlayDaily = { startDailyChallenge() },
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
                    soundEnabled = soundEnabled,
                    onSoundEnabledChange = { v ->
                        soundEnabled = v
                        Sounds.setEnabled(context, v)
                        if (v) Sounds.correct(context) // instant preview of the new setting
                    },
                    remindersEnabled = remindersEnabled,
                    onRemindersEnabledChange = { v ->
                        remindersEnabled = v
                        ReminderScheduler.setEnabled(context.applicationContext, v)
                    },
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
                        bonusXp = dailyStore.totalBonusXp(),
                        achievements = AchievementEngine.compute(
                            stats = quizStats,
                            hasPerfectQuiz = listOf("Easy", "Medium", "Hard", "Otaku Master", "Daily")
                                .any { recordsStore.quizBestPercent(it) >= 100 },
                            dailiesCompleted = dailyStore.completedCount(),
                        ),
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
                        revealStyle = guessRevealStyle,
                        onRevealStyleChange = {
                            guessRevealStyle = it
                            themePrefs.guessRevealStyle = it
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
                        isAiGenerated = aiGenerated,
                        onNextQuestion = { isCorrect, selected -> answer(isCorrect, selected) },
                        onCloseClick = { goHome() },
                    )

                    Screen.Results -> QuizCompleteScreen(
                        score = score,
                        totalQuestions = questions.size,
                        timeSpent = formatElapsed(quizStartedAt),
                        difficulty = quizDifficulty,
                        bestPercent = quizPrevBest,
                        isNewRecord = quizNewRecord,
                        dailyBonusXp = lastDailyBonus,
                        onPlayAnother = { goHome() },
                        onReviewAnswers = { navigate(Screen.Review) },
                        onSettingsClick = { navigate(Screen.Settings) },
                    )

                    Screen.Loading -> LoadingScreen(
                        state = generationState,
                        onRetry = { generationRequest?.let { launchGeneration(it) } },
                        onUseLocal = { generationRequest?.let { runLocal(it.topic, it.difficulty, it.count) } },
                        onCancel = {
                            generationState = GenerationState.Idle
                            goHome()
                        },
                        onHomeClick = { goHome() },
                        onSettingsClick = { navigate(Screen.Settings) },
                        availableModels = generationRequest?.provider?.let { apiKeyStore.getModels(it) } ?: emptyList(),
                        currentModel = generationRequest?.model ?: "",
                        onChangeModel = { model ->
                            generationRequest?.let { req ->
                                apiKeyStore.saveModel(req.provider, model)
                                val next = req.copy(model = model)
                                generationRequest = next
                                launchGeneration(next)
                            }
                        },
                    )

                    Screen.Review -> ReviewAnswersScreen(
                        questions = questions,
                        userAnswers = userAnswers,
                        onBackClick = { goBack() },
                        onHomeClick = { goHome() },
                    )

                    Screen.GuessingGame -> GuessingPlayScreen(
                        topic = guessTopic,
                        difficultyLabel = guessDifficulty,
                        round = guessRound,
                        totalRounds = guessTotalRounds,
                        score = guessScore,
                        phase = guessPhase,
                        roundResult = guessRoundResult,
                        revealStyle = guessRevealStyle,
                        onRetryRound = { prepareGuessRound() },
                        onOpenSettings = { navigate(Screen.Settings) },
                        onQuit = {
                            guessJob?.cancel()
                            guessPhase = GuessPhase.Idle
                            goHome()
                        },
                        onRoundComplete = { correct, answerText, remainingMs ->
                            guessRoundComplete(correct, answerText, remainingMs)
                        },
                        onNextRound = { guessNext() },
                    )

                    Screen.GuessingResults -> GuessingResultsScreen(
                        score = guessScore,
                        results = guessResults,
                        topic = guessTopic,
                        difficulty = guessDifficulty,
                        bestPoints = guessPrevBest,
                        isNewRecord = guessNewRecord,
                        onPlayAgain = {
                            goBack()
                            startGuessing(guessTopic, guessDifficulty, guessTotalRounds)
                        },
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

            // Onboarding sits above the app content/dialogs but BELOW the
            // cold-start intro, so the 謎 animation plays over it and fades
            // out to reveal the tour on a true first launch. The tour is also
            // a setup wizard: theme/accent/reveal changes apply LIVE (same
            // state + persistence as AppearanceScreen), a verified provider
            // key refreshes `selectedProvider`, and "Play Now" launches the
            // user's very first game straight from the tour.
            if (showOnboarding) {
                OnboardingScreen(
                    isDark = isDark,
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it; themePrefs.mode = it },
                    accentId = accentName,
                    onAccentChange = { accentName = it; themePrefs.accent = it },
                    revealStyle = guessRevealStyle,
                    onRevealStyleChange = {
                        guessRevealStyle = it
                        themePrefs.guessRevealStyle = it
                    },
                    onProvidersChanged = {
                        selectedProvider = apiKeyStore.getSelectedProvider()
                    },
                    onPlayNow = { mode, topic ->
                        onboardingPrefs.completed = true
                        showOnboarding = false
                        homeTopic = topic
                        if (mode == "GUESSING") {
                            homeMode = NazoMode.GUESSING.name
                            startGuessing(topic, "Medium", 3)
                        } else {
                            homeMode = NazoMode.QUIZ.name
                            startQuiz(topic, "Medium", 5)
                        }
                    },
                    onFinish = {
                        onboardingPrefs.completed = true
                        showOnboarding = false
                    },
                )
            }

            IntroOverlay(isDark = isDark)
        }
    }
}

private fun formatElapsed(startedAt: Long): String {
    if (startedAt == 0L) return "0m 0s"
    val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0)
    return "${secs / 60}m ${secs % 60}s"
}


