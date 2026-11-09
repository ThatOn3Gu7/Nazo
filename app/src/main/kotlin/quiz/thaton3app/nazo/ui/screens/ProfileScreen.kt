package quiz.thaton3app.nazo.ui.screens

import java.io.File
import quiz.thaton3app.nazo.ui.components.CoverBackgroundSheet
import quiz.thaton3app.nazo.ui.components.TransitioningCoverArtwork
import quiz.thaton3app.nazo.ui.components.ProfileCoverPreviewDialog
import quiz.thaton3app.nazo.ui.components.ProfileCoverVideoPreviewDialog
import quiz.thaton3app.nazo.ui.components.ProfileImagePreviewDialog
import quiz.thaton3app.nazo.data.profile.AutomaticCoverState
import quiz.thaton3app.nazo.data.profile.AutomaticCoverStore
import quiz.thaton3app.nazo.data.profile.CoverVisual
import quiz.thaton3app.nazo.data.profile.ProfileImageStore
import quiz.thaton3app.nazo.data.profile.ProfileImageSource
import quiz.thaton3app.nazo.data.profile.resolveCoverVisual
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.activity.result.PickVisualMediaRequest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import quiz.thaton3app.nazo.ui.components.NazoConfirmButton
import quiz.thaton3app.nazo.ui.components.NazoDangerButton
import quiz.thaton3app.nazo.ui.components.NazoPrimaryButton
import quiz.thaton3app.nazo.ui.components.NazoSecondaryButton
import quiz.thaton3app.nazo.ui.components.Haptics
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import quiz.thaton3app.nazo.ui.components.rememberRetained
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.R
import quiz.thaton3app.nazo.data.QuizStats
import quiz.thaton3app.nazo.progression.PlayerProgress
import quiz.thaton3app.nazo.progression.PlayerProgression
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.settings.ApiKeyStore
import quiz.thaton3app.nazo.data.settings.QuizStatsStore
import quiz.thaton3app.nazo.data.profile.AvatarMedia
import quiz.thaton3app.nazo.ui.components.AnimatedAvatarImage
import quiz.thaton3app.nazo.ui.components.AvatarVideoView
import quiz.thaton3app.nazo.ui.components.AvatarAnchorId
import quiz.thaton3app.nazo.ui.components.ProfileAvatar
import quiz.thaton3app.nazo.ui.components.SafeRemoteImage
import quiz.thaton3app.nazo.ui.components.rememberAvatarPlayback
import quiz.thaton3app.nazo.ui.theme.*

