package quiz.thaton3app.nazo.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.graphics.drawable.Animatable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.delay

/**
 * Playback plumbing for animated avatars — one shared video player and the
 * visibility/foreground gate every avatar surface obeys.
 *
 * Design constraints this file exists to satisfy:
 *
 *  - **Exactly one MediaPlayer exists at a time.** The Home circle, the
 *    Profile circle, the full-size viewer and the picker preview are never
 *    all on screen together, but transitions and dialogs CAN overlap two of
 *    them — so instead of one player per instance, [AvatarVideoHost] keeps a
 *    single player and an activation stack: the most recently activated view
 *    owns the surface, older views simply stop drawing video (they keep their
 *    last frame) and resume when the stack pops back to them. Switching
 *    screens hands the same prepared player to the new view, which is what
 *    makes Home ↔ Profile feel instant rather than "re-buffer".
 *  - **Nothing outlives its screen.** When the last registered view goes
 *    away the player pauses immediately and is released after a short grace
 *    period (one second), so a normal navigation either reuses the player or
 *    recreates it once — never leaves a codec parked behind a screen the
 *    user has left.
 *  - **Ordinary recomposition changes nothing.** Sessions are keyed on
 *    (uri, fit) and activation lives in `DisposableEffect` keys, so
 *    recomposing, scrolling, or a ripple cannot create or destroy a player.
 *  - **GIFs ride Coil, not this file.** coil-gif decodes them; [AnimatedAvatarImage]
 *    only adds the same play/pause gate so a scrolled-off or backgrounded
 *    avatar stops advancing frames instead of decoding into the void.
 *
 * Everything here assumes main-thread access — every entry point (Compose
 * effects, TextureView callbacks, MediaPlayer listeners of a main-thread
 * player) already runs there.
 */

// ---------------------------------------------------------------------------
// Playback gate
// ---------------------------------------------------------------------------

/**
 * "May this avatar animate right now?" — true only while the avatar's centre
 * is inside the window AND the app is in the foreground.
 *
 * Scrolled-off detection uses `boundsInWindow` against the window container
 * size: the avatar lives inside scrollable columns on both Home and Profile,
 * so "still in the composition" is NOT the same as "still on screen". The
 * centre point (rather than any overlap) is the threshold, so a half-visible
 * avatar plays and a fully-hidden one does not, with hysteresis-free flips.
 */
internal class AvatarPlayback {

    /** Window size in px, refreshed every recomposition by [rememberAvatarPlayback]. */
    var container: IntSize = IntSize.Zero

    /** False once the avatar has scrolled out of the window. */
    var visible: Boolean by mutableStateOf(true)
        private set

    /** False while the activity is paused (app backgrounded / covered). */
    // Deliberately not `private set`: rememberAvatarPlayback() (top level,
    // same file) drives it from the activity lifecycle observer.
    var foreground: Boolean by mutableStateOf(true)

    /**
     * Forced pause from the owner — set while a dialog covers this avatar so
     * a live GIF/video isn't decoding behind the scrim (the frame it was
     * showing is held instead). Lifecycle-independent.
     */
    var paused: Boolean by mutableStateOf(false)

    val shouldPlay: Boolean get() = visible && foreground && !paused

