package quiz.thaton3app.nazo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Shared orientation helpers.
 *
 * The app was built portrait-first and had no orientation handling at all, so
 * landscape simply stretched the portrait layout. Rather than scatter
 * `LocalConfiguration` checks through thirteen screens, every landscape
 * decision goes through this file.
 *
 * The rule of thumb used throughout:
 *
 *  * screens that are **vertical lists** (Settings, About, Statistics...) do not
 *    need a new layout — they only need a readable measure, so they get
 *    [NazoReadableColumn].
 *  * screens with a **dominant visual** (quiz question, score ring, mystery
 *    image) split into two panes via [NazoAdaptivePanes].
 */

/** True when the device is currently in landscape. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Widest a single column of text/controls should ever get.
 *
 * A full-width line on a landscape phone (or any tablet) is genuinely hard to
 * read, so list-style screens are capped and centred instead of stretched.
 */
val NazoReadableWidth = 600.dp

/**
 * The side navigation rail's footprint. Screens in landscape reserve this much
 * on the right so their content never sits underneath the rail.
 */
val NazoRailWidth = 84.dp

/**
 * A content column that stays readable in landscape.
 *
 * In portrait this is a plain full-width [Column]; in landscape the content is
 * capped at [NazoReadableWidth] and centred.
 */
@Composable
fun NazoReadableColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val landscape = isLandscape()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (landscape) Modifier.widthIn(max = NazoReadableWidth) else Modifier),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Lays two panes out side by side in landscape and stacked in portrait.
 *
 * [primary] is the dominant half (question, score ring, mystery image) and
 * [secondary] is the interactive half (answers, stats, buttons). [primaryWeight]
 * biases the split — 0.5 is even.
 *
 * In portrait the panes simply stack in the same order, so a screen written
 * against this helper keeps its existing portrait behaviour.
 */
@Composable
fun NazoAdaptivePanes(
    modifier: Modifier = Modifier,
    primaryWeight: Float = 0.5f,
    horizontalGap: Int = 20,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    if (isLandscape()) {
        Row(
            modifier = modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap.dp),
        ) {
            PaneBox(weight = primaryWeight, content = primary)
            PaneBox(weight = 1f - primaryWeight, content = secondary)
        }
    } else {
        Column(modifier = modifier.padding(contentPadding)) {
            primary()
            secondary()
        }
    }
}

@Composable
private fun RowScope.PaneBox(weight: Float, content: @Composable () -> Unit) {
    Column(modifier = Modifier.weight(weight)) { content() }
}
