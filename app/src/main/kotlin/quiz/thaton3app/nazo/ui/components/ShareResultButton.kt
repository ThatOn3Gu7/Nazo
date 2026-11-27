package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/** Shared result action with asynchronous rendering and an explicit privacy promise. */
@Composable
fun ShareResultButton(
    content: ShareResultCardContent,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = ShareResultCardPalette(
        background = NazoBackground,
        surface = NazoSurface,
        primary = NazoPrimary,
        onPrimary = NazoOnPrimary,
        textPrimary = NazoTextPrimary,
        textSecondary = NazoTextSecondary,
    )
    var sharing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NazoSecondaryButton(
            onClick = {
                if (!sharing) {
                    Haptics.light(context)
                    failed = false
                    sharing = true
                    scope.launch {
                        val shared = ShareResultCard.share(
                            context = context,
                            content = content,
                            palette = palette,
                        )
                        sharing = false
                        failed = !shared
                    }
                }
            },
            enabled = !sharing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            containerColor = NazoBackground,
            muted = true,
        ) {
            if (sharing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    strokeWidth = 2.dp,
                    color = NazoTextSecondary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = NazoTextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (sharing) "Preparing Card…" else "Share Result Card",
                color = if (sharing) NazoTextSecondary else NazoTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = RESULT_CARD_PRIVACY_NOTE,
            color = NazoTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        AnimatedVisibility(
            visible = failed,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = "Couldn't prepare the image. Please try again.",
                color = NazoError,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
