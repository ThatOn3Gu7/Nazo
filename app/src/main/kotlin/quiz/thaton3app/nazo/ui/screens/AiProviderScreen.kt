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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.ModelInfo
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
    val models: List<ModelInfo> = emptyList(),
)

// Clean initial empty state for all providers
private fun defaultProviders() = listOf(
    ProviderUiState(
        id = "gemini",
        name = "Google Gemini",
        avatarLetter = "G",
        status = KeyStatus.NOT_CONFIGURED,
    ),
    ProviderUiState(
        id = "claude",
        name = "Anthropic Claude",
        avatarLetter = "A",
        status = KeyStatus.NOT_CONFIGURED,
    ),
    ProviderUiState(
        id = "chatgpt",
        name = "OpenAI ChatGPT",
        avatarLetter = "O",
        status = KeyStatus.NOT_CONFIGURED,
    ),
    ProviderUiState(
        id = "openrouter",
        name = "OpenRouter",
        avatarLetter = "R",
        status = KeyStatus.NOT_CONFIGURED,
    ),
    ProviderUiState(
        id = "claude",
        name = "Anthropic Claude",
        avatarLetter = "A",
        status = KeyStatus.NOT_CONFIGURED,
    ),
)

// Load the persisted key/model/list for each provider so the UI reflects what's stored on device.
// The fetched model list is cached in prefs (see ApiKeyStore.getModels), so a previously fetched
// list survives an app restart and the user doesn't have to re-fetch.
private fun initialProviders(store: ApiKeyStore): List<ProviderUiState> =
    defaultProviders().map { p ->
        val storedKey = store.getKey(p.id).orEmpty()
        val storedModels = store.getModels(p.id)
        val models = if (storedModels.isNotEmpty()) storedModels else p.models
        val storedModel = store.getModel(p.id) ?: models.firstOrNull()?.id.orEmpty()
        val status = if (storedKey.isNotBlank()) KeyStatus.VALID else KeyStatus.NOT_CONFIGURED
        p.copy(apiKey = storedKey, model = storedModel, models = models, status = status)
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
    val scope = rememberCoroutineScope()
    var fetchingId by remember { mutableStateOf<String?>(null) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var fetchErrorId by remember { mutableStateOf<String?>(null) }

    fun fetchModelsFor(index: Int, provider: ProviderUiState) {
        val key = provider.apiKey
        if (key.isBlank()) {
            fetchErrorId = provider.id
            fetchError = "Enter an API key first."
            return
        }
        fetchingId = provider.id
        fetchError = null
        fetchErrorId = null
        scope.launch {
            ApiClient.fetchModels(provider.id, key)
                .onSuccess { models ->
                    fetchingId = null
                    providers = providers.toMutableList().also { list ->
                        // Keep the user's current selection if it's still in the new list;
                        // only fall back to the first model when it isn't (or the list is empty).
                        val next = if (models.any { it.id == provider.model }) provider.model
                        else models.firstOrNull()?.id ?: provider.model
                        list[index] = list[index].copy(models = models, model = next)
                    }
                    apiKeyStore.saveModels(provider.id, models)
                }
                .onFailure { e ->
                    fetchingId = null
                    fetchErrorId = provider.id
                    fetchError = e.message ?: "Couldn't fetch models."
                }
        }
    }

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
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
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
                        onFetchModels = { fetchModelsFor(index, provider) },
                        isFetching = fetchingId == provider.id,
                        fetchError = if (fetchErrorId == provider.id) fetchError else null,
                )
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            SaveButton(onClick = {
                providers.forEach { p ->
                    apiKeyStore.saveKey(p.id, p.apiKey)
                    apiKeyStore.saveModel(p.id, p.model)
                    apiKeyStore.saveModels(p.id, p.models)
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
    onFetchModels: () -> Unit = {},
    isFetching: Boolean = false,
    fetchError: String? = null,
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
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(220)),
        ) {
            Column {
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
                    Spacer(Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NazoPrimary)
                                .clickable(enabled = !isFetching, onClick = onFetchModels)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = isFetching,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(160)) togetherWith
                                        fadeOut(animationSpec = tween(160))
                                },
                                label = "fetchLabel",
                            ) { fetching ->
                                if (fetching) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = NazoOnPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Fetching…",
                                            color = NazoOnPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Fetch models",
                                        color = NazoOnPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = fetchError != null,
                            enter = fadeIn(animationSpec = tween(160)),
                            exit = fadeOut(animationSpec = tween(160)),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NazoErrorBg)
                                    .border(1.dp, NazoError, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = fetchError ?: "",
                                    color = NazoError,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
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
private fun ModelDropdown(models: List<ModelInfo>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(models, query) {
        if (query.isBlank()) {
            models
        } else if (query.trim().lowercase() == "free") {
            models.filter { it.isFree }
        } else {
            val q = query.lowercase()
            models.filter {
                it.id.lowercase().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }
    }

    val selectedInfo = models.firstOrNull { it.id == selected }
    val triggerText = if (selected.isNotBlank()) {
        selectedInfo?.name?.ifBlank { selected } ?: selected
    } else {
        models.firstOrNull()?.id ?: "Select a model"
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Filter icon on the LEFT — toggles an inline keyword search (e.g. "free" -> free models).
            IconButton(
                onClick = {
                    searching = !searching
                    if (searching) {
                        expanded = true
                    } else {
                        query = ""
                        expanded = false
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searching) "Close search" else "Search models",
                    tint = if (searching) NazoPrimary else NazoTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Trigger "pill" — tapping expands the list inline (no system/menu overlay).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NazoSurfaceVariant)
                    .clickable { if (!searching) expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NazoTextPrimary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search models…",
                                        color = NazoTextPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                } else {
                    Text(
                        text = triggerText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected.isEmpty()) NazoTextPlaceholder else NazoTextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = NazoTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            ) {
                if (filtered.isEmpty()) {
                    Text(
                        text = "No models match.",
                        color = NazoTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                } else {
                    filtered.forEachIndexed { i, m ->
                        val isSel = m.id == selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(m.id)
                                    expanded = false
                                    searching = false
                                    query = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = m.name.ifBlank { m.id },
                                        color = if (isSel) NazoPrimary else NazoTextPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    if (m.isFree) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(NazoSuccessBg)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "Free",
                                                color = NazoSuccess,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                                if (m.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = m.description,
                                        color = NazoTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                            }
                            if (isSel) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = NazoPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (i != filtered.lastIndex) {
                            HorizontalDivider(color = NazoTextSecondary.copy(alpha = 0.12f))
                        }
                    }
                }
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
