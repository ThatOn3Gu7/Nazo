package quiz.thaton3app.nazo.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.ui.components.ProfileAvatar
import quiz.thaton3app.nazo.ui.components.SafeRemoteImage
import quiz.thaton3app.nazo.ui.theme.*
import java.net.HttpURLConnection
import java.net.URL

val ProfileHeaderFont = FontFamily(
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    username: String,
    profilePictureUri: String?,
    quizStats: QuizStats = QuizStats(),
    onBack: () -> Unit = {},
    onUsernameChange: (String) -> Unit,
    onProfilePictureChange: (String?) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPictureDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                onProfilePictureChange(it.toString())
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ProfileHeaderFont,
                        fontWeight = FontWeight.Bold,
                        color = NazoPrimary,
                        letterSpacing = 0.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatar(
                name = username,
                pictureUri = profilePictureUri,
                size = 132.dp,
                onClick = { showPictureDialog = true },
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Surface(
                onClick = { showUsernameDialog = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Transparent,
                modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = username.ifBlank {
                            if (quizStats.totalQuizzes > 0) "Edit Profile" else "Create Profile"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NazoTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit username",
                        tint = NazoPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (quizStats.totalQuizzes > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = NazoSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ProfileStatCell(
                                value = quizStats.totalQuizzes.toString(),
                                label = "Quizzes",
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatCell(
                                value = quizStats.totalQuestionsAnswered.toString(),
                                label = "Answered",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ProfileStatCell(
                                value = quizStats.totalCorrect.toString(),
                                label = "Correct",
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatCell(
                                value = quizStats.currentStreakDays.toString(),
                                label = "Streak",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = NazoSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Filled.BarChart,
                        title = "Statistics",
                        subtitle = "View your quiz insights",
                        onClick = onNavigateToStatistics
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = NazoTextSecondary.copy(alpha = 0.15f)
                    )
                    ProfileMenuItem(
                        icon = Icons.Filled.Settings,
                        title = "Settings",
                        subtitle = "Appearance, categories, backup",
                        onClick = onNavigateToSettings
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    if (showUsernameDialog) {
        var text by remember { mutableStateOf(username) }
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
            title = { Text("Edit username") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Enter a username") },
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = {
                        IconButton(onClick = { text = randomAnimeUsername() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Generate random username"
                            )
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUsernameChange(text.trim())
                    showUsernameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPictureDialog) {
        var avatarTab by remember { mutableStateOf(0) }
        var nonce by remember { mutableStateOf("") }
        var dynamicTabs by remember { mutableStateOf<List<Pair<String, List<AvatarPreset>>>>(emptyList()) }
        var loadingDynamic by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        val tabs = remember(nonce, dynamicTabs) {
            buildStaticAvatarCategories(nonce) + dynamicTabs
        }
        val safeTab = avatarTab.coerceIn(0, tabs.size - 1)

        fun refreshAvatars() {
            nonce = Random.nextLong(100_000, 999_999).toString()
            scope.launch {
                loadingDynamic = true
                dynamicTabs = fetchDynamicAvatarTabs()
                loadingDynamic = false
            }
        }

        LaunchedEffect(Unit) {
            dynamicTabs = fetchDynamicAvatarTabs()
            loadingDynamic = false
        }

        AlertDialog(
            onDismissRequest = { showPictureDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Change profile picture", modifier = Modifier.weight(1f))
                    IconButton(onClick = { refreshAvatars() }) {
                        if (loadingDynamic) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh pictures"
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Pick a default avatar or upload your own. Tap refresh for a new batch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    ScrollableTabRow(
                        selectedTabIndex = safeTab,
                        containerColor = Color.Transparent,
                        edgePadding = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, (name, _) ->
                            Tab(
                                selected = safeTab == index,
                                onClick = { avatarTab = index },
                                text = { Text(name, style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        tabs[safeTab].second.forEach { preset ->
                            val presetKey = when (preset) {
                                is AvatarPreset.Emoji -> "emoji:${preset.symbol}"
                                is AvatarPreset.Generated -> preset.url
                            }
                            key(presetKey) {
                                Surface(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            val value = when (preset) {
                                                is AvatarPreset.Emoji -> "emoji:${preset.symbol}"
                                                is AvatarPreset.Generated -> preset.url
                                            }
                                            onProfilePictureChange(value)
                                            showPictureDialog = false
                                        },
                                    color = NazoSurfaceVariant,
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        when (preset) {
                                            is AvatarPreset.Emoji -> Text(
                                                text = preset.symbol,
                                                style = MaterialTheme.typography.headlineMedium
                                            )
                                            is AvatarPreset.Generated -> SafeRemoteImage(
                                                url = preset.url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                placeholder = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(20.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                    }
                                                },
                                                errorContent = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Refresh,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = NazoTextSecondary
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (loadingDynamic) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Loading extra styles…",
                            style = MaterialTheme.typography.labelMedium,
                            color = NazoTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        showPictureDialog = false
                        showUrlDialog = true
                    }) { Text("From URL") }
                    TextButton(onClick = {
                        showPictureDialog = false
                        galleryLauncher.launch(arrayOf("image/*"))
                    }) { Text("From Gallery") }
                    if (!profilePictureUri.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                onProfilePictureChange(null)
                                showPictureDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = NazoError
                            )
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        )
    }

    if (showUrlDialog) {
        var url by remember {
            mutableStateOf(profilePictureUri?.takeIf { !it.startsWith("emoji:") } ?: "")
        }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            title = { Text("Picture from URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://...") },
                    shape = MaterialTheme.shapes.large
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onProfilePictureChange(url.trim().ifBlank { null })
                    showUrlDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = NazoTextPrimary
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NazoPrimary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NazoTextSecondary
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ProfileStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NazoPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NazoTextSecondary
        )
    }
}

// --- Avatar presets & sources ---
//
// "Anime Style" = DiceBear "adventurer" avatars. Extra open-source sources:
// DiceBear pixel-art/shapes, RoboHash (robots/monsters/cats), Pravatar, RandomUser,
// Jikan (MyAnimeList) and waifu.im.

private sealed interface AvatarPreset {
    data class Emoji(val symbol: String) : AvatarPreset
    data class Generated(val id: String, val url: String) : AvatarPreset
}

private val ANIME_SEEDS = listOf(
    "Aiko", "Botan", "Chiyo", "Daichi", "Emi", "Fuma",
    "Haruto", "Ichika", "Jun", "Kaida", "Lin", "Mio",
    "Noa", "Ren", "Suzu", "Tsumugi"
)
private val ROBO_SEEDS = listOf("Rex", "Volt", "Zed", "Kilo", "Nova", "Byte", "Gizmo", "Unit")

private fun seeded(seed: String, nonce: String) = if (nonce.isBlank()) seed else "$seed-$nonce"

private fun dicebearUrl(style: String, seed: String, nonce: String) =
    "https://api.dicebear.com/10.x/$style/png?seed=${seeded(seed, nonce)}&size=256&backgroundType=gradientLinear"

private fun robohashUrl(set: String, seed: String, nonce: String) =
    "https://robohash.org/${seeded(seed, nonce)}?set=$set&size=256x256"

private fun pravatarUrl(seed: String, nonce: String): String {
    val n = abs(seeded(seed, nonce).hashCode() % 70) + 1
    return "https://i.pravatar.cc/256?img=$n"
}

/** Static tabs — rebuilt instantly on every refresh (new nonce = new art). */
private fun buildStaticAvatarCategories(nonce: String): List<Pair<String, List<AvatarPreset>>> = listOf(
    "Anime Style" to ANIME_SEEDS.map {
        AvatarPreset.Generated("adv-$it-$nonce", dicebearUrl("adventurer", it, nonce))
    },
    "Pixel Art" to ANIME_SEEDS.map {
        AvatarPreset.Generated("pix-$it-$nonce", dicebearUrl("pixel-art", it, nonce))
    },
    "Abstract" to ANIME_SEEDS.take(8).map {
        AvatarPreset.Generated("shp-$it-$nonce", dicebearUrl("shapes", it, nonce))
    },
    "Robots" to ROBO_SEEDS.map {
        AvatarPreset.Generated("rob-$it-$nonce", robohashUrl("set1", it, nonce))
    },
    "Monsters" to ROBO_SEEDS.map {
        AvatarPreset.Generated("mon-$it-$nonce", robohashUrl("set2", it, nonce))
    },
    "Cats" to ROBO_SEEDS.map {
        AvatarPreset.Generated("cat-$it-$nonce", robohashUrl("set4", it, nonce))
    },
    "Portraits" to (1..8).map { i ->
        AvatarPreset.Generated("prt-$i-$nonce", pravatarUrl("portrait$i", nonce))
    }
)

// --- Dynamic open-source APIs (fetched async, appear as extra tabs) ---

private fun httpGet(url: String): String? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    conn.requestMethod = "GET"
    val body = if (conn.responseCode in 200..299) {
        conn.inputStream.bufferedReader().use { it.readText() }
    } else {
        conn.disconnect()
        null
    }
    body
} catch (_: Exception) {
    null
}

/** Each call hits random pages/endpoints, so every refresh yields fresh faces. */
private suspend fun fetchDynamicAvatarTabs(): List<Pair<String, List<AvatarPreset>>> =
    withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, List<AvatarPreset>>>()

        // Jikan — open-source MyAnimeList REST API
        runCatching {
            val page = Random.nextInt(1, 40)
            val body = httpGet("https://api.jikan.moe/v4/characters?page=$page&order_by=members&sort=desc")
                ?: return@runCatching
            val arr = JSONObject(body).optJSONArray("data") ?: return@runCatching
            val items = mutableListOf<AvatarPreset>()
            for (i in 0 until arr.length()) {
                val url = arr.optJSONObject(i)
                    ?.optJSONObject("images")
                    ?.optJSONObject("jpg")
                    ?.optString("image_url")
                    ?.takeIf { it.isNotBlank() }
                url?.let { items.add(AvatarPreset.Generated("jikan-$i-$page", it)) }
            }
            if (items.isNotEmpty()) results.add("Anime Faces" to items)
        }

        // waifu.im — open-source anime image archive
        runCatching {
            val body = httpGet("https://api.waifu.im/images/?is_nsfw=false")
                ?: return@runCatching
            val arr = JSONObject(body).optJSONArray("images") ?: return@runCatching
            val items = mutableListOf<AvatarPreset>()
            for (i in 0 until arr.length()) {
                val url = arr.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }
                url?.let { items.add(AvatarPreset.Generated("waifu-$i", it)) }
            }
            if (items.isNotEmpty()) results.add("Anime Art" to items)
        }

        // RandomUser — free & open-source, real photos
        runCatching {
            val body = httpGet("https://randomuser.me/api/?results=10")
                ?: return@runCatching
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching
            val items = mutableListOf<AvatarPreset>()
            for (i in 0 until arr.length()) {
                val url = arr.optJSONObject(i)
                    ?.optJSONObject("picture")
                    ?.optString("medium")
                    ?.takeIf { it.isNotBlank() }
                url?.let { items.add(AvatarPreset.Generated("ruser-$i", it)) }
            }
            if (items.isNotEmpty()) results.add("Real Photos" to items)
        }

        results
    }

private val USERNAME_ADJECTIVES = listOf(
    "Shadow", "Neon", "Crimson", "Silent", "Lunar",
    "Electric", "Hidden", "Mystic", "Rapid", "Frozen"
)
private val USERNAME_NOUNS = listOf(
    "Shinobi", "Otaku", "Samurai", "Ronin", "Kitsune",
    "Titan", "Reaper", "Phantom", "Saiyan", "Wolf"
)

private fun randomAnimeUsername(): String {
    val adj = USERNAME_ADJECTIVES.random()
    val noun = USERNAME_NOUNS.random()
    val num = (10..99).random()
    return "$adj$noun$num"
}
