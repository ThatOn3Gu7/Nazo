package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * The app's button vocabulary.
 *
 * Buttons had drifted: some corners were 50% pills, some 14.dp, some 16.dp;
 * secondary actions were sometimes a filled grey surface and sometimes bare
 * text with no edge at all, which made them read as disabled next to a filled
 * primary. These wrappers give every button a defined role and a visible edge,
 * so a screen's actions look deliberate rather than assembled ad hoc.
 *
 * Three roles:
 *  - [NazoPrimaryButton]   the one obvious action. Filled accent.
 *  - [NazoSecondaryButton] alternatives and Cancel. Outlined, accent text.
 *  - [NazoDangerButton]    destructive. Filled red.
 *
 * All three share [NazoButtonShape] and a 1.5.dp edge so they line up visually
 * when placed side by side.
 */

/** Shared corner radius. One value, so rows of buttons agree. */
val NazoButtonShape: Shape = RoundedCornerShape(16.dp)

/** Border weight heavy enough to read against both themes. */
private val BorderWidth = 1.5.dp

private val DefaultContentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

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
        modifier = modifier,
        enabled = enabled,
        shape = NazoButtonShape,
        // A border on a filled button looks redundant in isolation but keeps
        // its silhouette identical to the outlined variants beside it.
        border = BorderStroke(BorderWidth, NazoPrimary),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoPrimary,
            contentColor = NazoOnPrimary,
            disabledContainerColor = NazoPrimary.copy(alpha = 0.35f),
            disabledContentColor = NazoOnPrimary.copy(alpha = 0.6f),
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
        modifier = modifier,
        enabled = enabled,
        shape = NazoButtonShape,
        border = BorderStroke(BorderWidth, tint.copy(alpha = if (muted) 0.4f else 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (muted) NazoTextPrimary else NazoPrimary,
            disabledContentColor = NazoTextSecondary.copy(alpha = 0.5f),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

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
        modifier = modifier,
        enabled = enabled,
        shape = NazoButtonShape,
        border = BorderStroke(BorderWidth, NazoError),
        colors = ButtonDefaults.buttonColors(
            containerColor = NazoError,
            contentColor = Color.White,
            disabledContainerColor = NazoError.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
        contentPadding = contentPadding,
        content = content,
    )
}
