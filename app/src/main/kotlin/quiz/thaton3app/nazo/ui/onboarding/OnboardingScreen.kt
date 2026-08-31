package quiz.thaton3app.nazo.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// One slide: a big two-line title, a hand-drawn doodle illustration and a
// left-aligned body where key phrases are bold (reference-style copy).
// Body is (text, isBold) segments — kept under ~140 chars per slide.
private data class OnboardingPage(
    val title: String,
    val body: List<Pair<String, Boolean>>,
)

private val PAGES = listOf(
    OnboardingPage(
        title = "Quiz\nYour Way",
        body = listOf(
            "Pick a topic, difficulty and length — then beat the clock. " to false,
            "AI-generated" to true,
            " questions online, a " to false,
            "huge local bank" to true,
            " offline." to false,
        ),
    ),
    OnboardingPage(
        title = "Guess\nthe Image",
        body = listOf(
            "A mystery image sharpens while the timer runs. " to false,
            "Name it fast" to true,
            " — the quicker you are, the " to false,
            "more you score" to true,
            "." to false,
        ),
    ),
    OnboardingPage(
        title = "Level Up\n& Track It",
        body = listOf(
            "Every game earns " to false,
            "XP" to true,
            ". Keep " to false,
            "daily streaks" to true,
            ", master your favorite anime and " to false,
            "share your stats" to true,
            "." to false,
        ),
    ),
)

