package quiz.thaton3app.nazo.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import quiz.thaton3app.nazo.data.profile.CoverPresetKind
import quiz.thaton3app.nazo.data.profile.CoverVisual

internal const val CoverArtworkTransitionDurationMs = 520
internal const val CoverArtworkIncomingScale = 1.012f
private const val CoverArtworkOutgoingScale = 0.994f
private val CoverArtworkEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The bounded transition plan used when a new selection arrives mid-fade.
 * There are never more than two artwork layers: the more visible old layer is
 * retained and every other interrupted layer is dropped before [latest] starts.
 */
internal data class CoverTransitionPlan<T>(
    val outgoing: T?,
    val incoming: T,
    val outgoingStartAlpha: Float,
    val outgoingStartScale: Float,
    val incomingStartAlpha: Float,
    val incomingStartScale: Float,
)

internal fun transitionLayerAlpha(startAlpha: Float, fraction: Float, incoming: Boolean): Float {
    val progress = fraction.coerceIn(0f, 1f)
    return if (incoming) {
        startAlpha + ((1f - startAlpha) * progress)
    } else {
        startAlpha * (1f - progress)
    }
}

internal fun transitionLayerScale(startScale: Float, fraction: Float, incoming: Boolean): Float {
    val progress = fraction.coerceIn(0f, 1f)
    val end = if (incoming) 1f else CoverArtworkOutgoingScale
    return startScale + ((end - startScale) * progress)
}

/**
 * Retarget an interrupted transition without queuing it. The visually dominant
 * old layer becomes the sole outgoing layer; if the user selected that layer
 * again, it simply eases back to fully visible.
 */
internal fun <T> retargetCoverTransition(
    outgoing: T?,
    incoming: T,
    fraction: Float,
    outgoingStartAlpha: Float,
    outgoingStartScale: Float,
    incomingStartAlpha: Float,
    incomingStartScale: Float,
    latest: T,
): CoverTransitionPlan<T> {
    val outgoingAlpha = outgoing?.let {
        transitionLayerAlpha(outgoingStartAlpha, fraction, incoming = false)
    } ?: -1f
    val incomingAlpha = transitionLayerAlpha(incomingStartAlpha, fraction, incoming = true)
    val keepOutgoing = outgoing != null && outgoingAlpha > incomingAlpha
    val dominant = if (keepOutgoing) outgoing!! else incoming
    val dominantAlpha = if (keepOutgoing) outgoingAlpha else incomingAlpha
    val dominantScale = if (keepOutgoing) {
        transitionLayerScale(outgoingStartScale, fraction, incoming = false)
    } else {
        transitionLayerScale(incomingStartScale, fraction, incoming = true)
    }

    return if (dominant == latest) {
        CoverTransitionPlan(
            outgoing = null,
            incoming = latest,
            outgoingStartAlpha = 0f,
            outgoingStartScale = 1f,
            incomingStartAlpha = dominantAlpha,
            incomingStartScale = dominantScale,
        )
    } else {
        CoverTransitionPlan(
            outgoing = dominant,
            incoming = latest,
            outgoingStartAlpha = dominantAlpha,
            outgoingStartScale = dominantScale,
            incomingStartAlpha = 0f,
            incomingStartScale = CoverArtworkIncomingScale,
        )
    }
}

/** Matching media-ready reveal for the photo/video acceptance previews. */
@Composable
internal fun CoverPreviewReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (rememberSystemReduceMotion()) {
        if (visible) Box(modifier = modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(CoverArtworkTransitionDurationMs, easing = CoverArtworkEasing),
        ) + scaleIn(
            initialScale = CoverArtworkIncomingScale,
            animationSpec = tween(CoverArtworkTransitionDurationMs, easing = CoverArtworkEasing),
        ),
        exit = fadeOut(animationSpec = tween(140)) +
            scaleOut(targetScale = 0.996f, animationSpec = tween(140)),
    ) {
        content()
    }
}

/**
 * One transition implementation for the real banner and live automatic tile.
 *
 * Static artwork and gradients overlap for a 520 ms crossfade with a 1.2%
 * settle. Video always has exactly one live TextureView/player target. A
 * video-to-video change keeps the old loop live while fading to the prepared
 * new poster, then changes the persistent plane's URI underneath that poster;
 * video-to-static likewise keeps the old loop through the fade. This preserves
 * motion without ever holding two hardware decoders for cosmetic polish.
 *
 * Local images and video posters are prepared before [visual] becomes the
 * incoming layer. A failed preparation leaves the last valid artwork in place.
 * Mid-transition changes are retargeted by [retargetCoverTransition], so the
 * latest selection wins and no animation queue or layer stack can build up.
 */
