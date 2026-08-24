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
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Shown when the device OS theme no longer matches the applied launcher icon.
 * Styled to match the app palette (green header + filled-green "Relaunch" /
 * outlined "Not now"), mirroring OfflineWarningDialog. "Relaunch" swaps the icon
 * and restarts the app; "Not now" defers the swap until the app exits.
 */
@Composable
fun IconThemeDialog(
    darkTarget: Boolean,
    onRelaunch: () -> Unit,
    onContinue: () -> Unit,
) {
    val title = "Update app icon?"
    val body = if (darkTarget) {
        "Your phone is now in dark mode. Relaunch to switch the icon to the dark-green variant?"
    } else {
        "Your phone is now in light mode. Relaunch to switch the icon to the light-green variant?"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onContinue),
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
                    .background(NazoPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text("◐", color = NazoOnPrimary, fontWeight = FontWeight.Bold, fontSize = 34.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NazoPrimary,
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
            // Relaunch: filled green.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NazoPrimary)
                    .clickable(onClick = onRelaunch),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Relaunch",
                    color = NazoOnPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Not now: outlined.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, NazoPrimary, RoundedCornerShape(16.dp))
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not now",
                    color = NazoPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