/**
 * First-launch onboarding tour, reference-inspired redesign: each slide is a
 * full-bleed pastel rounded card (per-slide tint from OUR palette) with a huge
 * left-aligned two-line title, a hand-drawn doodle illustration (drawn in
 * Compose — no image assets) and left-aligned body copy with bold key phrases.
 * Top: dash-style progress + always-visible Skip. Bottom: on slide 1 the Next
 * button spans the ENTIRE bottom (no back arrow, per owner); from slide 2 a
 * small circular back arrow slides in at the bottom-left beside it. Final CTA
 * "Start Playing". First-launch-only via OnboardingPrefs; no auto-rotate.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGES.size })
    val page = pagerState.currentPage
    val isLast = page == PAGES.size - 1

    // System back steps one slide back (never exits the tour accidentally);
    // on the first slide it falls through to the app's normal back handling.
    BackHandler(enabled = page > 0) {
        scope.launch { pagerState.animateScrollToPage(page - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground)
            // Barrier so touches on empty areas can't leak to the app below.
            // Runs in the Main pass AFTER children, so the pager/buttons work.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

            // Top bar: dash-style progress (reference's little lines, doubling
            // as our progress indicator) + Skip (hidden on the last slide).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PAGES.indices.forEach { i ->
                    val dashWidth by animateDpAsState(
                        targetValue = if (i == page) 30.dp else 16.dp,
                        animationSpec = tween(240),
                        label = "dash",
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(4.dp)
                            .width(dashWidth)
                            .clip(CircleShape)
                            .background(
                                if (i == page) NazoTextPrimary
                                else NazoTextSecondary.copy(alpha = 0.35f)
                            ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !isLast,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                ) {
                    TextButton(
                        onClick = {
                            Haptics.soft(context)
                            onFinish()
                        },
                    ) {
                        Text(
                            text = "Skip",
                            color = NazoTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // The pastel slide cards. Slight content padding shows the edge of
            // the neighboring card mid-swipe, like the reference mockup.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp),
                pageSpacing = 10.dp,
            ) { index ->
                SlideCard(index)
            }

            // Bottom bar. Slide 1: the Next button takes the ENTIRE bottom.
            // Slides 2-3: a small circular back arrow slides in on the left.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = page > 0,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkHorizontally(tween(220)) + fadeOut(tween(160)),
                ) {
                    Row {
                        IconButton(
                            onClick = {
                                Haptics.soft(context)
                                scope.launch { pagerState.animateScrollToPage(page - 1) }
                            },
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(NazoSurface)
                                .border(1.dp, NazoTextSecondary.copy(alpha = 0.25f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Previous",
                                tint = NazoTextPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                Button(
                    onClick = {
                        Haptics.light(context)
                        if (isLast) onFinish()
                        else scope.launch { pagerState.animateScrollToPage(page + 1) }
                    },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NazoPrimary,
                        contentColor = NazoOnPrimary,
                    ),
                ) {
                    AnimatedContent(
                        targetState = isLast,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                        label = "nextLabel",
                    ) { last ->
                        Text(
                            text = if (last) "Start Playing" else "Next",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideCard(index: Int) {
    val page = PAGES[index]
    // Per-slide pastel tint over the surface — all derived from OUR palette so
    // every accent + dark mode recolors automatically (reference used fixed
    // pink/green/peach; ours: accent mint / soft rose / soft green).
    val tint = when (index) {
        0 -> NazoPrimary.copy(alpha = 0.14f)
        1 -> NazoError.copy(alpha = 0.10f)
        else -> NazoSuccess.copy(alpha = 0.13f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(NazoSurface)
            .background(tint)
            .padding(horizontal = 28.dp, vertical = 26.dp),
    ) {
        // Huge two-line title, left-aligned like the reference.
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 40.sp,
                lineHeight = 46.sp,
            ),
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )

        // Doodle illustration fills the middle.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> QuizDoodle()
                1 -> GuessDoodle()
                else -> StatsDoodle()
            }
        }

        // Left-aligned body with bold key phrases.
        Text(
            text = buildAnnotatedString {
                page.body.forEach { (segment, bold) ->
                    if (bold) {
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, color = NazoTextPrimary)
                        ) { append(segment) }
                    } else {
                        append(segment)
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = NazoTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Hand-drawn doodle illustrations (all vector, drawn in Compose — no assets).
// Ink = NazoTextPrimary so they read in light AND dark mode; small pops of
// NazoPrimary keep them on-brand. Slight rotations give the sketchy feel.
// ---------------------------------------------------------------------------

/** Slide 1: a question card + an answer sheet, sparkles and a curly arrow. */
@Composable
private fun QuizDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        // Answer-sheet card (dark), behind, tilted right.
        Column(
            modifier = Modifier
                .size(116.dp, 132.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp, y = 14.dp)
                .rotate(9f)
                .clip(RoundedCornerShape(18.dp))
                .background(ink)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (i == 1) NazoPrimary else paper.copy(alpha = 0.45f)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (i == 1) 54.dp else 40.dp)
                            .clip(CircleShape)
                            .background(paper.copy(alpha = if (i == 1) 0.95f else 0.45f)),
                    )
                }
            }
        }
        // Question card (light, inked border), front, tilted left.
        Box(
            modifier = Modifier
                .size(112.dp, 138.dp)
                .align(Alignment.CenterStart)
                .offset(x = 14.dp, y = (-8).dp)
                .rotate(-8f)
                .clip(RoundedCornerShape(18.dp))
                .background(paper)
                .border(3.dp, ink, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "?", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = ink)
        }
        DoodleArrow(size = 54.dp, color = ink, modifier = Modifier.align(Alignment.TopCenter).offset(x = 8.dp))
        DoodleSparkle(size = 22.dp, color = ink, modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 22.dp))
        DoodleSparkle(size = 14.dp, color = ink, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = (-2).dp))
    }
}

