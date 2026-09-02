package quiz.thaton3app.nazo.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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

/** Bump this whenever CHANGELOG below gains entries the user should see. */
const val CHANGELOG_ID = "2026-09-03-v7"

data class ChangeEntry(val title: String, val points: List<String>)

val CHANGELOG: List<ChangeEntry> = listOf(
    ChangeEntry(
        "New game modes",
        listOf(
            "Survival — endless questions, 3 lives, chase your longest streak",
            "Blitz — 60 seconds, answer as many as you can, works offline",
            "Versus — pass & play: two players, same questions, one winner",
            "Versus results now include a swipeable head-to-head answer review",
        ),
    ),
    ChangeEntry(
        "Smarter quizzes",
        listOf(
            "Questions you've seen recently are remembered across launches and avoided",
            "Practice deck — replay the questions you got wrong, from the Home screen",
            "If a model fails to generate, Nazo automatically retries with another model",
        ),
    ),
    ChangeEntry(
        "A fresher Home",
        listOf(
            "Compact mode selector — all five game modes in one expandable card",
            "Daily streak flame on the Home screen — keep it burning!",
            "Tap anywhere outside a text field to dismiss the keyboard",
        ),
    ),
    ChangeEntry(
        "Celebrations & sharing",
        listOf(
            "Victory confetti now comes in 5 switchable styles (Appearance → Celebrations)",
            "Each confetti style has its own sound cue (when sounds are on)",
            "Share your results as an image from the completion screens",
        ),
    ),
    ChangeEntry(
        "Updates, upgraded",
        listOf(
            "App updates now download inside the app with a live progress bar",
            "See exactly which version you're upgrading from and to",
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NazoSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(NazoTextSecondary.copy(alpha = 0.3f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
        ) {
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
