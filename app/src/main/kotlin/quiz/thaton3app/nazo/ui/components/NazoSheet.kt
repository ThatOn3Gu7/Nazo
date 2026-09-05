package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * The app's single bottom-sheet style, shared by every sheet (app icon,
 * background effects, celebrations, provider switcher, what's new).
 *
 * It centralises three fixes that each sheet previously needed on its own:
 *
 *  1. **The sheet could be dragged up under the status bar / camera cutout.**
 *     `ModalBottomSheet` consumes no top inset by default, so a tall sheet's
 *     content ran to the very top of the display. [NazoModalSheet] pins the
 *     content to the status-bar inset instead.
 *
 *  2. **Scrollable sheet content had no upper bound.** With no top inset the
 *     sheet's expanded height ran past the status bar, so a scrollable child
 *     (the icon list) and the sheet's own drag could keep trading the gesture
 *     near the top edge and the sheet oscillated instead of settling. Fixing
 *     the inset gives the sheet a stable maximum height; scrolling inside it
 *     via [NazoSheetColumn] then behaves normally and simply stops at the top.
 *
 *  3. **Pressing the drag handle flashed a dark rounded block** — an
 *     indication ripple sized to the handle's touch target, behind a 36x4dp
 *     line. [NazoDragHandle] draws no press indication while keeping the
 *     long-press "Drag handle" tooltip.
 */

/**
 * An indication that draws nothing — used to strip the ripple from the drag
 * handle while leaving its click/long-press behaviour intact.
 */
private object NoIndication : IndicationNodeFactory {
    private class Node : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() = drawContent()
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = Node()

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

/** Top inset shared by every Nazo sheet: never draw under the status bar. */
val NazoSheetInsets: WindowInsets
    @Composable get() = WindowInsets.statusBars

/**
 * The shared drag handle: a 36x4dp pill with **no press indication**, still
 * exposing the "Drag handle" tooltip on long-press plus a TalkBack label.
 *
 * The generous transparent padding keeps the long-press target comfortable
 * without drawing anything behind the pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NazoDragHandle() {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            Surface(
                color = NazoSurface,
                contentColor = NazoTextPrimary,
                shape = CircleShape,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = "Drag handle",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        },
        state = tooltipState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .semantics { contentDescription = "Drag handle" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(NazoTextSecondary.copy(alpha = 0.3f)),
            )
        }
    }
}

/**
 * A [ModalBottomSheet] pre-wired with the Nazo look, the status-bar inset and
 * the indication-free drag handle.
 *
 * Lay content out with [NazoSheetColumn] so padding and scrolling behave the
 * same in every sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NazoModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = NazoSurface,
        contentWindowInsets = { NazoSheetInsets },
        dragHandle = {
            // ModalBottomSheet makes the drag-handle slot clickable for the
            // accessibility expand/collapse action, and that clickable is what
            // painted the dark rounded block on press. Removing the indication
            // for this subtree kills the block without touching the tooltip,
            // the long-press gesture, or the a11y action.
            CompositionLocalProvider(LocalIndication provides NoIndication) {
                NazoDragHandle()
            }
        },
        content = { content() },
    )
}

/**
 * Standard content column for a Nazo sheet: the usual horizontal/vertical
 * padding plus navigation-bar padding.
 *
 * Set [scrollable] to true for sheets whose content can outgrow the screen
 * (the app-icon list). The scroll is bounded by the sheet's status-bar inset,
 * so it settles at the top instead of fighting the sheet's drag.
 */
@Composable
fun NazoSheetColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier =
        if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scrollModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        content = content,
    )
}
