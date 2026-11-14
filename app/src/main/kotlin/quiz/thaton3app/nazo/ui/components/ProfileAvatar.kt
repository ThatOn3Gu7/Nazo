package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import quiz.thaton3app.nazo.data.profile.AvatarMedia
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface

/**
 * Loads a remote image with a composable placeholder/error fallback.
 *
 * [onSuccess] exposes the decoded drawable so callers can drive an animated
 * (GIF / animated WebP) result — see [AnimatedAvatarImage]; static images
 * simply never invoke it with an `Animatable`.
 */
@Composable
fun SafeRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { placeholder() },
        error = { errorContent() },
        onSuccess = onSuccess,
    )
}

@Composable
fun ProfileInitials(username: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NazoPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.Bold,
            ),
            color = NazoPrimary,
        )
    }
}

/**
 * The shared circular profile picture: initials, emoji, a (possibly animated)
 * image, or a looping muted video — everywhere it appears in the app.
 *
 * Animated media is dispatched by [AvatarMedia.displayKind] off the stored
 * URI (no disk I/O on the composition path): `file://…mp4` becomes a video
 * ANCHOR — the cached poster/fallback slot that [PersistentAvatarVideo]'s
 * single app-level TextureView is positioned over (no per-screen surfaces,
 * no handoff on navigation); GIF/animated-WebP ride Coil with the play gate
 * from [rememberAvatarPlayback]; every other case is the original static
 * image path, unchanged. The gate also pauses both kinds when the avatar
 * scrolls out of its column or the app is backgrounded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileAvatar(
    name: String,
    pictureUri: String?,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    /**
     * Optional press-and-hold action. Opt-in: callers that do not pass one keep
     * the original `Surface(onClick = ...)` path untouched, so tap behaviour is
     * unchanged everywhere this avatar is used.
     */
    onLongClick: (() -> Unit)? = null,
    /**
     * True while something covers this avatar (picker, preview, confirmation
     * dialogs): the live picture holds its current frame instead of decoding
     * behind the scrim — which is also what keeps opening those dialogs as
     * snappy as it was before avatars could animate.
     */
    paused: Boolean = false,
    /**
     * False while this screen is entering/leaving. GIF/WebP decoding pauses so
     * AnimatedContent never runs two animated avatar drawables at once. This is
     * deliberately separate from [paused]: video anchors stay available to the
     * persistent overlay during navigation and are not marked suppressed.
     */
    animationAllowed: Boolean = true,
    /**
     * Which screen's slot this avatar is, for the persistent video overlay:
     * the app-level [PersistentAvatarVideo] tracks the active screen's anchor
     * to position its single TextureView over this circle. Null (or a
     * non-video [pictureUri]) never touches the registry — GIF/static/emoji
     * rendering is unchanged.
     */
    anchorId: AvatarAnchorId? = null,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val playback = rememberAvatarPlayback()
    playback.paused = paused || !animationAllowed
    val displayKind = remember(pictureUri) { AvatarMedia.displayKind(pictureUri) }

    // Video avatars bind an anchor (video branch only): the overlay needs
    // this slot's window bounds, dialog-cover and visibility signals.
    val registry = LocalAvatarAnchorRegistry.current
    val anchorState = remember(anchorId, displayKind, registry) {
        if (anchorId != null && displayKind == AvatarMedia.DisplayKind.Video) {
            registry?.bind(anchorId)
        } else {
            null
        }
    }
    DisposableEffect(anchorState, registry) {
        val state = anchorState
        onDispose {
            state?.refreshBounds = null
            if (state != null) registry?.unbind(state)
        }
    }
    // Reading this snapshot value in composition guarantees one recomposition
    // when a slide settles. The SideEffect then samples the already-attached
    // coordinates even if no new measure/layout callback happens at rest.
    val captureAnchorBounds = registry?.captureEnabled != false
    SideEffect {
        anchorState?.suppressed = paused
        anchorState?.centerVisible = playback.visible
        if (captureAnchorBounds) anchorState?.refreshBounds?.invoke()
        // The persistent TextureView visually overlays this composable and
        // receives native input first. Keep its forwarded actions in sync with
        // the screen-owned tap and press-and-hold behavior.
        anchorState?.onClick = onClick
        anchorState?.onLongClick = onLongClick
    }

    val content: @Composable () -> Unit = {
        when {
            displayKind == AvatarMedia.DisplayKind.None -> ProfileInitials(name, size)

            displayKind == AvatarMedia.DisplayKind.Emoji -> Box(
                Modifier
                    .fillMaxSize()
                    .background(NazoPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    pictureUri!!.removePrefix("emoji:"),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = (size.value * 0.40f).sp,
                    ),
                    color = NazoPrimary,
                )
            }

            displayKind == AvatarMedia.DisplayKind.Video ->
                // Poster/fallback only — the live frames come from the one
                // persistent app-level TextureView (PersistentAvatarVideo),
                // which is positioned over this slot. No surface handoff on
                // navigation anymore; this circle is the underlay that covers
                // genuine initialization and every hidden-overlay state.
                AvatarVideoAnchorContent(
                    uri = pictureUri!!,
                    name = name,
                    size = size,
                    modifier = Modifier.fillMaxSize(),
                )

            else -> AnimatedAvatarImage(
                url = pictureUri!!,
                contentDescription = "Profile picture",
                playback = playback,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // Animated pictures hold their placeholder back briefly so a
                // screen change doesn't flash the initials over the crossfade;
                // plain photos keep the original immediate placeholder.
                placeholderDelayMs = if (AvatarMedia.isAnimatedImageUri(pictureUri)) {
                    GIF_PLACEHOLDER_DELAY_MS
                } else {
                    0L
                },
                placeholder = { ProfileInitials(name, size) },
                errorContent = { ProfileInitials(name, size) },
            )
        }
    }
    val base = modifier
        .size(size)
        .onGloballyPositioned { coords ->
            playback.onPositioned(coords)
            anchorState?.let { state ->
                state.present = true
                state.centerVisible = playback.visible
                // Skip capture during screen motion so the slot keeps its
                // resting position (the overlay replicates the ordinary or
                // predictive slide itself, frame-exactly). positionInWindow
                // + layout size: window coordinates that are never clipped, so
                // an off-window slot still reports its true rect.
                val refreshBounds = {
                    if (coords.isAttached) {
                        val p = coords.positionInWindow()
                        val s = coords.size
                        state.restBounds = Rect(p.x, p.y, p.x + s.width, p.y + s.height)
                        // Keep the root-level native surface clipped exactly
                        // like this in-screen slot. boundsInWindow intersects
                        // the circle with scroll and window clipping ancestors;
                        // positionInWindow above intentionally remains the full
                        // geometry so video crop/scale never changes at an edge.
                        state.visibleBounds = coords.boundsInWindow()
                    }
                }
                state.refreshBounds = refreshBounds
                if (captureAnchorBounds) refreshBounds()
            }
        }
        .clip(shape)
        .background(NazoSurface)
    when {
        // Surface(onClick = ...) cannot express a long press, so a caller that
        // wants one gets a plain Surface plus combinedClickable. The clickable
        // is applied AFTER clip(shape), so the ripple stays inside the circle
        // exactly as the Surface version does, and Role.Button keeps the same
        // accessibility semantics Surface(onClick) adds.
        onLongClick != null -> Surface(
            modifier = base.combinedClickable(
                role = Role.Button,
                onClick = onClick ?: {},
                onLongClick = onLongClick,
            ),
            shape = shape,
            color = NazoSurface,
        ) {
            content()
        }

        onClick != null -> Surface(onClick = onClick, modifier = base, shape = shape, color = NazoSurface) {
            content()
        }

        else -> Surface(modifier = base, shape = shape, color = NazoSurface) {
            content()
        }
    }
}
