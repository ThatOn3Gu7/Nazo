package quiz.thaton3app.nazo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A dialog that becomes a bottom drag sheet in landscape.
 *
 * Portrait keeps the familiar centred card. Landscape uses the same
 * [NazoModalSheet] the app-icon picker uses: it slides up from the bottom,
 * spans the full width and dims the whole screen behind it. A centred card in a
 * ~360 dp-tall window had to scroll between two wide bands of scrim, which
 * looked out of place next to the rest of the app's landscape UI.
 *
 * [dismissible] is honoured in both modes. The backup and restore previews are
 * deliberately non-dismissible, so in sheet form the drag-to-hide gesture, the
 * scrim tap and the back press are all refused rather than silently cancelling
 * a confirmation the user is mid-way through. [onDismissed] runs only after a
 * previously visible dialog has finished leaving, allowing destructive actions
 * to navigate without replacing the screen underneath an animating surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NazoAdaptiveDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    onDismissed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (isLandscape()) {
        // Keep the sheet mounted while hide() finishes. Previously an explicit
        // button changed visible to false and removed the sheet in one frame,
        // while drag/scrim dismissals animated — two exits for the same popup.
        val latestVisible by rememberUpdatedState(visible)
        val latestOnDismissed by rememberUpdatedState(onDismissed)
        var sheetComposed by remember { mutableStateOf(visible) }
        var hadVisibleContent by remember { mutableStateOf(visible) }
        // confirmValueChange blocks user drag-to-dismiss for strict dialogs,
        // but an explicit action may still close them after visible becomes
        // false. Reading latestVisible distinguishes those two paths.
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                dismissible || target != SheetValue.Hidden || !latestVisible
            },
        )
        LaunchedEffect(visible) {
            if (visible) {
                hadVisibleContent = true
                sheetComposed = true
            } else if (sheetComposed) {
                if (sheetState.currentValue != SheetValue.Hidden) sheetState.hide()
                sheetComposed = false
                if (hadVisibleContent) {
                    hadVisibleContent = false
                    latestOnDismissed()
                }
            }
        }
        if (!sheetComposed) return
        NazoModalSheet(
            onDismissRequest = { if (dismissible) onDismiss() },
            sheetState = sheetState,
        ) {
            // Standard sheet column: scrolls and is height-capped, so a long
            // category list can never push the action row out of reach.
            NazoSheetColumn { content() }
        }
        return
    }

    // Registered only while visible, so it never steals back from the screen.
    BackHandler(enabled = visible) {
        if (dismissible) onDismiss()
        // Otherwise: consumed and ignored on purpose.
    }

    val reduceMotion = rememberSystemReduceMotion()
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    var hadVisibleContent by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduceMotion) {
        if (visible) {
            hadVisibleContent = true
        } else if (hadVisibleContent) {
            if (!reduceMotion) delay(140)
            hadVisibleContent = false
            latestOnDismissed()
        }
    }
    val dialogTransition = updateTransition(
        targetState = visible,
        label = "nazoDialogTransition",
    )
    val cardScale by dialogTransition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap()
            else tween(durationMillis = if (targetState) 200 else 140)
        },
        label = "nazoDialogCardScale",
    ) { shown ->
        if (shown || reduceMotion) 1f else 0.96f
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(if (reduceMotion) snap() else tween(180)),
        exit = fadeOut(if (reduceMotion) snap() else tween(140)),
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
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .clickable(
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
