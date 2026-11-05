package quiz.thaton3app.nazo.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import quiz.thaton3app.nazo.data.profile.AvatarMedia
import quiz.thaton3app.nazo.ui.Screen
import kotlin.math.roundToInt

/**
 * The persistent, app-level video-avatar rendering surface.
 *
 * The Home and Profile circles used to each own a `TextureView`, so every
 * screen change handed the shared [AvatarVideoHost] player from one surface
 * to another — a gap the poster frame could only paper over. This component
 * flips that around: ONE `TextureView` is composed here, at the NazoApp root,
 * for as long as the saved avatar is a video, and it is merely MOVED and
 * RESIZED over whichever screen's avatar slot is currently active
 * (its [AvatarAnchorState.restBounds], reported by [ProfileAvatar]).
 *
 * Three pieces make that work:
 *
 *  - **Position** comes from the anchor's window bounds captured while the
 *    screens are at rest, plus a slide replicated from the screen transition
 *    ([avatarOverlayTransform]) while predictive-back motion runs. Bounds
 *    are the anchor's `positionInWindow` + layout size — never clipped to
 *    the window — so a circle sliding off the edge keeps its full size.
 *  - **Alpha** is replicated from the very same transitionSpec branches that
 *    animate the screens (co-located rule: change one, change the other), so
 *    the circle fades exactly with the screen it belongs to — including the
 *    finger-scrubbed predictive-back gesture. Compose exposes no API to read
 *    an ancestor's draw alpha, and position callbacks trail a graphicsLayer
 *    slide by a frame, so both values are mirrored from the transition state
 *    instead of read back.
 *  - **Lifetime** is independent of navigation: the AndroidView is created
 *    once (first anchor position) and disposed only when the avatar stops
 *    being a video. The player therefore never re-prepares and the surface
 *    never blinks across Home ↔ Profile, predictive back, or resume.
 *
 * The cached poster frame ([AvatarFrameCache]) remains as the underlay
 * inside the anchor slot: it covers genuine initialization (cold start, first
 * frames after a re-prepare) and every state the overlay is hidden for
 * (dialogs, backgrounding, screens without an avatar). Whenever the overlay
 * goes shown → hidden while it was genuinely visible, the frame on screen is
 * captured into the cache so the slot underneath shows exactly what was just
 * visible — never a jump. Navigation BETWEEN avatar screens keeps the
 * overlay shown throughout and performs no bitmap captures at all.
 */

// ---------------------------------------------------------------------------
// Overlay transform (pure — mirrors the screen transitionSpec in NazoApp)
// ---------------------------------------------------------------------------

/** What the overlay should look like for one frame of the screen transition. */
internal data class AvatarOverlayTransform(
    /** 0f..1f — multiplied into the overlay's draw alpha. */
    val alpha: Float,
    /** Horizontal slide in px, matching the predictive-back slide specs. */
    val slideX: Float,
)

/**
 * Replicates the alpha and slide the screen transition applies to the
 * avatar's host screen for the [resolveAvatarWinnerScreen] winner.
 *
 * **Co-located rule:** the branch conditions and durations below mirror the
 * `transitionSpec` of `screenTransition.AnimatedContent` in NazoApp.kt
 * one-for-one. If that spec changes, this must change with it. (Both
 * `SeekableTransitionState.seekTo` and `animateTo` drive child animations
 * from `fraction × totalDuration` with each animation's own easing applied —
 * verified against androidx `Transition.kt` — so `FastOutSlowIn` over the
 * fraction is exactly what the screen draws.)
 *
 * Pure on purpose: unit tests pin every branch without a device.
 *
 * @param fraction `SeekableTransitionState.fraction` — linear 0..1.
 * @param transitioning `currentState != targetState`.
 * @param predictiveTargetPresent `predictiveBackTarget != null`.
 * @param targetIsPredictive `targetState == predictiveBackTarget`.
 * @param initialIsPredictive `initialState == predictiveBackTarget`.
 * @param winnerIsTarget whether [resolveAvatarWinnerScreen] picked the
 *   transition's target (incoming) side — decides fade-in vs fade-out in
 *   the ordinary branch.
 * @param edgeDirection `predictiveBackEdgeDirection` (±1).
 * @param containerWidth screen content width in px (after the rail inset).
 */
