package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
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
    data class Error(val message: String, val isModelError: Boolean = false) : GenerationState
}

@Composable
fun LoadingScreen(
    state: GenerationState,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onCancel: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    availableModels: List<String> = emptyList(),
    currentModel: String = "",
    onChangeModel: (String) -> Unit = {},
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
                                isModelError = state.isModelError,
                                availableModels = availableModels,
                                currentModel = currentModel,
                                onRetry = onRetry,
                                onUseLocal = onUseLocal,
                                onCancel = onCancel,
                                onChangeModel = onChangeModel,
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
    isModelError: Boolean,
    availableModels: List<String>,
    currentModel: String,
    onRetry: () -> Unit,
    onUseLocal: () -> Unit,
    onCancel: () -> Unit,
    onChangeModel: (String) -> Unit,
) {
    val selected = remember { mutableStateOf(currentModel) }
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
    Spacer(Modifier.height(20.dp))
    if (isModelError && availableModels.isNotEmpty()) {
        Text(
            text = "Pick a different model",
            color = NazoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        ModelPicker(
            models = availableModels,
            selected = selected.value,
            onSelect = { selected.value = it },
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(label = "Change model & retry", onClick = { onChangeModel(selected.value) })
        Spacer(Modifier.height(12.dp))
        OutlineButton(label = "Use local quiz", onClick = onUseLocal)
        Spacer(Modifier.height(12.dp))
        TextButton(label = "Cancel", onClick = onCancel)
    } else {
        PrimaryButton(label = "Retry", onClick = onRetry)
        Spacer(Modifier.height(12.dp))
        OutlineButton(label = "Use local quiz", onClick = onUseLocal)
        Spacer(Modifier.height(12.dp))
        TextButton(label = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun ModelPicker(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NazoSurfaceVariant)
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selected.isNotBlank()) selected else "Select a model",
                color = NazoTextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = NazoTextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth().background(NazoSurface),
        ) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m, color = NazoTextPrimary) },
                    onClick = { onSelect(m); expanded = false },
                )
            }
        }
    }
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
