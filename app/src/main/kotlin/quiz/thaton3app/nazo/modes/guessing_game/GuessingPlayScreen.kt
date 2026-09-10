package quiz.thaton3app.nazo.modes.guessing_game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import quiz.thaton3app.nazo.hints.HintEngine
import quiz.thaton3app.nazo.hints.HintPill
import quiz.thaton3app.nazo.hints.HintRevealPill
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.WavySpinner
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.theme.*

/**
 * The active guessing-game screen: a mystery image with an on-device reveal
 * running alongside the per-difficulty countdown (Easy 25s, Medium 20s,
 * Hard 15s, Otaku Master 10s), plus the difficulty's input mode (4 choices
 * on Easy/Medium, fuzzy auto-complete on Hard/Otaku Master). The image
 * starts partially obscured — the starting strength scales with difficulty
 * (50% / 60% / 80% / 100%) — and sharpens as the timer runs out. The
 * obscuration is a blur by default, or a pixelated mosaic when the
 * Appearance settings say so. One shot per round — a correct answer scores
 * with a time bonus, a wrong answer or a timer at 0 reveals the answer; all
 * rounds are always played, then the results screen.
 *
 * All game state lives in the host (NazoApp); this screen is purely reactive
 * to [GuessPhase].
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
    revealStyle: String = "pixel",
    // Appearance → Guessing Game: retained for the future crop path.
    @Suppress("UNUSED_PARAMETER") autoCrop: Boolean = true,
    onRetryRound: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuit: () -> Unit,
    onRoundComplete: (correct: Boolean, answerText: String?, remainingMs: Long) -> Unit,
    onNextRound: () -> Unit,
) {
    val context = LocalContext.current
    val durationMs = GuessScoring.durationMsFor(difficultyLabel)
    val startFraction = GuessScoring.specFor(difficultyLabel).startEffectFraction

    val payload: GuessPayload? = (phase as? GuessPhase.Playing)?.payload
    val imageUrl: String? = (phase as? GuessPhase.Playing)?.imageUrl

    var submitted by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var imageReady by remember { mutableStateOf(false) }
    var imageFetchFailed by remember { mutableStateOf(false) }
    // One software bitmap is the single source of truth for both reveal paths.
    var remainingMs by remember { mutableLongStateOf(durationMs) }
    var showQuitDialog by remember { mutableStateOf(false) }

    // Reset synchronously on a round identity change so no previous-round image
    // or reveal state can leak into the first frame of the new round.
    var stateForPhase by remember { mutableStateOf<Any?>(null) }
    if (stateForPhase !== phase) {
        stateForPhase = phase
        submitted = null
        timedOut = false
        remainingMs = durationMs
        imageFetchFailed = false
        imageReady = false
    }

    var hintsLeft by remember { mutableStateOf(HintEngine.guessSupply(totalRounds)) }
    var hintLetters by remember(round) { mutableStateOf(0) }

    val revealed = submitted != null || timedOut || roundResult != null
    val timerFrac = remember(durationMs) {
        { if (durationMs > 0) (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f }
    }
    val displaySeconds by remember(durationMs) {
        derivedStateOf { ((remainingMs + 999) / 1000).toInt() }
    }

    // RAW MODE: no pre-fetch, no manual decode. Coil loads the URL directly in
    // MysteryImageCard, so there is no second decode path to disagree with it.
    // imageReady flips immediately; Coil shows its own progressive load.
    LaunchedEffect(phase) {
        imageFetchFailed = false
        imageReady = true
    }

    // Lifecycle-safe countdown, driven by the frame clock (monotonic, drift-free).
    // The loop stops as soon as the player answers or the timer reaches zero.
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
                    Sounds.wrong(context)
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
        if (correct) {
            Haptics.light(context)
            Sounds.correct(context)
        } else {
            Haptics.doubleLight(context)
            Sounds.wrong(context)
        }
        submitted = answer
        onRoundComplete(correct, answer, remainingMs)
    }

    BackHandler(enabled = true) { showQuitDialog = !showQuitDialog }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(20.dp))

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
                    LinearProgressIndicator(
                        progress = timerFrac,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (displaySeconds <= 5) NazoError else NazoPrimary,
                        trackColor = NazoSurface
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when (phase) {
                is GuessPhase.Preparing -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    PreparingCard(
                        round = phase.round,
                        totalRounds = totalRounds,
                        topic = topic,
                        onCancel = onQuit,
                    )
                }

                is GuessPhase.Error -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorCard(
                        message = phase.message,
                        onRetry = onRetryRound,
                        onOpenSettings = onOpenSettings,
                        onQuit = onQuit,
                    )
                }

                is GuessPhase.Playing -> GuessPlayBody(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    image = {
                        key(phase) {
                            MysteryImageCard(
                                imageUrl = phase.imageUrl,
                                imageReady = imageReady,
                                imageFetchFailed = imageFetchFailed,
                                query = phase.payload.imageQuery.ifBlank { topic },
                                round = round,
                                progress = timerFrac,
                                revealed = revealed,
                                revealStyle = revealStyle,
                                startFraction = startFraction,
                            )
                        }
                    },
                    answer = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedVisibility(
                                visible = hintLetters > 0,
                                enter = expandHorizontally(tween(280)) + fadeIn(tween(280)),
                                exit = shrinkHorizontally(tween(200)) + fadeOut(tween(160)),
                            ) {
                                HintRevealPill(
                                    text = HintEngine.maskedReveal(phase.payload.targetEntity, hintLetters),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            HintPill(
                                remaining = hintsLeft,
                                enabled = hintsLeft > 0 && !revealed,
                                onClick = {
                                    Haptics.light(context)
                                    hintsLeft--
                                    hintLetters += HintEngine.GUESS_LETTERS_PER_HINT
                                },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
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
                    },
                )

                GuessPhase.Idle -> Box(Modifier.weight(1f))
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

@Composable
private fun GuessPlayBody(
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
    answer: @Composable () -> Unit,
) {
    if (!isLandscape()) {
        Column(modifier = modifier.verticalScroll(rememberScrollState())) {
            image()
            Spacer(Modifier.height(20.dp))
            answer()
        }
        return
    }

    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            image()
        }
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            answer()
        }
    }
}

/**
 * Draws the mystery image. RAW MODE: Coil renders the URL directly and
 * pixelation now operate on exactly the same source pixels, removing the old
 * no reveal effect is applied — see the comment in the body.
 */
