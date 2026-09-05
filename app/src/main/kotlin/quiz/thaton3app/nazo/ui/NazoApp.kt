package quiz.thaton3app.nazo.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.data.LocalQuestionBank
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.Connectivity
import quiz.thaton3app.nazo.data.backup.BackupScheduler
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.data.settings.BackupPrefs
import quiz.thaton3app.nazo.data.settings.MissedQuestionsStore
import quiz.thaton3app.nazo.data.settings.ProfilePreferences
import quiz.thaton3app.nazo.data.settings.QuestionHistoryStore
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.IntroStyle
import quiz.thaton3app.nazo.LauncherIconSwitcher
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.records.RecordsStore
import quiz.thaton3app.nazo.daily.DailyChallenge
import quiz.thaton3app.nazo.daily.DailyStore
import quiz.thaton3app.nazo.achievements.AchievementEngine
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.components.OfflineWarningDialog
import quiz.thaton3app.nazo.ui.components.AiMissingDialog
import quiz.thaton3app.nazo.ui.components.AmbientBackground
import quiz.thaton3app.nazo.ui.components.FloatingParticlesBackground
import quiz.thaton3app.nazo.ui.components.StartupMode
import quiz.thaton3app.nazo.ui.components.CHANGELOG_ID
import quiz.thaton3app.nazo.ui.components.WhatsNewSheet
import quiz.thaton3app.nazo.ui.components.WhatsNewStore

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.random.Random
import kotlin.math.PI
import quiz.thaton3app.nazo.ui.launch.IntroOverlay
import quiz.thaton3app.nazo.ui.onboarding.OnboardingPrefs
import quiz.thaton3app.nazo.ui.onboarding.OnboardingScreen
import quiz.thaton3app.nazo.ui.screens.*
import quiz.thaton3app.nazo.ui.theme.NazoTheme
import quiz.thaton3app.nazo.data.remote.QuizCache
import quiz.thaton3app.nazo.ui.screens.GenerationState
import quiz.thaton3app.nazo.modes.guessing_game.GuessApiClient
import quiz.thaton3app.nazo.modes.guessing_game.GuessImageFetcher
import quiz.thaton3app.nazo.modes.guessing_game.GuessPayload
import quiz.thaton3app.nazo.modes.guessing_game.GuessPhase
import quiz.thaton3app.nazo.modes.guessing_game.GuessRoundResult
import quiz.thaton3app.nazo.modes.guessing_game.GuessScoring
import quiz.thaton3app.nazo.modes.guessing_game.GuessingPlayScreen
import quiz.thaton3app.nazo.modes.guessing_game.GuessingResultsScreen
import quiz.thaton3app.nazo.reminders.ReminderScheduler
import quiz.thaton3app.nazo.session.SessionMemory
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
    data object VersusHandoff : Screen
    data object VersusResults : Screen
    data object VersusReview : Screen
}

