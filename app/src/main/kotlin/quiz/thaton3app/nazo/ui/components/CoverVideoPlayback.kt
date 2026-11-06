package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Pure gate shared by the host and JVM tests for every pause/resume input. */
internal fun coverVideoShouldPlay(
    prepared: Boolean,
    lifecycleStarted: Boolean,
    playbackAllowed: Boolean,
    hasSurface: Boolean,
): Boolean = prepared && lifecycleStarted && playbackAllowed && hasSurface

internal fun normalizeCoverSyncPosition(positionMs: Int, durationMs: Int): Int =
    if (durationMs > 0) positionMs.coerceAtLeast(0) % durationMs else positionMs.coerceAtLeast(0)

/**
 * The cover's independent playback owner.
 *
 * This intentionally does not touch [AvatarVideoHost]. A video cover and video
 * avatar can therefore decode at the same time. The host owns at most one cover
 * MediaPlayer and hands it between the banner and the modal cover preview; the
 * newest registered TextureView is the active surface. Returning from a modal
 * restores the still-composed banner target automatically.
 *
 * A short no-surface grace period absorbs navigation animations and cancelled
 * predictive-back transitions. Real absence releases the decoder while keeping
 * a small saved-position map and bounded poster cache for a seamless return.
 */
internal object CoverVideoHost {
    private const val RELEASE_GRACE_MS = 1_500L
    private const val MAX_SAVED_POSITIONS = 8

    private data class Target(
        val view: TextureView,
        var uri: String,
        var playbackAllowed: Boolean,
        var lifecycleStarted: Boolean,
        var syncPositionProvider: (() -> Int?)?,
    )

    private data class Session(
        val uri: String,
        val player: MediaPlayer,
        var prepared: Boolean = false,
        var rendering: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targets = LinkedHashMap<TextureView, Target>()
    private val savedPositions = LinkedHashMap<String, Int>(MAX_SAVED_POSITIONS, 0.75f, true)
    private val _poster = MutableStateFlow<Bitmap?>(null)
    val poster = _poster.asStateFlow()

    private var session: Session? = null
    private var activeTarget: Target? = null
    private var surface: Surface? = null
    private var releaseJob: Job? = null

    fun attach(
        context: Context,
        view: TextureView,
        uri: String,
        playbackAllowed: Boolean,
        lifecycleStarted: Boolean,
        syncPositionProvider: (() -> Int?)?,
    ) {
        releaseJob?.cancel()
        releaseJob = null

        view.isOpaque = false
        view.alpha = 0f
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                if (activeTarget?.view === view) bindActiveSurface(context)
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(
                surfaceTexture: android.graphics.SurfaceTexture,
            ): Boolean {
                if (activeTarget?.view === view) {
                    pauseAndRemember()
                    clearSurface()
                }
                return true
            }

            override fun onSurfaceTextureUpdated(
                surfaceTexture: android.graphics.SurfaceTexture,
            ) {
                // The first real buffer, rather than prepare completion, is
                // the safe point to reveal an otherwise-black TextureView.
                if (activeTarget?.view === view && session?.prepared == true) {
                    session?.rendering = true
                    view.alpha = 1f
                }
            }
        }

        targets.remove(view)
        val target = Target(
            view,
            uri,
            playbackAllowed,
            lifecycleStarted,
            syncPositionProvider,
        )
        targets[view] = target
        activeTarget = target
        showPoster(context, uri)
        bindActiveSurface(context)
    }

    fun update(
        context: Context,
        view: TextureView,
        uri: String,
        playbackAllowed: Boolean,
        lifecycleStarted: Boolean,
        syncPositionProvider: (() -> Int?)?,
    ) {
        val target = targets[view]
        if (target == null) {
            attach(
                context,
                view,
                uri,
                playbackAllowed,
                lifecycleStarted,
                syncPositionProvider,
            )
            return
        }
        val sourceChanged = target.uri != uri
        target.uri = uri
        target.playbackAllowed = playbackAllowed
        target.lifecycleStarted = lifecycleStarted
        target.syncPositionProvider = syncPositionProvider
        if (activeTarget?.view === view) {
            if (sourceChanged) {
                showPoster(context, uri)
                bindActiveSurface(context)
            } else {
                updatePlayback()
            }
        }
    }

