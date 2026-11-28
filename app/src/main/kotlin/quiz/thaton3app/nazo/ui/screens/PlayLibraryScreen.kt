package quiz.thaton3app.nazo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.challenge.ChallengePack
import quiz.thaton3app.nazo.challenge.ChallengePackProgress
import quiz.thaton3app.nazo.data.settings.QuizCollection
import quiz.thaton3app.nazo.data.settings.QuizCollectionPolicy
import quiz.thaton3app.nazo.ui.components.ChallengePackGrid
import quiz.thaton3app.nazo.ui.components.ChallengePackShelf
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.QuizLaunchOrigin
import quiz.thaton3app.nazo.ui.components.isLandscape
import quiz.thaton3app.nazo.ui.components.rememberNazoPillCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberSystemReduceMotion
import quiz.thaton3app.nazo.ui.theme.NazoNavBar
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal enum class PlayLibrarySection { Curated, Collections }

internal enum class PlayLibraryNavigationPlacement { TopTabs, LeftRail }

internal fun playLibraryNavigationPlacement(landscape: Boolean): PlayLibraryNavigationPlacement =
    if (landscape) PlayLibraryNavigationPlacement.LeftRail
    else PlayLibraryNavigationPlacement.TopTabs

internal fun playLibraryInitialSection(collectionCount: Int): PlayLibrarySection =
    if (collectionCount > 0) PlayLibrarySection.Collections else PlayLibrarySection.Curated

/**
 * Reusable play belongs here rather than above Home's game builder. Curated
 * packs and player-created Collections share one destination but keep separate
 * labels, tabs, actions, and visual identity.
 */
