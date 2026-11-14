package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class CoverVideoSize(val width: Int, val height: Int)

internal data class CoverVideoCropScale(val x: Float, val y: Float)

/**
 * Turns encoded dimensions into display dimensions. Android's video pipeline
 * applies rotation metadata to the output buffer; 90°/270° therefore swap the
 * axes used by our crop transform. This is also the fallback when a player has
 * not reported its already-rotated display size yet.
 */
internal fun coverVideoDisplaySize(
    encodedWidth: Int,
    encodedHeight: Int,
    rotationDegrees: Int,
): CoverVideoSize? {
    if (encodedWidth <= 0 || encodedHeight <= 0) return null
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) {
        CoverVideoSize(encodedHeight, encodedWidth)
    } else {
        CoverVideoSize(encodedWidth, encodedHeight)
    }
}

/**
 * Center-crop correction for a TextureView whose producer maps content to the
 * full view bounds. Exactly one axis grows; the composite source-to-screen
 * scale is uniform, so source pixels are never stretched or compressed.
 */
internal fun coverVideoCropScale(
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
): CoverVideoCropScale? {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return null
    }
    val viewAspectRatio = viewWidth.toFloat() / viewHeight
    val videoAspectRatio = videoWidth.toFloat() / videoHeight
    return if (videoAspectRatio > viewAspectRatio) {
        CoverVideoCropScale(x = videoAspectRatio / viewAspectRatio, y = 1f)
    } else {
        CoverVideoCropScale(x = 1f, y = viewAspectRatio / videoAspectRatio)
    }
}

/** Pure gate shared by the host and JVM tests for every pause/resume input. */
internal fun coverVideoShouldPlay(
    prepared: Boolean,
    lifecycleStarted: Boolean,
    playbackAllowed: Boolean,
    hasSurface: Boolean,
): Boolean = prepared && lifecycleStarted && playbackAllowed && hasSurface

internal fun normalizeCoverSyncPosition(positionMs: Int, durationMs: Int): Int =
    if (durationMs > 0) positionMs.coerceAtLeast(0) % durationMs else positionMs.coerceAtLeast(0)

/** Modal previews must retain the one live cover surface over the banner behind them. */
internal enum class CoverVideoPriority {
    BANNER,
    SHEET_PREVIEW,
    DIALOG_PREVIEW,
}

internal fun coverVideoTargetOutranks(
    candidatePriority: CoverVideoPriority,
    candidateSequence: Long,
    activePriority: CoverVideoPriority,
    activeSequence: Long,
): Boolean = candidatePriority.ordinal > activePriority.ordinal ||
    (candidatePriority == activePriority && candidateSequence > activeSequence)

