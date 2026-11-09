package quiz.thaton3app.nazo.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizEngine
import quiz.thaton3app.nazo.hints.HintEngine
import quiz.thaton3app.nazo.hints.HintPill
import quiz.thaton3app.nazo.hints.HintRevealPill
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.components.rememberSystemReduceMotion
import quiz.thaton3app.nazo.ui.theme.*

/** A question's graded state, frozen the moment it is revealed. */
private data class GradedAnswer(val selected: String?, val timeUp: Boolean)

/**
 * AnimatedContent identity for one question. Grading does NOT change this
 * (same slot → the answer tint and the explanation card's expand animate in
 * place); only advancing to the next question does.
 */
private data class QuestionSlot(val index: Int, val question: Question)

private const val QuestionTransitionDurationMs = 300
private const val QuestionTransitionSlideFraction = 0.08f
private const val AnswerFeedbackDurationMs = 420
private const val QuizProgressWavePeriodMs = 1_200

/** Pure gameplay-polish policies, kept deterministic and covered by JVM tests. */
internal fun nextQuizComboStreak(currentStreak: Int, correct: Boolean): Int =
    if (correct) currentStreak.coerceAtLeast(0) + 1 else 0

internal fun quizComboLabel(streak: Int): String? =
    streak.takeIf { it >= 2 }?.let { "$it× COMBO" }

internal fun quizProgressFraction(questionIndex: Int, totalQuestions: Int): Float =
    if (totalQuestions <= 0) 0f
    else ((questionIndex + 1).toFloat() / totalQuestions).coerceIn(0f, 1f)

/** Normalized Material-Expressive wave. A full phase returns to the same rest shape. */
internal fun quizProgressWaveOffset(
    position: Float,
    wavelength: Float,
    phaseFraction: Float,
): Float {
    if (wavelength <= 0f) return 0f
    val phase = phaseFraction * 2f * PI.toFloat()
    return sin((position / wavelength) * 2f * PI.toFloat() - phase)
}

internal fun questionTransitionOffset(width: Int, direction: Int): Int =
    (width * QuestionTransitionSlideFraction * direction.coerceIn(-1, 1)).roundToInt()

/** Damped horizontal motion in normalized units; exactly at rest at both ends. */
internal fun wrongAnswerShakeOffset(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return (sin(p * PI.toFloat() * 6f) * (1f - p))
}

/** Gentle selected-correct settle: peaks at 1.8%, returns exactly to 1f. */
internal fun correctAnswerPulseScale(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f + (sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.018f)
}