private data class GenerationRequest(
    val topic: String,
    val difficulty: String,
    val count: Int,
    val provider: String,
    val key: String,
    val model: String,
    /** True when this request is the automatic one-shot retry with another model. */
    val isFallback: Boolean = false,
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
    val settingsScrollState = rememberScrollState()

    // First-launch onboarding tour (ui/onboarding). Shown as an overlay above
    // the app until completed/skipped, then never again (persisted flag).
    val onboardingPrefs = remember { OnboardingPrefs(context) }
    var showOnboarding by remember { mutableStateOf(!onboardingPrefs.completed) }

    // Persistent question anti-repeat memory (survives launches; the
    // session-scoped layer stays in SessionMemory).
    val questionHistory = remember { QuestionHistoryStore(context.applicationContext) }
    // Practice deck: questions the player has missed and not yet re-mastered.
    val missedStore = remember { MissedQuestionsStore(context.applicationContext) }
    var missedCount by remember { mutableIntStateOf(0) }

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
    var showAiMissingDialog by remember { mutableStateOf(false) }
    var pendingQuizRequest by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    val isOfflineMode = forceOffline || detectedOffline

    LaunchedEffect(Unit) {
        detectedOffline = !Connectivity.isOnline(context)
        // Only block with a popup when offline — the "you're online" notice is no longer needed.
        startupDialogMode = if (detectedOffline) StartupMode.OFFLINE else null
    }

    // In-app changelog: one-time "What's new" sheet after an update. A true
    // first launch (onboarding shown) marks the current changelog as seen
    // silently — a brand-new user needs no diff.
    val whatsNewStore = remember { WhatsNewStore(context.applicationContext) }
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(showOnboarding, startupDialogMode) {
        if (showOnboarding) {
            whatsNewStore.lastSeenId = CHANGELOG_ID
        } else if (startupDialogMode == null && whatsNewStore.lastSeenId != CHANGELOG_ID) {
            delay(700) // let the intro/home settle first
            showWhatsNew = true
        }
    }

    var themeMode by remember { mutableStateOf(themePrefs.mode) }
    var accentName by remember { mutableStateOf(themePrefs.accent) }
    var navBarFloating by remember { mutableStateOf(themePrefs.floatingNavBar) }
    var backgroundStyle by remember { mutableStateOf(themePrefs.backgroundStyle) }
    var celebrationStyle by remember { mutableStateOf(themePrefs.celebrationStyle) }
    // sanitize(): an update can retire an icon variant, leaving the saved pref
    // pointing at a manifest component that no longer exists.
    var appIcon by remember {
        mutableStateOf(
            LauncherIconSwitcher.sanitize(context, themePrefs.appIcon)
                .also { if (it != themePrefs.appIcon) themePrefs.appIcon = it }
        )
    }

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
        // Practice-deck badge stays fresh every time Home comes back.
        if (currentScreen == Screen.Home) missedCount = missedStore.count()
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
        if (showAiMissingDialog) {
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

    // ---- Question-based game modes (share the quiz screen/flow) ----
    // "normal" (incl. daily & practice) | "survival" | "blitz" | "versus".
    var quizMode by remember { mutableStateOf("normal") }
    // Survival: 3 lives; the run ends on the 3rd wrong answer. Batches of 5
    // keep getting appended (AI when available, local bank otherwise).
    var survivalWrongs by remember { mutableIntStateOf(0) }
    var survivalFetching by remember { mutableStateOf(false) }
    // Blitz: one global 60-second deadline (epoch ms).
    var blitzDeadline by remember { mutableStateOf(0L) }
    // Versus: which player is at the phone (1 or 2) + Player 1's final score.
    var versusStage by remember { mutableIntStateOf(1) }
    var versusP1Score by remember { mutableIntStateOf(0) }
    // P1's picks, frozen at the handoff so the head-to-head review can show
    // both players side by side.
    var versusP1Answers by remember { mutableStateOf<List<String?>>(emptyList()) }

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
    // Whether the guessing game auto-crops mystery images to the character's
    // face + upper body (Appearance → Guessing Game).
    var guessAutoCrop by remember { mutableStateOf(themePrefs.guessAutoCrop) }
    var guessRound by remember { mutableIntStateOf(1) }
    var guessTotalRounds by remember { mutableIntStateOf(3) }
    var guessScore by remember { mutableIntStateOf(0) }
    var guessResults by remember { mutableStateOf<List<GuessRoundResult>>(emptyList()) }
    // Outcome of the CURRENT round (null until the player answers or time runs
    // out) — drives the in-place reveal on the play screen.
    var guessRoundResult by remember { mutableStateOf<GuessRoundResult?>(null) }
    // Targets already played this game, so the AI keeps picking something new.
    // (Round-target anti-repeat moved to session/SessionMemory.guessAvoidList()
    // — it now spans ALL guessing games this launch, not just one game, and
    // shares the per-session lifecycle with the quiz question memory.)
    // In-flight round generation (AI call + image fetch) — cancelled on quit
    // so a stale round can never write into a newer game's state.
    var guessJob by remember { mutableStateOf<Job?>(null) }
    // Next-round prefetch: while round N is loading / being played, round
    // N+1's AI answer set + mystery image are already being built in the
    // background, so "Next Round" is usually instant (see
    // kickGuessPrefetch / guessNext).
    //  - [guessPrefetch]    = a finished, not-yet-consumed round
    //  - [guessPrefetchJob] = the in-flight build; when it completes AFTER
    //    the player tapped Next, it hands the round straight to Playing
    var guessPrefetch by remember { mutableStateOf<PrefetchedGuessRound?>(null) }
    var guessPrefetchJob by remember { mutableStateOf<Job?>(null) }

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
        // Anti-repeat (session + persistent history): over-fetch from the
        // bank, prefer questions the player hasn't answered this launch OR
        // in the recent past, and only top up with seen ones when the pool
        // is exhausted — a small topic must still fill a full quiz.
        val pool = LocalQuestionBank.getQuestions(count * 4, topic, difficulty)
        val (seen, fresh) = pool.partition {
            SessionMemory.isQuestionSeen(it.text) || questionHistory.isSeen(it.text)
        }
        questions = (fresh + seen).take(count)
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
                // Unseen questions first — a cached set may partially overlap
                // with an abandoned run from earlier this session.
                val (seen, fresh) = cached.partition { SessionMemory.isQuestionSeen(it.text) }
                questions = (fresh + seen).take(cached.size).map { it.withShuffledOptions() }
                userAnswers = emptyList()
                currentQuestionIndex = 0
                score = 0
                aiGenerated = true
                generationState = GenerationState.Idle
                if (navigationStack.last() == Screen.Loading) replace(Screen.Quiz) else navigate(Screen.Quiz)
                return
            }
        }
        generationState = GenerationState.Loading(
            "${req.provider} • ${req.model}" + if (req.isFallback) " (auto-retry)" else ""
        )
        if (navigationStack.last() != Screen.Loading) navigate(Screen.Loading)
        scope.launch {
            ApiClient.generateQuiz(
                req.provider, req.key, req.model, req.topic, req.difficulty, req.count,
                // Avoid list = persistent history (recent past launches) +
                // this session's questions, deduped, capped for prompt size.
                avoidQuestions = (questionHistory.recentForPrompt(20) + SessionMemory.questionAvoidList())
                    .distinct().takeLast(40),
            )
                .onSuccess { qs ->
                    if (qs.isEmpty()) {
                        runLocal(req.topic, req.difficulty, req.count)
                        return@onSuccess
                    }
                    QuizCache.put(cacheKey, qs)
                    // Belt and braces: even with the prompt avoid list, put any
                    // question the player already answered this session last.
                    val (seen, fresh) = qs.partition { SessionMemory.isQuestionSeen(it.text) }
                    questions = (fresh + seen).take(qs.size).map { it.withShuffledOptions() }
                    userAnswers = emptyList()
                    currentQuestionIndex = 0
                    score = 0
                    aiGenerated = true
                    generationState = GenerationState.Idle
                    replace(Screen.Quiz)
                }
                .onFailure { e ->
                    // Retry-with-fallback: on the FIRST failure, silently try
                    // once more with the next model configured for this
                    // provider before surfacing an error screen.
                    if (!req.isFallback) {
                        val altModel = apiKeyStore.getModels(req.provider)
                            .map { it.id }
                            .firstOrNull { it != req.model }
                        if (altModel != null) {
                            val retry = req.copy(model = altModel, isFallback = true)
                            generationRequest = retry
                            launchGeneration(retry)
                            return@onFailure
                        }
                    }
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
        quizMode = "normal"
        // Set BEFORE the offline early-return: this branch used to skip both
        // assignments, so offline quizzes inherited the PREVIOUS game's
        // difficulty (wrong timer/hints/stats/records) and a stale start time.
        quizDifficulty = difficulty
        quizStartedAt = SystemClock.elapsedRealtime()
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
            // User is online, but no API key is set up. Show non-dismissible AI missing dialog.
            pendingQuizRequest = Triple(topic, difficulty, count)
            showAiMissingDialog = true
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
        quizMode = "normal"
        quizStartedAt = SystemClock.elapsedRealtime()
        navigate(Screen.Quiz)
    }

    /**
     * Practice deck: replays up to 10 questions the player previously got
     * wrong (MissedQuestionsStore). Fully offline, no provider needed. Runs
     * through the normal quiz flow; stats land under the "Practice"
     * difficulty and correctly answered questions graduate out of the deck.
     */
    fun startPractice() {
        val set = missedStore.practiceSet(10)
        if (set.isEmpty()) return
        questions = set
        userAnswers = emptyList()
        currentQuestionIndex = 0
        score = 0
        aiGenerated = false
        generationState = GenerationState.Idle
        quizDifficulty = "Practice"
        isDailyQuiz = false
        quizMode = "normal"
        quizStartedAt = SystemClock.elapsedRealtime()
        navigate(Screen.Quiz)
    }

    // ---- Survival / Blitz / Versus (all reuse the quiz screen + answer flow) ----

    /** Appends a fresh batch of local-bank questions, skipping anything already loaded or seen. */
    fun appendLocalSurvivalBatch() {
        val loaded = questions.map { it.text }.toSet()
        val pool = LocalQuestionBank.getQuestions(40, homeTopic, quizDifficulty)
            .filter { it.text !in loaded }
        val (seen, fresh) = pool.partition {
            SessionMemory.isQuestionSeen(it.text) || questionHistory.isSeen(it.text)
        }
        questions = questions + (fresh + seen).take(5)
    }

    /**
     * Survival top-up: keeps the horizon at least a batch ahead. Online with
     * a provider it fetches 5 more AI questions in the background; otherwise
     * (or on failure) the local bank fills in — a run never starves.
     */
    fun survivalTopUp() {
        if (survivalFetching) return
        val req = generationRequest
        if (!isOfflineMode && req != null && aiGenerated) {
            survivalFetching = true
            scope.launch {
                ApiClient.generateQuiz(
                    req.provider, req.key, req.model, req.topic, req.difficulty, 5,
                    avoidQuestions = (questionHistory.recentForPrompt(20) + SessionMemory.questionAvoidList())
                        .distinct().takeLast(40),
                )
                    .onSuccess { qs ->
                        if (quizMode == "survival") {
                            val loaded = questions.map { it.text }.toSet()
                            val fresh = qs.filter {
                                it.text !in loaded && !SessionMemory.isQuestionSeen(it.text)
                            }
                            if (fresh.isNotEmpty()) {
                                questions = questions + fresh.map { it.withShuffledOptions() }
                            } else {
                                appendLocalSurvivalBatch()
                            }
                        }
                        survivalFetching = false
                    }
                    .onFailure {
                        if (quizMode == "survival") appendLocalSurvivalBatch()
                        survivalFetching = false
                    }
            }
        } else {
            appendLocalSurvivalBatch()
        }
    }

    /** Ends a survival run: fold answered questions into stats, check the longest-run record. */
    fun finishSurvival() {
        if (quizMode != "survival") return // double-fire guard
        quizMode = "survival-done" // also blocks any in-flight top-up from appending
        val answered = questions.take(userAnswers.size)
        val finishedAnswers = userAnswers
        val finishedDifficulty = quizDifficulty
        // The quiz screen may still render one exit-transition frame — keep the
        // index inside the truncated list.
        currentQuestionIndex = (userAnswers.size - 1).coerceAtLeast(0)
        questions = answered.ifEmpty { questions.take(1) } // Results/Review only see what was actually played
        scope.launch {
            statsStore.record(finishedDifficulty, answered, finishedAnswers)
            quizStats = statsStore.get()
            NazoWidgetProvider.refreshAll(context.applicationContext)
        }
        quizPrevBest = recordsStore.survivalBest()
        quizNewRecord = recordsStore.submitSurvival(score)
        replace(Screen.Results)
    }

    fun startSurvival(topic: String, difficulty: String) {
        themePrefs.lastMode = NazoMode.SURVIVAL.name
        isDailyQuiz = false
        quizMode = "survival"
        survivalWrongs = 0
        survivalFetching = false
        quizDifficulty = difficulty
        quizStartedAt = SystemClock.elapsedRealtime()
        if (isOfflineMode) {
            runLocal(topic, difficulty, 5)
            return
        }
        val provider = apiKeyStore.getSelectedProvider() ?: apiKeyStore.getActiveProvider()
        val key = provider?.let { apiKeyStore.getKey(it) }
        val model = provider?.let { apiKeyStore.getModel(it) }.orEmpty()
        if (provider != null && !key.isNullOrBlank() && model.isNotBlank()) {
            val req = GenerationRequest(topic, difficulty, 5, provider, key, model)
            generationRequest = req
            launchGeneration(req)
        } else {
            // No provider set up → survival still works instantly off the bank.
            runLocal(topic, difficulty, 5)
        }
    }

    /** Ends a blitz run (time-up or pool exhausted): stats + most-in-60s record. */
    fun finishBlitz() {
        if (quizMode != "blitz") return // double-fire guard (time-up races an answer)
        quizMode = "blitz-done"
        val answered = questions.take(userAnswers.size)
        val finishedAnswers = userAnswers
        // Time-up can land mid-question: clamp the index and never leave the
        // list empty, since the quiz screen may render one more exit frame.
        currentQuestionIndex = (userAnswers.size - 1).coerceAtLeast(0)
        questions = answered.ifEmpty { questions.take(1) }
        scope.launch {
            statsStore.record("Blitz", answered, finishedAnswers)
            quizStats = statsStore.get()
            NazoWidgetProvider.refreshAll(context.applicationContext)
        }
        quizPrevBest = recordsStore.blitzBest()
        quizNewRecord = recordsStore.submitBlitz(score)
        replace(Screen.Results)
    }

    fun startBlitz(topic: String, difficulty: String) {
        themePrefs.lastMode = NazoMode.BLITZ.name
        isDailyQuiz = false
        quizMode = "blitz"
        quizDifficulty = "Blitz"
        quizStartedAt = SystemClock.elapsedRealtime()
        // Blitz is instant + offline by design: a big local-bank pool, unseen
        // questions first. The chosen difficulty picks the pool.
        val pool = LocalQuestionBank.getQuestions(80, topic, difficulty)
        val (seen, fresh) = pool.partition {
            SessionMemory.isQuestionSeen(it.text) || questionHistory.isSeen(it.text)
        }
        questions = (fresh + seen).take(60)
        userAnswers = emptyList()
        currentQuestionIndex = 0
        score = 0
        aiGenerated = false
        generationState = GenerationState.Idle
        blitzDeadline = SystemClock.elapsedRealtime() + 60_000L
        navigate(Screen.Quiz)
    }

    fun startVersus(topic: String, difficulty: String, count: Int) {
        themePrefs.lastMode = NazoMode.VERSUS.name
        isDailyQuiz = false
        quizMode = "versus"
        versusStage = 1
        versusP1Score = 0
        versusP1Answers = emptyList()
        quizDifficulty = difficulty
        quizStartedAt = SystemClock.elapsedRealtime()
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
            // Party mode should start instantly — no setup nagging, local bank.
            runLocal(topic, difficulty, count)
        }
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
        // A finished run's screen can still fire one queued answer during the
        // exit transition (blitz auto-advance, double-tap) — ignore it.
        if (quizMode == "blitz-done" || quizMode == "survival-done") return
        // Anti-repeat: the moment a question is answered (right OR wrong)
        // it's remembered — for this launch (SessionMemory) AND across
        // launches (QuestionHistoryStore). Quitting mid-quiz still counts
        // the ones already faced. Applies to quiz, daily and offline.
        questions.getOrNull(currentQuestionIndex)?.let { q ->
            SessionMemory.recordQuestion(q.text)
            questionHistory.record(q.text)
            // Practice deck: a miss joins the deck; a correct answer anywhere
            // graduates it out. Versus is excluded — guest answers must not
            // pollute the owner's deck.
            if (quizMode != "versus") {
                if (isCorrect) missedStore.recordCorrect(q.text) else missedStore.recordMiss(q)
            }
        }
        userAnswers = userAnswers + selected
        if (isCorrect) score++

        // ---- SURVIVAL: 3 lives, endless horizon ----
        if (quizMode == "survival") {
            if (!isCorrect) survivalWrongs++
            if (survivalWrongs >= 3) {
                finishSurvival()
                return
            }
            // Keep at least a batch ahead; background AI fetch or local fill.
            if (currentQuestionIndex >= questions.size - 3) survivalTopUp()
            if (currentQuestionIndex < questions.lastIndex) {
                currentQuestionIndex++
            } else {
                // Horizon caught up with us — try an instant local fill.
                appendLocalSurvivalBatch()
                if (currentQuestionIndex < questions.lastIndex) {
                    currentQuestionIndex++
                } else {
                    finishSurvival() // pool truly exhausted
                }
            }
            return
        }

        // ---- BLITZ: only the clock (or an empty pool) ends the run ----
        if (quizMode == "blitz") {
            if (currentQuestionIndex < questions.lastIndex) {
                currentQuestionIndex++
            } else {
                finishBlitz()
            }
            return
        }

        // ---- VERSUS: stage 1 → handoff; stage 2 → head-to-head results ----
        if (quizMode == "versus") {
            if (currentQuestionIndex < questions.lastIndex) {
                currentQuestionIndex++
            } else if (versusStage == 1) {
                versusP1Score = score
                versusP1Answers = userAnswers
                // Same questions for Player 2, options re-shuffled; P1's
                // score is kept secret until the results screen.
                questions = questions.map { it.withShuffledOptions() }
                userAnswers = emptyList()
                currentQuestionIndex = 0
                score = 0
                versusStage = 2
                replace(Screen.VersusHandoff)
            } else {
                // Party mode: intentionally NO stats/records writes.
                replace(Screen.VersusResults)
            }
            return
        }

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
            // REPLACE the quiz screen (don't push on top of it): the finished
            // quiz must not stay on the back-stack, otherwise the system back
            // gesture from the results screen returns to the last question.
            // Back from Results now lands on Home. (Guessing mode already did
            // this via replace(Screen.GuessingResults).)
            replace(Screen.Results)
        }
    }

    // ---- Guessing Game orchestration (all UI lives in modes/guessing_game) ----

    /** Cancels an in-flight next-round prefetch and drops a stored one. */
    fun clearGuessPrefetch() {
        guessPrefetchJob?.cancel()
        guessPrefetchJob = null
        guessPrefetch = null
    }

    /**
     * Starts the next round's AI call + image fetch in the background while
     * the current round is in play. No visible UI changes when it runs —
     * the payoff shows up in [guessNext]:
     *
     *  - player still in round N when it finishes  → stashed in
     *    [guessPrefetch]; "Next Round" skips the preparing screen entirely
     *  - player ALREADY tapped Next when it finishes → handed straight to
     *    [GuessPhase.Playing] (the preparing screen was only shown briefly)
     *  - it FAILS while the player is still in N → silent (tapping Next
     *    regenerates on demand); visible error only when the player is
     *    already waiting on the preparing screen.
     *
     * The avoid list is snapshotted at kick time — round N's target was
     * recorded by [beginGuessRoundJob] before this is called, so the
     * prefetched round never repeats it.
     */
    fun kickGuessPrefetch(startedRound: Int) {
        if (startedRound >= guessTotalRounds) return // last round — nothing to build
        if (guessPrefetchJob != null || guessPrefetch != null) return // already in flight / done
        if (isOfflineMode) return
        // Silent provider lookup — a missing key here must NEVER disturb the
        // round the player is currently playing.
        val provider = apiKeyStore.getSelectedProvider() ?: apiKeyStore.getActiveProvider() ?: return
        val key = apiKeyStore.getKey(provider) ?: return
        val model = apiKeyStore.getModel(provider) ?: return
        if (key.isBlank() || model.isBlank()) return
        val avoid = SessionMemory.guessAvoidList()
        val targetRound = startedRound + 1
        guessPrefetchJob = scope.launch {
            // Stays "in flight" until the round is FULLY built (AI + image),
            // so that:
            //  - a "Next" tap during this window doesn't double-generate
            //    the same round (see guessNext's in-flight branch), and
            //  - quitting the game can cancel the work (clearGuessPrefetch).
            // generateGuessRound never throws (runCatching) — Result is the
            // only outcome; a cancellation ends the job, and the CANCELLER
            // (clearGuessPrefetch) owns the handle in that case.
            val result = GuessApiClient.generateGuessRound(
                provider, key, model, guessTopic, guessDifficulty, avoid,
            )
            var chainNext = false
            if (result.isSuccess) {
                val payload = result.getOrThrow()
                // Pre-fetch the image BYTES as well: the one-slot cache in
                // GuessImageFetcher then serves them to the play screen the
                // instant the round is handed over — no second network
                // round-trip before the countdown can start.
                // (fetchImageBytes is a BLOCKING plain-HTTP call — run it on
                // IO; fetchImageUrl is suspend and hops to IO itself.)
                val url = GuessImageFetcher.fetchImageUrl(
                    payload.targetEntity, payload.aliases, payload.imageQuery,
                    topic = guessTopic,
                )
                if (url != null) withContext(Dispatchers.IO) { GuessImageFetcher.fetchImageBytes(url) }
                // Record for the avoid list of any FUTURE round (this
                // prefetch's own list was snapshotted before it ran).
                SessionMemory.recordGuessTarget(payload.displayAnswer())
                payload.aliases.forEach { SessionMemory.recordGuessTarget(it) }
                if (guessRound == targetRound) {
                    // Player already tapped Next — the preparing screen is
                    // showing; hand the finished round over directly.
                    guessRoundResult = null
                    guessPhase = GuessPhase.Playing(payload, url)
                    // Keep the chain alive: this round was built by the
                    // prefetch, not beginGuessRoundJob, so kick ITS next
                    // round — AFTER the handle is cleared below, so the new
                    // job's handle survives.
                    chainNext = true
                } else {
                    // Still playing the previous round — stash for the
                    // instant "Next Round".
                    guessPrefetch = PrefetchedGuessRound(targetRound, payload, url)
                }
            } else {
                if (guessRound == targetRound) {
                    // Player already tapped Next and is waiting on the
                    // preparing screen — surface the error so its
                    // "Try again" path works.
                    guessPhase = GuessPhase.Error(result.exceptionOrNull()?.message ?: "Something went wrong.", isOffline = false)
                }
                // Otherwise silent: the player is still in round N, and
                // tapping Next regenerates N+1 on demand.
            }
            guessPrefetchJob = null
            if (chainNext) kickGuessPrefetch(targetRound)
        }
    }

    /**
     * The round job: AI answer set, then mystery image, then Playing.
     * Kicks the NEXT round's background build as soon as the current round's
     * payload exists (before its image fetch) — see [kickGuessPrefetch].
     */
    fun beginGuessRoundJob(provider: String, key: String, model: String) {
        guessRoundResult = null
        guessPhase = GuessPhase.Preparing(guessRound)
        val startedRound = guessRound
        guessJob?.cancel()
        guessJob = scope.launch {
            GuessApiClient.generateGuessRound(
                provider, key, model, guessTopic, guessDifficulty, SessionMemory.guessAvoidList(),
            )
                .onSuccess { payload ->
                    // A cancelled / stale job (quit, or a newer round started)
                    // must not write into a different round's state.
                    if (guessRound != startedRound) return@onSuccess
                    // Teach ALL later rounds this session (any game) not to
                    // repeat this round's target or its aliases — BEFORE the
                    // next-round prefetch snapshots its avoid list.
                    SessionMemory.recordGuessTarget(payload.displayAnswer())
                    payload.aliases.forEach { SessionMemory.recordGuessTarget(it) }
                    // Next-round prefetch: while the image below loads — and
                    // then while the player plays — build round N+1 in the
                    // background so "Next Round" is usually instant.
                    kickGuessPrefetch(startedRound)
                    // Image URL is best-effort: null just means the play screen
                    // shows its drawn placeholder instead of a fetched image.
                    val url = GuessImageFetcher.fetchImageUrl(
                        payload.targetEntity, payload.aliases, payload.imageQuery,
                        topic = guessTopic,
                    )
                    if (guessRound != startedRound) return@onSuccess
                    guessPhase = GuessPhase.Playing(payload, url)
                }
                .onFailure { e ->
                    if (guessRound != startedRound) return@onFailure
                    val msg = e.message ?: "Something went wrong."
                    guessPhase = GuessPhase.Error(msg, isOffline = false)
                }
        }
    }

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
        // A retry / fresh start invalidates any in-flight or stored prefetch
        // of the (now stale) next round.
        clearGuessPrefetch()
        beginGuessRoundJob(provider, key, model)
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
            // Game over — drop any lingering prefetch state (defensive: the
            // last round never kicks one, but a stale job must not outlive
            // the game).
            clearGuessPrefetch()
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
            guessRoundResult = null
            val prebuilt = guessPrefetch
            guessPrefetch = null
            if (prebuilt != null && prebuilt.round == guessRound) {
                // The background job already built this round while the
                // player was playing the last one — straight into the game,
                // no preparing screen.
                guessPhase = GuessPhase.Playing(prebuilt.payload, prebuilt.url)
                // Keep the prefetch chain alive for the round AFTER this one
                // (guarded: skips on the last round / a missing provider /
                // an existing in-flight build).
                kickGuessPrefetch(guessRound)
            } else if (guessPrefetchJob != null) {
                // A prefetch is already producing EXACTLY this round — it
                // will set Playing (or Error) the moment it finishes. Do NOT
                // start a second generation job (double API cost for the
                // same round); the preparing screen shows meanwhile, which
                // is shorter than a cold start because the work is underway.
                guessPhase = GuessPhase.Preparing(guessRound)
            } else {
                // No prefetch in flight (skipped — provider not configured,
                // last-round guard, or a failed build) — generate now.
                prepareGuessRound()
            }
        }
    }

    var selectedProvider by remember { mutableStateOf(apiKeyStore.getSelectedProvider()) }
    val activeProvider = selectedProvider ?: apiKeyStore.getActiveProvider()
    val configuredProviders = apiKeyStore.getConfiguredProviders()

    val rootFocusManager = LocalFocusManager.current
    NazoTheme(darkTheme = isDark, accentId = accentName) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // App-wide keyboard etiquette: tapping any dead space (outside
                // buttons/fields) clears focus, which dismisses the IME. Taps
                // consumed by children (fields, buttons) never reach this.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { rootFocusManager.clearFocus() })
                }
        ) {
            // Base color + animated floating particles live OUTSIDE AnimatedContent so the
            // animation never resets on screen changes. Screens render transparent on top,
            // letting the particles drift behind their content.
            Box(modifier = Modifier.fillMaxSize().background(NazoBackground))
            AmbientBackground(
                modifier = Modifier.fillMaxSize(),
                style = backgroundStyle,
            )
            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (startupDialogMode != null || showAiMissingDialog) Modifier.blur(16.dp) else Modifier),
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
                        onStartSurvival = { topic, difficulty -> startSurvival(topic, difficulty) },
                        onStartBlitz = { topic, difficulty -> startBlitz(topic, difficulty) },
                        onStartVersus = { topic, difficulty, count -> startVersus(topic, difficulty, count) },
                        dailyCompleted = dailyStore.isCompletedToday(),
                        dailyScore = dailyStore.lastScore(),
                        dailyBonus = dailyStore.lastBonus(),
                        onPlayDaily = { startDailyChallenge() },
                        streakDays = quizStats.currentStreakDays,
                        practiceCount = missedCount,
                        onStartPractice = { startPractice() },
                    )

                    Screen.Settings -> SettingsScreen(
                        scrollState = settingsScrollState,
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

                    Screen.Statistics -> {
                        // Memoised: this walks every achievement rule and hits
                        // the records/daily stores on each call. Inline as a
                        // parameter it re-ran on every recomposition of the
                        // screen (scrolling, theme change). The inputs only
                        // change when a quiz is recorded, so key on those.
                        val dailiesCompleted = dailyStore.completedCount()
                        val bonusXp = dailyStore.totalBonusXp()
                        val achievements = remember(quizStats, dailiesCompleted) {
                            AchievementEngine.compute(
                                stats = quizStats,
                                hasPerfectQuiz = listOf("Easy", "Medium", "Hard", "Otaku Master", "Daily")
                                    .any { recordsStore.quizBestPercent(it) >= 100 },
                                dailiesCompleted = dailiesCompleted,
                            )
                        }
                        StatisticsScreen(
                            stats = quizStats,
                            bonusXp = bonusXp,
                            achievements = achievements,
                            onBackClick = { goBack() },
                            onHomeClick = { goHome() },
                        )
                    }

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
                        guessAutoCrop = guessAutoCrop,
                        onGuessAutoCropChange = {
                            guessAutoCrop = it
                            themePrefs.guessAutoCrop = it
                        },
                        backgroundStyle = backgroundStyle,
                        onBackgroundStyleChange = {
                            backgroundStyle = it
                            themePrefs.backgroundStyle = it
                        },
                        celebrationStyle = celebrationStyle,
                        onCelebrationStyleChange = {
                            celebrationStyle = it
                            themePrefs.celebrationStyle = it
                        },
                        iconFollowsOsTheme = themePrefs.iconFollowsOsTheme,
                        onIconFollowsOsThemeChange = { enabled ->
                            // Just persist the preference; the actual swap happens silently
                            // when the app is backgrounded (MainActivity.onStop).
                            themePrefs.iconFollowsOsTheme = enabled
                        },
                        appIcon = appIcon,
                        onAppIconChange = { id ->
                            appIcon = id
                            themePrefs.appIcon = id
                            // A custom icon and "follow the OS theme" are mutually
                            // exclusive — picking one turns the automatic mode off.
                            themePrefs.iconFollowsOsTheme = false
                            LauncherIconSwitcher.select(context, id)
                            // Disabling the alias that launched this task makes Android
                            // tear the task down; finishing ourselves makes that graceful
                            // and predictable instead of looking like a crash.
                            (context as? Activity)?.finishAndRemoveTask()
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
                        endless = quizMode == "survival",
                        livesLeft = 3 - survivalWrongs,
                        playerLabel = if (quizMode == "versus") "P$versusStage" else null,
                        blitzDeadlineMs = if (quizMode == "blitz") blitzDeadline else null,
                        onBlitzTimeUp = { finishBlitz() },
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
                        modeLabel = when (quizMode) {
                            "survival", "survival-done" -> "Survival"
                            "blitz", "blitz-done" -> "Blitz"
                            else -> null
                        },
                        onPlayAnother = { goHome() },
                        onReviewAnswers = { navigate(Screen.Review) },
                        onSettingsClick = { navigate(Screen.Settings) },
                    )

                    Screen.VersusHandoff -> VersusHandoffScreen(
                        p1Score = versusP1Score,
                        totalQuestions = questions.size,
                        onPlayer2Ready = { replace(Screen.Quiz) },
                    )

                    Screen.VersusResults -> VersusResultsScreen(
                        p1Score = versusP1Score,
                        p2Score = score,
                        totalQuestions = questions.size,
                        topic = homeTopic,
                        difficulty = quizDifficulty,
                        onPlayAgain = {
                            startVersus(homeTopic, quizDifficulty, questions.size)
                        },
                        onReviewAnswers = { navigate(Screen.VersusReview) },
                        onHomeClick = { goHome() },
                    )

                    Screen.VersusReview -> VersusReviewScreen(
                        questions = questions,
                        p1Answers = versusP1Answers,
                        p2Answers = userAnswers,
                        p1Score = versusP1Score,
                        p2Score = score,
                        onBackClick = { goBack() },
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
                        autoCrop = guessAutoCrop,
                        onRetryRound = { prepareGuessRound() },
                        onOpenSettings = { navigate(Screen.Settings) },
                        onQuit = {
                            guessJob?.cancel()
                            clearGuessPrefetch()
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

            // The bottom nav lives OUTSIDE AnimatedContent, for the same reason
            // AmbientBackground does: anything inside gets torn down and rebuilt
            // on every screen change, so its animation state is destroyed.
            //
            // It used to be rendered by HomeScreen and SettingsScreen separately.
            // Those are two DIFFERENT composables that never coexist — switching
            // tabs disposed one and created the other, so animateColorAsState /
            // animateContentSize always started at their target value and the
            // expand/collapse transition could never be seen. One shared instance
            // here survives the swap, so the pill genuinely animates between tabs.
            //
            // Only the two tab destinations show it; submenus stay full-screen.
            val navTab = when (currentScreen) {
                Screen.Home -> NazoTab.Home
                Screen.Settings -> NazoTab.Settings
                else -> null
            }
            if (navTab != null && !showOnboarding) {
                NazoBottomNav(
                    selected = navTab,
                    onHomeClick = { if (currentScreen != Screen.Home) goHome() },
                    onSettingsClick = { if (currentScreen != Screen.Settings) navigate(Screen.Settings) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Match the blur AnimatedContent gets behind a startup
                        // dialog; the bar used to be inside a screen and so was
                        // blurred with it.
                        .then(
                            if (startupDialogMode != null || showAiMissingDialog) Modifier.blur(16.dp)
                            else Modifier
                        ),
                )
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

            // One-time "What's new" sheet after an update (in-app changelog).
            // Only over Home, never over dialogs/onboarding; dismissing marks
            // the current changelog id as seen.
            if (showWhatsNew && currentScreen == Screen.Home && !showOnboarding) {
                WhatsNewSheet(
                    onDismiss = {
                        whatsNewStore.lastSeenId = CHANGELOG_ID
                        showWhatsNew = false
                    },
                )
            }

            if (showAiMissingDialog) {
                AiMissingDialog(
                    onGoOffline = {
                        forceOffline = true
                        showAiMissingDialog = false
                        pendingQuizRequest?.let { (t, d, c) ->
                            runLocal(t, d, c)
                        }
                        pendingQuizRequest = null
                    },
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
                    backgroundStyle = backgroundStyle,
                    onBackgroundStyleChange = {
                        backgroundStyle = it
                        themePrefs.backgroundStyle = it
                    },
                    celebrationStyle = celebrationStyle,
                    onCelebrationStyleChange = {
                        celebrationStyle = it
                        themePrefs.celebrationStyle = it
                    },
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
                    soundEnabled = soundEnabled,
                    onSoundEnabledChange = { v ->
                        soundEnabled = v
                        Sounds.setEnabled(context, v)
                    },
                    remindersEnabled = remindersEnabled,
                    onRemindersEnabledChange = { v ->
                        remindersEnabled = v
                        ReminderScheduler.setEnabled(context.applicationContext, v)
                    },
                    forceOffline = forceOffline,
                    onForceOfflineChange = { v -> forceOffline = v },
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
                        } else if (mode == "SURVIVAL") {
                            homeMode = NazoMode.SURVIVAL.name
                            startSurvival(topic, "Medium")
                        } else if (mode == "BLITZ") {
                            homeMode = NazoMode.BLITZ.name
                            startBlitz(topic, "Medium")
                        } else if (mode == "VERSUS") {
                            homeMode = NazoMode.VERSUS.name
                            startVersus(topic, "Medium", 5)
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

            // KEEP LAST: the cold-start intro must be the final child of this
            // Box so it draws ON TOP of everything, including the onboarding
            // overlay. It previously sat before the onboarding block, so on a
            // true first launch the (opaque) tour covered it and the splash
            // zoom-through animation played invisibly underneath — the classic
            // "intro only works after setup" bug.
            // Continue the system splash's color: when a custom app icon is active
            // its flat splash color carries into the zoom-through, so there's no
            // color jump between the two. The follow-OS-theme pair passes null and
            // keeps the original light/dark greens.
            val introIcon = if (themePrefs.iconFollowsOsTheme) null
            else LauncherIconSwitcher.option(appIcon)
            IntroOverlay(
                isDark = isDark,
                backgroundColor = introIcon?.splashColor?.let { Color(it) },
                mark = introIcon?.introMark ?: R.drawable.ic_launcher_foreground,
                style = introIcon?.introStyle ?: IntroStyle.WARP,
            )
        }
    }
}

private fun formatElapsed(startedAt: Long): String {
    if (startedAt == 0L) return "0m 0s"
    // elapsedRealtime(), not currentTimeMillis(): a monotonic clock that can't
    // jump. Wall time moves when the OS does an NTP sync or the user edits the
    // date mid-quiz, which produced absurd or negative durations.
    val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt().coerceAtLeast(0)
    return "${secs / 60}m ${secs % 60}s"
}



/**
 * A fully built guessing round produced by the next-round prefetch (the
 * background job that runs while the previous round is in play): the AI
 * payload plus its resolved image URL, ready to hand straight to
 * [GuessPhase.Playing] the moment the player taps "Next Round" — or
 * immediately, when the prefetch finishes right after the tap.
 */
private data class PrefetchedGuessRound(
    val round: Int,
    val payload: GuessPayload,
    val url: String?,
)