/**
 * The cover's independent playback owner.
 *
 * This intentionally does not touch [AvatarVideoHost]. A video cover and video
 * avatar can therefore decode at the same time. The host owns at most one cover
 * MediaPlayer and hands it between the banner and modal previews. Explicit
 * preview priority prevents a banner recomposition from stealing the surface;
 * equal-priority targets still use newest registration. Returning from a modal
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
        var priority: CoverVideoPriority,
        val sequence: Long,
    )

    private data class Session(
        val uri: String,
        val player: MediaPlayer,
        var prepared: Boolean = false,
        var rendering: Boolean = false,
        var playerDisplaySize: CoverVideoSize? = null,
        var posterDisplaySize: CoverVideoSize? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targets = LinkedHashMap<TextureView, Target>()
    private val savedPositions = LinkedHashMap<String, Int>(MAX_SAVED_POSITIONS, 0.75f, true)

    private var session: Session? = null
    private var activeTarget: Target? = null
    private var surface: Surface? = null
    private var releaseJob: Job? = null
    private var nextTargetSequence = 0L

    fun attach(
        context: Context,
        view: TextureView,
        uri: String,
        playbackAllowed: Boolean,
        lifecycleStarted: Boolean,
        syncPositionProvider: (() -> Int?)?,
        priority: CoverVideoPriority,
    ) {
        releaseJob?.cancel()
        releaseJob = null

        view.isOpaque = false
        view.alpha = 0f
        view.setTransform(null)
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                if (activeTarget?.view === view) {
                    applyActiveTransform()
                    bindActiveSurface(context)
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                if (activeTarget?.view === view) revealActiveFrameIfReady()
            }

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
                    revealActiveFrameIfReady()
                }
            }
        }

        targets.remove(view)
        val target = Target(
            view = view,
            uri = uri,
            playbackAllowed = playbackAllowed,
            lifecycleStarted = lifecycleStarted,
            syncPositionProvider = syncPositionProvider,
            priority = priority,
            sequence = ++nextTargetSequence,
        )
        targets[view] = target
        refreshActiveTarget(context, forceRebind = target)
    }

    fun update(
        context: Context,
        view: TextureView,
        uri: String,
        playbackAllowed: Boolean,
        lifecycleStarted: Boolean,
        syncPositionProvider: (() -> Int?)?,
        priority: CoverVideoPriority,
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
                priority,
            )
            return
        }
        val sourceChanged = target.uri != uri
        val priorityChanged = target.priority != priority
        if (sourceChanged) {
            target.view.alpha = 0f
            target.view.setTransform(null)
        }
        target.uri = uri
        target.playbackAllowed = playbackAllowed
        target.lifecycleStarted = lifecycleStarted
        target.syncPositionProvider = syncPositionProvider
        target.priority = priority
        if (sourceChanged || priorityChanged) {
            refreshActiveTarget(
                context = context,
                forceRebind = target.takeIf { sourceChanged },
            )
        } else if (activeTarget?.view === view) {
            updatePlayback()
        }
    }

    fun detach(context: Context, view: TextureView) {
        val wasActive = activeTarget?.view === view
        targets.remove(view)
        view.surfaceTextureListener = null
        view.alpha = 0f
        view.setTransform(null)
        if (!wasActive) return

        pauseAndRemember()
        clearSurface()
        activeTarget = bestTarget()
        if (activeTarget != null) {
            bindActiveSurface(context)
        } else {
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
        val entry = CoverPosterCache.getOrCreate(context.applicationContext, uri)
        entry?.displaySize?.let { displaySize ->
            withContext(Dispatchers.Main.immediate) {
                session?.takeIf { it.uri == uri }?.let { current ->
                    current.posterDisplaySize = displaySize
                    revealActiveFrameIfReady()
                }
            }
        }
        return entry?.bitmap
    }

    private fun bestTarget(): Target? {
        var best: Target? = null
        targets.values.forEach { candidate ->
            val current = best
            if (
                current == null || coverVideoTargetOutranks(
                    candidatePriority = candidate.priority,
                    candidateSequence = candidate.sequence,
                    activePriority = current.priority,
                    activeSequence = current.sequence,
                )
            ) {
                best = candidate
            }
        }
        return best
    }

    private fun refreshActiveTarget(context: Context, forceRebind: Target?) {
        val previous = activeTarget
        val next = bestTarget()
        activeTarget = next
        val activeChanged = previous?.view !== next?.view
        if (next != null && (activeChanged || forceRebind === next)) {
            bindActiveSurface(context)
        } else if (next != null) {
            updatePlayback()
        }
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
        applyActiveTransform()

        surface = Surface(texture).also { videoSurface ->
            session?.takeIf { it.prepared }?.player?.setSurface(videoSurface)
        }
        session?.rendering = false
        updatePlayback()
    }

    private fun createSession(context: Context, uri: String) {
        val player = MediaPlayer()
        val created = Session(
            uri = uri,
            player = player,
            posterDisplaySize = CoverPosterCache.getDisplaySize(uri),
        )
        session = created
        try {
            player.setVolume(0f, 0f)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { _, width, height ->
                if (session !== created) return@setOnVideoSizeChangedListener
                created.playerDisplaySize = coverVideoDisplaySize(width, height, rotationDegrees = 0)
                revealActiveFrameIfReady()
            }
            player.setOnPreparedListener { preparedPlayer ->
                if (session !== created) return@setOnPreparedListener
                created.prepared = true
                created.playerDisplaySize = coverVideoDisplaySize(
                    encodedWidth = preparedPlayer.videoWidth,
                    encodedHeight = preparedPlayer.videoHeight,
                    // AOSP MediaPlayer reports display-oriented dimensions:
                    // NuPlayer has already swapped 90°/270° video axes.
                    rotationDegrees = 0,
                ) ?: created.playerDisplaySize
                runCatching {
                    // FIT deliberately maps the decoded frame to the TextureView
                    // bounds; our explicit matrix below reverses that non-uniform
                    // map into deterministic ContentScale.Crop semantics. Using
                    // codec-side CROP as well would apply the crop twice.
                    preparedPlayer.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    )
                }
                surface?.let { videoSurface ->
                    runCatching { preparedPlayer.setSurface(videoSurface) }
                }
                revealActiveFrameIfReady()
                updatePlayback()
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && session === created) {
                    created.rendering = true
                    revealActiveFrameIfReady()
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
            // Documented valid from Initialized onward and established before
            // prepare so no first frame can use an implicit vendor default.
            // FIT is also the documented default if an OEM rejects this call.
            runCatching {
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            }
            player.prepareAsync()
        } catch (_: Throwable) {
            if (session === created) {
                releaseSession()
                activeTarget?.view?.alpha = 0f
            }
        }
    }

    /** Applies the same centered crop as the poster's ContentScale.Crop. */
    private fun applyActiveTransform(): Boolean {
        val target = activeTarget ?: return false
        val current = session?.takeIf { it.uri == target.uri } ?: return false
        // A decoded poster is the strongest framing reference because it is
        // exactly what Compose shows beneath this view. MediaPlayer's display
        // size is available earlier and is already rotation-adjusted by AOSP.
        val displaySize = current.posterDisplaySize ?: current.playerDisplaySize
        val scale = displaySize?.let { size ->
            coverVideoCropScale(
                viewWidth = target.view.width,
                viewHeight = target.view.height,
                videoWidth = size.width,
                videoHeight = size.height,
            )
        } ?: run {
            target.view.setTransform(null)
            return false
        }
        target.view.setTransform(
            Matrix().apply {
                setScale(
                    scale.x,
                    scale.y,
                    target.view.width / 2f,
                    target.view.height / 2f,
                )
            }
        )
        return true
    }

    private fun revealActiveFrameIfReady() {
        val current = session ?: return
        val target = activeTarget ?: return
        if (current.uri != target.uri) return
        val geometryReady = applyActiveTransform()
        target.view.alpha = if (current.prepared && current.rendering && geometryReady) 1f else 0f
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

}