@Composable
internal fun TransitioningCoverArtwork(
    visual: CoverVisual,
    playbackAllowed: Boolean,
    modifier: Modifier = Modifier,
    transitionsAllowed: Boolean = true,
    videoPriority: CoverVideoPriority = CoverVideoPriority.BANNER,
) {
    val context = LocalContext.current
    val reduceMotion = rememberSystemReduceMotion()
    var outgoing by remember { mutableStateOf<CoverVisual?>(null) }
    var incoming by remember { mutableStateOf(visual) }
    var outgoingStartAlpha by remember { mutableFloatStateOf(1f) }
    var outgoingStartScale by remember { mutableFloatStateOf(1f) }
    var incomingStartAlpha by remember { mutableFloatStateOf(1f) }
    var incomingStartScale by remember { mutableFloatStateOf(1f) }
    var preparedVisual by remember { mutableStateOf<CoverVisual?>(null) }
    val progress = remember { Animatable(1f) }

    // Preparation and presentation are separate effects. A rapid selection can
    // cancel an obsolete preload without freezing the fade already on screen;
    // only a successfully prepared latest value retargets that fade.
    LaunchedEffect(visual, transitionsAllowed, reduceMotion) {
        if (!transitionsAllowed || reduceMotion) {
            preparedVisual = visual
        } else if (visual != incoming || outgoing != null) {
            if (prepareCoverArtwork(context, visual)) preparedVisual = visual
        }
    }

    LaunchedEffect(preparedVisual, transitionsAllowed, reduceMotion) {
        val prepared = preparedVisual ?: return@LaunchedEffect
        if (!transitionsAllowed || reduceMotion) {
            progress.snapTo(1f)
            outgoing = null
            incoming = prepared
            outgoingStartAlpha = 0f
            incomingStartAlpha = 1f
            incomingStartScale = 1f
            return@LaunchedEffect
        }
        if (prepared == incoming && outgoing == null) return@LaunchedEffect

        val plan = retargetCoverTransition(
            outgoing = outgoing,
            incoming = incoming,
            fraction = progress.value,
            outgoingStartAlpha = outgoingStartAlpha,
            outgoingStartScale = outgoingStartScale,
            incomingStartAlpha = incomingStartAlpha,
            incomingStartScale = incomingStartScale,
            latest = prepared,
        )
        outgoing = plan.outgoing
        incoming = plan.incoming
        outgoingStartAlpha = plan.outgoingStartAlpha
        outgoingStartScale = plan.outgoingStartScale
        incomingStartAlpha = plan.incomingStartAlpha
        incomingStartScale = plan.incomingStartScale
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = CoverArtworkTransitionDurationMs,
                easing = CoverArtworkEasing,
            ),
        )
        // A newer prepared value cancels this effect before cleanup, so only
        // the still-current target can retire its outgoing layer.
        outgoing = null
        outgoingStartAlpha = 0f
        incomingStartAlpha = 1f
        incomingStartScale = 1f
    }

    val outgoingVisual = outgoing
    val incomingVisual = incoming
    val outgoingVideo = outgoingVisual?.videoSpec()
    val incomingVideo = incomingVisual.videoSpec()
    val videoToVideo = outgoingVideo != null && incomingVideo != null
    // For video -> video, let the old live loop finish the fade while the new
    // prepared poster comes in. Only after settling does the persistent plane
    // change URI and decoder session underneath that identical poster.
    val liveVideo = if (videoToVideo) outgoingVideo else incomingVideo ?: outgoingVideo
    val liveVideoIsIncoming = !videoToVideo && incomingVideo != null

    Box(modifier = modifier) {
        outgoingVisual?.let { oldVisual ->
            val oldVideo = oldVisual.videoSpec()
            val oldVideoUsesLivePlane = oldVideo != null && oldVideo == liveVideo
            if (oldVideo == null || !oldVideoUsesLivePlane) {
                CoverArtworkLayer(
                    visual = oldVisual,
                    videoAsPoster = oldVideo != null,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .graphicsLayer {
                            alpha = transitionLayerAlpha(
                                outgoingStartAlpha,
                                progress.value,
                                incoming = false,
                            )
                            val scale = transitionLayerScale(
                                outgoingStartScale,
                                progress.value,
                                incoming = false,
                            )
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        val incomingVideoUsesLivePlane = incomingVideo != null && incomingVideo == liveVideo
        if (incomingVideo == null || !incomingVideoUsesLivePlane) {
            CoverArtworkLayer(
                visual = incomingVisual,
                videoAsPoster = incomingVideo != null,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        alpha = transitionLayerAlpha(
                            incomingStartAlpha,
                            progress.value,
                            incoming = true,
                        )
                        val scale = transitionLayerScale(
                            incomingStartScale,
                            progress.value,
                            incoming = true,
                        )
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        liveVideo?.let { video ->
            val layerIsIncoming = liveVideoIsIncoming
            CoverVideoBackground(
                uri = video.uri,
                playbackAllowed = playbackAllowed,
                syncPositionProvider = video.avatarUri?.let { avatarUri ->
                    { AvatarVideoHost.currentPositionFor(avatarUri) }
                },
                priority = videoPriority,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (layerIsIncoming) 1f else 0f)
                    .graphicsLayer {
                        if (layerIsIncoming) {
                            alpha = transitionLayerAlpha(
                                incomingStartAlpha,
                                progress.value,
                                incoming = true,
                            )
                            val scale = transitionLayerScale(
                                incomingStartScale,
                                progress.value,
                                incoming = true,
                            )
                            scaleX = scale
                            scaleY = scale
                        } else {
                            alpha = transitionLayerAlpha(
                                outgoingStartAlpha,
                                progress.value,
                                incoming = false,
                            )
                            val scale = transitionLayerScale(
                                outgoingStartScale,
                                progress.value,
                                incoming = false,
                            )
                            scaleX = scale
                            scaleY = scale
                        }
                    },
            )
        }
    }
}

private data class CoverVideoSpec(val uri: String, val avatarUri: String?)

private fun CoverVisual.videoSpec(): CoverVideoSpec? = when (this) {
    is CoverVisual.Video -> CoverVideoSpec(uri, avatarUri = null)
    is CoverVisual.AutomaticVideo -> CoverVideoSpec(uri, avatarUri)
    else -> null
}

@Composable
private fun CoverArtworkLayer(
    visual: CoverVisual,
    videoAsPoster: Boolean,
    modifier: Modifier,
) {
    when (visual) {
        is CoverVisual.Gallery -> SafeRemoteImage(
            url = visual.uri,
            contentDescription = "Cover photo",
            modifier = modifier,
        )
        is CoverVisual.AutomaticImage -> SafeRemoteImage(
            url = visual.uri,
            contentDescription = "Cover generated from profile picture",
            modifier = modifier,
        )
        is CoverVisual.Video -> if (videoAsPoster) {
            CoverVideoPoster(uri = visual.uri, modifier = modifier)
        }
        is CoverVisual.AutomaticVideo -> if (videoAsPoster) {
            CoverVideoPoster(uri = visual.uri, modifier = modifier)
        }
        is CoverVisual.Preset -> when (visual.preset.kind) {
            CoverPresetKind.IMAGE -> Image(
                painter = painterResource(visual.preset.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
            CoverPresetKind.GRADIENT -> AnimatedCoverGradient(
                preset = visual.preset,
                modifier = modifier,
            )
        }
        CoverVisual.Default -> Unit
    }
}

private suspend fun prepareCoverArtwork(context: Context, visual: CoverVisual): Boolean = when (visual) {
    is CoverVisual.Gallery -> prepareCoverImage(context, visual.uri)
    is CoverVisual.AutomaticImage -> prepareCoverImage(context, visual.uri)
    is CoverVisual.Video -> CoverVideoHost.warmPoster(context, visual.uri) != null
    is CoverVisual.AutomaticVideo -> CoverVideoHost.warmPoster(context, visual.uri) != null
    is CoverVisual.Preset,
    CoverVisual.Default -> true
}

private suspend fun prepareCoverImage(context: Context, uri: String): Boolean {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(1024, 576)
        .crossfade(false)
        .build()
    return runCatching { Coil.imageLoader(context).execute(request) is SuccessResult }
        .getOrDefault(false)
}
