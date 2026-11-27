package quiz.thaton3app.nazo.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.challenge.ChallengePack
import quiz.thaton3app.nazo.challenge.ChallengePackProgress
import quiz.thaton3app.nazo.challenge.ChallengePackResult
import quiz.thaton3app.nazo.challenge.ChallengePackStyle
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

private fun ChallengePackStyle.icon(): ImageVector = when (this) {
    ChallengePackStyle.SHINOBI -> Icons.Filled.Spa
    ChallengePackStyle.GRAND_LINE -> Icons.Filled.Sailing
    ChallengePackStyle.TITAN -> Icons.Filled.Security
    ChallengePackStyle.HASHIRA -> Icons.Filled.LocalFireDepartment
}

/** Always-available Home shelf for shipped, reviewed challenge packs. */
@Composable
fun ChallengePackShelf(
    packs: List<ChallengePack>,
    progress: List<ChallengePackProgress>,
    motionAllowed: Boolean,
    onStart: (ChallengePack, QuizLaunchOrigin) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CHALLENGE PACKS",
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "CURATED • OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(packs, key = ChallengePack::id) { pack ->
                ChallengePackCard(
                    pack = pack,
                    progress = progress.firstOrNull { it.packId == pack.id },
                    motionAllowed = motionAllowed,
                    onStart = onStart,
                )
            }
        }
    }
}

@Composable
private fun ChallengePackCard(
    pack: ChallengePack,
    progress: ChallengePackProgress?,
    motionAllowed: Boolean,
    onStart: (ChallengePack, QuizLaunchOrigin) -> Unit,
) {
    var bounds by remember(pack.id) { mutableStateOf(Rect.Zero) }
    val interactionSource = remember(pack.id) { MutableInteractionSource() }
    val indication = LocalIndication.current
    val shape = rememberNazoRoundedCornerMorphShape(
        interactionSource = interactionSource,
        restingCorner = 24.dp,
        pressedCorner = 12.dp,
    )
    val targetProgress = (progress?.bestScore ?: 0).toFloat() / pack.questionCount
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (motionAllowed) tween(420) else snap(),
        label = "challengePackProgress_${pack.id}",
    )
    Column(
        modifier = Modifier
            .width(286.dp)
            .clip(shape)
            .background(NazoSurface.copy(alpha = 0.92f), shape)
            .border(
                width = if (progress?.mastered == true) 2.dp else 1.dp,
                color = if (progress?.mastered == true) NazoSuccess else NazoPrimary.copy(alpha = 0.24f),
                shape = shape,
            )
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = {
                    onStart(
                        pack,
                        QuizLaunchOrigin(
                            left = bounds.left,
                            top = bounds.top,
                            width = bounds.width,
                            height = bounds.height,
                        ),
                    )
                },
            )
            .semantics {
                contentDescription = buildString {
                    append(pack.title)
                    append(", ${pack.questionCount} question curated pack")
                    progress?.let { append(", best ${it.bestScore} of ${it.questionCount}") }
                }
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = pack.style.icon(),
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    maxLines = 1,
                )
                Text(
                    text = pack.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress?.mastered == true) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Mastered",
                    tint = NazoSuccess,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = pack.description,
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (progress?.mastered == true) NazoSuccess else NazoPrimary,
            trackColor = NazoBackground,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress?.let {
                    "BEST ${it.bestScore}/${it.questionCount} • ${it.completionCount} CLEAR${if (it.completionCount == 1) "" else "S"}"
                } ?: "UNPLAYED • ${pack.questionCount} QUESTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (progress == null) "START" else "REPLAY",
                style = MaterialTheme.typography.labelMedium,
                color = NazoPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Compact in-run identity: pack play never looks like an ordinary generated Quiz. */
@Composable
fun ChallengePackRacePanel(
    pack: ChallengePack,
    currentScore: Int,
    currentQuestionIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NazoPrimary.copy(alpha = 0.10f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = pack.style.icon(),
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pack.eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
            Text(
                text = pack.title,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "$currentScore PTS • ${currentQuestionIndex + 1}/${pack.questionCount}",
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Pack-specific result chapter with durable best and mastery state. */
@Composable
fun ChallengePackResultCard(
    result: ChallengePackResult,
    modifier: Modifier = Modifier,
) {
    val progress = result.progress
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurfaceVariant)
            .border(
                width = if (progress.mastered) 2.dp else 1.dp,
                color = if (progress.mastered) NazoSuccess else NazoPrimary.copy(alpha = 0.26f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (progress.mastered) Icons.Filled.Check else result.pack.style.icon(),
                    contentDescription = null,
                    tint = if (progress.mastered) NazoSuccess else NazoPrimary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        result.firstMastery -> "PACK MASTERED"
                        result.newBest -> "NEW PACK BEST"
                        else -> "PACK PROGRESS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.mastered) NazoSuccess else NazoPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = result.pack.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${progress.lastScore}/${progress.questionCount}",
                style = MaterialTheme.typography.headlineSmall,
                color = NazoTextPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress.bestScore.toFloat() / progress.questionCount },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = if (progress.mastered) NazoSuccess else NazoPrimary,
            trackColor = NazoBackground,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = buildString {
                append("Best ${progress.bestScore}/${progress.questionCount}")
                append(" • ${progress.completionCount} completion")
                if (progress.completionCount != 1) append('s')
                result.previousBest?.takeIf { result.newBest }?.let { append(" • Previous best $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = result.pack.curatorNote,
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
        )
    }
}