private data class CoverPosterEntry(
    val bitmap: Bitmap?,
    val displaySize: CoverVideoSize?,
)

/** Three full-size frames at the cover's validated 1080p ceiling (about 24 MB). */
private object CoverPosterCache {
    private const val MAX_ENTRIES = 3
    private val entries = LinkedHashMap<String, CoverPosterEntry>(MAX_ENTRIES, 0.75f, true)
    private val decodeMutex = Mutex()

    @Synchronized
    fun get(uri: String): Bitmap? = entries[uri]?.bitmap

    @Synchronized
    fun getDisplaySize(uri: String): CoverVideoSize? = entries[uri]?.displaySize

    suspend fun getOrCreate(context: Context, uri: String): CoverPosterEntry? =
        withContext(Dispatchers.IO) {
            decodeMutex.withLock {
                getEntry(uri) ?: decode(context, uri)?.also { decoded ->
                    // Metadata-only fallbacks are useful to this live session,
                    // but do not make a transient frame-decode miss permanent.
                    if (decoded.bitmap != null) put(uri, decoded)
                }
            }
        }

    @Synchronized
    private fun getEntry(uri: String): CoverPosterEntry? = entries[uri]

    @Synchronized
    private fun put(uri: String, entry: CoverPosterEntry) {
        entries[uri] = entry
        while (entries.size > MAX_ENTRIES) {
            val eldest = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldest)
            // Do not eagerly recycle: a Compose Image may still be presenting
            // this frame during a source hand-off. Dropping our strong reference
            // is enough for the bitmap to become collectible afterwards.
        }
    }

    private fun decode(context: Context, uri: String): CoverPosterEntry? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            val encodedWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val encodedHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val frame = retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
            // Framework frame extraction normally applies rotation itself. Its
            // actual bitmap dimensions are therefore the best poster/live
            // agreement; metadata supplies a rotation-aware fallback when no
            // representative frame can be decoded.
            val frameSize = frame?.let {
                coverVideoDisplaySize(it.width, it.height, rotationDegrees = 0)
            }
            val metadataSize = coverVideoDisplaySize(encodedWidth, encodedHeight, rotation)
            if (frame == null && metadataSize == null) null else {
                CoverPosterEntry(bitmap = frame, displaySize = frameSize ?: metadataSize)
            }
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
    priority: CoverVideoPriority = CoverVideoPriority.BANNER,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var poster by remember(uri) { mutableStateOf(CoverPosterCache.get(uri)) }
    val textureView = remember {
        TextureView(context).apply {
            isOpaque = false
            alpha = 0f
        }
    }

    LaunchedEffect(uri) {
        poster = CoverVideoHost.warmPoster(context, uri)
    }

    // Keep the TextureView registered across URI changes. Host.update swaps the
    // necessary MediaPlayer session while retaining this rendering surface.
    DisposableEffect(textureView) {
        CoverVideoHost.attach(
            context = context,
            view = textureView,
            uri = uri,
            playbackAllowed = playbackAllowed,
            lifecycleStarted = lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED),
            syncPositionProvider = syncPositionProvider,
            priority = priority,
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
                    priority = priority,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
