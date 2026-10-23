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
 * NOTE: the id is a unique gate, not the release version. Currently a
 * pre-release id carrying the post-11.0 entries; the next release commit
 * sets its own id.
 */
const val CHANGELOG_ID = "2026-10-13-next1"

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
    ChangeEntry(
        "Sound styles",
        listOf(
            "Settings → Feedback → Sound style offers Chime, Arcade, Marimba and Bell",
            "Tap one to hear it — the choice applies to every sound in the app",
            "Chime is the original sound and stays the default",
        ),
    ),
    ChangeEntry(
        "Your profile picture, up close",
        listOf(
            "Press and hold your profile picture to see it full size — a quick tap still changes it",
            "The picture picker has a \"View current picture full size\" row and an Emoji tab",
            "A camera badge and caption explain both gestures; removing a picture asks first",
        ),
    ),
    ChangeEntry(
        "Questions say which anime they are about",
        listOf(
            "Each quiz question now carries its series name, so generic wording can never be mistaken for another anime",
        ),
    ),
    ChangeEntry(
        "Clearer text and buttons in every theme",
        listOf(
            "All 15 accent colours now meet accessibility contrast, in light and dark mode",
            "Buttons use the app’s own font, stay legible and are easier to tap",
            "Settings options show their state honestly — selected, on and off are obvious in both themes",
        ),
    ),
    ChangeEntry(
        "A tidier home, play and results flow",
        listOf(
            "Home reads top-to-bottom in one clear order, with the start button close to the fold",
            "Play screens lead with the question; the timer turns red in its final seconds and Blitz shows time draining",
            "Results celebrate behind the score instead of over it, and the review names both answers in words",
        ),
    ),
    ChangeEntry(
        "Backup & Restore, clearer and safer",
        listOf(
            "Creating, previewing and restoring report their progress in a notice under the title",
            "Your custom profile picture travels inside backups and comes back on restore",
            "A restore verifies the file first and refreshes the whole app immediately",
            "Large backups no longer freeze the screen, and interrupted writes keep the previous backup",
        ),
    ),
    ChangeEntry(
        "Nazo respects Android’s \"Remove animations\"",
        listOf(
            "The drifting background turns into a still image and celebration confetti stands down",
            "Navigation, dialogs and progress keep working normally",
        ),
    ),
    ChangeEntry(
        "What’s fixed",
        listOf(
            "A correct answer no longer flashes \"Not quite\" when moving on",
            "Survival hearts drop the moment you miss",
            "The daily streak resets when you miss a day",
            "The Daily Challenge is the same five questions all day, every time",
            "Broken AI replies are rejected and retried instead of scored on a wrong answer",
            "Pop-ups and panels survive rotating the screen",
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