internal fun avatarOverlayTransform(
    fraction: Float,
    transitioning: Boolean,
    predictiveTargetPresent: Boolean,
    targetIsPredictive: Boolean,
    initialIsPredictive: Boolean,
    winnerIsTarget: Boolean,
    edgeDirection: Int,
    containerWidth: Float,
): AvatarOverlayTransform {
    if (!transitioning) return AvatarOverlayTransform(alpha = 1f, slideX = 0f)
    val eased: (Float) -> Float = { FastOutSlowInEasing.transform(it.coerceIn(0f, 1f)) }
    return when {
        predictiveTargetPresent && targetIsPredictive -> AvatarOverlayTransform(
            // Branch 1 of the spec: the screen being LEFT slides toward the
            // edge (tween 220) and fades out (tween 220) while the revealed
            // screen comes in behind it. The winner is that left screen.
            alpha = 1f - eased(fraction),
            slideX = containerWidth * edgeDirection * eased(fraction),
        )

        predictiveTargetPresent && initialIsPredictive -> AvatarOverlayTransform(
            // Branch 2: the cancelled gesture settles back — the screen we
            // stay on slides home (tween 220) while its fade-in (tween 180)
            // runs against the segment's 220 ms total.
            alpha = eased(fraction * 220f / 180f),
            slideX = containerWidth * edgeDirection * (1f - eased(fraction)),
        )

        // Ordinary navigation: pure fade, no horizontal movement — the
        // incoming screen fades in (tween 220); if the winner is somehow the
        // outgoing side (navigation started while a settle was still
        // running), it rides the exit fade (tween 160) instead.
        winnerIsTarget -> AvatarOverlayTransform(alpha = eased(fraction), slideX = 0f)
        else -> AvatarOverlayTransform(
            alpha = 1f - eased(fraction * 220f / 160f),
            slideX = 0f,
        )
    }
}

/**
 * Which screen's avatar slot the overlay belongs to right now.
 *
 * At rest — and during ordinary navigation — that is simply [currentScreen],
 * so a forward push hands the circle to the incoming screen immediately.
 * While predictive-back motion runs it is the transition side that is NOT
 * the gesture's target: the screen being dismissed keeps the circle through
 * the whole gesture and its commit settle (sliding and fading out with it),
 * and a cancel keeps it on the screen we never left. The revealed screen
 * takes over only once the transition settles — by then its anchor has been
 * re-captured at rest, so the hand-off lands exactly on its slot.
 */
internal fun resolveAvatarWinnerScreen(
    currentScreen: Screen,
    currentState: Screen,
    targetState: Screen,
    predictiveTarget: Screen?,
): Screen = when {
    predictiveTarget != null && currentState != targetState ->
        if (currentState == predictiveTarget) targetState else currentState

    else -> currentScreen
}

// ---------------------------------------------------------------------------
// Anchor registry
// ---------------------------------------------------------------------------

/**
 * Which screen a video-avatar anchor belongs to. Public because
 * [ProfileAvatar] (a public composable) takes it as a parameter.
 */
enum class AvatarAnchorId { Home, Profile }

/**
 * Live state of one screen's avatar slot: where it is in window coordinates
 * (while at rest), whether it exists, whether it is on-screen, and whether a
 * dialog covers it. Written by [ProfileAvatar], read by [PersistentAvatarVideo].
 */
internal class AvatarAnchorState(val id: AvatarAnchorId) {

    /**
     * The slot's window bounds WITHOUT the predictive-back slide applied —
     * captured only while the transition is at rest (or pure-fading, which
     * does not move layout), so the overlay can add the replicated slide
     * itself and stay frame-exact during the gesture.
     */
    var restBounds: Rect by mutableStateOf(Rect.Zero)

    /** False until the owning screen has laid out at least once. */
    var present: Boolean by mutableStateOf(false)

    /** Mirror of `AvatarPlayback.visible` — centre inside the window. */
    var centerVisible: Boolean by mutableStateOf(true)

