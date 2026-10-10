package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.ChangelogEntry
import quiz.thaton3app.nazo.data.NAZO_CHANGELOG
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Every released version, newest first.
 *
 * Follows the reference layout: each version is a header band with a body card
 * of bullet points beneath it, and the newest release is tinted with the accent
 * colour so it stands out. Content comes from [NAZO_CHANGELOG].
 *
 * A `LazyColumn` is used rather than a scrolling `Column` because the list grows
 * with every release and only a couple of entries are ever on screen.
 */
@Composable
fun ChangelogScreen(onBackClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Spacer(Modifier.height(28.dp))
                AboutSubScreenHeader(title = "Changelogs", onBackClick = onBackClick)
                Spacer(Modifier.height(20.dp))
            }
            itemsIndexed(NAZO_CHANGELOG) { index, entry ->
                ChangelogCard(entry = entry, highlighted = index == 0)
                Spacer(Modifier.height(20.dp))
            }
            item {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/**
 * One version. [highlighted] tints the newest entry with the accent colour, as
 * in the reference; older entries use the neutral surface so the list reads as
 * "here is what is new, and here is the history".
 */
@Composable
private fun ChangelogCard(entry: ChangelogEntry, highlighted: Boolean) {
    val headerBg = if (highlighted) NazoPrimary.copy(alpha = 0.22f) else NazoSurface
    val bodyBg = if (highlighted) NazoPrimary.copy(alpha = 0.10f) else NazoSurface
    val bulletColor = if (highlighted) NazoPrimary else NazoTextSecondary

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header band.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(headerBg)
                .border(
                    1.dp,
                    NazoTextSecondary.copy(alpha = 0.08f),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Version",
                style = MaterialTheme.typography.titleLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleLarge,
                color = if (highlighted) NazoPrimary else NazoTextPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.date,
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Body card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(bodyBg)
                .border(
                    1.dp,
                    NazoTextSecondary.copy(alpha = 0.08f),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            entry.changes.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    // Small ring bullet, matching the reference.
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(2.dp, bulletColor, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextPrimary,
                        lineHeight = 21.sp,
                    )
                }
            }
        }
    }
}
