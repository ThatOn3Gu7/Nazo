package quiz.thaton3app.nazo.modes.guessing_game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.ImageRequest
import coil.compose.AsyncImage
import coil.execute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.*

/** Fully-blurred at the start of the timer; 0 = fully sharp at the end. */
private const val MAX_BLUR = 28f

/**
 * The active guessing-game screen: a mystery image with an on-device linear
 * un-blur reveal running alongside the per-difficulty countdown, plus the
 * difficulty's input mode (4 choices on Easy/Medium, fuzzy auto-complete on
 * Hard/Otaku Master). One shot per round — a correct answer advances, a wrong
 * answer or a timer at 0 eliminates the player and reveals the answer.
 *
 * All game state lives in the host (NazoApp); this screen is purely reactive
 * to [GuessPhase]:
 *  - [GuessPhase.Preparing] → spinner card while the round payload / image load
 *  - [GuessPhase.Error]     → error card (retry / settings / quit)
 *  - [GuessPhase.Playing]   → the game itself (timer, blur, input, reveal)
 */
@Composable
fun GuessingPlayScreen(
    topic: String,
    difficultyLabel: String,
    round: Int,
    totalRounds: Int,
    score: Int,
    phase: GuessPhase,
    roundResult: GuessRoundResult?,
    onRetryRound: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuit: () -> Unit,
    onRoundComplete: (correct: Boolean, answerText: String?, remainingMs: Long) -> Unit,
    onNextRound: () -> Unit,
) {
    val context = LocalContext.current
    val durationMs = GuessScoring.durationMsFor(difficultyLabel)
    val imageLoader = remember { ImageLoader(context) }

    val payload: GuessPayload? = (phase as? GuessPhase.Playing)?.payload
    val imageUrl: String? = (phase as? GuessPhase.Playing)?.imageUrl

    var submitted by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var imageReady by remember { mutableStateOf(false) }
    var imageFetchFailed by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableLongStateOf(durationMs) }
    var showQuitDialog by remember { mutableStateOf(false) }

    val revealed = submitted != null || timedOut || roundResult != null
    val timerFrac = if (durationMs > 0) (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displaySeconds = ((remainingMs + 999) / 1000).toInt()

    // Reset per round, then pre-fetch the image BYTES before the timer may start,
    // so the countdown (and the linear un-blur) only ever runs against pixels
    // that are actually on screen. A failed / timed-out pre-fetch falls back to
    // the drawn placeholder instead of blocking the round.
    LaunchedEffect(phase) {
        submitted = null
        timedOut = false
        remainingMs = durationMs
        imageFetchFailed = false
        imageReady = imageUrl == null // no URL at all → straight to the placeholder
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()
        val ok = withContext(Dispatchers.IO) {
            runCatching { withTimeout(20_000) { imageLoader.execute(request) } }
                .getOrNull()?.isSuccess ?: false
        }
        imageFetchFailed = !ok
        imageReady = true
    }

    // Lifecycle-safe countdown, driven by the frame clock (monotonic, drift-free).
    // The blur radius is a linear function of [remainingMs] — it decreases exactly
    // in step with the timer. The loop stops the moment the player answers.
    // (Note: `submitted`/`timedOut` are read as state INSIDE the loop — a plain
    // `revealed` val would be captured at effect start and go stale.)
    LaunchedEffect(imageReady, payload) {
        if (!imageReady || payload == null) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        var lastTickSecond = -1
        var done = false
        while (!done) {
            if (submitted != null || timedOut) break
            withFrameNanos { nowNanos ->
                val elapsedMs = (nowNanos - startNanos) / 1_000_000
                val remaining = (durationMs - elapsedMs).coerceAtLeast(0L)
                remainingMs = remaining
                val sec = ((remaining + 999) / 1000).toInt()
                if (sec != lastTickSecond) {
                    lastTickSecond = sec
                    // Same escalating final-5-seconds ramp as the quiz mode.
                    when (sec) {
                        5 -> Haptics.tick(context, 30)
                        4 -> Haptics.tick(context, 36)
                        3 -> Haptics.tick(context, 47)
                        2 -> Haptics.tick(context, 66)
                        1 -> Haptics.tick(context, 85)
                        else -> Unit
                    }
                }
                if (remaining <= 0L) {
                    Haptics.timeUp(context)
                    timedOut = true
                    onRoundComplete(false, null, 0L)
                    done = true
                }
            }
        }
    }

    fun submitAnswer(answer: String) {
        if (payload == null || revealed) return
        val correct = payload.isCorrect(answer)
        if (correct) Haptics.light(context) else Haptics.doubleLight(context)
        submitted = answer
        onRoundComplete(correct, answer, remainingMs)
    }

    // Intercept the system back gesture/button too, so leaving via gesture shows
    // the same "quit game?" confirmation as the X button.
    BackHandler(enabled = true) { showQuitDialog = !showQuitDialog }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            Haptics.light(context)
                            showQuitDialog = true
                        },
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
                            text = "Guessing Game",
                            style = MaterialTheme.typography.titleMedium,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Round $round of $totalRounds • $difficultyLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(NazoBadge)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$score pts",
                            color = NazoPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (payload != null && !revealed) {
                        Spacer(Modifier.width(12.dp))
                        TimerCircle(seconds = displaySeconds)
                    }
                }

                if (payload != null) {
                    Spacer(Modifier.height(16.dp))
                    // The shrinking bar makes the linear time decay visible.
                    LinearProgressIndicator(
                        progress = { timerFrac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (displaySeconds <= 5) NazoError else NazoPrimary,
                        trackColor = NazoSurface
                    )
                    Spacer(Modifier.height(20.dp))
                }

                when (phase) {
                    is GuessPhase.Preparing -> PreparingCard(
                        round = phase.round,
                        totalRounds = totalRounds,
                        topic = topic,
                    )

                    is GuessPhase.Error -> ErrorCard(
                        message = phase.message,
                        onRetry = onRetryRound,
                        onOpenSettings = onOpenSettings,
                        onQuit = onQuit,
                    )

                    is GuessPhase.Playing -> {
                        MysteryImageCard(
                            imageUrl = phase.imageUrl,
                            imageReady = imageReady,
                            imageFetchFailed = imageFetchFailed,
                            topic = topic,
                            round = round,
                            progress = timerFrac,
                            imageLoader = imageLoader,
                        )
                        Spacer(Modifier.height(20.dp))
                        when (GuessScoring.specFor(difficultyLabel).inputMode) {
                            GuessInputMode.CHOICE -> ChoiceInput(
                                payload = phase.payload,
                                revealed = revealed,
                                submitted = submitted,
                                onSubmit = { answer -> submitAnswer(answer) },
                            )
                            GuessInputMode.AUTOCOMPLETE -> AutocompleteInput(
                                payload = phase.payload,
                                revealed = revealed,
                                onSubmit = { answer -> submitAnswer(answer) },
                            )
                        }
                        if (roundResult != null) {
                            Spacer(Modifier.height(16.dp))
                            RevealCard(
                                result = roundResult,
                                totalRounds = totalRounds,
                                onNext = onNextRound,
                            )
                        }
                    }

                    GuessPhase.Idle -> Unit
                }
            }
        }

        QuitDialog(
            show = showQuitDialog,
            onStay = { showQuitDialog = false },
            onQuit = {
                showQuitDialog = false
                onQuit()
            },
        )
    }
}