    /** Mirror of `AvatarPlayback.paused` — a dialog covers this avatar. */
    var suppressed: Boolean by mutableStateOf(false)

    /** Current screen-owned actions forwarded by the persistent native surface. */
    var onClick: (() -> Unit)? by mutableStateOf(null)
    var onLongClick: (() -> Unit)? by mutableStateOf(null)
}

/**
 * One slot per screen. Binding replaces any previous instance (the portrait ↔
 * landscape Profile swap composes the new branch before disposing the old);
 * unbinding only clears the slot if it still owns it.
 */
internal class AvatarAnchorRegistry {
    private val anchors = mutableStateMapOf<AvatarAnchorId, AvatarAnchorState>()

    /**
     * False while predictive-back motion is animating: anchors keep their
     * pre-gesture rest position instead of sampling the sliding one.
     */
    var captureEnabled: Boolean by mutableStateOf(true)

    /** Set by the overlay when the selected video fails to play. */
    var failed: Boolean by mutableStateOf(false)

    fun bind(id: AvatarAnchorId): AvatarAnchorState =
        AvatarAnchorState(id).also { anchors[id] = it }

    fun unbind(state: AvatarAnchorState) {
        if (anchors[state.id] === state) anchors.remove(state.id)
    }

    operator fun get(id: AvatarAnchorId?): AvatarAnchorState? =
        id?.let { anchors[it] }
}

/** Forward a tap received by the native overlay to the current screen anchor. */
internal fun dispatchAvatarAnchorClick(anchor: AvatarAnchorState?) {
    anchor?.onClick?.invoke()
}

/** Forward a long press; false means the active anchor has no long-press action. */
internal fun dispatchAvatarAnchorLongClick(anchor: AvatarAnchorState?): Boolean {
    val action = anchor?.onLongClick ?: return false
    action()
    return true
}

/** Provided by NazoApp around the screens subtree. */
internal val LocalAvatarAnchorRegistry = compositionLocalOf<AvatarAnchorRegistry?> { null }

// ---------------------------------------------------------------------------
// Anchor slot content (what the circle shows under / instead of the overlay)
// ---------------------------------------------------------------------------

/**
 * The video branch of [ProfileAvatar]: the cached poster frame, or the
 * initials fallback for cold/failed states. Deliberately contains no
 * `TextureView` — the one persistent surface lives in the overlay.
 */
