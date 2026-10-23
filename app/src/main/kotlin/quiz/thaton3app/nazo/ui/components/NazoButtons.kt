package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.theme.disabledContainer
import quiz.thaton3app.nazo.ui.theme.disabledContent
import quiz.thaton3app.nazo.ui.theme.outlineBorderComposite
import quiz.thaton3app.nazo.ui.theme.readableTextOn

/**
 * The app's button vocabulary.
 *
 * Buttons had drifted: some corners were 50% pills, some 14.dp, some 16.dp;
 * secondary actions were sometimes a filled grey surface and sometimes bare
 * text with no edge at all, which made them read as disabled next to a filled
 * primary. These wrappers give every button a defined role and a visible edge,
 * so a screen's actions look deliberate rather than assembled ad hoc.
 *
 * Four roles, chosen by how consequential the action is:
 *  - [NazoPrimaryButton]   the one obvious action. Filled accent.
 *  - [NazoConfirmButton]   commits a change (save, restore, accept). Filled
 *                          accent, same as primary -- see below.
 *  - [NazoSecondaryButton] alternatives, Cancel, Back. Outlined.
 *  - [NazoDangerButton]    destructive and not undoable. Filled red.
 *
 * Confirm deliberately uses the theme accent rather than a fixed green. The
 * app ships several colour schemes, and a hard-coded green would clash with
 * every one of them and stop tracking the user's chosen theme; [NazoPrimary]
 * IS each scheme's most vivid colour, so affirmative actions stay the boldest
 * thing on screen whichever theme is active. Red is the one exception, because
 * destructive means the same thing in every scheme.
 *
 * All three share [NazoButtonShape] and a 1.5.dp edge so they line up visually
 * when placed side by side.
 */

/** Shared corner radius. One value, so rows of buttons agree. */
val NazoButtonShape: Shape = RoundedCornerShape(16.dp)

/** Border weight heavy enough to read against both themes. */
private val BorderWidth = 1.5.dp

private val DefaultContentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

/**
 * Accessibility touch target: Material buttons default to a 40.dp minimum
 * height, under the 48.dp Android accessibility guidance. Every Nazo button
 * honors 48.dp so small labels stay comfortably tappable.
 */
private fun Modifier.nazoTouchTarget(): Modifier = defaultMinSize(minHeight = 48.dp)

/** The single most important action on a screen or dialog. */
@Composable
fun NazoPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DefaultContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.nazoTouchTarget(),
        enabled = enabled,
        shape = NazoButtonShape,
        // A border on a filled button looks redundant in isolation but keeps
        // its silhouette identical to the outlined variants beside it.
        border = BorderStroke(BorderWidth, NazoPrimary),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoPrimary,
            contentColor = NazoOnPrimary,
            // Disabled keeps the "off" wash but its label stays legible
            // (>= 3:1 on the disabled fill) instead of dissolving into it.
            disabledContainerColor = disabledContainer(NazoPrimary, NazoBackground),
            disabledContentColor = disabledContent(disabledContainer(NazoPrimary, NazoBackground)),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Alternatives, "Cancel", "Back" — present but subordinate to the primary. */
@Composable
fun NazoSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Muted grey instead of accent, for a pure dismiss next to a real action. */
    muted: Boolean = false,
    contentPadding: PaddingValues = DefaultContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val tint = if (muted) NazoTextSecondary else NazoPrimary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.nazoTouchTarget(),
        enabled = enabled,
        shape = NazoButtonShape,
        // The accent edge is composited at the shared alpha that keeps it a
        // perceivable 3:1 control boundary; the muted variant is identified
        // by its 4.5:1 label instead, so its whisper border is decorative.
        border = BorderStroke(
            BorderWidth,
            if (muted) tint.copy(alpha = 0.4f) else outlineBorderComposite(NazoPrimary, NazoBackground),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (muted) NazoTextPrimary else NazoPrimary,
            disabledContentColor = NazoTextSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Commits a change: Save, Restore, Accept, Confirm.
 *
 * Currently identical to [NazoPrimaryButton] -- it exists as its own name so
 * call sites state intent, and so the affirmative role can be restyled
 * independently later without hunting through screens.
 */
@Composable
fun NazoConfirmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DefaultContentPadding,
    content: @Composable RowScope.() -> Unit,
) = NazoPrimaryButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    contentPadding = contentPadding,
    content = content,
)

/** Destructive and not undoable: delete, remove, wipe. */
@Composable
fun NazoDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = DefaultContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.nazoTouchTarget(),
        enabled = enabled,
        shape = NazoButtonShape,
        border = BorderStroke(BorderWidth, NazoError),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoError,
            // Red is fixed for destructive, but its LABEL must stay readable
            // on every theme's red — the dark theme's light red cannot carry
            // white text at 4.5:1, so the label adapts to the fill.
            contentColor = readableTextOn(NazoError, minContrast = 4.5f),
            disabledContainerColor = disabledContainer(NazoError, NazoBackground),
            disabledContentColor = disabledContent(disabledContainer(NazoError, NazoBackground)),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Lowest emphasis: dismiss, "Not now", "Email instead".
 *
 * Still carries a faint outline. A bare text button sitting beside a filled
 * one reads as disabled rather than optional, which is what made some screens
 * look unfinished.
 */
@Composable
fun NazoQuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.nazoTouchTarget(),
        enabled = enabled,
        shape = NazoButtonShape,
        // Decorative whisper edge (below 3:1 on purpose): this role is
        // identified by its 4.5:1 label, not its outline.
        border = BorderStroke(1.dp, NazoTextSecondary.copy(alpha = 0.28f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = NazoTextSecondary,
            disabledContentColor = NazoTextSecondary.copy(alpha = 0.4f),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}