    fun detach(context: Context, view: TextureView) {
        val wasActive = activeTarget?.view === view
        targets.remove(view)
        view.surfaceTextureListener = null
        view.alpha = 0f
        if (!wasActive) return

        clearSurface()
        activeTarget = targets.values.lastOrNull()
        val restored = activeTarget
        if (restored != null) {
            showPoster(context, restored.uri)
            bindActiveSurface(context)
        } else {
            pauseAndRemember()
            releaseJob?.cancel()
            releaseJob = scope.launch {
                delay(RELEASE_GRACE_MS)
                if (targets.isEmpty()) releaseSession()
            }
        }
    }

    fun setLifecycleStarted(view: TextureView, started: Boolean) {
        val target = targets[view] ?: return
        target.lifecycleStarted = started
        if (activeTarget?.view === view) updatePlayback()
    }

    suspend fun warmPoster(context: Context, uri: String): Bitmap? {
        val bitmap = CoverPosterCache.getOrCreate(context.applicationContext, uri)
        if (activeTarget?.uri == uri) _poster.value = bitmap
        return bitmap
    }

    private fun bindActiveSurface(context: Context) {
        val target = activeTarget ?: return
        val texture = target.view.surfaceTexture ?: return

        // Every surface hand-off is a pause/resume boundary. Automatic video
        // covers use that boundary to seek back to the avatar's live position;
        // manual covers simply resume their remembered position.
        pauseAndRemember()
        clearSurface()
        target.view.alpha = 0f
        val existing = session
        if (existing == null || existing.uri != target.uri) {
            releaseSession()
            createSession(context.applicationContext, target.uri)
        }

        surface = Surface(texture).also { videoSurface ->
            session?.takeIf { it.prepared }?.player?.setSurface(videoSurface)
        }
        session?.rendering = false
        updatePlayback()
    }

    private fun createSession(context: Context, uri: String) {
        val player = MediaPlayer()
        val created = Session(uri = uri, player = player)
        session = created
        try {
            player.setVolume(0f, 0f)
            player.isLooping = true
            player.setOnPreparedListener { preparedPlayer ->
                if (session !== created) return@setOnPreparedListener
                created.prepared = true
                runCatching {
                    preparedPlayer.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    )
                }
                surface?.let { videoSurface ->
                    runCatching { preparedPlayer.setSurface(videoSurface) }
                }
                updatePlayback()
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && session === created) {
                    created.rendering = true
                    activeTarget?.view?.alpha = 1f
                }
                false
            }
            player.setOnErrorListener { _, _, _ ->
                if (session === created) {
                    created.prepared = false
                    created.rendering = false
                    activeTarget?.view?.alpha = 0f
                }
                true
            }
            player.setDataSource(context, Uri.parse(uri))
            player.prepareAsync()
        } catch (_: Throwable) {
            if (session === created) {
                releaseSession()
                activeTarget?.view?.alpha = 0f
            }
        }
    }

    private fun updatePlayback() {
        val current = session ?: return
        val shouldPlay = coverVideoShouldPlay(
            prepared = current.prepared,
            lifecycleStarted = activeTarget?.lifecycleStarted == true,
            playbackAllowed = activeTarget?.playbackAllowed == true,
            hasSurface = surface != null,
        )
        if (shouldPlay) {
            runCatching {
                if (!current.player.isPlaying) {
                    val desired = activeTarget?.syncPositionProvider?.invoke()
                        ?: savedPositions[current.uri]
                    if (desired != null) {
                        val duration = current.player.duration
                        val normalized = normalizeCoverSyncPosition(desired, duration)
                        if (abs(current.player.currentPosition - normalized) > 120) {
                            current.player.seekTo(normalized)
                        }
                    }
                    current.player.start()
                }
            }
        } else {
            pauseAndRemember()
        }
    }

    private fun pauseAndRemember() {
        val current = session ?: return
        if (!current.prepared) return
        runCatching {
            if (current.player.isPlaying) current.player.pause()
            rememberPosition(current.uri, current.player.currentPosition)
        }
    }

    private fun rememberPosition(uri: String, position: Int) {
        savedPositions[uri] = position.coerceAtLeast(0)
        while (savedPositions.size > MAX_SAVED_POSITIONS) {
            val eldest = savedPositions.entries.firstOrNull()?.key ?: break
            savedPositions.remove(eldest)
        }
    }

    private fun clearSurface() {
        runCatching { session?.player?.setSurface(null) }
        surface?.release()
        surface = null
    }

    private fun releaseSession() {
        val current = session ?: return
        pauseAndRemember()
        clearSurface()
        runCatching { current.player.reset() }
        runCatching { current.player.release() }
        session = null
    }

    private fun showPoster(context: Context, uri: String) {
        _poster.value = CoverPosterCache.get(uri)
        scope.launch { warmPoster(context, uri) }
    }
}

