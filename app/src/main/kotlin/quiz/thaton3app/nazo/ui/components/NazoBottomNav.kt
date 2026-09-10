package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    floating: Boolean? = null,
    onHomeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    // The style is normally passed in from NazoApp's hoisted state so toggling
    // it in Appearance restyles the bar IMMEDIATELY. Reading the preference
    // here instead (as this used to) meant the new value was only picked up
    // when something else forced a recomposition — in practice a screen change
    // — so the bar kept its old style until the user navigated.
    // The fallback read keeps standalone previews/callers working.
    val floating = floating ?: ThemePreferences(LocalContext.current).floatingNavBar

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
            // Width of the widest item, so both can fill it equally.
            .then(if (floating) Modifier else Modifier.width(IntrinsicSize.Max))
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
            .padding(
                horizontal = if (floating) 6.dp else 8.dp,
                vertical = if (floating) 6.dp else 10.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Docked rail: both items get the SAME width so their selected fill is
        // identical. Left to itself the Column sizes each item to its own
        // content, and "Settings" (8 letters) drew a visibly wider highlight
        // than "Home" (4). IntrinsicSize.Max measures the widest item and
        // fillMaxWidth() makes the other match it. The floating rail is a
        // hugging capsule by design, so it opts out.
        val itemWidth = if (floating) Modifier else Modifier.fillMaxWidth()
        NazoNavRailItem(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = selected == NazoTab.Home,
            isFloating = floating,
            modifier = itemWidth,
            onClick = onHomeClick,
        )
        NazoNavRailItem(
            icon = Icons.Filled.Settings,
            label = "Settings",
            selected = selected == NazoTab.Settings,
            isFloating = floating,
            modifier = itemWidth,
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
    isFloating: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val targetTint = if (selected) NazoOnPrimary else NazoTextSecondary
    val targetBg = if (selected) NazoPrimary else Color.Transparent

    val spec = tween<Color>(TAB_ANIM_MS, easing = FastOutSlowInEasing)
    val animatedTint by animateColorAsState(targetValue = targetTint, animationSpec = spec, label = "rail_tint")
    val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = spec, label = "rail_bg")

    val currentTint = if (isFloating) animatedTint else targetTint
    val currentBg = if (isFloating) animatedBg else targetBg

    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(if (isFloating) RoundedCornerShape(50) else RoundedCornerShape(18.dp))
            .background(currentBg)
            .clickable {
                Haptics.light(context)
                onClick()
            }
            .padding(
                horizontal = if (isFloating) 9.dp else 8.dp,
                vertical = if (isFloating) 12.dp else 10.dp,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = currentTint,
            modifier = Modifier.size(if (isFloating) 18.dp else 20.dp),
        )

        // Same rule as the portrait pill: floating shows the label only for the
        // SELECTED tab, so the capsule grows as it gains the tab and shrinks to
        // a bare icon as it loses it. The rail's long axis is vertical, so the
        // expand/collapse must be VERTICAL here (the portrait pill uses the
        // horizontal pair for exactly the same reason, mirrored).
        AnimatedVisibility(
            visible = !isFloating || selected,
            enter = expandVertically(
                animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(TAB_ANIM_MS)),
            exit = shrinkVertically(
                animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(TAB_ANIM_MS / 2)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(5.dp))
                // The whole word is drawn as ONE Text with newlines between the
                // letters, not one Text per letter.
                //
                // Per-letter Texts were the cause of the leftover space under
                // the last letter: every Text is its own layout box carrying the
                // font's ascent/descent, and Trim.Both only trims the first and
                // last line WITHIN a single Text — with one line per Text it
                // trimmed nothing. The error was also proportional to the letter
                // count, which is why 8-letter "Settings" showed a big gap and
                // 4-letter "Home" looked fine.
                //
                // One Text means one layout box: Trim.Both now genuinely removes
                // the leading above the first letter and the descent below the
                // last, and the tight lineHeight applies BETWEEN letters.
                Text(
                    text = label.toCharArray().joinToString("\n"),
                    color = currentTint,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
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
