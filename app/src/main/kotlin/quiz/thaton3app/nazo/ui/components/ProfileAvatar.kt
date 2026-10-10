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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSurface

/** Loads a remote image with a composable placeholder/error fallback. */
@Composable
fun SafeRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
    errorContent: @Composable () -> Unit = {},
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { placeholder() },
        error = { errorContent() },
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
    val content: @Composable () -> Unit = {
        if (!pictureUri.isNullOrBlank()) {
            if (pictureUri.startsWith("emoji:")) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(NazoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        pictureUri.removePrefix("emoji:"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = (size.value * 0.40f).sp,
                        ),
                        color = NazoPrimary,
                    )
                }
            } else {
                SafeRemoteImage(
                    url = pictureUri,
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = { ProfileInitials(name, size) },
                    errorContent = { ProfileInitials(name, size) },
                )
            }
        } else {
            ProfileInitials(name, size)
        }
    }
    val base = modifier.size(size).clip(shape).background(NazoSurface)
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
