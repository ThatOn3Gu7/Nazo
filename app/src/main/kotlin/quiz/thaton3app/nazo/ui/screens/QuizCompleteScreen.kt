package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import quiz.thaton3app.nazo.daily.DailyBonusChip
import quiz.thaton3app.nazo.daily.DailyLadderClearCard
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.progression.ProgressionReward
import quiz.thaton3app.nazo.records.NewRecordBadge
import quiz.thaton3app.nazo.sound.Sounds
import quiz.thaton3app.nazo.ui.components.CelebrationOverlay
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.NazoPrimaryButton
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.ProgressionRewardBanner
import quiz.thaton3app.nazo.ui.components.ShareResultCard
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.components.rememberSystemReduceMotion
import quiz.thaton3app.nazo.ui.theme.*

internal enum class QuizResultStoryStage {
    Arriving,
    Heading,
    Score,
    Breakdown,
    Rewards,
    Actions,
}

internal data class QuizResultStoryTimeline(
    val headingAtMillis: Long,
    val scoreAtMillis: Long,
    val breakdownAtMillis: Long,
    val rewardsAtMillis: Long,
    val actionsAtMillis: Long,
)

internal fun quizResultStoryTimeline(hasRewards: Boolean): QuizResultStoryTimeline =
    QuizResultStoryTimeline(
        headingAtMillis = 80L,
        scoreAtMillis = 200L,
        breakdownAtMillis = 700L,
        rewardsAtMillis = 980L,
        actionsAtMillis = if (hasRewards) 1_280L else 1_060L,
    )

internal fun quizResultStoryShows(
    current: QuizResultStoryStage,
    target: QuizResultStoryStage,
): Boolean = current.ordinal >= target.ordinal

internal fun quizResultStoryInitialStage(
    playEntranceSequence: Boolean,
    reduceMotion: Boolean,
): QuizResultStoryStage =
    if (playEntranceSequence && !reduceMotion) QuizResultStoryStage.Arriving
    else QuizResultStoryStage.Actions

internal fun quizResultStatDelayMillis(index: Int, motionAllowed: Boolean): Int =
    if (motionAllowed) index.coerceIn(0, 2) * 80 else 0

internal fun quizResultPaceLabel(elapsedMillis: Long, answered: Int): String {
    if (elapsedMillis <= 0L || answered <= 0) return "—"
    val averageTenths = (elapsedMillis + answered.toLong() * 50L) /
        (answered.toLong() * 100L)
    return "${averageTenths / 10}.${averageTenths % 10}s/q"
}

internal fun mistakeRematchLabel(mistakeCount: Int): String =
    if (mistakeCount == 1) "Retry 1 Miss" else "Retry $mistakeCount Misses"

internal fun quizResultEyebrow(daily: Boolean, modeLabel: String?): String = when {
    daily -> "DAILY LADDER"
    modeLabel == "Survival" -> "SURVIVAL REPORT"
    modeLabel == "Blitz" -> "60-SECOND REPORT"
    else -> "FOCUSED QUIZ"
}

internal fun quizResultHeading(
    daily: Boolean,
    modeLabel: String?,
    difficulty: String,
    score: Int,
): String = when {
    daily -> "Daily Ladder Cleared"
    modeLabel == "Survival" -> "Run over — you survived $score!"
    modeLabel == "Blitz" -> "Time's up!"
    difficulty == "Practice" -> "Practice Complete"
    else -> "Quiz Complete"
}

internal fun quizResultReturnLabel(daily: Boolean, modeLabel: String?): String = when {
    daily -> "Back to Home"
    modeLabel == "Survival" -> "Try Survival Again"
    modeLabel == "Blitz" -> "Run Another Blitz"
    else -> "Play Another Quiz"
}

