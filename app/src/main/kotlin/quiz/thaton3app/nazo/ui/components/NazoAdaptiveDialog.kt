package quiz.thaton3app.nazo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoSurface

/**
 * A dialog that becomes a side panel in landscape.
 *
 * Portrait keeps the familiar centred card. Landscape slides a panel in from
 * the trailing edge instead, because a centred card in a ~360 dp-tall window
 * has to scroll awkwardly and leaves wide dead bands of scrim either side. A
 * side panel uses the full height it does have and matches how the app already
 * presents secondary content in landscape.
 *
 * The scrim dismisses on tap (when [dismissible]); the panel itself swallows
 * clicks so a tap inside never closes it.
 */
@Composable
fun NazoAdaptiveDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Registered only while visible, so it never steals back from the screen.
    BackHandler(enabled = visible) {
        if (dismissible) onDismiss()
        // Otherwise: consumed and ignored on purpose.
    }

    val landscape = isLandscape()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (dismissible) onDismiss() },
                ),
            contentAlignment = if (landscape) Alignment.CenterEnd else Alignment.Center,
        ) {
            if (landscape) {
                // Slide in from the trailing edge. The panel owns the full
                // height and scrolls internally, so long content can never push
                // its action row off screen.
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(tween(260)) { it },
                    exit = slideOutHorizontally(tween(220)) { it },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 420.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                            .background(NazoSurface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .safeDrawingPadding()
                                .padding(vertical = 12.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            content()
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