/** Rolling countdown circle (same pattern as the quiz mode's timer). */
@Composable
private fun TimerCircle(seconds: Int) {
    val timerColor by animateColorAsState(
        targetValue = if (seconds <= 5) NazoError else NazoPrimary,
        animationSpec = tween(400),
        label = "guessTimerColor"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(NazoSurface),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                if (targetState < initialState) {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> -height } + fadeOut())
                } else {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> height } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "guessTimerAnimation"
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

/**
 * The mystery image. While [imageReady] is false the fetch spinner shows (the
 * timer has not started). Once ready, the image — or the drawn placeholder when
 * the fetch failed — sits under an on-device blur layer whose radius is a
 * LINEAR function of the remaining time, with a subtle zoom-out as it sharpens.
 */
@Composable
private fun MysteryImageCard(
    imageUrl: String?,
    imageReady: Boolean,
    imageFetchFailed: Boolean,
    topic: String,
    round: Int,
    progress: Float,
    imageLoader: ImageLoader,
) {
    val blurRadius = (progress * MAX_BLUR).dp
    val revealScale = 1f + 0.12f * progress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(NazoSurfaceVariant)
    ) {
        if (imageReady) {
            Box(modifier = Modifier.fillMaxSize().scale(revealScale).blur(blurRadius)) {
                if (imageFetchFailed || imageUrl == null) {
                    GuessImagePlaceholder(query = topic.ifBlank { "Mystery image" })
                } else {
                    AsyncImage(
                        model = imageUrl,
                        imageLoader = imageLoader,
                        transform = { it.crossfade(true) },
                        contentScale = ContentScale.Crop,
                        contentDescription = "Mystery image, round $round",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            ImageFetchingIndicator()
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "ROUND $round",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ImageFetchingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = NazoPrimary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Fetching image…",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
        }
    }
}

/**
 * High-quality on-device fallback shown when no image could be fetched:
 * a themed dark card with the app emblem and the search query.
 */
@Composable
private fun GuessImagePlaceholder(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(NazoDarkCard, NazoDarkCardAccent))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = "謎",
                color = NazoOnDarkCard,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp),
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = query,
                color = NazoOnDarkCardMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "MYSTERY IMAGE",
                color = NazoOnDarkCardMuted.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
}

