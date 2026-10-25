package quiz.thaton3app.nazo.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// ---------------------------------------------------------------------------
// In-app changelog: a one-time "What's new" sheet shown on Home after an
// update. Gated on CHANGELOG_ID (not versionCode) so it also works when
// testing debug builds: bump the id together with the entries and every
// device shows the sheet exactly once. First-time installs (onboarding shown)
// mark the current id as seen silently — a brand-new user needs no diff.
// ---------------------------------------------------------------------------

/**
 * Bump this whenever CHANGELOG below gains entries the user should see.
 * NOTE: the id is a unique gate, not the release version; it only has to be
 * unique per set of entries. This is the 12.0-beta01 gate.
 */
const val CHANGELOG_ID = "2026-10-25-v12.0-beta01"

data class ChangeEntry(val title: String, val points: List<String>)

val CHANGELOG: List<ChangeEntry> = listOf(
    ChangeEntry(
        "Back gestures show where they'll take you",
        listOf(
            "Swipe from the screen edge and the current screen slides away with your finger, revealing the screen behind it",
            "Release to go back — or slide it back to change your mind",
            "Works with gesture navigation on Android 13 and newer; buttons and older devices keep the usual instant back",
        ),
    ),
)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        tint = NazoPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "What's new in Nazo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NazoTextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            CHANGELOG.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NazoSurfaceVariant)
                        .padding(16.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    entry.points.forEach { point ->
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
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
