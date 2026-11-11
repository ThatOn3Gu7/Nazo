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
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
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
 * unique per set of entries. This is the final 13.0 gate.
 */
const val CHANGELOG_ID = "2026-11-11-v13.0"

data class ChangeEntry(val title: String, val points: List<String>)

val CHANGELOG: List<ChangeEntry> = listOf(
    ChangeEntry(
        "Meet Sumi Ember",
        listOf(
            "Nazo now opens with a hand-painted 謎 mark built for launchers, splash screens and themed icons",
            "Sakura, Midnight and Ocean share the same balanced kanji footprint while keeping their own pigments",
            "Existing Brush N and Pixel N shortcuts migrate safely to the new default identity",
        ),
    ),
    ChangeEntry(
        "Five modes, five distinct experiences",
        listOf(
            "Quiz stays focused while Survival, Blitz, Versus and Guessing each gain clearer live identity and results",
            "Answers now resolve with restrained glow or shake, explanations reveal cleanly and correct streaks build a combo seal",
            "A travelling wavy progress line keeps question and Blitz timing readable without moving its endpoint",
        ),
    ),
    ChangeEntry(
        "Progress that connects",
        listOf(
            "Results, Profile and Statistics now agree on your level, lifetime XP and distance to the next level",
            "The collection expands to 20 achievement badges with clearer collected and locked states",
            "Level-ups and newly earned badges appear once at completion without replaying after Review",
        ),
    ),
    ChangeEntry(
        "A sharper Home setup",
        listOf(
            "Challenge Focus gives topics a prominent home with starters and compact autocomplete",
            "Difficulty and run length now live together in one Challenge Setup card with a live summary",
            "Mode changes smoothly adapt Questions, Rounds and Practice without making the rest of Home jump",
        ),
    ),
    ChangeEntry(
        "Polished from press to transition",
        listOf(
            "Interactive surfaces now respond through their corners instead of generic shrinking or movement",
            "Navigation, onboarding dismissal and profile media hand-offs feel connected while respecting Remove animations",
            "Settings scrolling, transient messages, frozen result timing and rotation behavior received final stability fixes",
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
            NazoSheetHeader(
                icon = Icons.Outlined.NewReleases,
                title = "What's new in Nazo",
            )
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
