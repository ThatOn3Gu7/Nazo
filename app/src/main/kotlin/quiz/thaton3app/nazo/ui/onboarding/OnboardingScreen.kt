package quiz.thaton3app.nazo.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// One slide of the intro tour. Copy stays under ~140 chars per slide
// (retention research: one purpose per slide, lead with value).
private data class OnboardingPage(
    val icon: ImageVector,
    val kicker: String,
    val title: String,
    val body: String,
)

private val PAGES = listOf(
    OnboardingPage(
        icon = Icons.Filled.Quiz,
        kicker = "GAME MODE 1",
        title = "Quiz Mode",
        body = "Anime trivia your way — pick a topic, difficulty and length, then beat the clock. AI-generated online, a huge local bank offline.",
    ),
    OnboardingPage(
        icon = Icons.Filled.ImageSearch,
        kicker = "GAME MODE 2",
        title = "Guessing Game",
        body = "A mystery image sharpens while the timer runs. Name it before it's fully revealed — the faster you are, the more you score.",
    ),
    OnboardingPage(
        icon = Icons.Filled.EmojiEvents,
        kicker = "YOUR JOURNEY",
        title = "Level Up & Track It All",
        body = "Every game earns XP. Level up, keep daily streaks, master your favorite anime — and share your stats card with friends.",
    ),
)

/**
 * First-launch onboarding tour: 3 slides (the two game modes + the stats/
 * leveling system), swipeable, with progress dots, an always-visible Skip,
 * a full-width Next/"Start Playing" button along the bottom and a small
 * back arrow in the bottom-left once past the first slide. Rendered as an
 * opaque overlay above the app (the app composes underneath, untouched);
 * `onFinish` persists the flag so it never shows again. No auto-rotate.
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

            // Top bar: brand mark left, Skip right (hidden on the last slide —
            // the big button IS the skip there).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "謎",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                )
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                OnboardingSlide(PAGES[index])
            }

            // Progress dots — the active one stretches into a pill.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PAGES.indices.forEach { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == page) 26.dp else 8.dp,
                        animationSpec = tween(240),
                        label = "dotWidth",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (i == page) NazoPrimary
                                else NazoTextSecondary.copy(alpha = 0.30f)
                            ),
                    )
                }
            }

            // Bottom bar: small back arrow (from slide 2 on) + the big Next
            // button filling the rest of the bottom edge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fixed-size slot so the Next button never jumps when the
                // arrow appears/disappears.
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    AnimatedVisibility(
                        visible = page > 0,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180)),
                    ) {
                        IconButton(
                            onClick = {
                                Haptics.soft(context)
                                scope.launch { pagerState.animateScrollToPage(page - 1) }
                            },
                            modifier = Modifier
                                .size(56.dp)
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
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        Haptics.light(context)
                        if (isLast) onFinish()
                        else scope.launch { pagerState.animateScrollToPage(page + 1) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
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
private fun OnboardingSlide(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Hero tile: soft accent square with the slide's icon.
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(NazoPrimary.copy(alpha = 0.14f))
                .border(1.dp, NazoPrimary.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = NazoPrimary,
                modifier = Modifier.size(76.dp),
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = page.kicker,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = NazoPrimary,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
    }
}
