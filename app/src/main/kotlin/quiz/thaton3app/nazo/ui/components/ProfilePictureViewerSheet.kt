package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.profile.AvatarMedia
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal fun profilePictureViewerSizeDp(
    screenWidthDp: Int,
    screenHeightDp: Int,
): Int {
    val landscape = screenWidthDp > screenHeightDp
    val shortEdge = minOf(screenWidthDp, screenHeightDp).coerceAtLeast(1)
    val target = shortEdge * (if (landscape) 58 else 72) / 100
    val availableWidth = (screenWidthDp - 40).coerceAtLeast(1)
    return minOf(target.coerceAtLeast(1), availableWidth, 360)
}

/** Read-only, uncropped view of the currently saved profile picture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilePictureViewerSheet(
    uri: String,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val previewSize = profilePictureViewerSizeDp(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    ).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewPlayback = rememberAvatarPlayback()
    var previewVideoFailed by remember(uri) { mutableStateOf(false) }

    NazoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NazoSheetColumn {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                NazoSheetHeader(
                    icon = Icons.Rounded.AccountCircle,
                    title = "Current profile picture",
                    subtitle = "Shown without the circular profile crop. Animated media plays automatically.",
                    modifier = Modifier.weight(1f),
                )
                val closeInteraction = remember { MutableInteractionSource() }
                val closeShape = rememberNazoPillCornerMorphShape(
                    interactionSource = closeInteraction,
                    pressedCornerPercent = 28f,
                )
                IconButton(
                    onClick = onDismiss,
                    interactionSource = closeInteraction,
                    modifier = Modifier.clip(closeShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close full-size picture",
                        tint = NazoTextPrimary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(previewSize)
                        .clip(RoundedCornerShape(24.dp))
                        .background(NazoSurfaceVariant)
                        .onGloballyPositioned(previewPlayback::onPositioned),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        uri.startsWith("emoji:") -> Text(
                            text = uri.removePrefix("emoji:"),
                            fontSize = (previewSize.value * 0.5f).sp,
                            textAlign = TextAlign.Center,
                        )

                        AvatarMedia.displayKind(uri) == AvatarMedia.DisplayKind.Video ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (previewVideoFailed) {
                                    ViewerLoadFailure()
                                } else {
                                    AvatarVideoView(
                                        uri = uri,
                                        fit = true,
                                        playback = previewPlayback,
                                        modifier = Modifier.fillMaxSize(),
                                        onFailed = { previewVideoFailed = true },
                                    )
                                }
                            }

                        else -> AnimatedAvatarImage(
                            url = uri,
                            contentDescription = "Profile picture shown full size",
                            playback = previewPlayback,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            errorContent = { ViewerLoadFailure() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ViewerLoadFailure() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Couldn’t load this picture",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
