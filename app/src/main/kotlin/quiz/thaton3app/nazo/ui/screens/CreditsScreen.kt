package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quiz.thaton3app.nazo.ui.components.rememberHapticBack
import quiz.thaton3app.nazo.data.Contributor
import quiz.thaton3app.nazo.data.NAZO_CONTRIBUTORS
import quiz.thaton3app.nazo.data.NAZO_SERVICES
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/**
 * Credits: the lead developer, then everyone else who contributed, then the
 * third-party services the app talks to.
 *
 * This replaces the old "About the Developer" dialog. The dialog crammed the
 * story, the bio, the project list and four links into a scrolling
 * `AlertDialog`; the reference treats the developer as a proper section with an
 * avatar, a one-line bio and a row of link tiles, which is what this does.
 *
 * There is deliberately no donation banner — the reference has one, but Nazo
 * has no support link to point at, and inventing one would be worse than
 * omitting it.
 */
@Composable
fun CreditsScreen(
    onBackClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(28.dp))
            AboutSubScreenHeader(title = "Credits", onBackClick = onBackClick)
            Spacer(Modifier.height(24.dp))

            SubSectionLabel("Lead developer")
            Spacer(Modifier.height(12.dp))
            LeadDeveloperCard(onOpenUrl = onOpenUrl)

            Spacer(Modifier.height(28.dp))

            SubSectionLabel("Contributors")
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
            ) {
                NAZO_CONTRIBUTORS.forEachIndexed { index, contributor ->
                    ContributorRow(contributor = contributor, onOpenUrl = onOpenUrl)
                    if (index != NAZO_CONTRIBUTORS.lastIndex) {
                        HorizontalDivider(
                            color = NazoTextSecondary.copy(alpha = 0.10f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SubSectionLabel("Built with")
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(NazoSurface)
                    .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Nazo is built with Kotlin and Jetpack Compose, with a local " +
                        "question bank and an optional AI provider for fresh questions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NazoTextPrimary,
                    lineHeight = 21.sp,
                )
                Text(
                    text = "Services used",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoPrimary,
                    fontWeight = FontWeight.Bold,
                )
                NAZO_SERVICES.forEach { service ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•  ", color = NazoTextSecondary)
                        Text(
                            text = service,
                            style = MaterialTheme.typography.bodySmall,
                            color = NazoTextSecondary,
                            lineHeight = 19.sp,
                        )
                    }
                }
                Text(
                    text = "These services are not affiliated with or endorsed by Nazo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NazoTextSecondary,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Avatar, name, bio and link tiles — the reference's "Lead developer" block. */
@Composable
private fun LeadDeveloperCard(onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(vertical = 24.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No photo is shipped, so the monogram stands in for the reference's
        // avatar rather than leaving an empty ring.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NazoPrimary.copy(alpha = 0.15f))
                .border(2.dp, NazoPrimary.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "謎",
                style = MaterialTheme.typography.headlineLarge,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "ThatOn3Gu7",
            style = MaterialTheme.typography.titleLarge,
            color = NazoPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sahil R. — learns by building, lives in the terminal.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // Link tiles.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NazoPrimary.copy(alpha = 0.12f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DevLinkTile(
                icon = Icons.Filled.MailOutline,
                label = "E-mail",
                modifier = Modifier.weight(1f),
            ) { onOpenUrl("mailto:socialzoneop@gmail.com") }
            TileDivider()
            DevLinkTile(
                icon = Icons.Filled.Code,
                label = "GitHub",
                modifier = Modifier.weight(1f),
            ) { onOpenUrl("https://github.com/ThatOn3Gu7") }
            TileDivider()
            DevLinkTile(
                icon = Icons.Filled.PhotoCamera,
                label = "Instagram",
                modifier = Modifier.weight(1f),
            ) { onOpenUrl("https://instagram.com/thaton3gu7") }
        }

        Spacer(Modifier.height(18.dp))

        // The story, kept from the old dialog — it is good copy, it just did not
        // belong in a popup.
        Text(
            text = "Nazo started as a learning project: I wanted to learn how to code, and " +
                "an anime quiz app felt like the perfect first one — simple to start, but " +
                "with enough real pieces to actually learn from.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Other projects",
                style = MaterialTheme.typography.labelMedium,
                color = NazoPrimary,
                fontWeight = FontWeight.Bold,
            )
            ProjectRow(
                title = "ProjectR",
                detail = "A modular Bash setup assistant that installs, inspects and backs " +
                    "up 240+ tools across Linux, macOS and Termux.",
            )
            ProjectRow(
                title = "UtilityKit",
                detail = "65 standalone Bash utilities — files, network, git — behind one " +
                    "interactive dashboard.",
            )
        }
    }
}

@Composable
private fun ProjectRow(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Filled.Terminal,
            contentDescription = null,
            tint = NazoPrimary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun TileDivider() {
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(1.dp)
            .background(NazoTextSecondary.copy(alpha = 0.18f)),
    )
}

@Composable
private fun DevLinkTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NazoTextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContributorRow(contributor: Contributor, onOpenUrl: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (contributor.url != null) {
                    Modifier.clickable { onOpenUrl(contributor.url) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(NazoPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contributor.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = NazoOnPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contributor.name,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = contributor.role,
                style = MaterialTheme.typography.bodySmall,
                color = NazoPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = contributor.detail,
                style = MaterialTheme.typography.bodySmall,
                color = NazoTextSecondary,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SubSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = NazoPrimary,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Shared header for the About sub-screens, matching AboutScreen's own
 * `ScreenHeader` (which is private to that file).
 */
@Composable
internal fun AboutSubScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoSurface)
                .clickable(onClick = rememberHapticBack(onBackClick)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = NazoTextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