/** Slide 2: a pixelated mystery card with a magnifier revealing the 謎. */
@Composable
private fun GuessDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    val accent = NazoPrimary
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        // Pixel-mosaic card (dark), tilted left — nods to our pixel reveal.
        Box(
            modifier = Modifier
                .size(158.dp, 122.dp)
                .align(Alignment.TopCenter)
                .offset(x = (-14).dp, y = 10.dp)
                .rotate(-5f)
                .clip(RoundedCornerShape(18.dp))
                .background(ink),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val cols = 6
                val rows = 4
                val cw = size.width / cols
                val ch = size.height / rows
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val shade = when ((r * 3 + c * 5) % 4) {
                            0 -> 0.12f
                            1 -> 0.28f
                            2 -> 0.48f
                            else -> 0.70f
                        }
                        drawRect(
                            color = paper.copy(alpha = shade),
                            topLeft = Offset(c * cw + 1.5f, r * ch + 1.5f),
                            size = Size(cw - 3f, ch - 3f),
                        )
                    }
                }
            }
        }
        // Magnifier handle (thick inked stroke)...
        Canvas(
            modifier = Modifier.size(44.dp).align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp),
        ) {
            drawLine(
                color = ink,
                start = Offset(size.width * 0.15f, size.height * 0.15f),
                end = Offset(size.width * 0.9f, size.height * 0.9f),
                strokeWidth = size.width * 0.22f,
                cap = StrokeCap.Round,
            )
        }
        // ...and the lens, revealing the crisp 謎.
        Box(
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-30).dp, y = (-26).dp)
                .clip(CircleShape)
                .background(paper)
                .border(4.dp, ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "謎", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        DoodleSparkle(size = 20.dp, color = ink, modifier = Modifier.align(Alignment.BottomStart).offset(x = 14.dp, y = (-18).dp))
        DoodleSparkle(size = 13.dp, color = ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 2.dp))
    }
}

/** Slide 3: a rising bar chart card, an XP badge and a victory arrow. */
@Composable
private fun StatsDoodle() {
    val ink = NazoTextPrimary
    val paper = NazoSurface
    val accent = NazoPrimary
    Box(modifier = Modifier.size(250.dp, 205.dp)) {
        // Chart card (light, inked border), tilted right.
        Box(
            modifier = Modifier
                .size(152.dp, 126.dp)
                .align(Alignment.Center)
                .offset(x = 12.dp, y = (-8).dp)
                .rotate(6f)
                .clip(RoundedCornerShape(18.dp))
                .background(paper)
                .border(3.dp, ink, RoundedCornerShape(18.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
                val barWidth = size.width * 0.18f
                val gap = (size.width - barWidth * 3f) / 2f
                val heights = listOf(0.45f, 0.68f, 1.0f)
                heights.forEachIndexed { i, hFrac ->
                    val h = size.height * hFrac
                    drawRoundRect(
                        color = if (i == 2) accent else ink.copy(alpha = 0.75f),
                        topLeft = Offset(i * (barWidth + gap), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f),
                    )
                }
            }
        }
        // XP badge (dark circle), overlapping bottom-left.
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.BottomStart)
                .offset(x = 22.dp, y = (-6).dp)
                .rotate(-10f)
                .clip(CircleShape)
                .background(ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "XP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = paper)
        }
        DoodleArrow(size = 52.dp, color = ink, modifier = Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = 6.dp))
        DoodleSparkle(size = 20.dp, color = ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 14.dp))
        DoodleSparkle(size = 13.dp, color = ink, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = (-14).dp))
    }
}

/** Four-point hand-drawn sparkle (the reference's little asterisks). */
@Composable
private fun DoodleSparkle(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.16f
        drawLine(color, Offset(s / 2f, 0f), Offset(s / 2f, s * 0.30f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s / 2f, s * 0.70f), Offset(s / 2f, s), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, s / 2f), Offset(s * 0.30f, s / 2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(s * 0.70f, s / 2f), Offset(s, s / 2f), stroke, StrokeCap.Round)
    }
}

/** A curly hand-drawn arrow, swooping up-right. */
@Composable
private fun DoodleArrow(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = w * 0.09f
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.88f)
            quadraticBezierTo(w * 0.05f, h * 0.30f, w * 0.78f, h * 0.22f)
        }
        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.58f, h * 0.10f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.62f, h * 0.42f), strokeWidth, StrokeCap.Round)
    }
}
