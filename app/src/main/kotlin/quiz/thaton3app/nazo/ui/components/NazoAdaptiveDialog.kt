package quiz.thaton3app.nazo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
 * a confirmation the user is mid-way through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NazoAdaptiveDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (isLandscape()) {
        if (!visible) return
        // confirmValueChange blocks the drag-to-dismiss gesture itself, which
        // is the only way to stop a non-dismissible sheet being swiped away;
        // refusing onDismissRequest alone would still let it animate out.
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> dismissible || target != SheetValue.Hidden },
        )
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
