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
 * Builds the OPEN edge curve shared by the app's top and bottom chrome: the
 * line hugs the screen corner, rounds through a soft knee, rises (or dips),
 * rounds again and runs flat across the middle. Control points sit at 80/20%
 * of the ramp so both bends are properly round — a flowing swoop rather than
 * a straight diagonal. [ceiling] mirrors it vertically for the top.
 */
fun curvedBarEdgePath(size: Size, rampPx: Float, ceiling: Boolean): Path {
    val w = size.width
    val h = size.height
    val ramp = rampPx.coerceAtMost(w * 0.25f)
    return Path().apply {
        if (ceiling) {
            // Starts at the top-left screen corner, swoops DOWN below the
            // header, runs flat, and swoops back UP to the top-right corner.
            moveTo(0f, 0f)
            cubicTo(ramp * 0.8f, 0f, ramp * 0.2f, h, ramp, h)
            lineTo(w - ramp, h)
            cubicTo(w - ramp * 0.2f, h, w - ramp * 0.8f, 0f, w, 0f)
        } else {
            // Starts at the bottom-left screen corner, swoops UP, runs flat
            // under the tabs, and swoops back DOWN to the bottom-right.
            moveTo(0f, h)
            cubicTo(ramp * 0.8f, h, ramp * 0.2f, 0f, ramp, 0f)
            lineTo(w - ramp, 0f)
            cubicTo(w - ramp * 0.2f, 0f, w - ramp * 0.8f, h, w, h)
        }
    }
}

/**
 * Filled variant of the same silhouette, used as the bottom nav's shape:
 * the edge curve closed along the screen edge.
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
        // Anchored bar with the curved silhouette: it rises out of the
        // bottom-left corner, runs flat under the tabs, and sinks back into
        // the bottom-right corner. The background is applied BEFORE the
        // navigation-bar padding so the plateau also covers the system
        // gesture area (the ambient particles only peek through the two
        // tapered corners, matching the header ceiling above).
        val curve = remember { CurvedBarShape(rampWidth = 56.dp, ceiling = false) }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(NazoNavBar, curve)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
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
