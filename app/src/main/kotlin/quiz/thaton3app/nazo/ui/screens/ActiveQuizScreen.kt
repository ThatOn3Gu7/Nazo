package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizEngine
import quiz.thaton3app.nazo.hints.HintEngine
import quiz.thaton3app.nazo.hints.HintPill
import quiz.thaton3app.nazo.hints.HintRevealPill
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.*

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
    // Blitz mode: one GLOBAL deadline replaces the per-question countdown;
    // answers auto-advance and the run ends via [onBlitzTimeUp].
    blitzDeadlineMs: Long? = null,
    onBlitzTimeUp: () -> Unit = {},
    onNextQuestion: (Boolean, String?) -> Unit,
    onCloseClick: () -> Unit,
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isTimeUp by remember { mutableStateOf(false) }
    val isBlitz = blitzDeadlineMs != null
    val secondsPerQuestion = QuizEngine.specFor(difficulty).secondsPerQuestion
    var remainingSeconds by remember { mutableIntStateOf(secondsPerQuestion) }
    val context = LocalContext.current
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
        }
    }

    // BLITZ global clock: one countdown for the whole run. Survives question
    // changes (keyed on the deadline, not the index); fires onBlitzTimeUp
    // exactly once when it crosses zero.
    if (blitzDeadlineMs != null) {
        LaunchedEffect(blitzDeadlineMs) {
            while (true) {
                val left = ((blitzDeadlineMs - System.currentTimeMillis() + 999) / 1000L)
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

    val isAnswered = selectedAnswer != null
    val reveal = isAnswered || isTimeUp
    val isCorrect = selectedAnswer == question.correctAnswer

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Spacer(Modifier.height(20.dp))

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
                        text = question.theme, // e.g., "Jujutsu Kaisen: Shibuya Arc"
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        // Survival is endless — no meaningful "of y".
                        text = if (endless) "Question ${currentQuestionIndex + 1}"
                        else "Question ${currentQuestionIndex + 1} of $totalQuestions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
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
                        .background(NazoSurface),
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

            Spacer(Modifier.height(16.dp))
            // Endless/blitz runs have no meaningful fraction — skip the bar.
            if (!endless && !isBlitz) {
                val progressAnim = animateFloatAsState(
                    targetValue = (currentQuestionIndex + 1) / totalQuestions.toFloat(),
                    animationSpec = tween(durationMillis = 400),
                    label = "quizProgress"
                ).value
                LinearProgressIndicator(
                    progress = { progressAnim },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = NazoPrimary,
                    trackColor = NazoSurface
                )
            }

            // Lifeline row (Phase 4): lives OUTSIDE the question AnimatedContent so it
            // never slides with the question. Revealed letter hint grows in from the
            // left; the hint button sits on the right and greys out once unusable.
            Spacer(Modifier.height(14.dp))
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

            Spacer(Modifier.height(24.dp))

            // Smooth sliding transition between questions
            AnimatedContent(
                targetState = question,
                transitionSpec = { 
                    (slideInHorizontally(tween(350)) { width -> width } + fadeIn(tween(350))) togetherWith 
                    (slideOutHorizontally(tween(350)) { width -> -width } + fadeOut(tween(350)))
                },
                label = "questionTransition"
            ) { q ->
                Column {
                // Question Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NazoSurface)
                        .padding(24.dp)
                ) {
                    Text(
                        text = q.theme.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NazoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = q.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Options
                val labels = listOf("A", "B", "C", "D")
                q.options.forEachIndexed { index, optionText ->
                    val isThisSelected = selectedAnswer == optionText
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .alpha(hintAlpha)
                            .clip(RoundedCornerShape(50))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(50))
                            .clickable(enabled = !reveal && !isHiddenByHint) {
                                if (optionText == q.correctAnswer) {
                                    Haptics.light(context)
                                    Sounds.correct(context)
                                } else {
                                    Haptics.doubleLight(context)
                                    Sounds.wrong(context)
                                }
                                selectedAnswer = optionText
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OptionCircle(
                            reveal = reveal,
                            isThisCorrectAnswer = isThisCorrectAnswer,
                            isThisSelected = isThisSelected,
                            label = labels[index]
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (reveal && (isThisCorrectAnswer || isThisSelected)) borderColor else NazoTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Explanation Card (shows after answering OR when time runs out).
                // Hidden in blitz: answers auto-advance on a 650ms beat instead.
                AnimatedVisibility(
                    visible = reveal && !isBlitz,
                    // Replaced simple fade with a beautiful expanding drop-down animation
                    enter = expandVertically(tween(350)) + fadeIn(tween(350)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(NazoSurface)
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = NazoPrimary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Explanation", style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
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
                                        onNextQuestion(isCorrect, selectedAnswer)
                                        selectedAnswer = null // Reset for next question
                                    }
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (!endless && currentQuestionIndex == totalQuestions - 1) "Finish Quiz" else "Next Question",
                                    color = NazoOnPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(32.dp))
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