    fun onPositioned(coords: LayoutCoordinates) {
        val bounds = coords.boundsInWindow()
        val inside = when {
            // No measurable window yet — assume visible rather than freeze off.
            container.width <= 0 || container.height <= 0 -> true
            // Fully scrolled/clipped away (empty once clipped to the window).
            bounds.width <= 0f || bounds.height <= 0f -> false
            else -> {
                val center: Offset = bounds.center
                center.x >= 0f && center.y >= 0f &&
                    center.x <= container.width && center.y <= container.height
            }
        }
        if (inside != visible) visible = inside
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Creates (and remembers) an [AvatarPlayback] wired to the ACTIVITY lifecycle.
 *
 * The activity — not `LocalLifecycleOwner` — is the source of truth because a
 * Compose `Dialog` hosts its own lifecycle owner, and only the activity
 * reliably receives ON_PAUSE when the whole app goes to the background. The
 * context is unwrapped for the same reason: inside a dialog, `LocalContext`
 * is a themed wrapper around the activity.
 */
@Composable
internal fun rememberAvatarPlayback(): AvatarPlayback {
    val context = LocalContext.current
    val fallbackOwner = LocalLifecycleOwner.current
    val playback = remember { AvatarPlayback() }

    // Read every recomposition so a window resize is picked up; written to a
    // plain field (not state) so this cannot cause a recomposition loop.
    playback.container = LocalWindowInfo.current.containerSize

    val owner = remember(context, fallbackOwner) {
        (context.findActivity() as? LifecycleOwner) ?: fallbackOwner
    }

    DisposableEffect(owner) {
        if (owner == null) {
            playback.foreground = true
            onDispose { }
        } else {
            playback.foreground =
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> playback.foreground = true
                    Lifecycle.Event.ON_PAUSE -> playback.foreground = false
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }
    return playback
}

// ---------------------------------------------------------------------------
// Shared video player
// ---------------------------------------------------------------------------

/** One registered avatar video view. Identity-equal; created per (uri, fit). */
internal class AvatarVideoSession(
    val uri: String,
    /** true = letterbox (full-size viewer), false = crop-fill (avatar circle). */
    val fit: Boolean,
    /** Application context: `content://` grants are UID-scoped, so this is enough. */
    val context: Context,
) {
    var surface: Surface? = null
    var onFailed: (() -> Unit)? = null

    /** Set by the host when this session's media could not be played. */
    var failed: Boolean = false
}

/**
 * The app-wide MediaPlayer. See the file header for the architecture; the
 * short version: activation stack + grace-period release + one player.
 */
internal object AvatarVideoHost {

    /**
     * How long a prepared player survives with zero registered views. Long
     * enough to cover a screen transition's disposal gap (so the player is
     * REUSED, not recreated), short enough that leaving the avatar behind
     * releases the codec promptly.
     */
    private const val RELEASE_GRACE_MS = 1_000L

    /** Active sessions, most recently activated last (the current owner). */
    private val active = ArrayDeque<AvatarVideoSession>()
    private val handler = Handler(Looper.getMainLooper())
    private val releaseIfIdle = Runnable { if (active.isEmpty()) releasePlayer() }

    private var player: MediaPlayer? = null
    private var preparedPath: String? = null
    private var prepared = false
    private var started = false
    private var attachedSurface: Surface? = null
    private var fitMode: Boolean? = null

    fun activate(session: AvatarVideoSession) {
        if (session.failed) return
        handler.removeCallbacks(releaseIfIdle)
        active.remove(session)
        active.addLast(session)
        sync()
    }

    fun deactivate(session: AvatarVideoSession) {
        active.remove(session)
        sync()
    }

    fun surfaceAvailable(session: AvatarVideoSession, surface: Surface) {
        session.surface = surface
        sync()
    }

    fun surfaceDestroyed(session: AvatarVideoSession) {
        session.surface = null
        val wasCurrent = active.lastOrNull() === session
        if (wasCurrent && prepared) {
            // Detaching mid-playback: drop the surface and pause rather than
            // decode frames nobody can see. onPrepared never races this —
            // both run on the main thread.
            attachedSurface = null
            runCatching { player?.setSurface(null) }
            pause()
        }
        sync()
    }

    /** Re-evaluates what the single player should be doing. Main thread only. */
    private fun sync() {
        val session = active.lastOrNull()
        if (session == null) {
            // Nobody is watching: stop immediately, release after the grace.
            pause()
            scheduleRelease()
            return
        }
        handler.removeCallbacks(releaseIfIdle)
        if (player == null) createPlayer()
        val p = player ?: return
        if (preparedPath != session.uri) {
            prepare(p, session)
            return
        }
        if (prepared) {
            applyFit(p, session.fit)
            applySurface(p, session)
            if (session.surface != null) play(p) else pause()
        }
        // Still preparing for this uri: onPrepared applies everything below.
    }

    private fun createPlayer() {
        val p = MediaPlayer()
        p.setOnPreparedListener { mp -> onPrepared(mp) }
        // Returning true marks the error handled: we do our own fallback and
        // the framework must not additionally tear into a callback-less state.
        p.setOnErrorListener { _, _, _ ->
            active.lastOrNull()?.let { failSession(it) } ?: releasePlayer()
            true
        }
        player = p
        preparedPath = null
        prepared = false
        started = false
        attachedSurface = null
        fitMode = null
    }

    private fun prepare(p: MediaPlayer, session: AvatarVideoSession) {
        runCatching { p.reset() }
        prepared = false
        started = false
        attachedSurface = null
        fitMode = null
        preparedPath = session.uri
        val configured = runCatching {
            // One path for file:// and content://; both are accepted by the
            // Context overload and the grant is UID-scoped either way.
            p.setDataSource(session.context, Uri.parse(session.uri))
            // Valid from Initialized on; re-asserted in onPrepared anyway.
            applyFit(p, session.fit)
        }
        configured.getOrElse {
            preparedPath = null
            failSession(session)
            return
        }
        p.prepareAsync()
    }

    private fun onPrepared(mp: MediaPlayer) {
        prepared = true
        started = false
        mp.isLooping = true
        mp.setVolume(0f, 0f) // avatars never make noise
        val session = active.lastOrNull()
        if (session == null || preparedPath != session.uri) {
            // The view went away (or the avatar changed) while preparing:
            // stay paused; the next sync() resumes from this prepared state.
            return
        }
        applyFit(mp, session.fit)
        applySurface(mp, session)
        if (session.surface != null) play(mp)
    }

    private fun applyFit(p: MediaPlayer, fit: Boolean) {
        if (fitMode == fit) return
        fitMode = fit
        runCatching {
            // Explicit Java call: the synthetic property does not resolve
            // against android.jar the way a Kotlin-defined getter/setter would.
            p.setVideoScalingMode(
                if (fit) {
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                } else {
                    // ContentScale.Crop semantics: fill the (square) view and
                    // crop the overflow, so a 16:9 clip fills the circle.
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                }
            )
        }
    }

    private fun applySurface(p: MediaPlayer, session: AvatarVideoSession) {
        if (!prepared) return // Preparing: onPrepared attaches instead.
        val target = session.surface
        if (target === attachedSurface) return
        runCatching { p.setSurface(target) }.onSuccess { attachedSurface = target }
    }

    private fun play(p: MediaPlayer) {
        if (!prepared || started) return
        runCatching { p.start(); started = true }
    }

    private fun pause() {
        val p = player ?: return
        if (!started) return
        runCatching { p.pause() }
        started = false
    }

    private fun failSession(session: AvatarVideoSession) {
        session.failed = true
        releasePlayer()
        active.remove(session)
        session.onFailed?.invoke()
        sync() // promotes any remaining view, or schedules the release
    }

    private fun releasePlayer() {
        handler.removeCallbacks(releaseIfIdle)
        val p = player ?: return
        player = null
        runCatching { p.release() }
        preparedPath = null
        prepared = false
        started = false
        attachedSurface = null
        fitMode = null
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(releaseIfIdle)
        handler.postDelayed(releaseIfIdle, RELEASE_GRACE_MS)
    }
}

// ---------------------------------------------------------------------------
// Video view
// ---------------------------------------------------------------------------

/**
 * How long a freshly composed video view may show its plain background before
 * the fallback (initials, an error state) appears. Long enough to cover the
 * warm-navigation path by a wide margin — a prepared player needs only a
 * surface plus a frame or two (~50–150 ms) — short enough that a genuinely
 * stuck or failing video still falls back promptly. [AvatarVideoView] shows
 * NO fallback during this window, which is what stops Home ↔ Profile from
 * flashing the initials avatar over the crossfade.
 */
internal const val VIDEO_FALLBACK_DELAY_MS = 500L

/**
 * How long an animated image may load before its placeholder (the initials
 * avatar) appears. Coil answers warm-cache navigation in a frame or two, so
 * a placeholder drawn immediately is what made GIF avatars flash back to the
 * initials during transitions; genuinely slow loads still show it.
 */
internal const val GIF_PLACEHOLDER_DELAY_MS = 300L

/**
 * Renders one looping, muted avatar video, driven by [playback].
 *
 * The TextureView is transparent until the first frame arrives. [fallback]
 * is drawn ONLY when it can be truthful: once the warm window above has
 * expired without a frame, or when playback has failed — never merely
 * because a fresh surface is still spinning up, which is the moment that
 * used to flash the initials avatar across a screen transition. On failure
 * [onFailed] fires once and the TextureView is removed entirely, leaving
 * the fallback as the final state.
 */
@Composable
internal fun AvatarVideoView(
    uri: String,
    fit: Boolean,
    playback: AvatarPlayback,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
    onFailed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val session = remember(uri, fit) {
        AvatarVideoSession(uri, fit, context.applicationContext)
    }
    var failed by remember(session) { mutableStateOf(false) }
    // True once the TextureView has drawn at least one frame for this
    // session; combined with the warm window below, it decides when the
    // fallback is allowed to appear.
    var frameRendered by remember(session) { mutableStateOf(false) }
    var warmExpired by remember(session) { mutableStateOf(false) }
    // Bumped when the surface dies so the warm window re-arms for whatever
    // SurfaceTexture the view creates next.
    var surfaceEpoch by remember(session) { mutableStateOf(0) }
    val currentOnFailed by rememberUpdatedState(onFailed)

    LaunchedEffect(session, surfaceEpoch) {
        delay(VIDEO_FALLBACK_DELAY_MS)
        warmExpired = true
    }

    DisposableEffect(session, playback.shouldPlay) {
        session.onFailed = {
            failed = true
            currentOnFailed?.invoke()
        }
        if (playback.shouldPlay) {
            AvatarVideoHost.activate(session)
        } else {
            AvatarVideoHost.deactivate(session)
        }
        onDispose {
            session.onFailed = null
            // Also covers the surface's own destruction order: whichever of
            // "effect disposed" / "texture destroyed" lands first, the host
            // ends up with no surface and no session.
            AvatarVideoHost.deactivate(session)
        }
    }

    Box(modifier = modifier) {
        if (failed || (warmExpired && !frameRendered)) {
            fallback()
        }
        if (!failed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                AvatarVideoHost.surfaceAvailable(session, Surface(surfaceTexture))
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                // The host drops its reference first; then the
                                // wrapper is released and the TextureView is
                                // allowed to release the SurfaceTexture itself.
                                val surface = session.surface
                                AvatarVideoHost.surfaceDestroyed(session)
                                surface?.release()
                                // Buffer gone (system pressure / detach): the
                                // view may spin up a NEW SurfaceTexture later,
                                // so forget the frame and re-arm the warm
                                // window in the same synchronised step — no
                                // stale fallback frame in between.
                                frameRendered = false
                                warmExpired = false
                                surfaceEpoch++
                                return true
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                                // Fires per frame; the flag makes it a no-op
                                // after the first one.
                                if (!frameRendered) frameRendered = true
                            }
                        }
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Gated image (static passthrough + GIF/animated-WebP play control)
// ---------------------------------------------------------------------------

/**
 * [SafeRemoteImage] plus the play/pause gate for animated drawables.
 *
 * Coil's painter starts an `Animatable` drawable when it is remembered and
 * stops it when forgotten — but that knows nothing about scroll position or
 * the app being backgrounded. This wrapper takes over the start/stop decision
 * from [AvatarPlayback.shouldPlay]; for static images the drawable is not
 * `Animatable` and every call is a no-op, so the static path is behaviourally
 * identical to plain [SafeRemoteImage].
 *
 * [placeholderDelayMs] holds the loading placeholder back for that long
 * (0 = show it immediately, the original behaviour). Warm navigation answers
 * inside the window, so the avatar never visibly reverts to the placeholder
 * between screens; a genuinely slow load still gets it.
 */
@Composable
internal fun AnimatedAvatarImage(
    url: String,
    contentDescription: String?,
    playback: AvatarPlayback,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderDelayMs: Long = 0L,
    placeholder: @Composable () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
) {
    var animatable by remember(url) { mutableStateOf<Animatable?>(null) }
    var placeholderVisible by remember(url) {
        mutableStateOf(placeholderDelayMs <= 0L)
    }
    LaunchedEffect(url, placeholderDelayMs) {
        if (placeholderDelayMs > 0L) {
            delay(placeholderDelayMs)
            placeholderVisible = true
        }
    }
    SafeRemoteImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = { if (placeholderVisible) placeholder() },
        errorContent = errorContent,
        onSuccess = { state: AsyncImagePainter.State.Success ->
            animatable = state.result.drawable as? Animatable
        },
    )
    DisposableEffect(animatable, playback.shouldPlay) {
        val animation = animatable
        if (playback.shouldPlay) animation?.start() else animation?.stop()
        // Disposal needs no work: Coil's painter stops and forgets the
        // drawable itself when the image leaves the composition.
        onDispose { }
    }
}
