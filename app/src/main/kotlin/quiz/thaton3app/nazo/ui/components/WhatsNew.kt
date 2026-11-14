package quiz.thaton3app.nazo.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.data.ChangelogEntry
import quiz.thaton3app.nazo.data.NAZO_CHANGELOG
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// ---------------------------------------------------------------------------
// In-app changelog: a one-time "What's new" sheet shown on Home after an
// update. The newest About changelog entry is the single source of truth for
// both the gate and the content, so beta/RC releases cannot be archived in
// About while accidentally leaving this sheet on the previous stable release.
// First-time installs (onboarding shown) mark the current id as seen silently —
// a brand-new user needs no diff.
// ---------------------------------------------------------------------------

internal data class WhatsNewContent(
    val id: String,
    val title: String,
    val points: List<String>,
)

internal fun latestWhatsNewContent(entries: List<ChangelogEntry>): WhatsNewContent {
    require(entries.isNotEmpty()) { "The in-app changelog must contain at least one release" }
    val latest = entries.first()
    return WhatsNewContent(
        id = "release-${latest.version}",
        title = "Nazo ${latest.version}",
        points = latest.changes,
    )
}

private val ACTIVE_WHATS_NEW = latestWhatsNewContent(NAZO_CHANGELOG)

/** Persisted gate for the newest stable or pre-release changelog entry. */
val CHANGELOG_ID: String = ACTIVE_WHATS_NEW.id

/** Persisted "which changelog has the user already seen" flag. */
class WhatsNewStore(context: Context) {
    private val prefs = context.getSharedPreferences("nazo_changelog", Context.MODE_PRIVATE)
    var lastSeenId: String
        get() = prefs.getString("last_seen_id", "") ?: ""
        set(value) = prefs.edit().putString("last_seen_id", value).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NazoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NazoSheetColumn() {
            NazoSheetHeader(
                icon = Icons.Outlined.NewReleases,
                title = "What's new in Nazo",
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NazoSurfaceVariant)
                    .padding(16.dp),
            ) {
                Text(
                    text = ACTIVE_WHATS_NEW.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                ACTIVE_WHATS_NEW.points.forEach { point ->
                    Row {
                        Text(
                            text = "•  ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoPrimary,
                        )
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NazoTextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
