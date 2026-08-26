package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.theme.NazoNavBar
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary
import quiz.thaton3app.nazo.ui.components.Haptics

enum class NazoTab { Home, Settings }

@Composable
fun NazoBottomNav(
    selected: NazoTab,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    // Floating mode is a user preference (Appearance → Layout). In floating mode the
    // bar becomes an elevated rounded pill, inset from the edges and slightly
    // translucent, so screen content scrolling *under* it stays visible. The caller
    // (Home) positions it via `modifier` (e.g. align(BottomCenter)) so it overlays.
    val floating = ThemePreferences(LocalContext.current).floatingNavBar

    if (floating) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(26.dp), clip = false)
                .background(NazoNavBar.copy(alpha = 0.92f), RoundedCornerShape(26.dp))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItems(selected = selected, onHomeClick = onHomeClick, onSettingsClick = onSettingsClick)
        }
    } else {
        // Solid bar: the background is applied BEFORE the navigation-bar padding so it
        // also covers the system gesture area, hiding the ambient particles behind it.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(NazoNavBar)
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItems(selected = selected, onHomeClick = onHomeClick, onSettingsClick = onSettingsClick)
        }
    }
}

@Composable
private fun NavItems(
    selected: NazoTab,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
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

@Composable
private fun NazoNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) NazoPrimary else NazoTextSecondary
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Clip to a rounded pill so the long-press ripple is round, not square.
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable {
                Haptics.light(context)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
