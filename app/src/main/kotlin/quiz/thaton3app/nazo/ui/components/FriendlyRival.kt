package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.records.RivalGhostPolicy
import quiz.thaton3app.nazo.records.RivalGhostProfile
import quiz.thaton3app.nazo.records.RivalGhostResult
import quiz.thaton3app.nazo.records.RivalGhostVerdict
import quiz.thaton3app.nazo.records.rivalGhostTimeLabel
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/** Compact Home notice shown only when the currently selected Quiz setup has a ghost. */
@Composable
fun FriendlyRivalHomeCard(
    profile: RivalGhostProfile,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NazoPrimary.copy(alpha = 0.08f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RivalIcon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FRIENDLY RIVAL READY",
                style = MaterialTheme.typography.labelSmall,
                color = NazoPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Beat ${profile.correct}/${profile.totalQuestions} in ${rivalGhostTimeLabel(profile.elapsedMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Starts automatically with this exact Quiz setup.",
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
            )
        }
    }
}

/** Live score-and-pace projection for the saved local run. */
@Composable
fun FriendlyRivalRacePanel(
    profile: RivalGhostProfile,
    currentScore: Int,
    elapsedMillis: Long,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val race = RivalGhostPolicy.liveRace(profile, elapsedMillis)
    val delta = currentScore - race.projectedCorrect
    val status = when {
        race.targetMillisRemaining == 0L -> "Rival finished — your run is still yours to close."
        delta > 0 -> "You're $delta correct ahead of the saved pace."
        delta < 0 -> "Rival is ${-delta} correct ahead — plenty of run remains."
        else -> "Neck and neck on correct answers."
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NazoSurface)
            .border(1.dp, NazoPrimary.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RivalIcon(size = if (compact) 32 else 38)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FRIENDLY RIVAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "You $currentScore • Rival ${race.projectedCorrect}/${profile.correct}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = NazoTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "${rivalGhostTimeLabel(elapsedMillis)} / ${rivalGhostTimeLabel(profile.elapsedMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        LinearProgressIndicator(
            progress = race.progressFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = NazoPrimary,
            trackColor = NazoBackground,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
        )
    }
}

/** Results comparison against the ghost that existed before this run. */
@Composable
fun FriendlyRivalResultCard(
    result: RivalGhostResult,
    modifier: Modifier = Modifier,
) {
    val title = when (result.verdict) {
        RivalGhostVerdict.FirstGhost -> "Friendly rival saved"
        RivalGhostVerdict.PlayerWon -> "You beat your rival"
        RivalGhostVerdict.RivalWon -> "Your rival stays ahead"
        RivalGhostVerdict.Draw -> "Photo finish"
    }
    val message = when (result.verdict) {
        RivalGhostVerdict.FirstGhost ->
            "Replay this exact setup and your saved score-and-time pace will run beside you."
        RivalGhostVerdict.PlayerWon ->
            "This stronger run is now the friendly target for your next race."
        RivalGhostVerdict.RivalWon ->
            "Nothing was lost. Choose the same setup whenever you want another friendly race."
        RivalGhostVerdict.Draw ->
            "Same score and finish time — your saved rival remains unchanged."
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NazoPrimary.copy(alpha = 0.08f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RivalIcon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FRIENDLY RIVAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RivalRunStat(
                label = "THIS RUN",
                profile = result.current,
                modifier = Modifier.weight(1f),
            )
            result.previous?.let { previous ->
                RivalRunStat(
                    label = "SAVED RIVAL",
                    profile = previous,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = NazoTextSecondary,
        )
    }
}

@Composable
private fun RivalRunStat(
    label: String,
    profile: RivalGhostProfile,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(NazoBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NazoTextSecondary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${profile.correct}/${profile.totalQuestions} • ${rivalGhostTimeLabel(profile.elapsedMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RivalIcon(size: Int = 38) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(NazoPrimary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}
