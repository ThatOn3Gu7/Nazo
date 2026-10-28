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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
 *  2. **A scrollable sheet could judder violently when dragged to the top.**
 *     Content taller than the screen (the nine app-icon cards) let
 *     `ModalBottomSheet` grow to the full display height. The sheet's drag and
 *     the inner `verticalScroll` then fought over the same upward gesture,
 *     producing a rapid up/down/up/down oscillation against the camera cutout.
 *     [NazoSheetColumn] now caps scrollable content at
 *     [SHEET_MAX_HEIGHT_FRACTION] of the screen, so the sheet settles at a
 *     fixed height and cannot be dragged any higher.
 *
 *     Note the status-bar inset alone did NOT fix this — padding moves content
 *     but never bounds the sheet's height. The cap is what matters.
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

/**
 * Tallest a scrollable sheet may grow, as a fraction of the screen.
 *
 * This bounds the CONTENT only. The sheet itself is also as tall as the
 * status-bar inset plus the drag handle, so the real figure to keep below 100%
 * is `inset + handle + fraction * screen` — see [sheetContentMaxHeight].
 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.78f

/**
 * Vertical space the drag handle occupies: 16.dp top padding + the 4.dp pill +
 * 8.dp bottom padding. Hard-coded alongside [NazoDragHandle]; keep in sync.
 */
private val DRAG_HANDLE_HEIGHT = 28.dp

/**
 * Largest height a sheet's scrolling content may take.
 *
 * Why this is not simply `fraction * screenHeight`: a sheet is taller than its
 * content by the status-bar inset plus the drag handle, and `screenHeightDp`
 * EXCLUDES system bars while the sheet is laid out against the full window. In
 * portrait the slack absorbs that (78% of ~800dp leaves ~120dp spare), but in
 * landscape 78% of ~360dp plus ~52dp of chrome comes to ~93% of the window.
 *
 * With almost no headroom the sheet is effectively full-height, so a fast
 * upward fling hands the leftover scroll to the content, the content bounces it
 * back to the sheet, and the two oscillate — the violent up/down judder seen
 * when the handle reaches the top of the screen.
 *
 * Subtracting the chrome guarantees a real gap between the settled sheet and
 * the top of the window in EVERY orientation, so the drag always terminates.
 */
@Composable
internal fun sheetContentMaxHeight(): Dp {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val statusBarInset = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val chrome = statusBarInset + DRAG_HANDLE_HEIGHT
    // Never let the sheet exceed this share of the window once chrome is added.
    val ceiling = screenHeight * SHEET_MAX_HEIGHT_FRACTION - chrome
    // Floor keeps very short windows (split screen) usable rather than clamping
    // the content to nothing.
    return maxOf(ceiling, screenHeight * 0.4f)
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
 * EVERY sheet scrolls and is height-capped, unconditionally.
 *
 * This used to be opt-in via a `scrollable` flag, which only the app-icon sheet
 * set. That was wrong: whether content "fits" is not a property of the sheet,
 * it is a property of the window. In landscape the usable height roughly
 * halves, so sheets that fit fine in portrait (theme, accent, sparkle,
 * celebration, difficulty) overflowed with no scroll container at all — the
 * options below the fold were simply unreachable and the sheet refused to
 * scroll. Making it unconditional means a sheet can never become a dead end on
 * a short window.
 *
 * The hard height cap is what keeps the scroll well-behaved: without it a sheet
 * taller than the screen grows to full height, and then the sheet's own drag
 * and the inner scroll fight over the same upward gesture (the judder against
 * the camera cutout fixed earlier). Capping means the sheet settles at a fixed
 * height and scrolling happens purely inside a container whose size never
 * changes.
 */
@Composable
fun NazoSheetColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxSheetHeight = sheetContentMaxHeight()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        content = content,
    )
}
