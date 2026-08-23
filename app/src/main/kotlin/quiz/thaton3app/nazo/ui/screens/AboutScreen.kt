package quiz.thaton3app.nazo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import quiz.thaton3app.nazo.ui.components.NazoBottomNav
import quiz.thaton3app.nazo.ui.components.NazoTab
import quiz.thaton3app.nazo.ui.theme.NazoBackground
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPillUnselected
import quiz.thaton3app.nazo.ui.theme.NazoSurface
import quiz.thaton3app.nazo.ui.theme.NazoTextPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.theme.NazoSurfaceVariant

@Composable
fun AboutScreen(
    onBackClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NazoBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            ScreenHeader(title = "About", onBackClick = onBackClick)
            Spacer(Modifier.height(20.dp))
            
            HeroCard()
            
            Spacer(Modifier.height(24.dp))
            SectionLabel("SUPPORT & SOURCE")
            Spacer(Modifier.height(10.dp))
            
            SettingsCard {
                ActionRow(
                    icon = Icons.Filled.Sync,
                    title = "Updates & Settings",
                    subtitle = "Check for updates from GitHub",
                    onClick = { /* TODO */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = "Send Feedback",
                    subtitle = "Report issues or share ideas",
                    onClick = { /* TODO */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Code,
                    title = "GitHub Repository",
                    subtitle = "View source code",
                    onClick = { /* TODO */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.PersonOutline,
                    title = "About the Developer",
                    subtitle = "Story & projects",
                    onClick = { /* TODO */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Balance,
                    title = "Licenses",
                    subtitle = "Open-source libraries",
                    onClick = { /* TODO */ }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.Event,
                    title = "Installed Date",
                    subtitle = "First launch of the app",
                    trailingText = "Aug 21, 2026",
                    onClick = { /* Non-clickable stat row */ }
                )
            }
            
            Spacer(Modifier.height(32.dp))
        }
        NazoBottomNav(selected = NazoTab.Settings, onHomeClick = onHomeClick, onSettingsClick = onBackClick)
    }
}

@Composable
private fun ScreenHeader(title: String, onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBackClick,
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
private fun HeroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NazoSurface)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NazoPillUnselected),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "謎", // Nazo (Mystery/Puzzle)
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp),
                color = NazoPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Nazo",
            style = MaterialTheme.typography.headlineMedium,
            color = NazoTextPrimary
        )
        
        Spacer(Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(NazoSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Version 1.0.0 (1)", // Updated to a standard Android 1.0 default
                style = MaterialTheme.typography.labelSmall,
                color = NazoTextPrimary
            )
        }
        
        Spacer(Modifier.height(20.dp))
        
        Text(
            text = "An anime quiz companion that turns any series, arc or theme into AI-generated questions — with your own API keys, stored securely on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = NazoTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text, 
        style = MaterialTheme.typography.labelSmall, 
        color = NazoTextSecondary,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NazoSurface),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = NazoBackground, 
        thickness = 2.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NazoBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = NazoTextPrimary, 
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle, 
                style = MaterialTheme.typography.bodyMedium, 
                color = NazoTextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = NazoTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