val ProfileHeaderFont = FontFamily(
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

// --- Profile cover banner (expanded header + bottom fade) ------------------
//
// Facebook-style cover: a large, responsively tall rounded header at the
// top of the Profile screen (portrait 300–360.dp at 12.dp gutters;
// landscape a smaller responsive share inside the left column) with the
// avatar straddling its bottom edge — 90% of the circle inside, 10%
// hanging below. The header's bottom fades into NazoBackground so it never
// ends on a hard rectangle. It is drawn inside the screen subtree, which
// sits above the root-level particle background, so it hides the particles
// beneath it while they keep drifting behind everything else.
// Custom cover media: center-cropped photos plus validated, muted H.264 MP4
// loops. The picker previews first, then ProfileImageStore writes an app-owned
// file and only afterwards replaces coverPictureUri in preferences.
// Built-in collections remain in CoverBackgroundSheet alongside the custom
// photo/video; their preset id is separate, so switching never discards the
// custom medium. Automatic matching has its own flag and cached artifact, so
// it follows avatar changes without overwriting those manual values. The
// default gradient remains visible while preparation/loading is incomplete.

/** Avatar diameter used by the cover straddle (same 132.dp as before). */
private val CoverAvatarSize = 132.dp

/** Share of the avatar that hangs BELOW the cover's bottom edge (10%). */
private const val CoverOverhangFraction = 0.10f

/** Corner radius of the cover band. */
private val CoverCornerRadius = 20.dp

/**
 * Share of the cover's height taken by the bottom fade into the page
 * background. Sized as a fraction (not a fixed dp) so the blend keeps the
 * same proportions across the responsive header heights and orientations.
 */
private const val CoverBottomFadeFraction = 0.4f

/** Distance the avatar overhangs the cover edge: 10% of its diameter. */
internal fun coverOverhang(avatarSize: Dp): Dp = avatarSize * CoverOverhangFraction

/**
 * Top spacing between the cover band and the username row, so the row keeps
 * its original 16.dp distance from the avatar's bottom edge: the avatar
 * itself hangs [coverOverhang] past the band, so the spacer is that slice
 * plus the old avatar→username gap.
 */
internal fun coverContentGap(avatarSize: Dp): Dp = coverOverhang(avatarSize) + 16.dp

/**
 * Cover height, responsive to the window so the header is the dominant
 * element of the upper profile without starving the content below (which
 * scrolls in portrait, and inside each column in landscape):
 *  - portrait: 34 % of the screen height, clamped to 220–280.dp;
 *  - landscape: 34 % of the screen height, clamped to 110–170.dp.
 * These shorter heights lift the avatar away from the screen midpoint while
 * leaving the cover visually prominent. Tiny split-screen windows still
 * scroll rather than clipping the profile content.
 */
internal fun coverHeight(isLandscape: Boolean, screenHeightDp: Int): Dp {
    // Shorten the expanded header so the overlapping avatar sits clearly
    // above the screen midpoint while the cover still dominates the profile.
    val fraction = 0.34f
    val (min, max) = if (isLandscape) 110f to 170f else 220f to 280f
    return (screenHeightDp * fraction).coerceIn(min, max).dp
}

internal data class LandscapeProfileSpacing(
    val startPadding: Dp,
    val columnSpacing: Dp,
)

/**
 * Moves only the landscape cover/avatar column toward the cutout-safe edge.
 * Scaffold's start inset already clears the display cutout/system bars, so the
 * additional 24.dp content gutter can shrink as horizontal room increases.
 * Every dp removed there is added to the 32.dp column gap: left-column width
 * and the right-column start edge therefore remain unchanged. The left shift
 * ramps at 4% above 600dp and caps at 15dp, retaining a 9dp visual guard even
 * on wide screens while protecting narrow split-screen windows.
 */
internal fun landscapeProfileSpacing(screenWidthDp: Int): LandscapeProfileSpacing {
    val coverLeftShift = ((screenWidthDp - 600) * 0.04f).coerceIn(0f, 15f).dp
    return LandscapeProfileSpacing(
        startPadding = 24.dp - coverLeftShift,
        columnSpacing = 32.dp + coverLeftShift,
    )
}

/** The right pane already starts below the status bar; this is visual breathing room only. */
internal val LandscapeRightColumnTopPadding = 8.dp

/**
 * The banner's width/height ratio for the current configuration, shared
 * with the cover preview dialog so its frame is exactly WYSIWYG. Mirrors
 * the layout math: portrait = cover width (screen − 24.dp for the cover's
 * 12.dp gutters) over the responsive [coverHeight]; landscape = left column
 * (40 % of screen − 80.dp combined outer/column spacing) over the responsive
 * cover height, including the top-bar overlap. [landscapeProfileSpacing]
 * exchanges start gutter for inter-column spacing while preserving that 80.dp
 * total, so the preview width remains exact. See the landscape Row and portrait
 * Column in [ProfileScreen].
 */
internal fun coverPreviewAspectRatio(
    screenWidthDp: Int,
    screenHeightDp: Int,
    isLandscape: Boolean,
    coverTopExtensionDp: Float = 0f,
): Float {
    val width = if (isLandscape) {
        (screenWidthDp - 80) * 0.4f
    } else {
        (screenWidthDp - 24).toFloat()
    }
    val height = coverHeight(isLandscape, screenHeightDp).value + coverTopExtensionDp
    // Degenerate-width guard (absurd split-screen slivers): never hand the
    // preview an aspect ratio at or below 1:1.
    return (width / height).coerceAtLeast(1f)
}

/**
 * The app-bar chrome the cover extends upward into (64.dp bar plus the
 * orientation-specific gutter), as plain dp for the preview dialog and the
 * selection sheet's WYSIWYG frames. Keep in sync with `coverTopExtension`
 * inside [ProfileScreen].
 */
internal fun coverPreviewTopExtensionDp(isLandscape: Boolean): Float =
    64f + (if (isLandscape) 8f else 16f)

/**
 * The cover header itself: a large rounded rectangle drawn from the
 * resolved [CoverVisual] — the placeholder gradient, custom photo/video,
 * generated avatar-matched image/video, a bundled preset photo (using the same
 * `ContentScale.Crop` rules: fill, clip excess, never stretch), or a preset
 * animated gradient (procedural and lifecycle-aware). Opaque either way, so
 * it hides the particle
 * background beneath it (the root draws the ambient particle layer behind
 * the transparent screens).
 *
 * The bottom [CoverBottomFadeFraction] of the header fades into
 * [NazoBackground] so the expanded cover doesn't end on a hard rectangular
 * edge — the blend target is the exact color the page paints, which keeps
 * it correct across themes. The strip's square bottom corners are
 * invisible: they paint the same color as the page showing past the
 * header's rounded corners.
 *
 * Two deliberate no-clip rules in this Box:
 *  1. The Box's own background uses `background(brush, shape)` rather than
 *     `clip(shape)` so children — the straddling avatar — can overflow the
 *     bottom edge; a clip would slice the overhanging 10% off. The photo
 *     and the fade are therefore clipped/sized on their OWN nodes.
 *  2. Paint order is photo → fade → content (avatar) → edit slot: the
 *     avatar and the pencil draw above the fade and stay crisp on top of it.
 */
@Composable
private fun ProfileCoverBanner(
    height: Dp,
    visual: CoverVisual,
    videoPlaybackAllowed: Boolean,
    liveVideoAllowed: Boolean,
    transitionsAllowed: Boolean,
    modifier: Modifier = Modifier,
    editButton: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(listOf(NazoPrimary, NazoSurface)),
                shape = RoundedCornerShape(CoverCornerRadius),
            ),
    ) {
        // One bounded transition layer owns every artwork kind. It preloads
        // local media, crossfades static/gradient content, and keeps at most one
        // live video plane. Paint order stays artwork → fade → content, so the
        // avatar and edit affordance retain their original geometry and depth.
        TransitioningCoverArtwork(
            visual = visual,
            playbackAllowed = videoPlaybackAllowed,
            liveVideoAllowed = liveVideoAllowed,
            transitionsAllowed = transitionsAllowed,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(CoverCornerRadius)),
        )
        // Bottom fade into the page: a soft transparent → NazoBackground
        // gradient over the lower part of the header. Static drawing only —
        // no blur layer, no per-frame work (the modest-phone budget from
        // the particle/overlay work still holds), and the 3-stop curve
        // eases the transition so no hard boundary is visible.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(CoverBottomFadeFraction)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to NazoBackground.copy(alpha = 0.55f),
                        1f to NazoBackground,
                    )
                ),
        )
        content()
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            editButton()
        }
    }
}

/**
 * Pencil in the banner's bottom-right corner: opens the unified cover
 * selection sheet (photos, animated gradients, custom gallery option).
 * 44.dp target with 8.dp margins; it clears the straddling avatar even in the
 * narrowest supported landscape left column (40/60 split at 640.dp). The icon
 * carries the accessibility label; the clickable Box merges it so TalkBack
 * announces one "Change cover background" control.
 */
