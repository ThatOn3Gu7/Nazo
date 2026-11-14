package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.profile.AutomaticCoverKind
import quiz.thaton3app.nazo.data.profile.AutomaticCoverState
import quiz.thaton3app.nazo.data.profile.CoverPreset
import quiz.thaton3app.nazo.data.profile.CoverPresetKind
import quiz.thaton3app.nazo.data.profile.CoverVisual
import quiz.thaton3app.nazo.data.profile.CoverPresets
import quiz.thaton3app.nazo.data.profile.CoverVideoMedia
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * The unified cover-selection sheet: an avatar-matched automatic background,
 * the user's own photo/video, bundled photos, and animated gradients — every
 * option previewed at the real banner aspect ratio ([bannerRatio], the same
 * WYSIWYG math as the gallery
 * preview dialog) and applied on tap.
 *
 * Follows the Background-effects sheet pattern exactly: Nazo modal sheet,
 * one shared frame clock drives every live gradient preview while the sheet
 * is open (frozen to the first frame under system "Remove animations"),
 * selecting applies instantly and keeps the sheet open for comparison.
 *
 * Pure presentation — persistence and precedence live in
 * `resolveCoverVisual` / `ProfilePreferences`; the caller wires selection,
 * custom-media reuse, the separate photo/video pickers, and removal.
 *
 * @param galleryUri the stored custom photo/video, if any (thumbnail +
 *   "switch back" affordance; never deleted by choosing a preset).
 * @param presetId currently applied preset id, or null for custom/default.
 * @param bannerRatio exact width/height ratio the real banner will render.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoverBackgroundSheet(
    galleryUri: String?,
    presetId: String?,
    automaticEnabled: Boolean,
    automaticState: AutomaticCoverState,
    bannerRatio: Float,
    onDismiss: () -> Unit,
    onPrepareAutomatic: () -> Unit,
    onStopPreparingAutomatic: () -> Unit,
    onSelectAutomatic: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onUseCustomMedia: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reduceMotion = rememberSystemReduceMotion()

    // One shared clock for every gradient preview card — lives only while
    // the sheet is composed (dismiss = loop cancelled = zero work).
    var previewTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                previewTime = (now - startNanos) / 1_000_000_000f
            }
        }
    }
    // Cheap: one float read per frame, and only the preview canvases read it.
    val time: () -> Float = { previewTime }

    val hasCustom = !galleryUri.isNullOrBlank()
    val customSelected = hasCustom && presetId == null && !automaticEnabled

    DisposableEffect(Unit) {
        onPrepareAutomatic()
        onDispose { onStopPreparingAutomatic() }
    }

    NazoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NazoSheetColumn {
            NazoSheetHeader(
                icon = Icons.Filled.Image,
                title = "Cover background",
                subtitle = "Preview each option, then tap to apply — your own photo or video " +
                    "is kept even while a preset is active.",
            )
            Spacer(Modifier.height(16.dp))

            // --- AUTOMATIC --------------------------------------------------
            SheetSectionLabel("AUTOMATIC")
            AutomaticCoverTile(
                state = automaticState,
                selected = automaticEnabled,
                bannerRatio = bannerRatio,
                onClick = {
                    Haptics.soft(context)
                    onSelectAutomatic()
                },
            )
            Spacer(Modifier.height(16.dp))

            // --- YOUR MEDIA -------------------------------------------------
            // The stored app-owned photo/video remains one first-class option,
            // even while a preset is active. Replacement actions are explicit
            // so Android's system picker opens with the right media filter.
            val customIsVideo = galleryUri?.let(CoverVideoMedia::isVideoUri) == true
            val customInteraction = remember { MutableInteractionSource() }
            val customShape = rememberNazoRoundedCornerMorphShape(
                interactionSource = customInteraction,
                restingCorner = 16.dp,
                pressedCorner = 8.dp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bannerRatio)
                    .clip(customShape)
                    .background(NazoSurfaceVariant, customShape)
                    .border(
                        width = if (customSelected) 2.dp else 1.dp,
                        color = if (customSelected) {
                            NazoPrimary
                        } else {
                            NazoTextSecondary.copy(alpha = 0.12f)
                        },
                        shape = customShape,
                    )
                    .clickable(
                        interactionSource = customInteraction,
                        indication = LocalIndication.current,
                    ) {
                        Haptics.soft(context)
                        if (hasCustom) onUseCustomMedia() else onPickPhoto()
                    },
            ) {
                if (hasCustom) {
                    if (customIsVideo) {
                        CoverVideoPoster(
                            uri = galleryUri!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                            }
                        }
                    } else {
                        SafeRemoteImage(
                            url = galleryUri!!,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (customIsVideo) "Your video" else "Your photo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Choose a photo or video",
                            style = MaterialTheme.typography.titleMedium,
                            color = NazoTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                AnimatedSelectedBadge(
                    visible = customSelected,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CoverPickerAction(
                    label = if (hasCustom && !customIsVideo) "Replace photo" else "Choose photo",
                    video = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Haptics.soft(context)
                        onPickPhoto()
                    },
                )
                CoverPickerAction(
                    label = if (hasCustom && customIsVideo) "Replace video" else "Choose video",
                    video = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Haptics.soft(context)
                        onPickVideo()
                    },
                )
            }

            // --- BUILT-IN PHOTOS --------------------------------------------
            Spacer(Modifier.height(16.dp))
            SheetSectionLabel("BUILT-IN PHOTOS")
            CoverPresets.IMAGES.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { preset ->
                        CoverPresetTile(
                            preset = preset,
                            selected = !automaticEnabled && presetId == preset.id,
                            bannerRatio = bannerRatio,
                            time = time,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Haptics.soft(context)
                                onSelectPreset(preset.id)
                            },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // --- ANIMATED GRADIENTS -----------------------------------------
            Spacer(Modifier.height(6.dp))
            SheetSectionLabel("ANIMATED GRADIENTS")
            CoverPresets.GRADIENTS.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { preset ->
                        CoverPresetTile(
                            preset = preset,
                            selected = !automaticEnabled && presetId == preset.id,
                            bannerRatio = bannerRatio,
                            time = time,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Haptics.soft(context)
                                onSelectPreset(preset.id)
                            },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // --- REMOVE (same destructive step as before) --------------------
            if (automaticEnabled || hasCustom || presetId != null) {
                Spacer(Modifier.height(6.dp))
                val removeInteraction = remember { MutableInteractionSource() }
                val removeShape = rememberNazoRoundedCornerMorphShape(
                    interactionSource = removeInteraction,
                    restingCorner = 14.dp,
                    pressedCorner = 7.dp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(removeShape)
                        .clickable(
                            interactionSource = removeInteraction,
                            indication = LocalIndication.current,
                        ) {
                            Haptics.soft(context)
                            onRemove()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = NazoError,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Remove cover",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoError,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AutomaticCoverTile(
    state: AutomaticCoverState,
    selected: Boolean,
    bannerRatio: Float,
    onClick: () -> Unit,
) {
    val ready = state as? AutomaticCoverState.Ready
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 16.dp,
        pressedCorner = 8.dp,
        enabled = ready != null,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(bannerRatio)
            .clip(shape)
            .background(NazoSurfaceVariant, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = shape,
            )
            .semantics { contentDescription = "Automatically match profile picture" }
            .clickable(
                enabled = ready != null,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        val previewVisual = ready?.artifact?.let { artifact ->
            when (artifact.kind) {
                AutomaticCoverKind.IMAGE -> CoverVisual.AutomaticImage(artifact.uri)
                AutomaticCoverKind.VIDEO -> CoverVisual.AutomaticVideo(
                    uri = artifact.uri,
                    avatarUri = artifact.sourceUri,
                )
            }
        } ?: CoverVisual.Default
        TransitioningCoverArtwork(
            visual = previewVisual,
            playbackAllowed = true,
            videoPriority = CoverVideoPriority.SHEET_PREVIEW,
            modifier = Modifier.fillMaxSize(),
        )

        when (val current = state) {
            is AutomaticCoverState.Ready -> Unit
            is AutomaticCoverState.Preparing,
            AutomaticCoverState.Idle -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NazoPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Preparing from your avatar…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                    )
                }
            }
            is AutomaticCoverState.Unavailable -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                        maxLines = 3,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Match avatar automatically",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AnimatedSelectedBadge(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CoverPickerAction(
    label: String,
    video: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 12.dp,
        pressedCorner = 6.dp,
    )
    Row(
        modifier = modifier
            .clip(shape)
            .background(NazoSurfaceVariant, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (video) Icons.Filled.PlayArrow else Icons.Filled.Image,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = NazoTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Small caps section kicker inside the sheet (same look as Settings labels). */
@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

/** Round check badge shown on the selected option's top-end corner. */
@Composable
private fun AnimatedSelectedBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    CoverPreviewReveal(visible = visible, modifier = modifier) {
        SelectedBadge()
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(NazoPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Selected",
            tint = NazoOnPrimary,
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * One banner-shaped option tile: photo (crop, like the real banner) or a
 * live gradient preview, plus selection border/badge. The whole tile is one
 * TalkBack target labelled by the preset name.
 */
@Composable
private fun CoverPresetTile(
    preset: CoverPreset,
    selected: Boolean,
    bannerRatio: Float,
    time: () -> Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 16.dp,
        pressedCorner = 8.dp,
    )
    Box(
        modifier = modifier
            .aspectRatio(bannerRatio)
            .clip(shape)
            .background(NazoSurfaceVariant, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = shape,
            )
            .semantics { contentDescription = preset.label }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        when (preset.kind) {
            CoverPresetKind.IMAGE -> Image(
                painter = painterResource(preset.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            CoverPresetKind.GRADIENT -> {
                // Same draw function the real banner uses, driven by the
                // sheet's shared clock (or frozen under reduce-motion).
                val colors = remember(preset) {
                    preset.gradientColors.map { Color(0xFF000000L or it) }
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCoverGradient(
                        colors = colors,
                        t = time(),
                        speed = preset.gradientSpeed,
                        seed = preset.gradientSeed,
                        style = preset.gradientStyle,
                    )
                }
            }
        }
        // Visible name chip so each preview can be identified at a glance.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(7.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        AnimatedSelectedBadge(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
