package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.ui.theme.NazoNavBar
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

// Confirmed against the mockup pixels: the active tab has NO pill/capsule highlight
// behind it — active vs inactive is shown purely by icon/label color (dark green vs
// muted gray-green) and label weight.

enum class NazoTab { Home, Settings }

@Composable
fun NazoBottomNav(
    selected: NazoTab,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NazoNavBar)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NazoNavItem(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = selected == NazoTab.Home,
            onClick = onHomeClick,
        )
        NazoNavItem(
            icon = Icons.Filled.Settings,
            label = "Settings",
            selected = selected == NazoTab.Settings,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun NazoNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) NazoPrimary else NazoTextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.height(22.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
