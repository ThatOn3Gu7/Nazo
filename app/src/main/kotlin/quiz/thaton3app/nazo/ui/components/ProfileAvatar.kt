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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
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
 * URI (no disk I/O on the composition path): `file://…mp4` goes to the shared
 * [AvatarVideoView], GIF/animated-WebP ride Coil with the play gate from
 * [rememberAvatarPlayback], and every other case is the original static
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
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val playback = rememberAvatarPlayback()
    val displayKind = remember(pictureUri) { AvatarMedia.displayKind(pictureUri) }
    // Once a video has failed to play, fall back to the initials for good
    // (until the URI changes) instead of retrying the broken file on every
    // scroll or resume.
    var videoFailed by remember(pictureUri) { mutableStateOf(false) }

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

            displayKind == AvatarMedia.DisplayKind.Video -> Box(Modifier.fillMaxSize()) {
                // Underlay: visible until the first frame lands, and the
                // permanent fallback if the video fails to play.
                ProfileInitials(name, size)
                if (!videoFailed) {
                    AvatarVideoView(
                        uri = pictureUri!!,
                        fit = false,
                        playback = playback,
                        modifier = Modifier.fillMaxSize(),
                        onFailed = { videoFailed = true },
                    )
                }
            }

            else -> AnimatedAvatarImage(
                url = pictureUri!!,
                contentDescription = "Profile picture",
                playback = playback,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = { ProfileInitials(name, size) },
                errorContent = { ProfileInitials(name, size) },
            )
        }
    }
    val base = modifier
        .size(size)
        .onGloballyPositioned(playback::onPositioned)
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