/** Three full-size frames at the cover's validated 1080p ceiling (about 24 MB). */
private object CoverPosterCache {
    private const val MAX_ENTRIES = 3
    private val entries = LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true)

    @Synchronized
    fun get(uri: String): Bitmap? = entries[uri]

    suspend fun getOrCreate(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        get(uri) ?: decode(context, uri)?.also { put(uri, it) }
    }

    @Synchronized
    private fun put(uri: String, bitmap: Bitmap) {
        entries[uri] = bitmap
        while (entries.size > MAX_ENTRIES) {
            val eldest = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldest)
            // Do not eagerly recycle: a Compose Image may still be presenting
            // this frame during a source hand-off. Dropping our strong reference
            // is enough for the bitmap to become collectible afterwards.
        }
    }

    private fun decode(context: Context, uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** Poster-only thumbnail used by the selection sheet (no decoder/player). */
@Composable
internal fun CoverVideoPoster(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var frame by remember(uri) { androidx.compose.runtime.mutableStateOf(CoverPosterCache.get(uri)) }
    LaunchedEffect(uri) {
        frame = CoverVideoHost.warmPoster(context, uri)
    }
    frame?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Poster-backed, crop-to-fill cover video surface. A caller should leave its
 * normal gradient/image fallback behind this composable; until the first video
 * frame arrives the TextureView is transparent rather than a black rectangle.
 */
@Composable
internal fun CoverVideoBackground(
    uri: String,
    playbackAllowed: Boolean,
    modifier: Modifier = Modifier,
    syncPositionProvider: (() -> Int?)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val poster by CoverVideoHost.poster.collectAsState()
    val textureView = remember {
        TextureView(context).apply {
            isOpaque = false
            alpha = 0f
        }
    }

    LaunchedEffect(uri) {
        CoverVideoHost.warmPoster(context, uri)
    }

    DisposableEffect(textureView, uri) {
        CoverVideoHost.attach(
            context = context,
            view = textureView,
            uri = uri,
            playbackAllowed = playbackAllowed,
            lifecycleStarted = lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED),
            syncPositionProvider = syncPositionProvider,
        )
        onDispose { CoverVideoHost.detach(context, textureView) }
    }

    DisposableEffect(lifecycleOwner, textureView) {
        val lifecycle = lifecycleOwner.lifecycle
        CoverVideoHost.setLifecycleStarted(
            textureView,
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        val observer = LifecycleEventObserver { _, _ ->
            CoverVideoHost.setLifecycleStarted(
                textureView,
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }
        lifecycle.addObserver(observer)
        onDispose {
            // Lifecycle state belongs to the target, so an old Activity's
            // ON_STOP during recreation cannot pause the replacement surface.
            lifecycle.removeObserver(observer)
        }
    }

    Box(modifier) {
        poster?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        AndroidView(
            factory = { textureView },
            update = { view ->
                CoverVideoHost.update(
                    context = context,
                    view = view,
                    uri = uri,
                    playbackAllowed = playbackAllowed,
                    lifecycleStarted = lifecycleOwner.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED),
                    syncPositionProvider = syncPositionProvider,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
