package quiz.thaton3app.nazo.ui.components

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.profile.AvatarMedia
import quiz.thaton3app.nazo.data.profile.CoverImage
import quiz.thaton3app.nazo.data.profile.ProfileImageSource
import quiz.thaton3app.nazo.data.profile.ProfileImageStore
import quiz.thaton3app.nazo.ui.screens.coverPreviewAspectRatio
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Preview-then-accept step for a cover (banner) pick — the lean counterpart
 * to [ProfileImagePreviewDialog] for the agreed Phase-2 scope: gallery-only,
 * static-only, and NO crop tool. The frame renders at the exact banner
 * aspect ratio the Profile screen will use ([coverPreviewAspectRatio]) with
 * `ContentScale.Crop`, so Accept is WYSIWYG: what is framed here is what
 * the banner shows, with the excess clipped at the edges — never letterboxed
 * and never stretched.
 *
 * Validation order: animated sniff (reject) → oriented size floor → decode.
 * Nothing is written until Accept, so every exit leaves the current cover
 * untouched. The caller owns the picked source (retained across rotation);
 * this composable keeps only transient decode state keyed on [source].
 */
@Composable
fun ProfileCoverPreviewDialog(
    source: ProfileImageSource,
    onDismiss: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        // Sniff first: an animated pick (GIF / animated WebP — the system
        // picker's ImageOnly filter already hides videos) is rejected before
        // any decode work happens.
        val head = ProfileImageStore.readHead(context, source)
        if (head != null && AvatarMedia.isAnimatedPick(head)) {
            error = CoverImage.ANIMATED_ERROR
            return@LaunchedEffect
        }
        // Size floor against the ORIGINAL oriented dimensions — checking the
        // downsampled decode would falsely reject ≥4096-px-wide panoramas
        // whose height halves below the floor.
        val dims = ProfileImageStore.readOrientedDimensions(context, source)
        if (dims == null) {
            error = "That image couldn't be opened."
            return@LaunchedEffect
        }
        CoverImage.sizeError(dims.first, dims.second)?.let { message ->
            error = message
            return@LaunchedEffect
        }
        val decoded = ProfileImageStore.decodeForEditing(context, source)
        if (decoded == null) {
            error = "That image couldn't be opened."
        } else {
            bitmap = decoded
        }
    }

    val current = bitmap
    val configuration = LocalConfiguration.current
    val bannerRatio = coverPreviewAspectRatio(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = "Cover photo",
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
                    text = error ?: "This is how it will look on your profile.",
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
                        if (current == null) {
                            Text(
                                text = "Loading…",
                                style = MaterialTheme.typography.bodySmall,
                                color = NazoTextSecondary,
                            )
                        } else {
                            // Crop, exactly like the banner will draw it.
                            Image(
                                bitmap = current.asImageBitmap(),
                                contentDescription = "Cover photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // One action row (same layout as the avatar preview): Cancel and
            // Accept weighted evenly so neither wraps in a narrow dialog.
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
                        val accepted = current
                        if (!saving && accepted != null && error == null) {
                            saving = true
                            scope.launch {
                                val saved = ProfileImageStore.saveCover(context, accepted)
                                saving = false
                                saved.onSuccess(onAccepted).onFailure { failure ->
                                    error = failure.message
                                        ?: "That image couldn't be saved. Try a different photo."
                                }
                            }
                        }
                    },
                    enabled = current != null && error == null && !saving,
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
