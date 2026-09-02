package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.theme.NazoNavBar
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

enum class NazoTab { Home, Settings }

/**
 * Builds the OPEN edge silhouette shared by the app's top and bottom chrome.
 * ONE bend per side: the edge leaves the flat plateau through a wide rounded
 * shoulder and then runs STRAIGHT down the screen edge — no second curve
 * back into the corners (owner's 2026-09-02 doodle: "cut from there").
 * [ceiling] mirrors it vertically for the top.
 */
fun curvedBarEdgePath(size: Size, rampPx: Float, ceiling: Boolean): Path {
    val w = size.width
    val h = size.height
    val ramp = rampPx.coerceAtMost(w * 0.25f)
    // Vertical extent of the single bend; whatever remains below (or above,
    // for the ceiling) is a straight vertical drop into the screen edge.
    val bend = (h * 0.62f).coerceAtMost(ramp)
    return Path().apply {
        if (ceiling) {
            // Straight down the left screen edge, one bend onto the flat
            // bottom, flat across, one bend back up, straight up to the top.
            moveTo(0f, 0f)
            lineTo(0f, h - bend)
            cubicTo(0f, h - bend * 0.45f, ramp * 0.45f, h, ramp, h)
            lineTo(w - ramp, h)
            cubicTo(w - ramp * 0.45f, h, w, h - bend * 0.45f, w, h - bend)
            lineTo(w, 0f)
        } else {
            // Up the left screen edge, ONE bend onto the plateau (vertical
            // tangent at the edge, horizontal at the plateau), flat across,
            // one bend down, then straight down into the screen bottom.
            moveTo(0f, h)
            lineTo(0f, bend)
            cubicTo(0f, bend * 0.45f, ramp * 0.45f, 0f, ramp, 0f)
            lineTo(w - ramp, 0f)
            cubicTo(w - ramp * 0.45f, 0f, w, bend * 0.45f, w, bend)
            lineTo(w, h)
        }
    }
}

/**
 * Filled variant of the same silhouette, used as the bottom nav's shape.
 * The edge already includes the straight vertical sides, so closing it just
 * runs along the screen edge (bottom for the nav, top for the ceiling).
 */
class CurvedBarShape(
    private val rampWidth: Dp,
    private val ceiling: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = curvedBarEdgePath(size, with(density) { rampWidth.toPx() }, ceiling)
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun NazoBottomNav(
    selected: NazoTab,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    // Floating mode is a user preference (Appearance → Layout). In floating mode
    // the bar is a compact centred pill in the app's pill design language —
    // wrap-content width, solid surface, subtle border — so it reads like the
    // rest of the UI instead of a detached slab. The caller (Home) positions it
    // via `modifier` (e.g. align(BottomCenter)) so it overlays.
    val floating = ThemePreferences(LocalContext.current).floatingNavBar

    if (floating) {
        Row(
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50), clip = false)
                .background(NazoNavBar, RoundedCornerShape(50))
                .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems(selected = selected, onHomeClick = onHomeClick, onSettingsClick = onSettingsClick)
        }
    } else {
        // Anchored bar with the single-bend silhouette: the top edge bends
        // down off the plateau through one wide shoulder and then drops
        // STRAIGHT down the screen edge — no second curve into the corners.
        // The background is applied BEFORE the navigation-bar padding so the
        // bar also covers the system gesture area.
        val curve = remember { CurvedBarShape(rampWidth = 56.dp, ceiling = false) }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(NazoNavBar, curve)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
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
    // Same pill language as the rest of the app (mode/difficulty pills):
    // the selected tab is a solid accent pill with icon + label side by side,
    // the unselected tab is quiet text. Much slimmer than icon-over-label.
    val tint = if (selected) NazoOnPrimary else NazoTextSecondary
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) NazoPrimary else Color.Transparent)
            .clickable {
                Haptics.light(context)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
