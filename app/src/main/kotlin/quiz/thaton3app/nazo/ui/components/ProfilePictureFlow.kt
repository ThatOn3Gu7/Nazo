package quiz.thaton3app.nazo.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import quiz.thaton3app.nazo.data.profile.AnimatedAvatarInfo
import quiz.thaton3app.nazo.data.profile.AvatarMedia
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
    /**
     * Screen name that owns this dialog, used to namespace the retained crop
     * state so it is pruned when the user leaves that screen. See
     * RetainedState.kt.
     */
    stateKeyPrefix: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(source) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Animated picks (GIF / animated WebP / MP4) never enter the bitmap+crop
    // pipeline: the sniffer below routes them here instead. [animatedInfo] is
    // set only after the pick passes every validation; [animatedFailure] is
    // the friendly explanation when it does not (the previous avatar is
    // untouched either way — nothing is written until Accept).
    var animatedInfo by remember(source) { mutableStateOf<AnimatedAvatarInfo?>(null) }
    var animatedFailure by remember(source) { mutableStateOf<String?>(null) }
    // A pick that is a video we can't use (MKV, MOV, …) gets the specific
    // "unsupported format" copy instead of the generic image wording. Set
    // lazily below, only when the static decode actually fails.
    var videoMessage by remember(source) { mutableStateOf<String?>(null) }
    val playback = rememberAvatarPlayback()

    // Which image the user is currently cropping, rather than a bare boolean.
    //
    // Rotation re-creates this dialog, so a `remember(source)` flag dropped the
    // user out of the crop step. Retaining the SOURCE IDENTITY instead of a
    // boolean keeps both behaviours: it survives the re-creation, and it still
    // resets automatically when a different image is opened, which is what
    // keying the old flag on `source` was for.
    val sourceKey = when (source) {
        is ProfileImageSource.LocalFile -> "file:" + source.file.absolutePath
        is ProfileImageSource.ContentUri -> "uri:" + source.uri
    }
    var croppingSource by rememberRetained<String?>("$stateKeyPrefix.croppingSource") { null }
    val cropping = croppingSource == sourceKey

    LaunchedEffect(source) {
        // Sniff first: an animated pick must keep its ORIGINAL bytes (a
        // decode/re-encode would flatten it to a single static frame), so it
        // goes down the probe path and the static pipeline stays untouched.
        val head = ProfileImageStore.readHead(context, source)
        if (head != null && AvatarMedia.isAnimatedPick(head)) {
            ProfileImageStore.probeAnimatedAvatar(context, source)
                .onSuccess { animatedInfo = it }
                .onFailure { animatedFailure = it.message ?: "That file couldn't be used as a profile picture." }
        } else {
            val decoded = ProfileImageStore.decodeForEditing(context, source)
            if (decoded == null) {
                loadFailed = true
                videoMessage = ProfileImageStore.unsupportedVideoMessage(context, source)
            } else {
                bitmap = decoded
            }
        }
    }

    // NOTE: the draft used to be deleted from an onDispose here. That is wrong
    // now the dialog can be re-created by a rotation: the swap disposed this
    // composable with accepted == false and deleted the temp file out from
    // under a dialog that was about to come straight back.
    //
    // Deletion therefore belongs to the CALLER, which owns the draft and is the
    // only thing that knows the difference between "the user backed out" and
    // "the layout changed shape". Every user-initiated exit goes through
    // onDismiss (Cancel and onDismissRequest both call it), and accepting
    // discards the draft below, so the only uncovered case is process death
    // mid-edit -- already handled by ProfileImageStore.clearAllDrafts() in
    // MainActivity.onCreate, which is not called on rotation.

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
                croppingSource = null
                ProfileImageStore.discardDraft(draftFile)
                onAccepted(uri)
            }.onFailure {
                loadFailed = true
            }
        }
    }

    /**
     * Accept path for animated picks: [ProfileImageStore.saveAnimatedAvatar]
     * re-validates and writes the ORIGINAL bytes (no re-encode — that would
     * flatten the animation), then hands back the URI exactly like a static
     * accept. A failure only sets [animatedFailure]: the current avatar and
     * every other preference stay untouched.
     */
    fun acceptAnimated() {
        if (saving) return
        saving = true
        scope.launch {
            val saved = ProfileImageStore.saveAnimatedAvatar(context, source)
            saving = false
            saved.onSuccess { uri ->
                croppingSource = null
                ProfileImageStore.discardDraft(draftFile)
                onAccepted(uri)
            }.onFailure {
                animatedFailure = it.message ?: "That file couldn't be used as a profile picture."
            }
        }
    }

    // What the preview area is showing. Drives AnimatedContent so each state
    // cross-fades exactly like the original bitmap/crop/failed triple did.
    val stage = when {
        animatedFailure != null -> PreviewStage.Rejected(animatedFailure!!)
        videoMessage != null -> PreviewStage.Rejected(videoMessage!!)
        animatedInfo != null -> PreviewStage.Animated(animatedInfo!!)
        loadFailed -> PreviewStage.Failed
        current == null -> PreviewStage.Loading
        cropping -> PreviewStage.Cropping
        else -> PreviewStage.Static
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
                        // Validation failures: a SHORT heading on top; the full
                        // explanation lives once, inside the preview area.
                        animatedFailure != null -> AvatarMedia.failureHeading(animatedFailure!!)
                        videoMessage != null -> AvatarMedia.failureHeading(videoMessage!!)
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
                    targetState = stage,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "profile_preview",
                ) { shown ->
                    // AnimatedContent's scope is a Box, so the branches must
                    // supply their own layout.
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (shown) {
                            is PreviewStage.Rejected -> PreviewMessage(
                                icon = true,
                                text = shown.message,
                            )

                            PreviewStage.Failed -> PreviewMessage(
                                icon = true,
                                text = "We couldn't read that image.",
                            )

                            PreviewStage.Loading -> PreviewMessage(icon = false, text = "Loading…")

                            PreviewStage.Cropping ->
                                if (current != null && cropState != null) {
                                    ProfileImageCropper(bitmap = current, state = cropState)
                                }

                            PreviewStage.Static ->
                                if (current != null) SquarePreview(current)

                            is PreviewStage.Animated -> AnimatedAvatarPreview(
                                info = shown.info,
                                source = source,
                                playback = playback,
                                onFailed = { message -> animatedFailure = message },
                            )
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
                NazoSecondaryButton(
                    onClick = {
                        if (cropping) {
                            // Leave the crop step, keeping the image.
                            cropState?.reset()
                            croppingSource = null
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = !saving,
                    muted = true,
                    // Weighted flank buttons + single-line labels: in a narrow
                    // dialog the row used to crush "Accept" until its last
                    // letter wrapped ("Accep\nt") and broke the row height.
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (cropping) "Back" else "Cancel", maxLines = 1)
                }

                if (!cropping && current != null) {
                    Spacer(Modifier.width(4.dp))
                    val cropInteraction = remember { MutableInteractionSource() }
                    val cropShape = rememberNazoRoundedCornerMorphShape(
                        interactionSource = cropInteraction,
                        restingCorner = 16.dp,
                        pressedCorner = 8.dp,
                        enabled = !saving,
                    )
                    TextButton(
                        onClick = { croppingSource = sourceKey },
                        enabled = !saving,
                        interactionSource = cropInteraction,
                        modifier = Modifier.clip(cropShape),
                    ) {
                        Icon(
                            Icons.Filled.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NazoPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Crop", color = NazoPrimary, maxLines = 1)
                    }
                }

                Spacer(Modifier.width(8.dp))
                NazoPrimaryButton(
                    onClick = {
                        when {
                            cropping -> {
                                val result = cropState?.cropToBitmap()
                                if (result != null) accept(result, alreadySquare = true)
                            }
                            // Animated picks skip crop entirely — there is no
                            // bitmap, and re-encoding would kill the animation.
                            animatedInfo != null -> acceptAnimated()
                            else -> current?.let { accept(it, alreadySquare = false) }
                        }
                    },
                    // Animated previews are ready to accept the moment the
                    // probe succeeded (animatedInfo), independently of bitmap.
                    enabled = (current != null || animatedInfo != null) && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NazoOnPrimary,
                        )
                    } else {
                        Text(if (cropping) "Done" else "Accept", maxLines = 1)
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

/**
 * Which preview the dialog is currently showing. Replaces the original
 * `Triple(hasBitmap, cropping, loadFailed)` so AnimatedContent can cross-fade
 * between static, crop, animated and the two distinct failure kinds.
 */
private sealed interface PreviewStage {
    data object Loading : PreviewStage
    data object Static : PreviewStage
    data object Cropping : PreviewStage
    /** Static decode failed (generic wording, or the unsupported-video copy). */
    data object Failed : PreviewStage
    /** An animated pick was rejected; [message] is the user-facing explanation. */
    data class Rejected(val message: String) : PreviewStage
    /** A validated animated pick, ready to preview and accept. */
    data class Animated(val info: AnimatedAvatarInfo) : PreviewStage
}

/** The playable URI for a preview source: owned file for drafts, as-is for gallery picks. */
private fun ProfileImageSource.asUriString(): String = when (this) {
    is ProfileImageSource.LocalFile -> Uri.fromFile(file).toString()
    is ProfileImageSource.ContentUri -> uri.toString()
}

/**
 * Square looping preview for an animated pick — muted looping video (letter-
 * boxed, matching the old full-size viewer's "show exactly what is saved"
 * semantics) or the animated image itself. The play icon underneath shows
 * until the first frame lands; a playback failure reports back so the dialog
 * can swap to the rejection message.
 */
@Composable
private fun AnimatedAvatarPreview(
    info: AnimatedAvatarInfo,
    source: ProfileImageSource,
    playback: AvatarPlayback,
    onFailed: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurfaceVariant)
            .onGloballyPositioned(playback::onPositioned),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = NazoTextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        val uri = source.asUriString()
        if (info.format == AvatarMedia.Format.Mp4) {
            AvatarVideoView(
                uri = uri,
                fit = true,
                playback = playback,
                modifier = Modifier.fillMaxSize(),
                onFailed = {
                    onFailed("That video couldn't be played. Try a different MP4.")
                },
            )
        } else {
            AnimatedAvatarImage(
                url = uri,
                contentDescription = "Selected picture",
                playback = playback,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                errorContent = {
                    Text(
                        text = "Couldn't load this picture",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
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
                // Long explanations wrap across several lines; without this
                // they hug the left edge of the preview box.
                textAlign = TextAlign.Center,
            )
        }
    }
}