@Composable
fun QuizCompleteScreen(
    score: Int,
    totalQuestions: Int,
    timeSpent: String,
    difficulty: String,
    elapsedMillis: Long = 0L,
    bestPercent: Int = -1,
    isNewRecord: Boolean = false,
    daily: Boolean = false,
    dailyBonusXp: Int = 0,
    progressionReward: ProgressionReward? = null,
    mistakeCount: Int = 0,
    // "Survival" | "Blitz" | null (normal quiz). Adjusts the heading and the
    // personal-best caption: those modes track a COUNT (longest run / most
    // correct in 60s) in bestPercent's slot, not a percentage.
    modeLabel: String? = null,
    /** True only for the first presentation immediately after gameplay ends. */
    playEntranceSequence: Boolean = true,
    /** Consumes the owner-level one-shot before any sound or delay can run. */
    onEntranceSequenceStarted: () -> Unit = {},
    onPlayAnother: () -> Unit,
    onPracticeMistakes: () -> Unit,
    onReviewAnswers: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val landscape = isLandscape()
    val accuracy = if (totalQuestions > 0) ((score.toFloat() / totalQuestions) * 100).toInt() else 0
    val isSuccess = accuracy >= 50

    val reduceMotion = rememberSystemReduceMotion()
    // Capture this composition's role. The owner consumes the gate as soon as
    // the first effect starts; later recompositions of this same screen must
    // continue that story, while a reconstructed back target starts settled.
    val ownsEntranceSequence = remember { playEntranceSequence }
    val initialStoryStage = remember {
        quizResultStoryInitialStage(ownsEntranceSequence, reduceMotion)
    }
    val runEntranceSequence = initialStoryStage == QuizResultStoryStage.Arriving
    val hasRewards = isNewRecord || dailyBonusXp > 0 || progressionReward != null
    val timeline = remember(hasRewards) { quizResultStoryTimeline(hasRewards) }
    var storyStage by remember { mutableStateOf(initialStoryStage) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val showHeader = quizResultStoryShows(storyStage, QuizResultStoryStage.Heading)
    val showCard = quizResultStoryShows(storyStage, QuizResultStoryStage.Score)
    val showStats = quizResultStoryShows(storyStage, QuizResultStoryStage.Breakdown)
    val showRewards = quizResultStoryShows(storyStage, QuizResultStoryStage.Rewards)
    val showButtons = quizResultStoryShows(storyStage, QuizResultStoryStage.Actions)

    // Score and ring finish their count-up around the time the reward chapter
    // arrives. A reconstructed or reduced-motion result starts at its real value.
    val valueAnimationMillis = if (runEntranceSequence) 900 else 0
    val animatedAccuracy by animateIntAsState(
        targetValue = if (showCard) accuracy else 0,
        animationSpec = tween(valueAnimationMillis, easing = FastOutSlowInEasing),
        label = "accuracy"
    )
    val animatedScore by animateIntAsState(
        targetValue = if (showCard) score else 0,
        animationSpec = tween(valueAnimationMillis, easing = FastOutSlowInEasing),
        label = "score"
    )
    val progressAnim by animateFloatAsState(
        targetValue = if (showCard) accuracy.toFloat() / 100f else 0f,
        animationSpec = tween(
            durationMillis = valueAnimationMillis,
            easing = FastOutSlowInEasing,
            delayMillis = if (runEntranceSequence) 100 else 0,
        ),
        label = "progress"
    )

    val context = LocalContext.current
    val celebrationStyle = remember { ThemePreferences(context).celebrationStyle }
    LaunchedEffect(Unit) {
        if (!ownsEntranceSequence) return@LaunchedEffect
        // Consume first: Results can now be rebuilt for an ordinary or
        // predictive back preview without replaying any part of this story.
        onEntranceSequenceStarted()
        Sounds.complete(context) // opt-in, no-op when sounds are disabled

        if (!runEntranceSequence) {
            // Remove animations makes the complete result available on frame
            // one, while the user's enabled completion cues remain intact.
            if (isSuccess) Sounds.celebration(context, celebrationStyle)
            if (isNewRecord) {
                Haptics.light(context)
                Sounds.record(context)
            }
            return@LaunchedEffect
        }

        delay(timeline.headingAtMillis)
        storyStage = QuizResultStoryStage.Heading
        delay(timeline.scoreAtMillis - timeline.headingAtMillis)
        storyStage = QuizResultStoryStage.Score
        if (isSuccess) {
            triggerConfetti = true
            // Per-variant cue queued after the completion arpeggio (opt-in).
            Sounds.celebration(context, celebrationStyle)
        }
        delay(timeline.breakdownAtMillis - timeline.scoreAtMillis)
        storyStage = QuizResultStoryStage.Breakdown
        delay(timeline.rewardsAtMillis - timeline.breakdownAtMillis)
        storyStage = QuizResultStoryStage.Rewards
        if (isNewRecord) {
            Haptics.light(context)
            Sounds.record(context)
        }
        delay(timeline.actionsAtMillis - timeline.rewardsAtMillis)
        storyStage = QuizResultStoryStage.Actions
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Celebration overlay — variant is a user preference (Appearance →
        // Celebrations). Drawn FIRST so it renders behind the content layer:
        // confetti drifts through the margins and gaps but can never cross the
        // score card, the stats, or the action buttons.
        if (triggerConfetti) {
            CelebrationOverlay(
                style = celebrationStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        // Landscape gives each pane its own scroll, so the page
                        // must not scroll as well.
                        if (landscape) Modifier else Modifier.verticalScroll(rememberScrollState())
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Spacer(Modifier.height(if (landscape) 12.dp else 40.dp))

                // Header
                AnimatedVisibility(
                    visible = showHeader,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -40 } + fadeIn()
                ) {
                    Column {
                        Text(
                            text = quizResultEyebrow(daily = daily, modeLabel = modeLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = NazoPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = quizResultHeading(
                                daily = daily,
                                modeLabel = modeLabel,
                                difficulty = difficulty,
                                score = score,
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(if (landscape) 12.dp else 24.dp))

                TwoPaneResults(
                    landscape = landscape,
                    ring = {
                // Big Trophy Score Card
                AnimatedVisibility(
                    visible = showCard,
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    ScoreCardContent(
                        animatedAccuracy = animatedAccuracy,
                        accuracy = accuracy,
                        animatedScore = animatedScore,
                        score = score,
                        totalQuestions = totalQuestions,
                        progressAnim = progressAnim,
                        isSuccess = isSuccess,
                        modeLabel = modeLabel,
                        motionAllowed = !reduceMotion,
                    )
                }

                // A standing best is context for the score chapter. A newly
                // earned record joins XP and bonuses in the later reward chapter.
                if (!isNewRecord && bestPercent >= 0) {
                    AnimatedVisibility(
                        visible = showCard,
                        enter = fadeIn(tween(180)),
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when {
                                        daily -> "Best Daily ladder: $bestPercent%"
                                        modeLabel == "Survival" -> "Longest run: $bestPercent correct"
                                        modeLabel == "Blitz" -> "Best blitz: $bestPercent correct"
                                        else -> "Personal best on $difficulty: $bestPercent%"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NazoTextSecondary
                                )
                            }
                        }
                    }
                }

                    },
                    stats = {
                ResultBreakdownChapter(
                    visible = showStats,
                    motionAllowed = runEntranceSequence,
                    modeLabel = modeLabel,
                    totalQuestions = totalQuestions,
                    timeSpent = timeSpent,
                    elapsedMillis = elapsedMillis,
                    accuracy = accuracy,
                    difficulty = difficulty,
                    daily = daily,
                )

                if (daily) {
                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(
                        visible = showStats,
                        enter = fadeIn(tween(if (runEntranceSequence) 220 else 1)),
                    ) {
                        DailyLadderClearCard()
                    }
                }

                if (hasRewards) {
                    Spacer(Modifier.height(20.dp))
                    AnimatedVisibility(
                        visible = showRewards,
                        enter = slideInVertically(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                        ) { height -> height / 4 } + fadeIn(tween(180)),
                    ) {
                        ResultRewardsChapter(
                            isNewRecord = isNewRecord,
                            dailyBonusXp = dailyBonusXp,
                            progressionReward = progressionReward,
                        )
                    }
                }

                Spacer(Modifier.height(if (landscape) 16.dp else 32.dp))

                // Action Buttons
                AnimatedVisibility(
                    visible = showButtons,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { 100 } + fadeIn()
                ) {
                    Column {
                        val playAgainLabel = quizResultReturnLabel(
                            daily = daily,
                            modeLabel = modeLabel,
                        )
                        val playAgainIcon = if (daily) Icons.Outlined.Home else Icons.Filled.Replay
                        if (mistakeCount > 0) {
                            // The most useful next action stays one tap away and
                            // retries this run's mistakes, not the whole saved deck.
                            NazoPrimaryButton(
                                onClick = {
                                    Haptics.light(context)
                                    onPracticeMistakes()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            ) {
                                Icon(
                                    Icons.Filled.School,
                                    contentDescription = null,
                                    tint = NazoOnPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = mistakeRematchLabel(mistakeCount),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            NazoSecondaryButton(
                                onClick = onPlayAnother,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                containerColor = NazoBackground,
                                muted = true,
                            ) {
                                Icon(
                                    playAgainIcon,
                                    contentDescription = null,
                                    tint = NazoTextPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = playAgainLabel,
                                    color = NazoTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            NazoPrimaryButton(
                                onClick = onPlayAnother,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            ) {
                                Icon(
                                    playAgainIcon,
                                    contentDescription = null,
                                    tint = NazoOnPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = playAgainLabel,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        NazoSecondaryButton(
                            onClick = onReviewAnswers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            // Solid bed: a control is an island the celebration
                            // can never show through. Keep it on the animated
                            // surface so fill and edge morph together.
                            containerColor = NazoBackground,
                            muted = true,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Review Answers & Explanations", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Share the run as a themed image card (system share sheet).
                        NazoSecondaryButton(
                            onClick = {
                                Haptics.light(context)
                                ShareResultCard.share(
                                    context = context,
                                    heading = when {
                                        daily -> "Daily Ladder"
                                        modeLabel == "Survival" -> "Survival Run"
                                        modeLabel == "Blitz" -> "60-Second Blitz"
                                        else -> "Quiz Complete"
                                    },
                                    headline = if (modeLabel != null) "$score" else "$accuracy%",
                                    headlineCaption = if (modeLabel != null) "correct" else "accuracy",
                                    stats = listOf(
                                        "Score" to "$score / $totalQuestions",
                                        (if (daily) "Path" else "Difficulty") to
                                            (if (daily) "Easy → Medium → Hard" else difficulty),
                                        "Time" to timeSpent,
                                    ),
                                    background = NazoBackground,
                                    surface = NazoSurface,
                                    primary = NazoPrimary,
                                    onPrimary = NazoOnPrimary,
                                    textPrimary = NazoTextPrimary,
                                    textSecondary = NazoTextSecondary,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            containerColor = NazoBackground,
                            muted = true,
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = NazoTextPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Result", color = NazoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                    },
                )
            }
        }

    }
}

private data class ResultStat(
    val icon: ImageVector,
    val title: String,
    val value: String,
)

@Composable
private fun ResultBreakdownChapter(
    visible: Boolean,
    motionAllowed: Boolean,
    modeLabel: String?,
    totalQuestions: Int,
    timeSpent: String,
    elapsedMillis: Long,
    accuracy: Int,
    difficulty: String,
    daily: Boolean,
) {
    val pace = quizResultPaceLabel(elapsedMillis, totalQuestions)
    val stats = when (modeLabel) {
        "Blitz" -> listOf(
            ResultStat(Icons.Outlined.Timer, "Window", "60 sec"),
            ResultStat(Icons.Outlined.TrackChanges, "Accuracy", "$accuracy%"),
            ResultStat(Icons.Outlined.Speed, "Avg pace", pace),
        )
        "Survival" -> listOf(
            ResultStat(Icons.Outlined.Timer, "Run time", timeSpent),
            ResultStat(Icons.Outlined.TrackChanges, "Accuracy", "$accuracy%"),
            ResultStat(Icons.Outlined.Speed, "Avg pace", pace),
        )
        else -> listOf(
            ResultStat(Icons.Outlined.Timer, "Time", timeSpent),
            ResultStat(Icons.Outlined.TrackChanges, "Accuracy", "$accuracy%"),
            ResultStat(Icons.Outlined.Speed, "Avg pace", pace),
        )
    }

    Column {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(if (motionAllowed) 180 else 1)),
        ) {
            Text(
                text = if (daily) "LADDER BREAKDOWN • 3 STAGES"
                else "RUN BREAKDOWN • ${difficulty.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stats.forEachIndexed { index, stat ->
                Box(modifier = Modifier.weight(1f)) {
                    val revealDelay = quizResultStatDelayMillis(index, motionAllowed)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(
                            tween(
                                durationMillis = if (motionAllowed) 200 else 1,
                                delayMillis = revealDelay,
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = if (motionAllowed) 240 else 1,
                                delayMillis = revealDelay,
                                easing = FastOutSlowInEasing,
                            )
                        ) { height -> height / 4 },
                    ) {
                        StatCard(
                            modifier = Modifier.fillMaxWidth(),
                            icon = stat.icon,
                            title = stat.title,
                            value = stat.value,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRewardsChapter(
    isNewRecord: Boolean,
    dailyBonusXp: Int,
    progressionReward: ProgressionReward?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "REWARDS EARNED",
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (isNewRecord) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NewRecordBadge(animateEntrance = false)
            }
        }
        if (dailyBonusXp > 0) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DailyBonusChip(
                    bonusXp = dailyBonusXp,
                    animateEntrance = false,
                )
            }
        }
        progressionReward?.let { reward ->
            ProgressionRewardBanner(
                reward = reward,
                animateEntrance = false,
            )
        }
    }
}

/**
 * Results layout.
 *
 * Portrait tells the story top-to-bottom: score, breakdown, rewards, actions.
 *
 * Landscape keeps the score chapter on the left and the breakdown, rewards,
 * and actions on the right, so nothing is stretched and the full sequence
 * remains readable without one extra-wide card.
 */
@Composable
private fun TwoPaneResults(
    landscape: Boolean,
    ring: @Composable () -> Unit,
    stats: @Composable () -> Unit,
) {
    if (!landscape) {
        Column {
            ring()
            Spacer(Modifier.height(24.dp))
            stats()
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ring()
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            stats()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScoreCardContent(
    animatedAccuracy: Int,
    accuracy: Int,
    animatedScore: Int,
    score: Int,
    totalQuestions: Int,
    progressAnim: Float,
    isSuccess: Boolean,
    modeLabel: String?,
    motionAllowed: Boolean,
) {
    // Keep the trophy alive after the story settles, but do not start an
    // infinite animation clock when Android Remove animations is enabled.
    val floatOffset = if (motionAllowed) {
        val floatInfinite = rememberInfiniteTransition(label = "float")
        val animatedOffset by floatInfinite.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
        animatedOffset
    } else {
        0f
    }

    val message = when (modeLabel) {
        "Survival" -> when {
            score >= 15 -> "A formidable endurance run — you kept the pressure under control."
            score >= 7 -> "You held the line. One more run could push the record further."
            else -> "Three lives go quickly — steady reads will carry the next run farther."
        }
        "Blitz" -> when {
            score >= 12 -> "Fast and accurate — you found a strong rhythm against the clock."
            score >= 6 -> "Good pace. Quicker decisions will open up the next sprint."
            else -> "The clock won this round. Trust the first read and keep moving."
        }
        else -> when {
            accuracy >= 90 -> "Impressive run — you're nearly an Otaku Master on this arc."
            accuracy >= 70 -> "Great job! Your anime knowledge is sharp."
            accuracy >= 50 -> "Good effort! A little more training and you'll be unstoppable."
            else -> "Keep watching, keep learning. You'll get it next time!"
        }
    }

    val icon = when (modeLabel) {
        "Survival" -> Icons.Outlined.LocalFireDepartment
        "Blitz" -> Icons.Outlined.Bolt
        else -> if (isSuccess) Icons.Outlined.EmojiEvents else Icons.Outlined.FitnessCenter
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            // Opaque bed under the gradient: the celebration renders behind
            // the content layer, and this card is score information — nothing
            // may show through the surface the numbers sit on. The two
            // background layers composite to exactly the old look.
            .background(NazoBackground)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NazoSurface, NazoSurfaceVariant.copy(alpha = 0.3f))
                )
            )
            .border(
                width = 1.dp,
                color = NazoPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Progress Ring & Trophy
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background track
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = NazoPrimary.copy(alpha = 0.1f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Animated sweeping progress arc
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NazoPrimary.copy(alpha = 0.4f),
                            NazoPrimary,
                            NazoPrimary.copy(alpha = 0.8f),
                            NazoPrimary.copy(alpha = 0.4f) // FIXED: Re-added the start color at the end to make the gradient loop seamlessly at 3 o'clock!
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progressAnim,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Floating inner trophy
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .offset(y = floatOffset.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NazoPrimary, NazoPrimary.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NazoOnPrimary, modifier = Modifier.size(38.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // Animated Numbers. The hero follows the mode's headline metric —
        // accuracy for a quiz (the score rides in the pill below), the correct
        // COUNT for Survival/Blitz, matching their heading, record captions
        // and share card.
        Text(
            text = if (modeLabel != null) "$animatedScore" else "$animatedAccuracy%",
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 56.sp),
            color = NazoPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = when (modeLabel) {
                "Survival" -> "QUESTIONS SURVIVED"
                "Blitz" -> "CORRECT IN 60 SECONDS"
                else -> "ACCURACY"
            },
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )

        Surface(
            color = NazoPrimary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        ) {
            Text(
                text = when (modeLabel) {
                    "Survival" -> "$animatedAccuracy% accuracy • $totalQuestions faced"
                    "Blitz" -> "$totalQuestions answered • $animatedAccuracy% accuracy"
                    else -> "$animatedScore / $totalQuestions Correct"
                },
                style = MaterialTheme.typography.titleMedium,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoTextSecondary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = NazoTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = NazoTextPrimary, fontWeight = FontWeight.Bold)
    }
}