@Composable
fun ActiveQuizScreen(
    question: Question,
    currentQuestionIndex: Int,
    totalQuestions: Int,
    difficulty: String = "Medium",
    isAiGenerated: Boolean = false,
    // Survival mode: header shows lives instead of "x of y" / progress.
    endless: Boolean = false,
    livesLeft: Int = 0,
    // Versus mode: whose turn it is ("P1" / "P2"), shown as a header badge.
    playerLabel: String? = null,
    // Daily Challenge: a header badge marking the once-a-day run.
    daily: Boolean = false,
    // Blitz mode: one GLOBAL deadline replaces the per-question countdown;
    // answers auto-advance and the run ends via [onBlitzTimeUp].
    blitzDeadlineMs: Long? = null,
    onBlitzTimeUp: () -> Unit = {},
    // Survival: fired the moment a wrong answer (or a timeout) is GRADED —
    // before the player advances — so the lost heart is redacted immediately
    // and the button can say "Finish Quiz" on the fatal question.
    onLifeLost: () -> Unit = {},
    onNextQuestion: (Boolean, String?) -> Unit,
    onCloseClick: () -> Unit,
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isTimeUp by remember { mutableStateOf(false) }
    // Frozen grade record, keyed by question index. The question body reads its
    // grade from HERE, not from the live vars above: when the Next button (or
    // blitz auto-advance) resets the live state and the next question slides
    // in, the OUTGOING body keeps reading its own entry — without this, the
    // still-sliding card flipped a correct answer to "Not quite" (and
    // un-highlighted the picked option) for the length of the transition.
    val gradedByIndex = remember { mutableStateMapOf<Int, GradedAnswer>() }
    // Visual-only combo state. It never participates in score/evaluation and is
    // frozen per question for the same reason as GradedAnswer: the outgoing
    // question must keep its own badge while the next one enters.
    var comboStreak by remember { mutableIntStateOf(0) }
    val comboByIndex = remember { mutableStateMapOf<Int, Int>() }
    fun recordVisualGrade(index: Int, correct: Boolean) {
        if (comboByIndex.containsKey(index)) return
        comboStreak = nextQuizComboStreak(comboStreak, correct)
        comboByIndex[index] = comboStreak
    }

    val isBlitz = blitzDeadlineMs != null
    val secondsPerQuestion = QuizEngine.specFor(difficulty).secondsPerQuestion
    var remainingSeconds by remember { mutableIntStateOf(secondsPerQuestion) }
    val context = LocalContext.current
    val reduceMotion = rememberSystemReduceMotion()
    var showQuitDialog by remember { mutableStateOf(false) }

    // Lifelines (Phase 4): a small supply shared across the whole quiz (this
    // composable stays alive for all questions), one use per question.
    // Easy/Medium: fades out 2 wrong options. Hard/Otaku: first-letter hint.
    var hintsLeft by remember { mutableStateOf(HintEngine.quizSupply(totalQuestions)) }
    var hiddenOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var letterHint by remember { mutableStateOf<String?>(null) }

    // Intercept the system back gesture/button too, so leaving via gesture shows the same
    // "quit quiz?" confirmation as the X button (instead of silently going back).
    BackHandler(enabled = true) {
        showQuitDialog = !showQuitDialog
    }

    // Lifecycle-safe countdown: one timer per question (keyed on the index). The
    // coroutine auto-cancels when this screen leaves composition, and it stops early
    // the moment the user answers. On timeout the answer is revealed and the
    // question counts as incorrect (the user is "eliminated").
    // In BLITZ mode this effect only resets the per-question state — the
    // clock is the single global deadline handled below.
    LaunchedEffect(currentQuestionIndex) {
        selectedAnswer = null
        isTimeUp = false
        hiddenOptions = emptySet()
        letterHint = null
        // Keep only the previous question's frozen grade (still sliding out)
        // and this one's slot.
        gradedByIndex.keys
            .filter { it < currentQuestionIndex - 1 }
            .forEach { gradedByIndex.remove(it) }
        comboByIndex.keys
            .filter { it < currentQuestionIndex - 1 }
            .forEach { comboByIndex.remove(it) }
        if (isBlitz) return@LaunchedEffect
        remainingSeconds = secondsPerQuestion
        while (remainingSeconds > 0 && selectedAnswer == null) {
            delay(1000)
            remainingSeconds--
            // Escalating buzz in the final 5 seconds: starts at 30% strength, ramps
            // up each second, peaks with a 100% buzz the moment time hits zero.
            when (remainingSeconds) {
                5 -> Haptics.tick(context, 30)
                4 -> Haptics.tick(context, 36)   // ~20% stronger than the previous
                3 -> Haptics.tick(context, 47)   // ~30% stronger than the previous
                2 -> Haptics.tick(context, 66)   // ~40% stronger than the previous
                1 -> Haptics.tick(context, 85)   // last second before zero
                0 -> {
                    Haptics.timeUp(context)      // time's up — full-strength buzz
                    Sounds.wrong(context)        // opt-in sound, no-op when disabled
                }
            }
        }
        if (remainingSeconds == 0 && selectedAnswer == null) {
            isTimeUp = true
            // A timeout is graded on the spot: it costs a survival heart at
            // once (not at the Next tap) and freezes the "Time's up!" outcome
            // for the exit transition.
            gradedByIndex[currentQuestionIndex] = GradedAnswer(selected = null, timeUp = true)
            recordVisualGrade(currentQuestionIndex, correct = false)
            onLifeLost()
        }
    }

    // BLITZ global clock: one countdown for the whole run. Survives question
    // changes (keyed on the deadline, not the index); fires onBlitzTimeUp
    // exactly once when it crosses zero.
    if (blitzDeadlineMs != null) {
        LaunchedEffect(blitzDeadlineMs) {
            while (true) {
                // Monotonic clock — matches how blitzDeadlineMs is produced
                // in NazoApp, so an NTP sync mid-round cannot warp the timer.
                val left = ((blitzDeadlineMs - SystemClock.elapsedRealtime() + 999) / 1000L)
                    .coerceAtLeast(0L).toInt()
                if (left != remainingSeconds) {
                    remainingSeconds = left
                    when (left) {
                        5 -> Haptics.tick(context, 30)
                        4 -> Haptics.tick(context, 36)
                        3 -> Haptics.tick(context, 47)
                        2 -> Haptics.tick(context, 66)
                        1 -> Haptics.tick(context, 85)
                    }
                }
                if (left <= 0) {
                    Haptics.timeUp(context)
                    Sounds.wrong(context)
                    onBlitzTimeUp()
                    break
                }
                delay(200)
            }
        }
        // Blitz auto-advance: no Next button — a short beat to see the
        // right/wrong colors, then straight to the next question.
        LaunchedEffect(selectedAnswer, currentQuestionIndex) {
            if (selectedAnswer != null) {
                val wasCorrect = selectedAnswer == question.correctAnswer
                val picked = selectedAnswer
                delay(650)
                onNextQuestion(wasCorrect, picked)
                selectedAnswer = null
            }
        }
    }

    // Live chrome state (hint pill, timer). The question BODY reads its grade
    // from gradedByIndex instead — see the note there.
    val reveal = selectedAnswer != null || isTimeUp

    // Landscape splits the screen: question (and its explanation) on the left,
    // answers on the right. The chrome above the split is also tightened,
    // because vertical space is the scarce dimension in landscape.
    val landscape = isLandscape()
    val chromeGap = if (landscape) 8.dp else 16.dp
    val topGap = if (landscape) 8.dp else 20.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Reading scrim: the app-wide ambient particles drift behind every
            // screen; gameplay is a reading surface, so they are dimmed here to
            // a whisper instead of passing under the question text.
            .background(NazoBackground.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // In landscape each pane scrolls on its own, so the page
                    // itself must not also scroll (two nested scrollers would
                    // fight over the same drag).
                    if (landscape) Modifier.weight(1f)
                    else Modifier.verticalScroll(rememberScrollState())
                )
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(topGap))

            // Header with Progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { Haptics.light(context); showQuitDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NazoTextSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // Orientation first: which question am I on.
                        // Survival is endless — no meaningful "of y".
                        text = if (endless) "Question ${currentQuestionIndex + 1}"
                        else "Question ${currentQuestionIndex + 1} of $totalQuestions",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        // anime: theme, e.g. "Jujutsu Kaisen: Shibuya Arc" — the
                        // anime name makes generic questions ("What is the name of
                        // the first arc?") unambiguous about their franchise. It is
                        // context, so it rides under the progress instead of
                        // competing with it (and with the question card below).
                        text = listOf(question.anime, question.theme)
                            .filter { it.isNotBlank() }.joinToString(": "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
                }
                if (daily) {
                    // Daily Challenge: same rules as a quiz, one run a day —
                    // labelled so the player knows why there is no AI label.
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(NazoPrimary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "☀ DAILY",
                            color = NazoPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (playerLabel != null) {
                    // Versus: whose turn is it (P1 / P2).
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(NazoPrimary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = playerLabel,
                            color = NazoPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (endless) {
                    // Survival lives: three hearts, misses hollow them out.
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { i ->
                            Icon(
                                imageVector = if (i < livesLeft) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (i < livesLeft) NazoError else NazoTextSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp),
                            )
                            if (i < 2) Spacer(Modifier.width(2.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (isAiGenerated) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(NazoPrimary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✦ AI",
                            color = NazoPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        // Urgency shows on the whole chip, not just the digit:
                        // in a row of badges the tinted disc is what the eye
                        // finds at a glance.
                        .background(
                            animateColorAsState(
                                targetValue = if (isTimeUp || remainingSeconds <= 5) NazoErrorBg else NazoSurface,
                                animationSpec = tween(400),
                                label = "timerBg",
                            ).value
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Smoothly animate the timer color when hitting the final 5 seconds
                    val timerColor by animateColorAsState(
                        targetValue = if (isTimeUp || remainingSeconds <= 5) NazoError else NazoPrimary,
                        animationSpec = tween(400),
                        label = "timerColor"
                    )
                    
                    // The rolling countdown animation
                    AnimatedContent(
                        targetState = remainingSeconds,
                        transitionSpec = {
                            if (targetState < initialState) {
                                (slideInVertically { height -> height } + fadeIn()) togetherWith 
                                (slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()) togetherWith 
                                (slideOutVertically { height -> height } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "timerAnimation"
                    ) { sec ->
                        Text(
                            text = sec.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = timerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(chromeGap))
            // Endless runs have no meaningful fraction — skip the bar. The
            // custom bar is one Canvas: a Material-Expressive wave keeps
            // travelling inside the completed portion while its endpoint stays
            // fixed, and only the fill length advances with question progress.
            if (!endless && !isBlitz) {
                PremiumQuizProgress(
                    progress = quizProgressFraction(currentQuestionIndex, totalQuestions),
                    color = NazoPrimary,
                    trackColor = NazoSurface,
                    motionAllowed = !reduceMotion,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isBlitz) {
                // Blitz has no question fraction but it does have one global
                // clock — show THAT as the bar so the run's remaining time is
                // readable at a glance, not only as a digit in the corner.
                val blitzTotalMs = remember(blitzDeadlineMs) {
                    ((blitzDeadlineMs ?: 0L) - SystemClock.elapsedRealtime()).coerceAtLeast(1_000L)
                }
                PremiumQuizProgress(
                    progress = (remainingSeconds * 1000f / blitzTotalMs).coerceIn(0f, 1f),
                    color = if (remainingSeconds <= 10) NazoError else NazoPrimary,
                    trackColor = NazoSurface,
                    motionAllowed = !reduceMotion,
                    animationDurationMs = 200,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Lifeline row (Phase 4): lives OUTSIDE the question AnimatedContent so it
            // never slides with the question. Revealed letter hint grows in from the
            // left; the hint button sits on the right and greys out once unusable.
            Spacer(Modifier.height(chromeGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = letterHint != null,
                    enter = expandHorizontally(tween(280)) + fadeIn(tween(280)),
                    exit = shrinkHorizontally(tween(200)) + fadeOut(tween(160))
                ) {
                    HintRevealPill(text = "Starts with \u201C${letterHint ?: ""}\u201D")
                }
                Spacer(Modifier.weight(1f))
                HintPill(
                    remaining = hintsLeft,
                    enabled = hintsLeft > 0 && !reveal && hiddenOptions.isEmpty() && letterHint == null,
                    onClick = {
                        Haptics.light(context)
                        hintsLeft--
                        if (HintEngine.usesLetterHint(difficulty)) {
                            letterHint = HintEngine.firstLetter(question.correctAnswer)
                        } else {
                            hiddenOptions = HintEngine.optionsToHide(
                                options = question.options,
                                correctAnswer = question.correctAnswer,
                                seed = currentQuestionIndex
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(if (landscape) 10.dp else 24.dp))

            // One restrained content hand-off: the old question yields by 8%
            // while the next settles into the same stable layout. Full-width
            // sweeps and AnimatedContent size morphing made long/short questions
            // feel like different pages; this keeps the player's eye anchored.
            // The slot is question identity only — grading stays in place — and
            // the body's grade remains frozen per index to prevent result flashes.
            AnimatedContent(
                targetState = QuestionSlot(currentQuestionIndex, question),
                transitionSpec = {
                    val direction = if (targetState.index >= initialState.index) 1 else -1
                    val enter = slideInHorizontally(
                        animationSpec = tween(
                            QuestionTransitionDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    ) { width -> questionTransitionOffset(width, direction) } +
                        fadeIn(tween(220))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(
                            QuestionTransitionDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    ) { width -> questionTransitionOffset(width, -direction) } +
                        fadeOut(tween(180))
                    (enter togetherWith exit).using(null).apply {
                        targetContentZIndex = 1f
                    }
                },
                label = "questionTransition"
            ) { slot ->
                val q = slot.question
                // Frozen display state for THIS question (see gradedByIndex).
                val graded = gradedByIndex[slot.index]
                val shownAnswer = graded?.selected
                val isTimeUp = graded?.timeUp == true
                val reveal = graded != null
                val isCorrect = shownAnswer == q.correctAnswer
                val shownCombo = comboByIndex[slot.index] ?: 0
                TwoPaneQuizBody(
                    landscape = landscape,
                    question = {
                // Question Card. The franchise label used to repeat here as a
                // kicker; it now lives once in the header, so the card's whole
                // job is the question itself — the main thing to read.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NazoSurface)
                        .padding(if (landscape) 18.dp else 24.dp)
                ) {
                    Text(
                        text = q.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val comboLabel = quizComboLabel(shownCombo)
                    AnimatedVisibility(
                        visible = isCorrect && comboLabel != null,
                        enter = fadeIn(tween(180)) + scaleIn(
                            initialScale = if (reduceMotion) 1f else 0.88f,
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                        ),
                        exit = fadeOut(tween(120)),
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            ComboPill(
                                label = comboLabel.orEmpty(),
                                streak = shownCombo,
                                motionAllowed = !reduceMotion,
                            )
                        }
                    }
                }

                    },
                    options = {
                // Options
                val labels = listOf("A", "B", "C", "D")
                q.options.forEachIndexed { index, optionText ->
                    // FROZEN selection (see gradedByIndex): the outgoing slide
                    // keeps its pick highlighted instead of un-selecting.
                    val isThisSelected = shownAnswer == optionText
                    val isThisCorrectAnswer = optionText == q.correctAnswer
                    // 50/50 lifeline: eliminated options fade to a ghost and stop
                    // accepting taps — the layout itself never shifts.
                    val isHiddenByHint = hiddenOptions.contains(optionText) && !reveal
                    val hintAlpha by animateFloatAsState(
                        targetValue = if (isHiddenByHint) 0.22f else 1f,
                        animationSpec = tween(320),
                        label = "hintAlpha"
                    )

                    val bgColor by animateColorAsState(
                        targetValue = when {
                            !reveal -> NazoSurfaceVariant
                            isThisCorrectAnswer -> NazoSuccessBg
                            isThisSelected && !isThisCorrectAnswer -> NazoErrorBg
                            else -> NazoSurfaceVariant
                        },
                        animationSpec = tween(220),
                        label = "optionBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = when {
                            !reveal -> Color.Transparent
                            isThisCorrectAnswer -> NazoSuccess 
                            isThisSelected && !isThisCorrectAnswer -> NazoError 
                            else -> Color.Transparent
                        },
                        animationSpec = tween(220),
                        label = "optionBorder"
                    )

                    PremiumAnswerOption(
                        reveal = reveal,
                        isThisCorrectAnswer = isThisCorrectAnswer,
                        isThisSelected = isThisSelected,
                        isHiddenByHint = isHiddenByHint,
                        hintAlpha = hintAlpha,
                        backgroundColor = bgColor,
                        borderColor = borderColor,
                        label = labels[index],
                        text = optionText,
                        motionAllowed = !reduceMotion,
                        onClick = {
                            // Idempotence guard: `reveal` only flips on the
                            // next recomposition, so two taps landing in the
                            // same frame both pass `enabled`. `graded` also
                            // closes the timeout race.
                            if (selectedAnswer == null && graded == null) {
                                val wasCorrect = optionText == q.correctAnswer
                                if (wasCorrect) {
                                    Haptics.light(context)
                                    Sounds.correct(context)
                                } else {
                                    Haptics.doubleLight(context)
                                    Sounds.wrong(context)
                                    // Grading time — not Next-tap time: the
                                    // survival heart is redacted immediately.
                                    onLifeLost()
                                }
                                selectedAnswer = optionText
                                // Freeze result + visual streak for this outgoing
                                // slot. Neither participates in answer evaluation.
                                gradedByIndex[slot.index] =
                                    GradedAnswer(selected = optionText, timeUp = false)
                                recordVisualGrade(slot.index, wasCorrect)
                            }
                        },
                    )
                }

                    },
                    explanation = {
                // Explanation Card (shows after answering OR when time runs out).
                // Hidden in blitz: answers auto-advance on a 650ms beat instead.
                AnimatedVisibility(
                    visible = reveal && !isBlitz,
                    enter = if (reduceMotion) {
                        fadeIn(tween(100))
                    } else {
                        expandVertically(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) + slideInVertically(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                        ) { height -> height / 12 } + fadeIn(tween(220))
                    },
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
                ) {
                    // The outcome IS the news; the explanation follows it.
                    val outcomeTitle = when {
                        isTimeUp -> "Time's up!"
                        isCorrect -> "Correct!"
                        else -> "Not quite"
                    }
                    val outcomeColor = if (isCorrect && !isTimeUp) NazoSuccess else NazoError
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        0f to outcomeColor.copy(alpha = 0.12f),
                                        0.45f to NazoSurface,
                                        1f to NazoSurface,
                                    )
                                )
                                .border(
                                    1.dp,
                                    outcomeColor.copy(alpha = 0.28f),
                                    RoundedCornerShape(24.dp),
                                )
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isTimeUp -> Icons.Filled.Schedule
                                        isCorrect && !isTimeUp -> Icons.Filled.Check
                                        else -> Icons.Filled.Close
                                    },
                                    contentDescription = null,
                                    tint = outcomeColor,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    outcomeTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = outcomeColor,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "EXPLANATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NazoTextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = q.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NazoTextSecondary,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(20.dp))

                            // Next Question Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(NazoPrimary)
                                    .clickable {
                                        Haptics.light(context)
                                        onNextQuestion(isCorrect, shownAnswer)
                                        selectedAnswer = null // Reset for next question
                                    }
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    // The label must name the click that ENDS
                                    // the run: the last question of a fixed
                                    // quiz, or the fatal question of a survival
                                    // run (its heart is redacted at grading, so
                                    // livesLeft already reads 0 here).
                                    if ((!endless && currentQuestionIndex == totalQuestions - 1) ||
                                        (endless && livesLeft <= 0)
                                    ) "Finish Quiz" else "Next Question",
                                    color = NazoOnPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                    },
                )
            }
            Spacer(Modifier.height(if (landscape) 12.dp else 32.dp))
        }
        }

        // Quit Dialog Backdrop & Content
        AnimatedVisibility(
            visible = showQuitDialog,
            // Added scale animation for a "pop up" feel
            enter = fadeIn(tween(180)) + scaleIn(tween(180, easing = LinearOutSlowInEasing), initialScale = 0.9f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180, easing = FastOutLinearInEasing), targetScale = 0.9f),
        ) {
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(NazoSurface)
                            .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(NazoError),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "!",
                                color = NazoOnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Quit quiz?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Do you really want to quit? Your progress in this quiz will be lost.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(22.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                .background(NazoPrimary)
                                .clickable { Haptics.light(context); showQuitDialog = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Stay",
                                    color = NazoOnPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, NazoError, RoundedCornerShape(16.dp))
                                .clickable {
                                    Haptics.light(context)
                                    showQuitDialog = false
                                    onCloseClick()
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Quit",
                                    color = NazoError,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
        }
    }
}

/**
 * Question / options layout for one quiz question.
 *
 * Portrait keeps the original single column: question, options, then the
 * explanation underneath.
 *
 * Landscape splits into two panes — question on the left with the explanation
 * appearing beneath it (there is dead space there once the question is short),
 * and the four options on the right where they are all reachable without
 * scrolling. Each pane scrolls independently so a long question can never push
 * the answers off-screen.
 */
@Composable
private fun TwoPaneQuizBody(
    landscape: Boolean,
    question: @Composable () -> Unit,
    options: @Composable () -> Unit,
    explanation: @Composable () -> Unit,
) {
    if (!landscape) {
        Column {
            question()
            Spacer(Modifier.height(24.dp))
            options()
            explanation()
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            question()
            // The explanation lives under the question in landscape: that pane
            // has room once answered, and it keeps the answers where the eye
            // already is.
            explanation()
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            options()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PremiumQuizProgress(
    progress: Float,
    color: Color,
    trackColor: Color,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    animationDurationMs: Int = 420,
) {
    val target = progress.coerceIn(0f, 1f)
    val displayed by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (motionAllowed) animationDurationMs else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "premiumQuizProgress",
    )
    // The waveform travels forever while this small gameplay composable is on
    // screen, but its endpoint does not move: only [displayed] can advance quiz
    // progress. Restart is seamless because phase 1 is identical to phase 0.
    // Do not even create the infinite transition under Remove animations.
    val wavePhase = if (motionAllowed) {
        val transition = rememberInfiniteTransition(label = "quizProgressWave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(QuizProgressWavePeriodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "quizProgressWavePhase",
        )
    } else {
        null
    }
    // Keep the existing bounded cap bloom as separate update feedback. It runs
    // once per progress target while the wave itself continues underneath.
    val progressPulse = remember { Animatable(0f) }
    LaunchedEffect(target, motionAllowed) {
        if (!motionAllowed || target <= 0f) {
            progressPulse.snapTo(0f)
            return@LaunchedEffect
        }
        progressPulse.snapTo(0f)
        progressPulse.animateTo(
            1f,
            animationSpec = tween(520, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier.height(16.dp)) {
        val strokeWidth = 4.dp.toPx()
        val amplitude = 3.dp.toPx().coerceAtMost((size.height - strokeWidth) / 2f)
        val wavelength = 20.dp.toPx()
        val centerY = size.height / 2f
        val startX = strokeWidth / 2f
        val usableWidth = (size.width - strokeWidth).coerceAtLeast(0f)
        val endX = startX + usableWidth * displayed.coerceIn(0f, 1f)
        val phaseFraction = wavePhase?.value ?: 0f

        // Material Expressive's inactive rail stays calm while the completed
        // portion carries the waveform, like a music-player progress indicator.
        // A small token gap keeps the wave cap distinct from the remaining rail.
        val trackEndX = (size.width - startX).coerceAtLeast(startX)
        val trackStartX = if (endX <= startX) {
            startX
        } else {
            (endX + strokeWidth * 1.5f).coerceAtMost(trackEndX)
        }
        if (trackStartX < trackEndX) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, centerY),
                end = Offset(trackEndX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        if (endX > startX) {
            val path = Path()
            val sampleStep = 2.dp.toPx().coerceAtLeast(1f)
            var x = startX
            var endY = centerY
            while (x <= endX) {
                val y = centerY + amplitude * quizProgressWaveOffset(
                    position = x - startX,
                    wavelength = wavelength,
                    phaseFraction = phaseFraction,
                )
                if (x == startX) path.moveTo(x, y) else path.lineTo(x, y)
                endY = y
                x += sampleStep
            }
            // Always terminate exactly at the animated progress value rather
            // than one sample before it; the round cap then advances smoothly.
            endY = centerY + amplitude * quizProgressWaveOffset(
                position = endX - startX,
                wavelength = wavelength,
                phaseFraction = phaseFraction,
            )
            path.lineTo(endX, endY)

            val pulseEnvelope = sin(
                progressPulse.value.coerceIn(0f, 1f) * PI.toFloat()
            ).coerceAtLeast(0f)
            if (pulseEnvelope > 0f) {
                drawCircle(
                    color = color.copy(alpha = 0.14f * pulseEnvelope),
                    radius = strokeWidth * (1.2f + 0.5f * pulseEnvelope),
                    center = Offset(endX, endY),
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ComboPill(
    label: String,
    streak: Int,
    motionAllowed: Boolean,
) {
    val intensity = ((streak - 2).coerceIn(0, 4) / 4f)
    val flash = remember(label) { Animatable(0f) }
    LaunchedEffect(label, motionAllowed) {
        if (!motionAllowed) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                val lift = flash.value * 0.015f
                scaleX = 1f + lift
                scaleY = 1f + lift
            }
            .clip(RoundedCornerShape(50))
            .background(NazoPrimary.copy(alpha = 0.12f + intensity * 0.08f))
            .border(
                1.dp,
                NazoPrimary.copy(alpha = 0.42f + intensity * 0.22f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✦  $label",
            style = MaterialTheme.typography.labelMedium,
            color = NazoPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun PremiumAnswerOption(
    reveal: Boolean,
    isThisCorrectAnswer: Boolean,
    isThisSelected: Boolean,
    isHiddenByHint: Boolean,
    hintAlpha: Float,
    backgroundColor: Color,
    borderColor: Color,
    label: String,
    text: String,
    motionAllowed: Boolean,
    onClick: () -> Unit,
) {
    val feedback = remember { Animatable(1f) }
    LaunchedEffect(reveal, isThisSelected, isThisCorrectAnswer, motionAllowed) {
        if (!reveal || !isThisSelected) {
            feedback.snapTo(1f)
            return@LaunchedEffect
        }
        if (!motionAllowed) {
            feedback.snapTo(1f)
            return@LaunchedEffect
        }
        feedback.snapTo(0f)
        feedback.animateTo(
            1f,
            animationSpec = tween(AnswerFeedbackDurationMs, easing = FastOutSlowInEasing),
        )
    }

    val density = LocalDensity.current
    val progress = feedback.value
    val correctSelected = reveal && isThisSelected && isThisCorrectAnswer
    val wrongSelected = reveal && isThisSelected && !isThisCorrectAnswer
    val shakePx = with(density) { 8.dp.toPx() } *
        if (wrongSelected) wrongAnswerShakeOffset(progress) else 0f
    val pulseScale = if (correctSelected) correctAnswerPulseScale(progress) else 1f
    val halo = if (correctSelected) {
        sin(progress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .alpha(hintAlpha)
            .graphicsLayer {
                translationX = shakePx
                scaleX = pulseScale
                scaleY = pulseScale
            },
    ) {
        if (correctSelected) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = halo * 0.34f
                        val expansion = 1f + halo * 0.055f
                        scaleX = expansion
                        scaleY = expansion
                    }
            ) {
                drawRoundRect(
                    color = NazoSuccess,
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(backgroundColor)
                .border(
                    if (reveal && isThisSelected) 1.5.dp else 1.dp,
                    borderColor,
                    RoundedCornerShape(50),
                )
                .clickable(enabled = !reveal && !isHiddenByHint, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptionCircle(
                reveal = reveal,
                isThisCorrectAnswer = isThisCorrectAnswer,
                isThisSelected = isThisSelected,
                label = label,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (reveal && (isThisCorrectAnswer || isThisSelected)) {
                    borderColor
                } else {
                    NazoTextPrimary
                },
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OptionCircle(
    reveal: Boolean,
    isThisCorrectAnswer: Boolean,
    isThisSelected: Boolean,
    label: String,
) {
    // Also updated inner circle colors to match the dynamic theme variables perfectly
    val circleColor = animateColorAsState(
        targetValue = when {
            !reveal -> NazoBackground
            isThisCorrectAnswer -> NazoSuccess
            isThisSelected && !isThisCorrectAnswer -> NazoError
            else -> NazoBackground
        },
        animationSpec = tween(220),
        label = "optionCircle"
    ).value
    
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(circleColor),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !(reveal && (isThisCorrectAnswer || isThisSelected)),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary, fontWeight = FontWeight.Bold)
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = reveal && isThisCorrectAnswer,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(120))
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = reveal && isThisSelected && !isThisCorrectAnswer,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(120))
        ) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
