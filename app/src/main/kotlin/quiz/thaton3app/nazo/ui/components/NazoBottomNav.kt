package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import quiz.thaton3app.nazo.data.settings.ThemePreferences
import quiz.thaton3app.nazo.ui.theme.NazoNavBar
import quiz.thaton3app.nazo.ui.theme.NazoOnPrimary
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

enum class NazoTab { Home, Settings }

/**
 * Makes the bar's whole surface an opaque hit target.
 *
 * The bar is a plain [Row]: only the two tab pills were interactive, so taps on
 * the bar's padding, its rounded shoulders, or the gap between the pills fell
 * straight through to whatever sat behind it (mode cards, the Generate button).
 *
 * `detectTapGestures {}` registers the entire Row as a pointer-input node and
 * consumes taps that land on it. The tabs keep working because they are
 * descendants, and Compose hit-tests descendants before their parent.
 */
private fun Modifier.blockTouchThrough(): Modifier =
    this.pointerInput(Unit) { detectTapGestures { /* absorb: not a tab */ } }

/** Shared duration for the floating bar's tab transition (tint + label expand). */
private const val TAB_ANIM_MS = 280

@Composable
fun NazoBottomNav(
    selected: NazoTab,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val floating = ThemePreferences(LocalContext.current).floatingNavBar

    // Landscape: the bar becomes a RAIL on the right edge. A bottom bar costs
    // ~56dp of the scarcest dimension in landscape, where vertical space is
    // what the content actually needs; the right edge is cheap by comparison.
    // Labels stack under their icons so the rail stays narrow.
    if (isLandscape()) {
        NazoNavRail(
            selected = selected,
            floating = floating,
            modifier = modifier,
            onHomeClick = onHomeClick,
            onSettingsClick = onSettingsClick,
        )
        return
    }

    if (floating) {
        Row(
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
                // Only the pill itself blocks touches; the transparent area
                // beside it stays interactive, which is the point of floating.
                .blockTouchThrough()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50), clip = false)
                .background(NazoNavBar, RoundedCornerShape(50))
                .border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems(selected = selected, isFloating = true, onHomeClick = onHomeClick, onSettingsClick = onSettingsClick)
        }
    } else {
        val cornerRadius = 24.dp
        val overhang = 14.dp 
        
        Row(
            modifier = modifier
                // The docked bar spans the full width and is opaque, so nothing
                // behind it should be reachable — see blockTouchThrough().
                .blockTouchThrough()
                .background(
                    color = NazoNavBar, 
                    shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                )
                .navigationBarsPadding()
                .padding(horizontal = cornerRadius + overhang)
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems(selected = selected, isFloating = false, onHomeClick = onHomeClick, onSettingsClick = onSettingsClick)
        }
    }
}

/**
 * The landscape navigation rail: the same two tabs, stacked vertically against
 * the right edge with each label sitting under its icon.
 *
 * Mirrors the bar's two looks — `floating` gives a detached rounded pill,
 * otherwise it is a solid surface anchored to the edge.
 */
@Composable
private fun NazoNavRail(
    selected: NazoTab,
    floating: Boolean,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val shape = if (floating) {
        RoundedCornerShape(50)
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    }

    Column(
        modifier = modifier
            .then(if (floating) Modifier.padding(end = 12.dp) else Modifier)
            .blockTouchThrough()
            .then(
                if (floating) {
                    Modifier.shadow(elevation = 6.dp, shape = shape, clip = false)
                } else {
                    Modifier
                }
            )
            .background(NazoNavBar, shape)
            .then(
                if (floating) {
                    Modifier.border(1.dp, NazoTextSecondary.copy(alpha = 0.08f), shape)
                } else {
                    Modifier
                }
            )
            // Keep clear of the gesture bar / cutout on the short edges.
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NazoNavRailItem(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = selected == NazoTab.Home,
            onClick = onHomeClick,
        )
        NazoNavRailItem(
            icon = Icons.Filled.Settings,
            label = "Settings",
            selected = selected == NazoTab.Settings,
            onClick = onSettingsClick,
        )
    }
}

/**
 * One rail tab: icon above, label below.
 *
 * Unlike the portrait pill the label is always shown — a rail is wide enough
 * for it, and hiding it would leave two unlabelled icons with no context.
 */
@Composable
private fun NazoNavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spec = tween<Color>(TAB_ANIM_MS, easing = FastOutSlowInEasing)
    val tint by animateColorAsState(
        targetValue = if (selected) NazoOnPrimary else NazoTextSecondary,
        animationSpec = spec,
        label = "rail_tint",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) NazoPrimary else Color.Transparent,
        animationSpec = spec,
        label = "rail_bg",
    )

    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable {
                Haptics.light(context)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun NavItems(
    selected: NazoTab,
    isFloating: Boolean,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    NazoNavItem(
        icon = Icons.Filled.Home,
        label = "Home",
        selected = selected == NazoTab.Home,
        isFloating = isFloating,
        onClick = onHomeClick,
    )
    NazoNavItem(
        icon = Icons.Filled.Settings,
        label = "Settings",
        selected = selected == NazoTab.Settings,
        isFloating = isFloating,
        onClick = onSettingsClick,
    )
}

@Composable
private fun NazoNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isFloating: Boolean,
    onClick: () -> Unit,
) {
    val targetTint = if (selected) NazoOnPrimary else NazoTextSecondary
    val targetBg = if (selected) NazoPrimary else Color.Transparent

    // One shared duration so the colour fade and the label expand/collapse finish
    // together — otherwise the pill keeps growing after it has finished tinting.
    val spec = tween<Color>(TAB_ANIM_MS, easing = FastOutSlowInEasing)
    val animatedTint by animateColorAsState(targetValue = targetTint, animationSpec = spec, label = "nav_tint")
    val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = spec, label = "nav_bg")

    val currentTint = if (isFloating) animatedTint else targetTint
    val currentBg = if (isFloating) animatedBg else targetBg

    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(currentBg)
            .clickable {
                Haptics.light(context)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = label, 
            tint = currentTint, 
            modifier = Modifier.size(18.dp)
        )
        
        // In floating mode only the SELECTED tab shows its label, so the pill
        // expands as it gains the tab and collapses to a bare icon as it loses it.
        //
        // The enter/exit must be HORIZONTAL. AnimatedVisibility defaults to
        // expandVertically/shrinkVertically, which grew the label from zero
        // HEIGHT — on a short horizontal pill that reads as a vertical squash,
        // not an expand. expandHorizontally + the shared duration gives the
        // sideways grow/shrink this is meant to be.
        //
        // This also replaces animateContentSize() on the Row: AnimatedVisibility
        // already animates the size it contributes, so having both meant two
        // animators fighting over the same width.
        AnimatedVisibility(
            visible = !isFloating || selected,
            enter = expandHorizontally(
                animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Start,
            ) + fadeIn(animationSpec = tween(TAB_ANIM_MS)),
            exit = shrinkHorizontally(
                animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Start,
            ) + fadeOut(animationSpec = tween(TAB_ANIM_MS / 2)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    color = currentTint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
