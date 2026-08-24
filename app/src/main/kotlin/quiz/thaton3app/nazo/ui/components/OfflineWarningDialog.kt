package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

enum class StartupMode { OFFLINE, ONLINE }

/**
 * Startup popup shown once per app launch (driven by the connectivity probe).
 *
 * - OFFLINE: a blocking warning — the dimmed/blurred app behind is NOT tappable
 *   (the scrim consumes clicks and does nothing) and the only way forward is the
 *   "Go Offline" button. This forces an explicit acknowledgement.
 * - ONLINE: informational — tells the user there's no online content yet and they'll
 *   play the local library. The scrim (and the "Continue" button) dismiss it.
 */
@Composable
fun OfflineWarningDialog(
    mode: StartupMode,
    onGoOffline: () -> Unit,
    onContinue: () -> Unit,
) {
    val isOffline = mode == StartupMode.OFFLINE
    val iconBg: Color
    val iconText: String
    val title: String
    val body: String
    val buttonText: String
    if (isOffline) {
        iconBg = NazoError
        iconText = "!"
        title = "You're offline"
        body = "You have limited questions and limited data to play quizzes on. " +
            "Connect to the internet if you want fresh, varied questions every time — " +
            "though our local library is huge, so you won't run out anytime soon."
        buttonText = "Go Offline"
    } else {
        iconBg = NazoPrimary
        iconText = "✓"
        title = "You're online"
        body = "There's no online content yet — the AI-generated question feature isn't " +
            "ready, so you'll be playing our built-in local library for now. We'll add " +
            "fresh, AI-generated quizzes in a future update."
        buttonText = "Continue"
    }

    // OFFLINE scrim consumes clicks and does nothing (blocks the app behind);
    // ONLINE scrim dismisses the informational popup.
    val scrimClick: () -> Unit = if (isOffline) ({}) else onContinue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = scrimClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(NazoSurface)
                .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconText,
                    color = NazoOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NazoPrimary)
                    .clickable(onClick = if (isOffline) onGoOffline else onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = buttonText,
                    color = NazoOnPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
