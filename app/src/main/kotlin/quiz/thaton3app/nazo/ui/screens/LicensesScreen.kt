package quiz.thaton3app.nazo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.data.GITHUB_REPO
import quiz.thaton3app.nazo.data.NAZO_LIBRARIES
import quiz.thaton3app.nazo.data.NAZO_LICENSE_ID
import quiz.thaton3app.nazo.data.NAZO_LICENSE_SUMMARY
import quiz.thaton3app.nazo.data.ThirdPartyLibrary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Nazo's own licence, then the third-party libraries it ships.
 *
 * Mirrors the reference layout: a tinted card for the application itself with
 * its SPDX badge and links, then a searchable list of dependencies that expand
 * to show details.
 *
 * The library list is the hand-maintained [NAZO_LIBRARIES] rather than a
 * generated one — see the note in `AboutContent.kt` for why.
 */
@Composable
fun LicensesScreen(
    onBackClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        val q = query.trim()
        if (q.isBlank()) {
            NAZO_LIBRARIES
        } else {
            NAZO_LIBRARIES.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.license.contains(q, ignoreCase = true) ||
                    it.version.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Spacer(Modifier.height(28.dp))
                AboutSubScreenHeader(title = "Licenses", onBackClick = onBackClick)
                Spacer(Modifier.height(20.dp))
                AppLicenseCard(onOpenUrl = onOpenUrl)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Third-party libraries",
                    style = MaterialTheme.typography.titleSmall,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${NAZO_LIBRARIES.size} libraries bundled",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text("Search libraries, licenses…", color = NazoTextSecondary)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = NazoTextSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NazoTextPrimary,
                        unfocusedTextColor = NazoTextPrimary,
                        focusedBorderColor = NazoPrimary,
                        unfocusedBorderColor = NazoTextSecondary.copy(alpha = 0.2f),
                        cursorColor = NazoPrimary,
                    ),
                )
                Spacer(Modifier.height(14.dp))
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "No libraries match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(filtered, key = { it.name }) { library ->
                LibraryRow(library = library, onOpenUrl = onOpenUrl)
                Spacer(Modifier.height(10.dp))
            }

            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

/** The highlighted card describing Nazo's own licence. */
@Composable
private fun AppLicenseCard(onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoPrimary.copy(alpha = 0.12f))
            .border(1.dp, NazoPrimary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Balance,
                    contentDescription = null,
                    tint = NazoPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Nazo",
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "This application",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // SPDX badge.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoPrimary)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = NAZO_LICENSE_ID,
                style = MaterialTheme.typography.labelMedium,
                color = quiz.thaton3app.nazo.ui.theme.NazoOnPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = NAZO_LICENSE_SUMMARY,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextPrimary,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LicenseLink(label = "View on GitHub") {
                onOpenUrl("https://github.com/$GITHUB_REPO")
            }
            LicenseLink(label = "Full license text") {
                onOpenUrl("https://github.com/$GITHUB_REPO/blob/master/LICENSE")
            }
        }
    }
}

@Composable
private fun LicenseLink(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Icon(
            Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NazoPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One dependency; tapping expands it to show its licence and a link. */
@Composable
private fun LibraryRow(library: ThirdPartyLibrary, onOpenUrl: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "library_chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Initial avatar, as in the reference.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NazoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = library.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NazoTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = library.version,
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoPrimary,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = NazoTextSecondary,
                modifier = Modifier.rotate(chevronRotation),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NazoSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = library.license,
                        style = MaterialTheme.typography.labelSmall,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LicenseLink(label = "Project page") { onOpenUrl(library.url) }
        }
    }
}
