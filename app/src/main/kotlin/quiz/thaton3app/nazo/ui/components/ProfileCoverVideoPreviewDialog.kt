package quiz.thaton3app.nazo.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.profile.CoverVideoInfo
import quiz.thaton3app.nazo.data.profile.ProfileImageSource
import quiz.thaton3app.nazo.data.profile.ProfileImageStore
import quiz.thaton3app.nazo.ui.screens.coverPreviewAspectRatio
import quiz.thaton3app.nazo.ui.screens.coverPreviewTopExtensionDp
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Validated, muted and looping WYSIWYG preview for a system-picked cover MP4.
 * The source is only copied into app-private storage after Accept succeeds, so
 * Cancel or any validation/write failure leaves the active cover untouched.
 */
@Composable
fun ProfileCoverVideoPreviewDialog(
    source: ProfileImageSource.ContentUri,
    onDismiss: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember(source) { mutableStateOf<CoverVideoInfo?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var validating by remember(source) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        validating = true
        error = null
        ProfileImageStore.probeCoverVideo(context, source)
            .onSuccess { info = it }
            .onFailure { failure ->
                error = failure.message ?: "That video couldn't be used as a cover."
            }
        validating = false
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coverTopExtensionDp = coverPreviewTopExtensionDp(isLandscape)
    val bannerRatio = coverPreviewAspectRatio(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        isLandscape = isLandscape,
        coverTopExtensionDp = coverTopExtensionDp,
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = "Cover video",
                fontWeight = FontWeight.Bold,
                color = NazoTextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: if (validating) {
                        "Checking video compatibility…"
                    } else {
                        "This muted loop is how it will look on your profile."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) NazoError else NazoTextSecondary,
                    textAlign = TextAlign.Center,
                )
                if (error == null) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bannerRatio)
                            .clip(RoundedCornerShape(20.dp))
                            .background(NazoSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverPreviewReveal(
                            visible = info != null,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            CoverVideoBackground(
                                uri = source.uri.toString(),
                                playbackAllowed = !saving,
                                priority = CoverVideoPriority.DIALOG_PREVIEW,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (validating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = NazoTextSecondary,
                            )
                        }
                    }
                    info?.let { video ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = buildString {
                                append(video.width)
                                append('×')
                                append(video.height)
                                append("  •  ")
                                append(String.format(Locale.US, "%.1f s", video.durationMs / 1000f))
                                video.frameRate?.let { fps ->
                                    append("  •  ")
                                    append(String.format(Locale.US, "%.1f fps", fps))
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = NazoTextSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NazoSecondaryButton(
                    onClick = onDismiss,
                    enabled = !saving,
                    muted = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel", maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                NazoPrimaryButton(
                    onClick = {
                        if (!saving && info != null && error == null) {
                            saving = true
                            scope.launch {
                                val saved = ProfileImageStore.saveCoverVideo(context, source)
                                saving = false
                                saved.onSuccess(onAccepted).onFailure { failure ->
                                    error = failure.message
                                        ?: "That video couldn't be saved. Try a different video."
                                }
                            }
                        }
                    },
                    enabled = info != null && error == null && !validating && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NazoOnPrimary,
                        )
                    } else {
                        Text("Accept", maxLines = 1)
                    }
                }
            }
        },
    )
}
