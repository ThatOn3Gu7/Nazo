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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.challenge.ChallengePack
import quiz.thaton3app.nazo.challenge.ChallengePackProgress
import quiz.thaton3app.nazo.data.settings.QuizCollection
import quiz.thaton3app.nazo.data.settings.QuizCollectionPolicy
import quiz.thaton3app.nazo.ui.components.ChallengePackShelf
import quiz.thaton3app.nazo.ui.components.Haptics
import quiz.thaton3app.nazo.ui.components.QuizLaunchOrigin
import quiz.thaton3app.nazo.ui.components.rememberNazoPillCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberNazoRoundedCornerMorphShape
import quiz.thaton3app.nazo.ui.components.rememberSystemReduceMotion
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

internal enum class PlayLibrarySection { Curated, Collections }

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
    var section by rememberSaveable {
        mutableStateOf(playLibraryInitialSection(collections.size))
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
                .widthIn(max = 920.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
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

        PlayLibraryTabs(
            section = section,
            packCount = packs.size,
            collectionCount = collections.size,
            onSelected = { selected ->
                if (selected != section) Haptics.light(context)
                section = selected
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = section,
            transitionSpec = {
                fadeIn(androidx.compose.animation.core.tween(if (reduceMotion) 1 else 180))
                    .togetherWith(
                        fadeOut(androidx.compose.animation.core.tween(if (reduceMotion) 1 else 120)),
                    )
            },
            modifier = Modifier.fillMaxSize(),
            label = "playLibrarySection",
        ) { selected ->
            when (selected) {
                PlayLibrarySection.Curated -> CuratedLibrary(
                    packs = packs,
                    progress = packProgress,
                    motionAllowed = !reduceMotion,
                    onStartPack = onStartPack,
                )
                PlayLibrarySection.Collections -> CollectionsLibrary(
                    collections = collections,
                    onApply = onApplyCollection,
                    onDelete = onDeleteCollection,
                )
            }
        }
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
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interaction,
        restingCorner = 16.dp,
        pressedCorner = 8.dp,
    )
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) NazoPrimary else androidx.compose.ui.graphics.Color.Transparent, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else NazoTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else NazoTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CuratedLibrary(
    packs: List<ChallengePack>,
    progress: List<ChallengePackProgress>,
    motionAllowed: Boolean,
    onStartPack: (ChallengePack, QuizLaunchOrigin) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
    onApply: (QuizCollection) -> Unit,
    onDelete: (QuizCollection) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 920.dp)
            .background(NazoPrimary.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
            .border(1.dp, NazoPrimary.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(NazoPrimary.copy(alpha = 0.15f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = NazoPrimary)
        }
        Spacer(Modifier.size(13.dp))
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
