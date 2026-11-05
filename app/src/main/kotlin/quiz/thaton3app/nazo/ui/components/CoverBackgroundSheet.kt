package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import quiz.thaton3app.nazo.data.profile.CoverPreset
import quiz.thaton3app.nazo.data.profile.CoverPresetKind
import quiz.thaton3app.nazo.data.profile.CoverPresets
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * The unified cover-selection sheet: the user's own photo, the bundled
 * static photos, and the animated gradients — every option previewed at the
 * real banner aspect ratio ([bannerRatio], same WYSIWYG math as the gallery
 * preview dialog) and applied on tap.
 *
 * Follows the Background-effects sheet pattern exactly: Nazo modal sheet,
 * one shared frame clock drives every live gradient preview while the sheet
 * is open (frozen to the first frame under system "Remove animations"),
 * selecting applies instantly and keeps the sheet open for comparison.
 *
 * Pure presentation — persistence and precedence live in
 * `resolveCoverVisual` / `ProfilePreferences`; the caller wires the four
 * actions (pick preset, use custom photo, open gallery, remove).
 *
 * @param galleryUri the stored custom photo, if any (thumbnail + "switch
 *   back" affordance; never deleted by choosing a preset).
 * @param presetId currently applied preset id, or null for custom/default.
 * @param bannerRatio exact width/height ratio the real banner will render.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverBackgroundSheet(
    galleryUri: String?,
    presetId: String?,
    bannerRatio: Float,
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onUseCustomPhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
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
    val customSelected = hasCustom && presetId == null

    NazoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NazoSheetColumn {
            // Header — same anatomy as the Background-effects sheet.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = NazoPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Cover background",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NazoTextPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Preview each option, then tap to apply — your own photo " +
                    "is kept even while a preset is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
            Spacer(Modifier.height(16.dp))

            // --- YOUR PHOTO -------------------------------------------------
            // One card doing both jobs: apply the already-stored photo (the
            // "switch back without re-picking" promise), or open the existing
            // gallery picker when none is stored yet / to replace it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bannerRatio)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NazoSurfaceVariant)
                    .border(
                        width = if (customSelected) 2.dp else 1.dp,
                        color = if (customSelected) {
                            NazoPrimary
                        } else {
                            NazoTextSecondary.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable {
                        Haptics.soft(context)
                        if (hasCustom) onUseCustomPhoto() else onPickFromGallery()
                    },
            ) {
                if (hasCustom) {
                    SafeRemoteImage(
                        url = galleryUri!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Label chip: bottom-start.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Your photo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                    // Change chip: bottom-end, opens the existing picker.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable {
                                Haptics.soft(context)
                                onPickFromGallery()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    // Empty state: the whole card is the gallery entry point.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = NazoTextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Choose from gallery",
                                style = MaterialTheme.typography.titleMedium,
                                color = NazoTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (customSelected) {
                    SelectedBadge(modifier = Modifier.align(Alignment.TopEnd))
                }
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
                            selected = presetId == preset.id,
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
                            selected = presetId == preset.id,
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
            if (hasCustom || presetId != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
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
    Box(
        modifier = modifier
            .aspectRatio(bannerRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(NazoSurfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NazoPrimary else NazoTextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
            )
            .semantics { contentDescription = preset.label }
            .clickable(onClick = onClick),
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
        if (selected) {
            SelectedBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}
