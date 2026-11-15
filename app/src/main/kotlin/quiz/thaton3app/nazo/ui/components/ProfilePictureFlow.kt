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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * Accept deletes it here after persistence; the caller deletes it on a real
 * dismiss while retaining it through configuration changes. Gallery picks pass
 * null, since there is no temporary file and the original must never be touched.
 */
internal fun profileImagePreviewSizeDp(
    screenWidthDp: Int,
    screenHeightDp: Int,
): Int {
    val availableWidth = (screenWidthDp - 40).coerceAtLeast(1)
    val landscape = screenWidthDp > screenHeightDp
    val heightBound = (
        screenHeightDp.coerceAtLeast(1) * if (landscape) 44 else 42
    ) / 100
    val absoluteCeiling = if (landscape) 320 else 420
    return minOf(availableWidth, heightBound.coerceAtLeast(1), absoluteCeiling)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileImagePreviewSheet(
    source: ProfileImageSource,
    draftFile: File?,
    onDismiss: () -> Unit,
    onAccepted: (String) -> Unit,
    /**
     * Screen name that owns this sheet, used to namespace the retained crop
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
    // Rotation re-creates this sheet, so a `remember(source)` flag dropped the
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
    // now the sheet can be re-created by a rotation: the swap disposed this
    // composable with accepted == false and deleted the temp file out from
    // under a sheet that was about to come straight back.
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

    val configuration = LocalConfiguration.current
    val previewSize = profileImagePreviewSizeDp(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    ).dp
    // Do not let a swipe hide the sheet while a save is in flight. The caller
    // removes it after success, just as it did for the former dialog.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || !saving },
    )

    NazoModalSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
    ) {
        NazoSheetColumn {
            NazoSheetHeader(
                icon = Icons.Rounded.AccountCircle,
                title = if (cropping) "Crop picture" else "Preview picture",
                subtitle = when {
                    // Validation failures: a short heading belongs in the
                    // header; the full explanation remains in the preview.
                    animatedFailure != null -> AvatarMedia.failureHeading(animatedFailure!!)
                    videoMessage != null -> AvatarMedia.failureHeading(videoMessage!!)
                    loadFailed -> "That image couldn't be opened."
                    cropping -> "Drag the square to frame what will be visible."
                    else -> "This is how your picture will look."
                },
            )
            Spacer(Modifier.height(16.dp))
            val actions: @Composable (Modifier) -> Unit = { modifier ->
                ProfileImagePreviewActions(
                    cropping = cropping,
                    hasStaticImage = current != null,
                    hasAnimatedMedia = animatedInfo != null,
                    saving = saving,
                    modifier = modifier,
                    onSecondary = {
                        if (cropping) {
                            // Leave the crop step, keeping the image.
                            cropState?.reset()
                            croppingSource = null
                        } else {
                            onDismiss()
                        }
                    },
                    onCrop = { croppingSource = sourceKey },
                    onAccept = {
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
                )
            }
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                ProfileImagePreviewContent(
                    stage = stage,
                    current = current,
                    cropState = cropState,
                    source = source,
                    playback = playback,
                    previewSize = previewSize,
                    onAnimatedFailure = { message -> animatedFailure = message },
                    modifier = modifier,
                )
            }

            if (configuration.screenWidthDp > configuration.screenHeightDp) {
                // Landscape uses the abundant horizontal room so the preview
                // and actions stay together within the shorter sheet.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    preview(Modifier.weight(0.42f))
                    actions(Modifier.weight(0.58f))
                }
            } else {
                preview(Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                actions(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
        }
    }

}

@Composable
private fun ProfileImagePreviewContent(
    stage: PreviewStage,
    current: Bitmap?,
    cropState: CropState?,
    source: ProfileImageSource,
    playback: AvatarPlayback,
    previewSize: Dp,
    onAnimatedFailure: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "profile_preview",
        modifier = modifier,
    ) { shown ->
        // The preview is width-led in portrait, but height-capped in landscape
        // so the controls remain nearby instead of following an oversized square.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(previewSize),
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
                        onFailed = onAnimatedFailure,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileImagePreviewActions(
    cropping: Boolean,
    hasStaticImage: Boolean,
    hasAnimatedMedia: Boolean,
    saving: Boolean,
    onSecondary: () -> Unit,
    onCrop: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep one predictable action row in both orientations. Weighted edge
    // actions protect the labels on narrow portrait windows.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NazoSecondaryButton(
            onClick = onSecondary,
            enabled = !saving,
            muted = true,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (cropping) "Back" else "Cancel", maxLines = 1)
        }

        if (!cropping && hasStaticImage) {
            Spacer(Modifier.width(4.dp))
            val cropInteraction = remember { MutableInteractionSource() }
            val cropShape = rememberNazoRoundedCornerMorphShape(
                interactionSource = cropInteraction,
                restingCorner = 16.dp,
                pressedCorner = 8.dp,
                enabled = !saving,
            )
            TextButton(
                onClick = onCrop,
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
            onClick = onAccept,
            enabled = (hasStaticImage || hasAnimatedMedia) && !saving,
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
 * Which preview the sheet is currently showing. Replaces the original
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
 * until the first frame lands; a playback failure reports back so the sheet
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
