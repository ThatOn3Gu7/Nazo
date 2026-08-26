package quiz.thaton3app.nazo.ui.screens

import quiz.thaton3app.nazo.ui.components.rememberHapticBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoErrorBg
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess
import quiz.thaton3app.nazo.ui.theme.NazoSuccessBg
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPlaceholder
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

private enum class KeyStatus(val label: String) {
    VALID("Valid Key"),
    NOT_CONFIGURED("Not Configured"),
    ERROR("Key Error"),
}

private data class ProviderUiState(
    val id: String,
    val name: String,
    val avatarLetter: String,
    val status: KeyStatus,
    val apiKey: String = "",
    val model: String = "",
    val models: List<String> = emptyList(),
)

// Clean initial empty state for all providers
private fun defaultProviders() = listOf(
    ProviderUiState(
        id = "gemini",
        name = "Google Gemini",
        avatarLetter = "G",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("gemini-2.5-flash", "gemini-1.5-pro"),
    ),
    ProviderUiState(
        id = "claude",
        name = "Anthropic Claude",
        avatarLetter = "A",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("claude-3-5-sonnet", "claude-3-haiku"),
    ),
    ProviderUiState(
        id = "chatgpt",
        name = "OpenAI ChatGPT",
        avatarLetter = "O",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("gpt-4o", "gpt-4o-mini"),
    ),
    ProviderUiState(
        id = "openrouter",
        name = "OpenRouter",
        avatarLetter = "R",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("anthropic/claude-3.5-sonnet", "deepseek/deepseek-chat"),
    ),
    ProviderUiState(
        id = "deepseek",
        name = "DeepSeek",
        avatarLetter = "D",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("deepseek-chat", "deepseek-coder"),
    ),
    ProviderUiState(
        id = "mistral",
        name = "Mistral AI",
        avatarLetter = "M",
        status = KeyStatus.NOT_CONFIGURED,
        models = listOf("mistral-large-latest", "mistral-small-latest"),
    ),
)

// Load the persisted key/model for each provider so the UI reflects what's stored on device.
private fun initialProviders(store: ApiKeyStore): List<ProviderUiState> =
    defaultProviders().map { p ->
        val storedKey = store.getKey(p.id).orEmpty()
        val storedModel = store.getModel(p.id) ?: p.models.firstOrNull().orEmpty()
        val status = if (storedKey.isNotBlank()) KeyStatus.VALID else KeyStatus.NOT_CONFIGURED
        p.copy(apiKey = storedKey, model = storedModel, status = status)
    }

@Composable
fun AiProviderScreen(
    apiKeyStore: ApiKeyStore,
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    var providers by remember { mutableStateOf(initialProviders(apiKeyStore)) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            ScreenHeader(title = "AI & Model Configuration", onBackClick = onBackClick)
            Spacer(Modifier.height(20.dp))
            providers.forEachIndexed { index, provider ->
                ProviderCard(
                    provider = provider,
                    expanded = expandedId == provider.id,
                    onToggleExpand = {
                        expandedId = if (expandedId == provider.id) null else provider.id
                    },
                    onApiKeyChange = { newKey ->
                        providers = providers.toMutableList().also { 
                            val updatedStatus = if (newKey.isNotBlank()) KeyStatus.VALID else KeyStatus.NOT_CONFIGURED
                            it[index] = it[index].copy(apiKey = newKey, status = updatedStatus) 
                        }
                    },
                    onModelChange = { newModel ->
                        providers = providers.toMutableList().also { it[index] = it[index].copy(model = newModel) }
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            SaveButton(onClick = {
                providers.forEach { p ->
                    apiKeyStore.saveKey(p.id, p.apiKey)
                    apiKeyStore.saveModel(p.id, p.model)
                }
                onSaved()
            })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = rememberHapticBack(onBackClick),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NazoTextPrimary)
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderUiState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NazoPillUnselected),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.avatarLetter,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(provider.status)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = NazoTextSecondary,
            )
        }
        if (expanded) {
            HorizontalDivider(color = NazoBackground, thickness = 1.dp)
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Model", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
                Spacer(Modifier.height(8.dp))
                ModelDropdown(models = provider.models, selected = provider.model, onSelect = onModelChange)
                Spacer(Modifier.height(16.dp))
                Text("API Key", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
                Spacer(Modifier.height(8.dp))
                ApiKeyField(value = provider.apiKey, onValueChange = onApiKeyChange)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stored Securely on device", style = MaterialTheme.typography.bodyMedium, color = NazoTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: KeyStatus) {
    val (bg, fg) = when (status) {
        KeyStatus.VALID -> NazoSuccessBg to NazoSuccess
        KeyStatus.NOT_CONFIGURED -> NazoPillUnselected to NazoTextPrimary
        KeyStatus.ERROR -> NazoErrorBg to NazoError
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text = status.label, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
private fun ModelDropdown(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NazoSurfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = selected.ifEmpty { models.firstOrNull() ?: "Select a model" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected.isEmpty()) NazoTextPlaceholder else NazoTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(text = { Text(m) }, onClick = { onSelect(m); expanded = false })
            }
        }
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NazoSurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = NazoTextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = NazoTextPrimary),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { visible = !visible }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Hide API key" else "Show API key",
                tint = NazoTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NazoPrimary)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "Save Changes",
            style = MaterialTheme.typography.bodyLarge,
            color = NazoOnPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