/** Easy/Medium input: the standard 4-choice buttons from `easy_medium_options`. */
@Composable
private fun ChoiceInput(
    payload: GuessPayload,
    revealed: Boolean,
    submitted: String?,
    onSubmit: (String) -> Unit,
) {
    val labels = listOf("A", "B", "C", "D")
    val options = remember(payload) { payload.choiceOptions.shuffled() }
    options.forEachIndexed { index, optionText ->
        val isThisSelected = submitted == optionText
        val isThisCorrect = payload.isCorrect(optionText)

        val bgColor by animateColorAsState(
            targetValue = when {
                !revealed -> NazoSurfaceVariant
                isThisCorrect -> NazoSuccessBg
                isThisSelected -> NazoErrorBg
                else -> NazoSurfaceVariant
            },
            animationSpec = tween(220),
            label = "choiceBg"
        )
        val borderColor by animateColorAsState(
            targetValue = when {
                !revealed -> Color.Transparent
                isThisCorrect -> NazoSuccess
                isThisSelected -> NazoError
                else -> Color.Transparent
            },
            animationSpec = tween(220),
            label = "choiceBorder"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(50))
                .clickable(enabled = !revealed) { onSubmit(optionText) }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val circleColor = animateColorAsState(
                targetValue = when {
                    !revealed -> NazoBackground
                    isThisCorrect -> NazoSuccess
                    isThisSelected -> NazoError
                    else -> NazoBackground
                },
                animationSpec = tween(220),
                label = "choiceCircle"
            ).value
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (revealed && (isThisCorrect || isThisSelected)) Color.White else NazoTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = optionText,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Hard/Otaku input: a text field with fuzzy auto-complete over
 * `hard_autocomplete_pool`. A SINGLE TAP on a suggestion auto-submits it.
 */
@Composable
private fun AutocompleteInput(
    payload: GuessPayload,
    revealed: Boolean,
    onSubmit: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var typed by remember { mutableStateOf("") }
    val suggestions = remember(typed, payload) { FuzzyMatch.topMatches(typed, payload.suggestionPool, 6) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NazoSurface)
            .padding(18.dp)
    ) {
        SectionLabelGuess("TYPE THE NAME")
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
                if (typed.isEmpty()) {
                    Text(
                        text = "Type a name from the topic…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NazoTextPlaceholder,
                    )
                }
                BasicTextField(
                    value = typed,
                    onValueChange = { if (!revealed) typed = it },
                    enabled = !revealed,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!revealed && typed.isNotBlank()) {
                                focusManager.clearFocus()
                                onSubmit(typed)
                            }
                        },
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (typed.isNotEmpty() && !revealed) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(NazoTextSecondary.copy(alpha = 0.2f))
                        .clickable { typed = "" },
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

        if (!revealed) {
            Spacer(Modifier.height(14.dp))
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        // Single tap on a suggestion auto-submits the answer.
                        .clickable {
                            focusManager.clearFocus()
                            onSubmit(suggestion)
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
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val canSubmit = typed.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSubmit) NazoPrimary else NazoPillUnselected)
                    .then(if (canSubmit) Modifier.clickable { focusManager.clearFocus(); onSubmit(typed) } else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Submit Answer",
                    color = if (canSubmit) NazoOnPrimary else NazoTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Shown in place after the round resolves — reveals the answer + points. */
@Composable
private fun RevealCard(
    result: GuessRoundResult,
    totalRounds: Int,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val isSuccess = result.correct
    AnimatedVisibility(
        visible = true,
        enter = expandVertically(tween(350)) + fadeIn(tween(350)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NazoSurface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSuccess) NazoSuccess else NazoError),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isSuccess) "Correct!" else "Eliminated!",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSuccess) NazoSuccess else NazoError,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSuccess) {
                            "${(result.remainingFraction * 100).toInt()}% time left — +${result.points} pts"
                        } else {
                            result.answerText?.let { "You answered \"$it\"" } ?: "Time's up!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "It was",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.target,
                style = MaterialTheme.typography.headlineSmall,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (result.aliases.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "also known as: ${result.aliases.joinToString(" • ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(NazoPrimary)
                    .clickable {
                        Haptics.light(context)
                        onNext()
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSuccess && result.round < totalRounds) "Next Round" else "See Results",
                    color = NazoOnPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NazoOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Spinner card while the round payload and image URL are being fetched. */
@Composable
private fun PreparingCard(round: Int, totalRounds: Int, topic: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "guessPreparing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guessPreparingScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(NazoSurface)
            .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(NazoPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "謎",
                    color = NazoOnPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Round $round of $totalRounds",
                color = NazoTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (topic.isBlank()) "Summoning your mystery image…" else "Summoning your mystery image from \"$topic\"…",
                color = NazoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                color = NazoPrimary,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

/** Card for unrecoverable round errors (no connection / no provider / API failure). */
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(NazoSurface)
            .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            .padding(28.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(NazoError),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Guessing Game unavailable",
                color = NazoError,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = NazoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            GuessPrimaryButton(label = "Try again", onClick = onRetry)
            Spacer(Modifier.height(10.dp))
            GuessOutlineButton(label = "Open settings", onClick = onOpenSettings)
            Spacer(Modifier.height(10.dp))
            GuessTextButton(label = "Quit to home", onClick = onQuit)
        }
    }
}

/** Same quit-confirmation pattern as the quiz mode. */
@Composable
private fun QuitDialog(
    show: Boolean,
    onStay: () -> Unit,
    onQuit: () -> Unit,
) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = show,
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
                    text = "Quit guessing game?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Your score in this game will be lost.",
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
                            .clickable {
                                Haptics.light(context)
                                onStay()
                            },
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
                                onQuit()
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

@Composable
private fun SectionLabelGuess(text: String) {
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
private fun GuessPrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NazoPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoOnPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GuessOutlineButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, NazoPrimary, RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GuessTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