@Composable
private fun MysteryImageCard(
    imageUrl: String?,
    imageReady: Boolean,
    imageFetchFailed: Boolean,
    query: String,
    round: Int,
    progress: () -> Float,
    revealed: Boolean,
    revealStyle: String,
    startFraction: Float,
) {
    // Reveal animation state is intentionally gone while raw mode is active.
    // revealStyle / progress / revealed / startFraction are still accepted so
    // restoring the effects is a one-commit revert.

    val cardHeight = if (isLandscape()) {
        (LocalConfiguration.current.screenHeightDp * 0.62f).dp.coerceAtMost(300.dp)
    } else {
        300.dp
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(NazoSurfaceVariant)
    ) {
        // RAW-IMAGE DIAGNOSTIC MODE.
        //
        // Everything between the network and the screen has been removed:
        //  - no BitmapFactory decode of our own
        //  - no alpha flattening, colour-space pinning or config forcing
        //  - no PortraitCrop face crop / rescale / re-encode
        //  - no pixelation, no blur, no reveal scale
        //
        // Coil is handed the URL and renders whatever it downloads. This is the
        // simplest path the image can possibly take, so if it STILL renders
        // wrong then nothing in this app's image handling is at fault and the
        // problem is the source bytes (or the device/Coil decode of them). If
        // it renders correctly, the fault is provably in one of the removed
        // stages and they can be restored one at a time.
        //
        // The mystery is deliberately spoiled while this mode is active: the
        // answer is visible from the first frame. That is expected.
        if (imageUrl.isNullOrBlank()) {
            GuessImagePlaceholder(query = query.ifBlank { "Mystery image" })
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Mystery image, round $round",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
                // Build marker: confirms the running APK contains this change.
                text = "ROUND $round · RAW",
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
            WavySpinner(color = NazoPrimary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Fetching image…",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
        }
    }
}

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
                        text = if (isSuccess) "Correct!" else "Missed!",
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
                    text = if (result.round < totalRounds) "Next Round" else "See Results",
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

@Composable
private fun PreparingCard(round: Int, totalRounds: Int, topic: String, onCancel: () -> Unit) {
    val landscape = isLandscape()
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
            .padding(if (landscape) 20.dp else 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (landscape) 60.dp else 88.dp)
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
            Spacer(Modifier.height(if (landscape) 10.dp else 18.dp))
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
            Spacer(Modifier.height(if (landscape) 12.dp else 20.dp))
            WavySpinner(color = NazoPrimary, modifier = Modifier.size(if (landscape) 32.dp else 44.dp))
            Spacer(Modifier.height(if (landscape) 14.dp else 24.dp))
            CancelTextButton(label = "Cancel", onClick = onCancel)
        }
    }
}

@Composable
private fun CancelTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
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