@Composable
internal fun AvatarVideoAnchorContent(
    uri: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val registry = LocalAvatarAnchorRegistry.current
    val failed = registry?.failed == true
    val poster = AvatarFrameCache.get(uri)
    Box(modifier = modifier) {
        when {
            // Failure: the fallback tells the truth; a stale poster must not
            // claim the video is still alive.
            failed -> ProfileInitials(name, size)

            poster != null -> Image(
                bitmap = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Genuine cold state — honest initials for the few dozen ms until
            // the background frame warm-up lands.
            else -> ProfileInitials(name, size)
        }
    }
}

// ---------------------------------------------------------------------------
// The overlay
// ---------------------------------------------------------------------------

/**
 * Composed once by NazoApp, above the screens and below every dialog/sheet.
 * See the file header for the architecture.
 *
 * @param winnerId the avatar slot to track, or null when the current screen
 *   has no avatar (the overlay hides and the player idles).
 * @param alpha from [avatarOverlayTransform], computed in NazoApp next to
 *   the transitionSpec it mirrors.
 * @param slideX likewise — the replicated predictive-back slide, in px.
 * @param railInset mirrors the screens area's rail padding so the container
 *   width equals the slide width the transitionSpec uses.
 * @param blurBlocking true while the NazoApp-level blocking dialogs blur the
 *   screens — the overlay blurs with them instead of sitting sharp on top.
 */
@Composable
internal fun PersistentAvatarVideo(
    uri: String?,
    registry: AvatarAnchorRegistry,
    winnerId: AvatarAnchorId?,
    alpha: Float,
    slideX: Float,
    railInset: Dp,
    blurBlocking: Boolean,
) {
    val context = LocalContext.current
    val playback = rememberAvatarPlayback()
    val isVideo = uri != null &&
        AvatarMedia.displayKind(uri) == AvatarMedia.DisplayKind.Video

    // Failure resets only when the avatar itself changes.
    LaunchedEffect(uri) { registry.failed = false }

    val session = remember(uri) {
        if (isVideo) {
            AvatarVideoSession(
                uri = uri!!,
                fit = false,
                context = context.applicationContext,
            )
        } else {
            null
        }
    }

    // The AndroidView factory runs once but must see the CURRENT session,
    // video URI and screen anchor for its whole life (the view outlives both
    // avatar changes and Home/Profile navigation).
    val anchor = registry[winnerId]
    val live = remember { arrayOfNulls<Any?>(3) } // [0] = session, [1] = video uri, [2] = active anchor
    SideEffect {
        live[0] = session
        live[1] = if (isVideo) uri else null
        live[2] = anchor
    }
    val failed = registry.failed
    val window: IntSize = LocalWindowInfo.current.containerSize

    // "Any part of the circle is inside the window" — position-based, so a
    // half-scrolled avatar still shows (and only pauses, via centerVisible),
    // while a fully off-screen or fully slid-away slot hides the overlay.
    val onWindow = anchor?.restBounds?.let { b ->
        window.width <= 0 || window.height <= 0 ||
            (b.left < window.width && b.top < window.height &&
                b.right > 0f && b.bottom > 0f)
    } ?: false

    val shown = isVideo && !failed && playback.foreground &&
        anchor != null && anchor.present && !anchor.suppressed && onWindow
    // Read action-presence flags in composition so attaching or removing an
    // anchor callback also updates the native view's hit-testing state.
    val anchorHasClickAction = anchor?.onClick != null
    val anchorHasLongClickAction = anchor?.onLongClick != null

    // Decode only while the circle is actually shown AND its centre is
    // on-screen — the same gate the circles have always used.
    val shouldPlay = shown && anchor?.centerVisible != false

    val textureHolder = remember { arrayOfNulls<TextureView>(1) }

    DisposableEffect(session, shouldPlay) {
        val s = session
        if (s != null) {
            s.onFailed = { registry.failed = true }
            // The texture survives avatar changes: if it is already up, hand
            // its existing SurfaceTexture to the new session immediately.
            val texture = textureHolder[0]?.surfaceTexture
            if (texture != null && s.surface == null) {
                AvatarVideoHost.surfaceAvailable(s, Surface(texture))
            }
            if (shouldPlay) AvatarVideoHost.activate(s)
            else AvatarVideoHost.deactivate(s)
        }
        onDispose {
            s?.onFailed = null
            s?.let { AvatarVideoHost.deactivate(it) }
        }
    }

    // Sticky last-known slot geometry: the child keeps a stable position
    // even for a frame where the winner's anchor is unbound (avatar change,
    // screen swap) — alpha gates visibility anyway.
    var lastBounds by remember { mutableStateOf(Rect.Zero) }
    SideEffect {
        val b = anchor?.restBounds
        if (b != null && !b.isEmpty) lastBounds = b
    }

    // Capture policy: only on a shown → hidden transition that happens while
    // the circle was genuinely visible (alpha above noise). Home ↔ Profile
    // keeps `shown` true throughout, so navigation between the avatar screens
    // performs ZERO bitmap captures; a slide-out or a push to an avatar-less
    // screen hides at alpha ≈ 0 and skips the capture too.
    var frameRendered by remember { mutableStateOf(false) }
    var wasShown by remember { mutableStateOf(false) }
    var lastShownAlpha by remember { mutableFloatStateOf(1f) }
    SideEffect { if (shown) lastShownAlpha = alpha }
    LaunchedEffect(shown, uri) {
        if (wasShown && !shown && !registry.failed && lastShownAlpha > 0.05f) {
            val videoUri = live[1] as? String
            if (videoUri != null) capturePosterFrame(textureHolder[0], videoUri)
        }
        wasShown = shown
    }

    // Container mirrors the screens area (fillMaxSize + rail inset + the
    // same blur behind blocking dialogs): its content width equals the slide
    // width, and its window origin converts the anchor's window bounds into
    // local placement offsets.
    var containerOrigin by remember { mutableStateOf<Offset?>(null) }
    val effectiveAlpha = if (shown) alpha else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = railInset)
            .then(if (blurBlocking) Modifier.blur(16.dp) else Modifier)
            .onGloballyPositioned { coords ->
                // positionInWindow (not boundsInWindow) on every Compose
                // version: a pure transform, never clipped to the window.
                val o = coords.positionInWindow()
                if (containerOrigin != o) containerOrigin = o
            }
            .clearAndSetSemantics { },
    ) {
        val origin = containerOrigin
        if (isVideo && origin != null && !lastBounds.isEmpty) {
            Box(
                modifier = Modifier
                    // Placement in px: window bounds → container-local, plus
                    // the replicated predictive-back slide. offset moves the
                    // NODE (so clip/alpha follow it); layout below only fixes
                    // the node's size to the slot's own size — 42 dp Home and
                    // 132 dp Profile both match exactly.
                    .offset {
                        IntOffset(
                            x = (lastBounds.left - origin.x + slideX).roundToInt(),
                            y = (lastBounds.top - origin.y).roundToInt(),
                        )
                    }
                    .layout { measurable, _ ->
                        val w = lastBounds.size.width.roundToInt().coerceAtLeast(1)
                        val h = lastBounds.size.height.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.place(0, 0) }
                    }
                    .alpha(effectiveAlpha)
                    .clip(CircleShape),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        TextureView(viewContext).apply {
                            textureHolder[0] = this
                            isFocusable = false
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            // Native Android views are layered above Compose drawing.
                            // Do not rely on a non-clickable TextureView to pass input
                            // through to the composable avatar behind it: explicitly
                            // forward both gestures to whichever anchor currently owns
                            // this persistent rendering surface.
                            setOnClickListener {
                                dispatchAvatarAnchorClick(live[2] as? AvatarAnchorState)
                            }
                            setOnLongClickListener {
                                dispatchAvatarAnchorLongClick(live[2] as? AvatarAnchorState)
                            }
                            surfaceTextureListener =
                                object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surfaceTexture: SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        val s = live[0] as? AvatarVideoSession ?: return
                                        AvatarVideoHost.surfaceAvailable(s, Surface(surfaceTexture))
                                    }

                                    override fun onSurfaceTextureDestroyed(
                                        surfaceTexture: SurfaceTexture,
                                    ): Boolean {
                                        // The overlay's view dies only when the
                                        // avatar stops being a video: keep the
                                        // last frame as the poster first.
                                        val videoUri = live[1] as? String
                                        if (frameRendered && videoUri != null) {
                                            capturePosterFrame(this@apply, videoUri)
                                        }
                                        val s = live[0] as? AvatarVideoSession
                                        val surface = s?.surface
                                        s?.let { AvatarVideoHost.surfaceDestroyed(it) }
                                        surface?.release()
                                        frameRendered = false
                                        textureHolder[0] = null
                                        return true
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surfaceTexture: SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) = Unit

                                    override fun onSurfaceTextureUpdated(
                                        surfaceTexture: SurfaceTexture,
                                    ) {
                                        if (!frameRendered) frameRendered = true
                                    }
                                }
                        }
                    },
                    update = { view ->
                        // Alpha=0 does not remove a native view from hit testing.
                        // INVISIBLE does: keep the TextureView attached (and its
                        // SurfaceTexture alive), but remove its hit region whenever
                        // there is no visible avatar anchor to represent.
                        view.visibility = if (shown) View.VISIBLE else View.INVISIBLE
                        view.isClickable = shown && anchorHasClickAction
                        view.isLongClickable = shown && anchorHasLongClickAction
                    },
                )
            }
        }
    }
}

/** One `getBitmap` → cache put; never throws. Main thread (hide events). */
private fun capturePosterFrame(view: TextureView?, uri: String) {
    if (view == null) return
    runCatching { view.getBitmap() }.getOrNull()
        ?.let { AvatarFrameCache.put(uri, it) }
}
