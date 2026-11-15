package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import quiz.thaton3app.nazo.ui.components.NazoDangerButton
import quiz.thaton3app.nazo.ui.components.NazoModalSheet
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.NazoSheetColumn
import quiz.thaton3app.nazo.ui.components.NazoSheetHeader
import quiz.thaton3app.nazo.ui.components.SafeRemoteImage
import quiz.thaton3app.nazo.ui.components.rememberNazoPillCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal fun AvatarPreset.storedProfilePictureValue(): String = when (this) {
    is AvatarPreset.Emoji -> "emoji:$symbol"
    is AvatarPreset.Generated -> url
}

/**
 * Bottom-sheet replacement for the old profile-picture AlertDialog.
 *
 * It deliberately owns only catalogue presentation and lazy source loading.
 * URL/gallery launchers, preview/crop, persistence, full-size viewing, and the
 * destructive confirmation remain owned by [ProfileScreen] through callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProfilePictureSheet(
    hasCurrentPicture: Boolean,
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onViewCurrent: () -> Unit,
    onOpenUrl: () -> Unit,
    onOpenGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var avatarTab by remember { mutableStateOf(0) }
    var nonce by remember { mutableStateOf("") }
    var dynamicResults by remember {
        mutableStateOf<Map<DynamicAvatarSource, AvatarSourceResult>>(emptyMap())
    }
    var loadingSource by remember { mutableStateOf<DynamicAvatarSource?>(null) }

    val staticTabs = remember(nonce) { AvatarPickerSources.staticCategories(nonce) }
    val tabs = staticTabs + AvatarPickerSources.dynamicSources.map { source ->
        source.tabTitle to (dynamicResults[source]?.items ?: emptyList())
    }
    val safeTab = avatarTab.coerceIn(0, tabs.lastIndex)
    val selectedDynamicSource = AvatarPickerSources.sourceForTab(safeTab, staticTabs.size)
    val selectedDynamicResult = selectedDynamicSource?.let(dynamicResults::get)
    val loadingDynamic = loadingSource != null

    fun refreshAvatars() {
        nonce = Random.nextLong(100_000, 999_999).toString()
        selectedDynamicSource?.let { source ->
            dynamicResults = dynamicResults - source
        }
    }

    // Opening the sheet defaults to the offline Emoji tab. A dynamic provider
    // is contacted only after its tab is deliberately selected.
    LaunchedEffect(selectedDynamicSource, nonce) {
        val source = selectedDynamicSource ?: return@LaunchedEffect
        if (dynamicResults[source] != null) return@LaunchedEffect
        loadingSource = source
        try {
            dynamicResults = dynamicResults + (source to AvatarPickerSources.fetch(source))
        } finally {
            if (loadingSource == source) loadingSource = null
        }
    }

    NazoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NazoSheetColumn {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NazoSheetHeader(
                    icon = Icons.Rounded.AccountCircle,
                    title = "Profile picture",
                    subtitle = "Choose an avatar, or add your own photo, GIF, or video.",
                    modifier = Modifier.weight(1f),
                )
                val refreshInteraction = remember { MutableInteractionSource() }
                val refreshShape = rememberNazoPillCornerMorphShape(
                    interactionSource = refreshInteraction,
                    pressedCornerPercent = 28f,
                )
                IconButton(
                    onClick = ::refreshAvatars,
                    interactionSource = refreshInteraction,
                    modifier = Modifier.clip(refreshShape),
                ) {
                    if (loadingDynamic) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh pictures",
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            AvatarSheetSectionLabel("AVATAR COLLECTION")
            PrimaryScrollableTabRow(
                selectedTabIndex = safeTab,
                containerColor = Color.Transparent,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, (name, _) ->
                    Tab(
                        selected = safeTab == index,
                        onClick = { avatarTab = index },
                        text = { Text(name, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                tabs[safeTab].second.forEach { preset ->
                    val value = preset.storedProfilePictureValue()
                    key(value) {
                        AvatarPresetTile(
                            preset = preset,
                            onClick = { onSelectPreset(value) },
                        )
                    }
                }
            }

            if (loadingDynamic && selectedDynamicSource == loadingSource) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Loading ${selectedDynamicSource?.tabTitle?.lowercase() ?: "avatars"}…",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoTextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else if (selectedDynamicResult?.unavailable == true) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "This avatar source is unavailable right now. Try refresh or choose another tab.",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (hasCurrentPicture) {
                Spacer(Modifier.height(10.dp))
                val viewInteraction = remember { MutableInteractionSource() }
                val viewShape = rememberNazoRoundedCornerMorphShape(
                    interactionSource = viewInteraction,
                    restingCorner = 16.dp,
                    pressedCorner = 8.dp,
                )
                TextButton(
                    onClick = onViewCurrent,
                    interactionSource = viewInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(viewShape),
                ) {
                    Text("View current picture full size", color = NazoPrimary)
                }
            }

            Spacer(Modifier.height(18.dp))
            AvatarSheetSectionLabel("YOUR MEDIA")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NazoSecondaryButton(
                    onClick = onOpenUrl,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add from URL", maxLines = 1)
                }
                NazoSecondaryButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Choose media", maxLines = 1)
                }
            }

            if (hasCurrentPicture) {
                Spacer(Modifier.height(10.dp))
                NazoDangerButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Remove profile picture", maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AvatarPresetTile(
    preset: AvatarPreset,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberNazoPillCornerMorphShape(
        interactionSource = interactionSource,
        pressedCornerPercent = 28f,
    )
    Surface(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(64.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        color = NazoSurfaceVariant,
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (preset) {
                is AvatarPreset.Emoji -> Text(
                    text = preset.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                )
                is AvatarPreset.Generated -> SafeRemoteImage(
                    url = preset.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    errorContent = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = NazoTextSecondary,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Small-caps section label matching the cover-background sheet. */
@Composable
private fun AvatarSheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NazoPrimary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}
