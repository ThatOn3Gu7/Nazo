package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Drives the quiz-generation screen. The host (NazoApp) keeps this state in memory and
 * decides navigation. On a generation failure we surface an explicit Error state with
 * Retry / Use Local — we never silently fall back to the local bank.
 */
sealed interface GenerationState {
    data object Idle : GenerationState
    data class Loading(val providerModel: String) : GenerationState
    data class Error(val message: String) : GenerationState
}

@Composable
fun LoadingScreen(
    state: GenerationState,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onCancel: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NazoSurface),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(NazoSurface)
                        .border(1.5.dp, NazoTextSecondary.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                        .padding(28.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (state) {
                            is GenerationState.Loading -> LoadingContent(
                                providerModel = state.providerModel,
                                onCancel = onCancel,
                            )
                            is GenerationState.Error -> ErrorContent(
                                message = state.message,
                                onRetry = onRetry,
                                onUseLocal = onUseLocal,
                                onCancel = onCancel,
                            )
                            GenerationState.Idle -> LoadingContent(
                                providerModel = "",
                                onCancel = onCancel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(providerModel: String, onCancel: () -> Unit) {
    Text(
        text = "Generating your quiz…",
        color = NazoTextPrimary,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    if (providerModel.isNotBlank()) {
        Text(
            text = "Using $providerModel",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(22.dp))
    CircularProgressIndicator(
        color = NazoPrimary,
        strokeWidth = 3.dp,
        modifier = Modifier.size(38.dp),
    )
    Spacer(Modifier.height(24.dp))
    TextButton(label = "Cancel", onClick = onCancel)
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "Couldn't generate quiz",
        color = NazoError,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = message,
        color = NazoTextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(label = "Retry", onClick = onRetry)
    Spacer(Modifier.height(12.dp))
    OutlineButton(label = "Use local quiz", onClick = onUseLocal)
    Spacer(Modifier.height(12.dp))
    TextButton(label = "Cancel", onClick = onCancel)
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NazoPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoOnPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, NazoPrimary, RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
