package quiz.thaton3app.nazo.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.profile.ProfileImageSource
import quiz.thaton3app.nazo.data.profile.ProfileImageStore
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import java.io.File

/**
 * The shared preview-then-crop step used by BOTH the gallery and URL flows.
 *
 * The brief asks for one confirmation experience rather than two, so this owns
 * the whole "look at it, optionally crop it, accept or go back" interaction and
 * the callers only supply where the image came from.
 *
 * Draft lifecycle: [draftFile] is the temporary download backing a URL image.
 * It is deleted on every exit that is not an accept — dismiss, back, or the
 * composable leaving the tree (which covers the user navigating away). Gallery
 * picks pass null, since there is no temporary file and the original must never
 * be touched.
 */
@Composable
fun ProfileImagePreviewDialog(
    source: ProfileImageSource,
    draftFile: File?,
    onDismiss: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(source) { mutableStateOf(false) }
    var cropping by remember(source) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        val decoded = ProfileImageStore.decodeForEditing(context, source)
        if (decoded == null) loadFailed = true else bitmap = decoded
    }

    // Any exit that is not an accept must not leave a temp file behind. Putting
    // this in onDispose covers dismissal, back, and the user navigating away
    // mid-edit, which an onClick handler alone would miss.
    DisposableEffect(source) {
        onDispose {
            if (!accepted) ProfileImageStore.discardDraft(draftFile)
        }
    }

    val current = bitmap
    val cropState = current?.let { rememberCropState(it) }

    /**
     * [alreadySquare] is true for a crop result. An un-cropped accept must be
     * squared first so the saved avatar matches the square preview the user
     * just approved.
     */
    fun accept(result: Bitmap, alreadySquare: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val squared =
                if (alreadySquare) result else ProfileImageStore.centerCropSquare(result)
            val saved = ProfileImageStore.saveAvatar(context, squared)
            saving = false
            saved.onSuccess { uri ->
                // Mark accepted BEFORE dismissing so onDispose does not delete
                // the draft we just consumed.
                accepted = true
                ProfileImageStore.discardDraft(draftFile)
                onAccepted(uri)
            }.onFailure {
                loadFailed = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                text = if (cropping) "Crop picture" else "Preview",
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
                    text = when {
                        loadFailed -> "That image couldn't be opened."
                        cropping -> "Pinch to zoom and drag to reposition. The circle shows what will be visible."
                        else -> "This is how your picture will look."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                AnimatedContent(
                    targetState = Triple(current != null, cropping, loadFailed),
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "profile_preview",
                ) { (hasBitmap, isCropping, failed) ->
                    // AnimatedContent's scope is a Box, so the branches must
                    // supply their own layout.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            failed -> PreviewMessage(
                                icon = true,
                                text = "We couldn't read that image.",
                            )

                            !hasBitmap -> PreviewMessage(icon = false, text = "Loading…")

                            isCropping && current != null && cropState != null ->
                                ProfileImageCropper(bitmap = current, state = cropState)

                            current != null -> SquarePreview(current)
                        }
                    }
                }
            }
        },
        confirmButton = {
            // One action bar instead of splitting buttons between
            // confirmButton and dismissButton: AlertDialog lays those out as a
            // FlowRow, so with three actions they wrapped and drifted around.
            // Everything lives here, in a fixed order, and dismissButton is
            // left unset.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (cropping) {
                            // Leave the crop step, keeping the image.
                            cropState?.reset()
                            cropping = false
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = !saving,
                ) {
                    Text(if (cropping) "Back" else "Cancel", color = NazoTextSecondary)
                }

                if (!cropping && current != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { cropping = true }, enabled = !saving) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NazoPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Crop", color = NazoPrimary)
                    }
                }

                Spacer(Modifier.width(8.dp))
                NazoPrimaryButton(
                    onClick = {
                        if (cropping) {
                            val result = cropState?.cropToBitmap()
                            if (result != null) accept(result, alreadySquare = true)
                        } else {
                            current?.let { accept(it, alreadySquare = false) }
                        }
                    },
                    enabled = current != null && !saving,
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NazoOnPrimary,
                        )
                    } else {
                        Text(if (cropping) "Done" else "Accept")
                    }
                }
            }
        },
    )
}

@Composable
private fun SquarePreview(bitmap: Bitmap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Selected picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PreviewMessage(icon: Boolean, text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = NazoError,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = NazoPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
        }
    }
}