@Composable
private fun CoverEditButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Change cover background",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    username: String,
    profilePictureUri: String?,
    /** Cover banner medium: null, or our app-owned photo/video `file://`. */
    coverPictureUri: String? = null,
    /** Built-in cover preset id (`photo_*` / `gradient_*`); null = custom/default flow. */
    coverPresetId: String? = null,
    automaticCoverEnabled: Boolean = false,
    automaticCoverState: AutomaticCoverState = AutomaticCoverState.Idle,
    /** True only when Profile is the fully settled screen, never during overlap. */
    screenActive: Boolean = true,
    /**
     * False only for an ordinary Profile entry: show the prepared poster until
     * the screen settles, then create the live TextureView/decoder. Outgoing and
     * predictive transitions keep an existing surface attached.
     */
    coverVideoSurfaceAllowed: Boolean = true,
    /**
     * Remains true during an uncommitted predictive-back scrub, so the banner
     * video keeps advancing exactly like the persistent video avatar. Becomes
     * false only after navigation commits away from Profile.
     */
    coverVideoPlaybackAllowed: Boolean = screenActive,
    quizStats: QuizStats = QuizStats(),
    progression: PlayerProgress = PlayerProgression.from(quizStats),
    achievementsUnlocked: Int = 0,
    achievementCount: Int = 0,
    /** When true, never hit the network: use the local generator directly. */
    offline: Boolean = false,
    onBack: () -> Unit = {},
    onUsernameChange: (String) -> Unit,
    onProfilePictureChange: (String?) -> Unit,
    onCoverPictureChange: (String?) -> Unit = {},
    onCoverPresetChange: (String?) -> Unit = {},
    onAutomaticCoverChange: (Boolean) -> Unit = {},
    onPrepareAutomaticCover: () -> Unit = {},
    onStopPreparingAutomaticCover: () -> Unit = {},
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    // Rotation swaps the whole layout subtree, discarding plain `remember` and
    // closing any open dialog. rememberRetained holds these above that swap so
    // the dialog survives the orientation change. See RetainedState.kt.
    var showUsernameDialog by rememberRetained("Profile.showUsernameDialog") { false }
    val nicknameContext = LocalContext.current
    val apiKeyStore = remember(nicknameContext) { ApiKeyStore(nicknameContext) }
    val nicknameStats = remember(nicknameContext) { QuizStatsStore(nicknameContext) }
    val scope = rememberCoroutineScope()
    var showPictureDialog by rememberRetained("Profile.showPictureDialog") { false }
    // Press-and-hold the avatar to see the current picture full size.
    var showAvatarPreview by rememberRetained("Profile.showAvatarPreview") { false }
    var showUrlDialog by rememberRetained("Profile.showUrlDialog") { false }
    // Removing a picture is destructive from here (a custom photo would have
    // to be re-added by hand), so it gets one explicit confirmation step.
    var showRemoveConfirm by rememberRetained("Profile.showRemoveConfirm") { false }
    // Cover (banner) flow: the pencil's action sheet, the remove
    // confirmation (same destructive-action rule as the avatar's Remove),
    // and the picked source — retained so a rotation reopens the preview
    // dialog instead of dropping the pick (same rule as pendingSource).
    var showCoverSheet by rememberRetained("Profile.showCoverSheet") { false }
    var showCoverRemoveConfirm by rememberRetained("Profile.showCoverRemoveConfirm") { false }
    var pendingCoverSource by rememberRetained<ProfileImageSource?>("Profile.pendingCoverSource") { null }
    var pendingCoverIsVideo by rememberRetained("Profile.pendingCoverIsVideo") { false }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // A generated artifact is valid only for the avatar URI that produced it;
    // this guard prevents one frame of the old background after an avatar swap.
    val pendingAutomaticState = profilePictureUri?.let(AutomaticCoverState::Preparing)
        ?: AutomaticCoverState.Unavailable(
            null,
            "Add a photo or MP4 video avatar to preview automatic matching.",
        )
    val visibleAutomaticState = when (val state = automaticCoverState) {
        is AutomaticCoverState.Ready ->
            if (state.artifact.sourceUri == profilePictureUri) state else pendingAutomaticState
        is AutomaticCoverState.Preparing ->
            if (state.sourceUri == profilePictureUri) state else pendingAutomaticState
        is AutomaticCoverState.Unavailable ->
            if (state.sourceUri == profilePictureUri) state else pendingAutomaticState
        AutomaticCoverState.Idle -> state
    }
    val automaticArtifact = (visibleAutomaticState as? AutomaticCoverState.Ready)?.artifact
    val coverVisual = resolveCoverVisual(
        galleryUri = coverPictureUri,
        presetId = coverPresetId,
        automaticEnabled = automaticCoverEnabled,
        automaticArtifact = automaticArtifact,
    )

    // The image being previewed/cropped, and the temp file behind it (URL only).
    // Retained so a rotation does not throw away an edit in progress. The draft
    // FILE is now deleted here rather than from the dialog's onDispose, because
    // only this side can tell "the user backed out" (onDismiss) apart from "the
    // layout swapped shape" (dispose) -- see ProfileImagePreviewDialog.
    var pendingSource by rememberRetained<ProfileImageSource?>("Profile.pendingSource") { null }
    var pendingDraft by rememberRetained<File?>("Profile.pendingDraft") { null }

    // While any dialog covers this screen, the big avatar behind it holds its
    // current frame instead of animating (GIF decode / MediaCodec output) —
    // less work during the dialog's open animation, same visuals on return.
    val avatarBehindDialogsPaused =
        showPictureDialog || showAvatarPreview || showUrlDialog ||
            showRemoveConfirm || pendingSource != null ||
            pendingCoverSource != null || showCoverSheet || showCoverRemoveConfirm

    // PickVisualMedia is the system photo picker: a gallery grid with no
    // storage permission and no filesystem browsing. It replaces
    // OpenDocument, which dropped the user into a file manager.
    //
    // ImageAndVideo (was ImageOnly) so animated avatars can be picked: the
    // preview dialog sniffs the bytes and routes GIF/MP4 down the animated
    // path while photos keep the exact crop flow they have today. Unsupported
    // videos are rejected there with a specific message.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                // Read-only access for the preview/crop step. We never write
                // back to this URI -- accepting writes a fresh copy of our own,
                // so the user's original photo is untouched.
                pendingDraft = null
                pendingSource = ProfileImageSource.ContentUri(it)
            }
        }
    )

    // Separate photo/video launch actions keep the system picker's filter
    // honest. Both are previewed first; accepted media is copied into our
    // private cover directory, so no long-lived provider permission is needed.
    val coverPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                pendingCoverIsVideo = false
                pendingCoverSource = ProfileImageSource.ContentUri(it)
            }
        },
    )
    val coverVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                pendingCoverIsVideo = true
                pendingCoverSource = ProfileImageSource.ContentUri(it)
            }
        },
    )


    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                // Align the compact toolbar capsule with the cover's left edge,
                // inset slightly so it sits comfortably inside the artwork.
                title = {},
                navigationIcon = {
                    Surface(
                        // Match the pill's 8.dp top breathing room: portrait
                        // cover starts at 12.dp; landscape's left column at
                        // 24.dp. Add the same 8.dp inset in either orientation.
                        modifier = Modifier.padding(start = if (isLandscape) 32.dp else 20.dp),
                        shape = CircleShape,
                        color = NazoSurface.copy(alpha = 0.94f),
                        contentColor = NazoTextPrimary,
                        border = BorderStroke(1.dp, NazoPrimary.copy(alpha = 0.18f)),
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(end = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NazoTextPrimary,
                                )
                            }
                            Text(
                                "Profile",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = ProfileHeaderFont,
                                fontWeight = FontWeight.Bold,
                                color = NazoPrimary,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                )
            )
        }
    ) { padding ->
        // Only offered when there is actually a picture to preview; with no
        // picture set the avatar is just initials, so a hold would open an
        // empty viewer. Null disables the gesture and leaves the original
        // tap-only Surface path in place.
        val avatarLongPress: (() -> Unit)? = if (!profilePictureUri.isNullOrBlank()) {
            {
                Haptics.light(context)
                showAvatarPreview = true
            }
        } else null

        // One cover edit affordance for both orientations, handed to the
        // banner as a slot so the pencil and its menu anchor to the banner's
        // bottom-right corner regardless of layout.
        val coverEditButton: @Composable BoxScope.() -> Unit = {
            CoverEditButton(onClick = { showCoverSheet = true })
        }

        // Scaffold draws the transparent top app bar above its body content.
        // Start the body at the top of the window, then extend the cover by the
        // inset/gutter that used to sit above it. This puts the image behind the
        // toolbar without shifting the avatar or the content below the cover.
        val layoutDirection = LocalLayoutDirection.current
        // Keep the system status-bar region clear. Only overlap the app-bar
        // row itself; the status icons/clock/battery stay on the page surface.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val coverTopExtension = (padding.calculateTopPadding() - statusBarTop)
            .coerceAtLeast(0.dp) + (if (isLandscape) 8.dp else 16.dp)

        if (isLandscape) {
            // --- LANDSCAPE LAYOUT ---
            val landscapeSpacing = landscapeProfileSpacing(configuration.screenWidthDp)
            Row(
                modifier = Modifier
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = statusBarTop,
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding(),
                    )
                    .fillMaxSize()
                    .padding(
                        start = landscapeSpacing.startPadding,
                        end = 24.dp,
                        bottom = 8.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(landscapeSpacing.columnSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Cover & Username. The banner lives ENTIRELY
                // in this column (40/60 split) — it must not stretch across
                // the screen or push the stats side out of view.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Cover band: same straddling avatar as portrait, living
                    // entirely inside this column (40/60 split).
                    ProfileCoverBanner(
                        height = coverHeight(
                            isLandscape = true,
                            screenHeightDp = configuration.screenHeightDp,
                        ) + coverTopExtension,
                        visual = coverVisual,
                        videoPlaybackAllowed =
                            coverVideoPlaybackAllowed && !avatarBehindDialogsPaused,
                        liveVideoAllowed = coverVideoSurfaceAllowed,
                        transitionsAllowed = screenActive,
                        editButton = coverEditButton,
                    ) {
                        ProfileAvatar(
                            // Bottom-centered, pushed down by exactly the
                            // overhang so the 10% slice clears the band's edge.
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = coverOverhang(CoverAvatarSize)),
                            name = username,
                            pictureUri = profilePictureUri,
                            size = CoverAvatarSize,
                            onClick = { showPictureDialog = true },
                            onLongClick = avatarLongPress,
                            paused = avatarBehindDialogsPaused,
                            animationAllowed = screenActive,
                            anchorId = AvatarAnchorId.Profile,
                        )
                    }

                    // Keep the avatar's overhang clear of the row content.
                    Spacer(Modifier.height(coverContentGap(CoverAvatarSize)))

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

                    Spacer(Modifier.height(8.dp))
                    // Discloses both picture gestures; the press-and-hold
                    // preview was previously a hidden easter egg.
                    Text(
                        text = if (profilePictureUri.isNullOrBlank())
                            "Tap the picture to choose one"
                        else
                            "Tap the picture to change it · press and hold to preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                        textAlign = TextAlign.Center,
                    )

                }

                // Right Column: Stats & Menu. The parent already begins below
                // the status bar, so this pane must not reserve the toolbar's
                // cover-only overlap. Top arrangement keeps the statistics card
                // near the safe edge; verticalScroll still handles short windows.
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .padding(top = LandscapeRightColumnTopPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    ProfileProgressCard(
                        stats = quizStats,
                        progression = progression,
                        achievementsUnlocked = achievementsUnlocked,
                        achievementCount = achievementCount,
                        onClick = onNavigateToStatistics,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileMenuItem(
                            icon = Icons.Filled.BarChart,
                            title = "Statistics",
                            subtitle = "View your quiz insights",
                            onClick = onNavigateToStatistics
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
        } else {
            // --- PORTRAIT LAYOUT (Original) ---
            Column(
                modifier = Modifier
                    .padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = statusBarTop,
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = padding.calculateBottomPadding(),
                    )
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expanded cover: wider than the content (12.dp gutters
                // vs 24.dp) and responsively tall — the dominant element
                // of the upper profile — with the avatar straddling its
                // bottom edge: 90% inside the band, 10% below it.
                ProfileCoverBanner(
                    height = coverHeight(
                        isLandscape = false,
                        screenHeightDp = configuration.screenHeightDp,
                    ) + coverTopExtension,
                    visual = coverVisual,
                    videoPlaybackAllowed =
                        coverVideoPlaybackAllowed && !avatarBehindDialogsPaused,
                    liveVideoAllowed = coverVideoSurfaceAllowed,
                    transitionsAllowed = screenActive,
                    editButton = coverEditButton,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    ProfileAvatar(
                        // Bottom-centered, pushed down by exactly the
                        // overhang so the 10% slice clears the band's edge.
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = coverOverhang(CoverAvatarSize)),
                        name = username,
                        pictureUri = profilePictureUri,
                        size = CoverAvatarSize,
                        onClick = { showPictureDialog = true },
                        onLongClick = avatarLongPress,
                        paused = avatarBehindDialogsPaused,
                        animationAllowed = screenActive,
                        anchorId = AvatarAnchorId.Profile,
                    )
                }

                Spacer(Modifier.height(coverContentGap(CoverAvatarSize)))

                // Everything below keeps the original 24.dp gutters, so
                // the wider cover doesn't disturb the content's rhythm.
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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

                    Spacer(Modifier.height(8.dp))
                    // Discloses both picture gestures; the press-and-hold preview
                    // was previously a hidden easter egg.
                    Text(
                        text = if (profilePictureUri.isNullOrBlank())
                            "Tap the picture to choose one"
                        else
                            "Tap the picture to change it · press and hold to preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    ProfileProgressCard(
                        stats = quizStats,
                        progression = progression,
                        achievementsUnlocked = achievementsUnlocked,
                        achievementCount = achievementCount,
                        onClick = onNavigateToStatistics,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileMenuItem(
                            icon = Icons.Filled.BarChart,
                            title = "Statistics",
                            subtitle = "View your quiz insights",
                            onClick = onNavigateToStatistics
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
    }

    // Scaffold(
    //     containerColor = Color.Transparent,
    //     topBar = {
    //         CenterAlignedTopAppBar(
    //             title = {
    //                 Text(
    //                     "Profile",
    //                     style = MaterialTheme.typography.headlineSmall,
    //                     fontFamily = ProfileHeaderFont,
    //                     fontWeight = FontWeight.Bold,
    //                     color = NazoPrimary,
    //                     letterSpacing = 0.5.sp
    //                 )
    //             },
    //             navigationIcon = {
    //                 IconButton(onClick = onBack) {
    //                     Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    //                 }
    //             },
    //             colors = TopAppBarDefaults.topAppBarColors(
    //                 containerColor = Color.Transparent
    //             )
    //         )
    //     }
    // ) { padding ->
    //     Column(
    //         modifier = Modifier
    //             .padding(padding)
    //             .fillMaxSize()
    //             .verticalScroll(rememberScrollState())
    //             .padding(horizontal = 24.dp, vertical = 16.dp),
    //         horizontalAlignment = Alignment.CenterHorizontally
    //     ) {
    //         ProfileAvatar(
    //             name = username,
    //             pictureUri = profilePictureUri,
    //             size = 132.dp,
    //             onClick = { showPictureDialog = true },
    //             modifier = Modifier.padding(bottom = 16.dp),
    //         )
    //
    //         Surface(
    //             onClick = { showUsernameDialog = true },
    //             shape = MaterialTheme.shapes.extraLarge,
    //             color = Color.Transparent,
    //             modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
    //         ) {
    //             Row(
    //                 verticalAlignment = Alignment.CenterVertically,
    //                 horizontalArrangement = Arrangement.Center,
    //                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    //             ) {
    //                 Text(
    //                     text = username.ifBlank {
    //                         if (quizStats.totalQuizzes > 0) "Edit Profile" else "Create Profile"
    //                     },
    //                     style = MaterialTheme.typography.headlineMedium,
    //                     fontWeight = FontWeight.Bold,
    //                     maxLines = 1,
    //                     overflow = TextOverflow.Ellipsis,
    //                     color = NazoTextPrimary
    //                 )
    //                 Spacer(modifier = Modifier.width(8.dp))
    //                 Icon(
    //                     imageVector = Icons.Rounded.Edit,
    //                     contentDescription = "Edit username",
    //                     tint = NazoPrimary,
    //                     modifier = Modifier.size(22.dp)
    //                 )
    //             }
    //         }
    //
    //         Spacer(modifier = Modifier.height(32.dp))
    //
    //         if (quizStats.totalQuizzes > 0) {
    //             ProfileStatsCard(stats = quizStats)
    //             Spacer(modifier = Modifier.height(24.dp))
    //         }
    //
    //         Column(
    //             modifier = Modifier.fillMaxWidth(),
    //             verticalArrangement = Arrangement.spacedBy(12.dp)
    //         ) {
    //             ProfileMenuItem(
    //                 icon = Icons.Filled.BarChart,
    //                 title = "Statistics",
    //                 subtitle = "View your quiz insights",
    //                 onClick = onNavigateToStatistics
    //             )
    //             ProfileMenuItem(
    //                 icon = Icons.Filled.Settings,
    //                 title = "Settings",
    //                 subtitle = "Appearance, categories, backup",
    //                 onClick = onNavigateToSettings
    //             )
    //         }
    //     }
    // }

    // --- Dialogs ---

    if (showUsernameDialog) {
        var text by remember { mutableStateOf(username) }
        // Scoped to the dialog so state resets naturally when it closes and a
        // recomposition can never trigger a generation on its own.
        var generatingName by remember { mutableStateOf(false) }
        var nameError by remember { mutableStateOf<String?>(null) }
        // Every handle offered while this dialog has been open. Sent back to the
        // model as a do-not-repeat list; an identical request otherwise returns
        // an identical name however many times Refresh is tapped.
        val suggested = remember { mutableStateListOf<String>() }
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
                    enabled = !generatingName,
                    trailingIcon = {
                        // One button, two behaviours. With a generation-ready
                        // provider it asks the model; without one it falls back
                        // to the local generator silently — no API error for a
                        // user who never configured a key.
                        IconButton(
                            enabled = !generatingName,
                            onClick = {
                                val provider = apiKeyStore.getGenerationProvider()
                                val key = provider?.let { apiKeyStore.getKey(it) }
                                val model = provider?.let { apiKeyStore.getModel(it) }
                                // Offline: go straight to the local generator.
                                // Attempting the request would spin for the
                                // whole timeout and then show a network error
                                // for something we can do instantly on-device.
                                if (offline ||
                                    provider == null ||
                                    key.isNullOrBlank() ||
                                    model.isNullOrBlank()
                                ) {
                                    // Local generator: keep drawing until it
                                    // differs from what is already in the field,
                                    // so a tap always visibly changes something.
                                    var local = randomAnimeUsername()
                                    var tries = 0
                                    while (local == text && tries < 8) {
                                        local = randomAnimeUsername()
                                        tries++
                                    }
                                    text = local
                                    return@IconButton
                                }
                                // Guard against a second tap while in flight, so
                                // one press can never become two requests.
                                if (generatingName) return@IconButton
                                generatingName = true
                                nameError = null
                                scope.launch {
                                    // The series the player has answered most
                                    // questions about, so the handle reflects
                                    // what they actually play. Empty on a fresh
                                    // install, which just yields a generic name.
                                    val favourites = nicknameStats.get().animeAnswered
                                        .entries
                                        .sortedByDescending { it.value }
                                        .take(3)
                                        .map { it.key }
                                    val result = ApiClient.generateNickname(
                                        provider, key, model, favourites,
                                        avoid = suggested.toList() + text.trim(),
                                    )
                                    // The dialog may have been dismissed while
                                    // the request was running.
                                    result
                                        .onSuccess {
                                            text = it
                                            suggested += it
                                        }
                                        .onFailure {
                                            // Keep whatever the user had; the
                                            // local generator stays available.
                                            nameError = "Couldn't reach the AI — tap again or edit manually"
                                        }
                                    generatingName = false
                                }
                            },
                        ) {
                            if (generatingName) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Generate a username"
                                )
                            }
                        }
                    },
                    // Always present, so the field does not jump as the error
                    // appears and disappears; AnimatedVisibility fades and
                    // expands the text instead of snapping it in.
                    supportingText = {
                        AnimatedVisibility(
                            visible = nameError != null,
                            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                        ) {
                            Text(
                                text = nameError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    isError = nameError != null
                )
            },
            confirmButton = {
                NazoConfirmButton(onClick = {
                    onUsernameChange(text.trim())
                    showUsernameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                NazoSecondaryButton(onClick = { showUsernameDialog = false }, muted = true) {
                    Text("Cancel")
                }
            }
        )
    }

    // Press-and-hold preview of the avatar. The stored picture is already
    // square, so ContentScale.Fit shows exactly what is saved -- including the
    // edges the circular avatar crops off, which is the point of a preview.
    // Sized off the SHORTER screen edge so it fits landscape as well as
    // portrait. Tapping outside or pressing back closes it; nothing here can
    // change the picture, it is a viewer only.
    if (showAvatarPreview && !profilePictureUri.isNullOrBlank()) {
        val uri = profilePictureUri.orEmpty()
        val previewSize = (
            minOf(configuration.screenWidthDp, configuration.screenHeightDp) * 0.62f
            ).dp.coerceIn(160.dp, 320.dp)
        // Animated avatars play in the viewer too: muted looping video
        // (letterboxed — same "show exactly what is saved" semantics as the
        // Fit image) or the GIF animating through Coil, both behind the same
        // visibility/foreground gate as the circles.
        val previewPlayback = rememberAvatarPlayback()
        var previewVideoFailed by remember(uri) { mutableStateOf(false) }
        Dialog(onDismissRequest = { showAvatarPreview = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = NazoSurface,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .size(previewSize)
                        .onGloballyPositioned(previewPlayback::onPositioned),
                    contentAlignment = Alignment.Center,
                ) {
                    // Visible way out — the viewer previously closed only by
                    // tapping outside or Back, which nothing advertised.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NazoSurfaceVariant)
                            .clickable { showAvatarPreview = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close preview",
                            tint = NazoTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    when {
                        uri.startsWith("emoji:") -> Text(
                            text = uri.removePrefix("emoji:"),
                            fontSize = (previewSize.value * 0.5f).sp,
                            textAlign = TextAlign.Center,
                        )

                        AvatarMedia.displayKind(uri) == AvatarMedia.DisplayKind.Video ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (previewVideoFailed) {
                                    Text(
                                        "Couldn\u2019t load this picture",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NazoTextSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    AvatarVideoView(
                                        uri = uri,
                                        fit = true,
                                        playback = previewPlayback,
                                        modifier = Modifier.fillMaxSize(),
                                        onFailed = { previewVideoFailed = true },
                                    )
                                }
                            }

                        else -> AnimatedAvatarImage(
                            url = uri,
                            contentDescription = "Profile picture preview",
                            playback = previewPlayback,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            placeholder = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            errorContent = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Couldn\u2019t load this picture",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NazoTextSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
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

        // LANDSCAPE SPACING ONLY -- portrait is untouched.
        //
        // A landscape phone gives this dialog roughly 330dp of height, but the
        // portrait layout asks for about 500dp (title, description, tab row,
        // the two spacers, a fixed 260dp avatar grid and the action row). The
        // AlertDialog text slot is the only flexible part, so it absorbed the
        // whole overflow: the grid collapsed to well under one row of avatars
        // and the whole thing bunched up against the buttons.
        //
        // Fix: in landscape the CONTENT scrolls as a whole and the grid drops
        // its fixed cap, so nothing has to be squeezed to fit and every control
        // keeps its natural size. Portrait keeps the original behaviour (header
        // and tabs pinned, only the grid scrolls) -- the two scrollers are
        // mutually exclusive, so they can never fight.
        val contentScroll = rememberScrollState()
        val gridScroll = rememberScrollState()

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
                // The header and tabs stay put; only the avatar grid scrolls.
                // Nesting a scroller inside a scrolling Column would make the
                // two fight, so the outer Column must NOT scroll.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) Modifier.verticalScroll(contentScroll)
                            else Modifier
                        )
                ) {
                    Text(
                        "Pick a default avatar, or upload your own photo, GIF or video. Tap refresh for a new batch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NazoTextSecondary
                    )
                    Spacer(Modifier.height(if (isLandscape) 12.dp else 16.dp))
                    PrimaryScrollableTabRow(
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
                    Spacer(Modifier.height(if (isLandscape) 16.dp else 24.dp))
                    // Some categories (Anime, Pixel) return far more presets
                    // than the others, which stretched the dialog to the full
                    // screen height. Capping it keeps every tab the same size
                    // and lets the long ones scroll in place.
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isLandscape) Modifier
                                else Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(gridScroll)
                            ),
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
                    if (!profilePictureUri.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        // The full-size viewer used to be reachable only by an
                        // undisclosed press-and-hold; this is its visible route.
                        TextButton(
                            onClick = {
                                showPictureDialog = false
                                showAvatarPreview = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("View current picture full size", color = NazoPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                // One row of equal-width actions rather than a FlowRow that
                // wrapped Remove onto its own line. Each source button is
                // outlined; Remove is a filled red container so a destructive
                // action reads as destructive.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NazoSecondaryButton(
                        onClick = {
                            showPictureDialog = false
                            showUrlDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text("URL", maxLines = 1)
                    }

                    NazoSecondaryButton(
                        onClick = {
                            showPictureDialog = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    // Videos included so MP4 avatars can be
                                    // picked; the preview dialog validates the
                                    // bytes and rejects anything unsupported.
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text("Gallery", maxLines = 1)
                    }

                    if (!profilePictureUri.isNullOrBlank()) {
                        NazoDangerButton(
                            onClick = {
                                // Destructive from here (a custom photo must be
                                // re-added by hand) — one confirmation first.
                                showRemoveConfirm = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text("Remove", maxLines = 1)
                        }
                    }
                }
            }
        )
    }

    // Remove-picture confirmation. Overlays the picker (which stays open
    // behind), so Cancel returns straight to the choices.
    if (showRemoveConfirm) {
        // Name what is actually being removed — a video/GIF avatar isn't a
        // "photo". Stored URIs are ours to name (extension + emoji: prefix),
        // so this needs no disk access. Kept in one phrase with its article
        // so the sentence below always reads correctly.
        val removedNoun = when {
            AvatarMedia.displayKind(profilePictureUri) == AvatarMedia.DisplayKind.Video ->
                "a video avatar"
            AvatarMedia.isAnimatedImageUri(profilePictureUri) -> "an animated avatar"
            profilePictureUri?.startsWith("emoji:") == true -> "an emoji"
            else -> "a photo"
        }
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = NazoError) },
            title = { Text("Remove profile picture?") },
            text = {
                Text(
                    "Your avatar goes back to your initial letter. To use $removedNoun again " +
                        "you would need to choose or add one again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                NazoDangerButton(onClick = {
                    Haptics.doubleLight(context)
                    onProfilePictureChange(null)
                    showRemoveConfirm = false
                    showPictureDialog = false
                }) {
                    Text("Remove", maxLines = 1)
                }
            },
            dismissButton = {
                NazoSecondaryButton(onClick = { showRemoveConfirm = false }, muted = true) {
                    Text("Cancel", maxLines = 1)
                }
            },
        )
    }

    if (showUrlDialog) {
        var url by remember {
            mutableStateOf(profilePictureUri?.takeIf { !it.startsWith("emoji:") } ?: "")
        }
        var fetching by remember { mutableStateOf(false) }
        var fetchError by remember { mutableStateOf<String?>(null) }
        // The image fetched for the inline square preview. Held as a draft file
        // so that accepting or cropping does not download it a second time.
        var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var previewDraft by remember { mutableStateOf<File?>(null) }
        val urlScope = rememberCoroutineScope()

        fun clearPreview() {
            ProfileImageStore.discardDraft(previewDraft)
            previewDraft = null
            previewBitmap = null
        }

        fun closeUrlDialog() {
            // Abandoning the dialog must not strand the downloaded temp file.
            clearPreview()
            showUrlDialog = false
        }

        fun fetch() {
            val target = url.trim()
            if (target.isBlank() || fetching) return
            fetching = true
            fetchError = null
            urlScope.launch {
                clearPreview()
                val result = ProfileImageStore.downloadDraft(context, target)
                result.onSuccess { file ->
                    val head = ProfileImageStore.readHead(
                        context,
                        ProfileImageSource.LocalFile(file),
                    )
                    if (head != null && AvatarMedia.isAnimatedPick(head)) {
                        // An animated download (a GIF link, say): hand it
                        // straight to the shared preview step, which validates
                        // and shows it looping before anything is saved. Draft
                        // ownership moves to pendingDraft WITHOUT discarding —
                        // clearPreview() must not delete it on the way out.
                        previewDraft = null
                        previewBitmap = null
                        showUrlDialog = false
                        pendingDraft = file
                        pendingSource = ProfileImageSource.LocalFile(file)
                        return@onSuccess
                    }
                    val decoded = ProfileImageStore.decodeForEditing(
                        context,
                        ProfileImageSource.LocalFile(file),
                        maxEdge = 1024,
                    )
                    if (decoded == null) {
                        ProfileImageStore.discardDraft(file)
                        fetchError = "That link doesn't point to an image we can read."
                    } else {
                        previewDraft = file
                        previewBitmap = decoded
                    }
                }.onFailure { error ->
                    fetchError = error.message ?: "Couldn't load that image."
                }
                fetching = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!fetching) closeUrlDialog() },
            icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            title = { Text("Picture from URL") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Square preview sits between the title and the field, so
                    // the user sees the actual image before accepting it.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(NazoSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val shown = previewBitmap
                        when {
                            fetching -> CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = NazoPrimary,
                            )

                            shown != null -> Image(
                                bitmap = shown.asImageBitmap(),
                                contentDescription = "Image from the entered link",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            else -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = NazoTextSecondary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Paste a link, then tap Load",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NazoTextSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            // The preview no longer matches the typed link.
                            if (previewBitmap != null) clearPreview()
                            fetchError = null
                        },
                        singleLine = true,
                        placeholder = { Text("https://...") },
                        shape = MaterialTheme.shapes.large,
                        isError = fetchError != null,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { fetch() }),
                        // Loading belongs to the field, not to a separate
                        // button floating below it.
                        trailingIcon = {
                            if (fetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NazoPrimary,
                                )
                            } else {
                                IconButton(
                                    onClick = { fetch() },
                                    enabled = url.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = if (previewBitmap != null) {
                                            Icons.Filled.Refresh
                                        } else {
                                            Icons.AutoMirrored.Filled.ArrowForward
                                        },
                                        contentDescription =
                                            if (previewBitmap != null) "Reload image" else "Load image",
                                        tint = if (url.isNotBlank()) NazoPrimary else NazoTextSecondary,
                                    )
                                }
                            }
                        },
                    )

                    // Fades/expands rather than snapping the dialog taller.
                    AnimatedVisibility(
                        visible = fetchError != null,
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = fetchError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = NazoError,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                }
            },
            confirmButton = {
                // Single action bar, same order as the preview dialog:
                // Cancel | Crop | Accept. Splitting these between
                // confirmButton and dismissButton made them wrap unpredictably.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NazoSecondaryButton(
                        onClick = { closeUrlDialog() },
                        enabled = !fetching,
                        muted = true,
                        // Weighted flank buttons + single-line labels: a narrow
                        // dialog used to crush "Accept" until its last letter
                        // wrapped and broke the row height.
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", maxLines = 1)
                    }
                    if (previewBitmap != null) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                // Hand the downloaded draft to the shared crop
                                // step, which takes ownership of the file.
                                val file = previewDraft
                                previewDraft = null
                                previewBitmap = null
                                showUrlDialog = false
                                if (file != null) {
                                    pendingDraft = file
                                    pendingSource = ProfileImageSource.LocalFile(file)
                                }
                            },
                            enabled = !fetching,
                        ) {
                            Icon(
                                Icons.Filled.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = NazoPrimary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Crop", color = NazoPrimary, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    NazoPrimaryButton(
                        onClick = {
                            val bitmap = previewBitmap ?: return@NazoPrimaryButton
                            urlScope.launch {
                                // Square it so the saved avatar matches the
                                // square preview shown above.
                                val squared = ProfileImageStore.centerCropSquare(bitmap)
                                ProfileImageStore.saveAvatar(context, squared)
                                    .onSuccess { saved ->
                                        onProfilePictureChange(saved)
                                        clearPreview()
                                        showUrlDialog = false
                                    }
                            }
                        },
                        // Accept is only meaningful once an image is loaded, so
                        // it is disabled rather than doubling as a Load button.
                        enabled = previewBitmap != null && !fetching,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Accept", maxLines = 1)
                    }
                }
            },
        )
    }

    // Shared preview + crop step. Gallery picks arrive here directly; URL
    // images arrive here already downloaded to a draft file.
    val activeSource = pendingSource
    if (activeSource != null) {
        ProfileImagePreviewDialog(
            source = activeSource,
            draftFile = pendingDraft,
            onDismiss = {
                // A real user-initiated exit, so the temp download goes with it.
                ProfileImageStore.discardDraft(pendingDraft)
                pendingSource = null
                pendingDraft = null
            },
            onAccepted = { savedUri ->
                // The dialog already consumed and discarded the draft.
                onProfilePictureChange(savedUri)
                pendingSource = null
                pendingDraft = null
            },
            stateKeyPrefix = "Profile",
        )
    }

    // Cover pick preview: validate → WYSIWYG banner frame → safe app-private
    // save. Video validation also proves an H.264 decoder can produce a frame.
    val activeCoverSource = pendingCoverSource
    if (activeCoverSource != null) {
        val dismissCoverPreview = {
            pendingCoverSource = null
            pendingCoverIsVideo = false
        }
        val acceptCover: (String) -> Unit = { savedUri ->
            pendingCoverSource = null
            pendingCoverIsVideo = false
            onAutomaticCoverChange(false)
            onCoverPictureChange(savedUri)
            // The new custom medium should show immediately. The old custom
            // file is only pruned after this save has fully validated.
            onCoverPresetChange(null)
        }
        if (pendingCoverIsVideo && activeCoverSource is ProfileImageSource.ContentUri) {
            ProfileCoverVideoPreviewDialog(
                source = activeCoverSource,
                onDismiss = dismissCoverPreview,
                onAccepted = acceptCover,
            )
        } else {
            ProfileCoverPreviewDialog(
                source = activeCoverSource,
                onDismiss = dismissCoverPreview,
                onAccepted = acceptCover,
            )
        }
    }


    // Unified cover selection: bundled photos, animated gradients, and the
    // custom gallery option, each previewed at the real banner ratio.
    // Applying a preset writes only the preset id — the stored photo file
    // and its uri stay untouched, so "Your photo" switches back for free.
    if (showCoverSheet) {
        CoverBackgroundSheet(
            galleryUri = coverPictureUri,
            presetId = coverPresetId,
            automaticEnabled = automaticCoverEnabled,
            automaticState = visibleAutomaticState,
            bannerRatio = coverPreviewAspectRatio(
                screenWidthDp = configuration.screenWidthDp,
                screenHeightDp = configuration.screenHeightDp,
                isLandscape = isLandscape,
                coverTopExtensionDp = coverPreviewTopExtensionDp(isLandscape),
            ),
            onDismiss = { showCoverSheet = false },
            onPrepareAutomatic = onPrepareAutomaticCover,
            onStopPreparingAutomatic = onStopPreparingAutomaticCover,
            onSelectAutomatic = { onAutomaticCoverChange(true) },
            onSelectPreset = { id ->
                onAutomaticCoverChange(false)
                onCoverPresetChange(id)
            },
            onUseCustomMedia = {
                onAutomaticCoverChange(false)
                onCoverPresetChange(null)
            },
            onPickPhoto = {
                showCoverSheet = false
                coverPhotoLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onPickVideo = {
                showCoverSheet = false
                coverVideoLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.VideoOnly
                    )
                )
            },
            onRemove = {
                showCoverSheet = false
                showCoverRemoveConfirm = true
            },
        )
    }

    // Remove-cover confirmation — the same destructive-action step the
    // avatar's Remove uses; Cancel leaves the photo untouched.
    if (showCoverRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showCoverRemoveConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = NazoError) },
            title = { Text("Remove cover background?") },
            text = {
                Text(
                    if (automaticCoverEnabled) {
                        "Automatic matching will be turned off and your profile will return " +
                            "to the plain cover background."
                    } else if (!coverPictureUri.isNullOrBlank()) {
                        "Your profile goes back to the plain cover background. To use " +
                            "this photo or video again you would need to choose it again."
                    } else {
                        "Your profile goes back to the plain cover background."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                NazoDangerButton(onClick = {
                    Haptics.doubleLight(context)
                    onAutomaticCoverChange(false)
                    AutomaticCoverStore.discardAll(context)
                    ProfileImageStore.deleteCover(coverPictureUri)
                    onCoverPictureChange(null)
                    // Removing clears BOTH layers (custom file + preset id)
                    // back to the placeholder — same destructive step as
                    // before, with the same confirmation.
                    onCoverPresetChange(null)
                    showCoverRemoveConfirm = false
                }) {
                    Text("Remove", maxLines = 1)
                }
            },
            dismissButton = {
                NazoSecondaryButton(onClick = { showCoverRemoveConfirm = false }, muted = true) {
                    Text("Cancel", maxLines = 1)
                }
            },
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
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NazoPrimary.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NazoSurfaceVariant.copy(alpha = 0.5f)),
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
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun ProfileStatCell(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NazoPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun ProfileProgressCard(
    stats: QuizStats,
    progression: PlayerProgress,
    achievementsUnlocked: Int,
    achievementCount: Int,
    onClick: () -> Unit,
) {
    val accuracy = if (stats.totalQuestionsAnswered > 0) {
        stats.totalCorrect * 100 / stats.totalQuestionsAnswered
    } else {
        0
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NazoPrimary.copy(alpha = 0.30f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = NazoSurfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NazoPrimary.copy(alpha = 0.14f))
                        .border(1.dp, NazoPrimary.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "LV",
                            style = MaterialTheme.typography.labelSmall,
                            color = NazoTextSecondary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            progression.level.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = NazoPrimary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Level ${progression.level} Otaku",
                        style = MaterialTheme.typography.titleMedium,
                        color = NazoTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${progression.totalXp} lifetime XP · $achievementsUnlocked/$achievementCount badges",
                        style = MaterialTheme.typography.bodySmall,
                        color = NazoTextSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View progression and insights",
                    tint = NazoPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NazoPrimary.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progression.progressFraction.coerceAtLeast(0.001f))
                        .height(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(NazoPrimary.copy(alpha = 0.72f), NazoPrimary),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${progression.currentLevelXp} / ${progression.xpPerLevel} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoTextSecondary,
                )
                Text(
                    "${progression.xpToNextLevel} XP to Level ${progression.level + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NazoPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = NazoTextSecondary.copy(alpha = 0.10f),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ProfileStatCell(
                    icon = Icons.Filled.Quiz,
                    value = stats.totalQuizzes.toString(),
                    label = "Games",
                    modifier = Modifier.weight(1f),
                )
                ProfileStatCell(
                    icon = Icons.Filled.TrackChanges,
                    value = "$accuracy%",
                    label = "Accuracy",
                    modifier = Modifier.weight(1f),
                )
                ProfileStatCell(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = stats.currentStreakDays.toString(),
                    label = "Streak",
                    modifier = Modifier.weight(1f),
                )
                ProfileStatCell(
                    icon = Icons.Filled.EmojiEvents,
                    value = achievementsUnlocked.toString(),
                    label = "Badges",
                    modifier = Modifier.weight(1f),
                )
            }
        }
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

/**
 * Emoji avatars. The `emoji:` picture format has always been supported end to
 * end (avatar rendering, full-size preview, backup payloads) — this list is
 * its first visible entry point in the picker.
 */
private val EMOJI_CHOICES = listOf(
    "⚡", "🔥", "🌙", "⭐", "🌸", "🍀", "👻", "✨",
    "🦊", "🐱", "🎮", "📚", "🎯", "🍜", "💤", "😎",
)

/** Static tabs — rebuilt instantly on every refresh (new nonce = new art). */
private fun buildStaticAvatarCategories(nonce: String): List<Pair<String, List<AvatarPreset>>> = listOf(
    "Emoji" to EMOJI_CHOICES.map { AvatarPreset.Emoji(it) },
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