@Composable
fun PlayLibraryScreen(
    packs: List<ChallengePack>,
    packProgress: List<ChallengePackProgress>,
    collections: List<QuizCollection>,
    onStartPack: (ChallengePack, QuizLaunchOrigin) -> Unit,
    onApplyCollection: (QuizCollection) -> Unit,
    onDeleteCollection: (QuizCollection) -> Unit,
    onBackClick: () -> Unit,
) {
    BackHandler(onBack = onBackClick)
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = rememberSystemReduceMotion()
    val landscape = isLandscape()
    var section by rememberSaveable {
        mutableStateOf(playLibraryInitialSection(collections.size))
    }
    val selectSection: (PlayLibrarySection) -> Unit = { selected ->
        if (selected != section) Haptics.light(context)
        section = selected
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (landscape) 1_200.dp else 920.dp)
                .padding(
                    horizontal = 20.dp,
                    vertical = if (landscape) 8.dp else 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backShape = rememberNazoPillCornerMorphShape(backInteraction)
            IconButton(
                onClick = {
                    Haptics.light(context)
                    onBackClick()
                },
                interactionSource = backInteraction,
                modifier = Modifier
                    .size(44.dp)
                    .clip(backShape)
                    .background(NazoSurface, backShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = NazoTextPrimary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Play Library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Curated journeys and your saved setups",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(NazoPrimary.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CollectionsBookmark,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (playLibraryNavigationPlacement(landscape) ==
            PlayLibraryNavigationPlacement.LeftRail
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1_200.dp)
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(168.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    PlayLibraryRail(
                        section = section,
                        packCount = packs.size,
                        collectionCount = collections.size,
                        onSelected = selectSection,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(14.dp))
                PlayLibraryContent(
                    section = section,
                    packs = packs,
                    packProgress = packProgress,
                    collections = collections,
                    motionAllowed = !reduceMotion,
                    landscape = true,
                    onStartPack = onStartPack,
                    onApplyCollection = onApplyCollection,
                    onDeleteCollection = onDeleteCollection,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            PlayLibraryTabs(
                section = section,
                packCount = packs.size,
                collectionCount = collections.size,
                onSelected = selectSection,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))
            PlayLibraryContent(
                section = section,
                packs = packs,
                packProgress = packProgress,
                collections = collections,
                motionAllowed = !reduceMotion,
                landscape = false,
                onStartPack = onStartPack,
                onApplyCollection = onApplyCollection,
                onDeleteCollection = onDeleteCollection,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlayLibraryContent(
    section: PlayLibrarySection,
    packs: List<ChallengePack>,
    packProgress: List<ChallengePackProgress>,
    collections: List<QuizCollection>,
    motionAllowed: Boolean,
    landscape: Boolean,
    onStartPack: (ChallengePack, QuizLaunchOrigin) -> Unit,
    onApplyCollection: (QuizCollection) -> Unit,
    onDeleteCollection: (QuizCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            fadeIn(androidx.compose.animation.core.tween(if (motionAllowed) 180 else 1))
                .togetherWith(
                    fadeOut(androidx.compose.animation.core.tween(if (motionAllowed) 120 else 1)),
                )
        },
        modifier = modifier,
        label = "playLibrarySection",
    ) { selected ->
        when (selected) {
            PlayLibrarySection.Curated -> CuratedLibrary(
                packs = packs,
                progress = packProgress,
                motionAllowed = motionAllowed,
                landscape = landscape,
                onStartPack = onStartPack,
            )
            PlayLibrarySection.Collections -> CollectionsLibrary(
                collections = collections,
                landscape = landscape,
                onApply = onApplyCollection,
                onDelete = onDeleteCollection,
            )
        }
    }
}

@Composable
private fun PlayLibraryRail(
    section: PlayLibrarySection,
    packCount: Int,
    collectionCount: Int,
    onSelected: (PlayLibrarySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .background(NazoNavBar, shape)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LibraryTab(
            label = "Curated",
            count = packCount,
            selected = section == PlayLibrarySection.Curated,
            icon = Icons.Filled.Explore,
            onClick = { onSelected(PlayLibrarySection.Curated) },
            centered = false,
            modifier = Modifier.fillMaxWidth(),
        )
        LibraryTab(
            label = "My Collections",
            count = collectionCount,
            selected = section == PlayLibrarySection.Collections,
            icon = Icons.Filled.Bookmark,
            onClick = { onSelected(PlayLibrarySection.Collections) },
            centered = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlayLibraryTabs(
    section: PlayLibrarySection,
    packCount: Int,
    collectionCount: Int,
    onSelected: (PlayLibrarySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(NazoSurfaceVariant, RoundedCornerShape(20.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryTab(
            label = "Curated",
            count = packCount,
            selected = section == PlayLibrarySection.Curated,
            icon = Icons.Filled.Explore,
            onClick = { onSelected(PlayLibrarySection.Curated) },
            modifier = Modifier.weight(1f),
        )
        LibraryTab(
            label = "My Collections",
            count = collectionCount,
            selected = section == PlayLibrarySection.Collections,
            icon = Icons.Filled.Bookmark,
            onClick = { onSelected(PlayLibrarySection.Collections) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LibraryTab(
    label: String,
    count: Int,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interaction,
        restingCorner = 16.dp,
        pressedCorner = 8.dp,
    )
    Row(
        modifier = modifier
            .height(if (centered) 52.dp else 58.dp)
            .clip(shape)
            .background(if (selected) NazoPrimary else androidx.compose.ui.graphics.Color.Transparent, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else NazoTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = if (centered) "$label · $count" else label,
            modifier = if (centered) Modifier else Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else NazoTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!centered) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                } else {
                    NazoTextSecondary
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CuratedLibrary(
    packs: List<ChallengePack>,
    progress: List<ChallengePackProgress>,
    motionAllowed: Boolean,
    landscape: Boolean,
    onStartPack: (ChallengePack, QuizLaunchOrigin) -> Unit,
) {
    if (landscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        ) {
            LibraryIntro(
                icon = Icons.Filled.Explore,
                eyebrow = "CURATED • OFFLINE",
                title = "Quality-reviewed journeys",
                body = "Fixed authored lineups with their own progress, results, and mastery.",
                compact = true,
            )
            Spacer(Modifier.height(10.dp))
            ChallengePackGrid(
                packs = packs,
                progress = progress,
                motionAllowed = motionAllowed,
                onStart = onStartPack,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
    ) {
        item {
            LibraryIntro(
                icon = Icons.Filled.Explore,
                eyebrow = "CURATED • OFFLINE",
                title = "Quality-reviewed journeys",
                body = "Fixed authored lineups with their own progress, results, and mastery.",
                compact = landscape,
            )
            Spacer(Modifier.height(16.dp))
        }
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ChallengePackShelf(
                    packs = packs,
                    progress = progress,
                    motionAllowed = motionAllowed,
                    onStart = onStartPack,
                    modifier = Modifier.widthIn(max = 920.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionsLibrary(
    collections: List<QuizCollection>,
    landscape: Boolean,
    onApply: (QuizCollection) -> Unit,
    onDelete: (QuizCollection) -> Unit,
) {
    if (landscape) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                bottom = 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(
                key = "collections-intro",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryIntro(
                        icon = Icons.Filled.Bookmark,
                        eyebrow = "${collections.size}/${QuizCollectionPolicy.MAX_COLLECTIONS} SAVED",
                        title = "Your reusable setups",
                        body = "Choose one to return Home with its setup ready.",
                        compact = true,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
            if (collections.isEmpty()) {
                item(
                    key = "collections-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    EmptyCollectionsCard(modifier = Modifier.fillMaxWidth())
                }
            } else {
                gridItems(collections, key = QuizCollection::id) { collection ->
                    CollectionLibraryCard(
                        collection = collection,
                        onApply = { onApply(collection) },
                        onDelete = { onDelete(collection) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
                LibraryIntro(
                    icon = Icons.Filled.Bookmark,
                    eyebrow = "${collections.size}/${QuizCollectionPolicy.MAX_COLLECTIONS} SAVED",
                    title = "Your reusable setups",
                    body = "Choose one to return Home with its mode, topic, difficulty, and length ready.",
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (collections.isEmpty()) {
            item {
                EmptyCollectionsCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
                )
            }
        } else {
            items(collections, key = QuizCollection::id) { collection ->
                CollectionLibraryCard(
                    collection = collection,
                    onApply = { onApply(collection) },
                    onDelete = { onDelete(collection) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryIntro(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    eyebrow: String,
    title: String,
    body: String,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 920.dp)
            .background(NazoPrimary.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
            .border(1.dp, NazoPrimary.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 38.dp else 46.dp)
                .background(NazoPrimary.copy(alpha = 0.15f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = NazoPrimary)
        }
        Spacer(Modifier.size(if (compact) 10.dp else 13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
                maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollectionLibraryCard(
    collection: QuizCollection,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val interaction = remember(collection.id) { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interaction,
        restingCorner = 21.dp,
        pressedCorner = 10.dp,
    )
    Row(
        modifier = modifier
            .clip(shape)
            .background(NazoSurface, shape)
            .border(1.dp, NazoPrimary.copy(alpha = 0.22f), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
            ) {
                Haptics.light(context)
                onApply()
            }
            .padding(start = 15.dp, top = 10.dp, end = 7.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(NazoPrimary.copy(alpha = 0.13f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = NazoPrimary,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quizCollectionHeadline(collection),
                style = MaterialTheme.typography.titleMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quizCollectionDetail(collection),
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "USE SETUP",
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(18.dp),
        )
        IconButton(
            onClick = {
                Haptics.light(context)
                onDelete()
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete ${quizCollectionHeadline(collection)} collection",
                tint = NazoTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyCollectionsCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(NazoSurface, RoundedCornerShape(22.dp))
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "No saved Collections yet",
            style = MaterialTheme.typography.titleMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Build a game on Home, then use its bookmark to save the setup here.",
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
        )
    }
}
