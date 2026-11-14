package quiz.thaton3app.nazo.ui.components

import android.os.SystemClock
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Shared state for a game's quit confirmation and its timer pause.
 *
 * Dialog visibility and timer suspension are deliberately separate: pressing
 * Quit hides the dialog for its exit animation but must NOT resume gameplay in
 * the brief interval before navigation completes. Pressing Stay (or dismissing
 * with back/scrim) closes the dialog and returns the exact paused duration so a
 * host-level elapsed clock can exclude it too.
 */
internal class GameplayQuitPauseState(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    var dialogVisible: Boolean by mutableStateOf(false)
        private set

    var timerPaused: Boolean by mutableStateOf(false)
        private set

    var accumulatedPauseMillis: Long by mutableLongStateOf(0L)
        private set

    private var pauseStartedAtMillis: Long = 0L

    fun requestQuit() {
        if (timerPaused) return
        pauseStartedAtMillis = nowMillis()
        timerPaused = true
        dialogVisible = true
    }

    /** Resume gameplay and return the duration of this completed pause. */
    fun stay(): Long {
        dialogVisible = false
        if (!timerPaused) return 0L
        val pausedMillis = (nowMillis() - pauseStartedAtMillis).coerceAtLeast(0L)
        accumulatedPauseMillis += pausedMillis
        pauseStartedAtMillis = 0L
        timerPaused = false
        return pausedMillis
    }

    /** Hide the confirmation while keeping every game clock suspended. */
    fun hideForQuit() {
        dialogVisible = false
    }
}

@Composable
internal fun rememberGameplayQuitPauseState(): GameplayQuitPauseState =
    remember { GameplayQuitPauseState() }

/** Monotonic elapsed time with only pauses completed since this timer began removed. */
internal fun elapsedGameplayMillis(
    startedAtMillis: Long,
    nowMillis: Long,
    accumulatedPauseMillis: Long,
    pauseMillisAtStart: Long,
): Long = (
    nowMillis - startedAtMillis -
        (accumulatedPauseMillis - pauseMillisAtStart).coerceAtLeast(0L)
).coerceAtLeast(0L)

/**
 * One quit-confirmation interaction for every game. Portrait uses the shared
 * restrained dialog motion; short landscape windows use the standard draggable
 * Nazo sheet, so the actions can never be clipped below the fold.
 *
 * Back and scrim taps mean "stay". Only the explicit destructive button leaves
 * the current game, keeping navigation and predictive-back behaviour reliable.
 */
@Composable
fun NazoQuitDialog(
    visible: Boolean,
    title: String,
    message: String,
    onStay: () -> Unit,
    /** Hides the dialog after Quit without resuming the suspended game clock. */
    onHideForQuit: () -> Unit,
    onQuit: () -> Unit,
) {
    val context = LocalContext.current
    val landscape = isLandscape()
    var quitPending by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) quitPending = false
    }

    NazoAdaptiveDialog(
        visible = visible,
        onDismiss = onStay,
        dismissible = true,
        onDismissed = { if (quitPending) onQuit() },
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (landscape) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .widthIn(max = 340.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(NazoSurface)
                            .border(
                                1.5.dp,
                                NazoTextSecondary.copy(alpha = 0.3f),
                                RoundedCornerShape(32.dp),
                            )
                    }
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NazoError),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = NazoOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NazoTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NazoSecondaryButton(
                    onClick = {
                        Haptics.light(context)
                        onStay()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    muted = true,
                ) {
                    Text(
                        text = "Stay",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                NazoDangerButton(
                    onClick = {
                        if (!quitPending) {
                            quitPending = true
                            Haptics.light(context)
                            // NazoAdaptiveDialog calls onDismissed only after
                            // the portrait fade or landscape hide completes.
                            // Keep the game clock paused during that exit.
                            onHideForQuit()
                        }
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    enabled = !quitPending,
                ) {
                    Text(
                        text = "Quit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
