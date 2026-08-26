package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.background
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

@Composable
fun ProfileAvatar(
    name: String,
    pictureUri: String?,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
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
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = shape, color = NazoSurface) {
            content()
        }
    } else {
        Surface(modifier = base, shape = shape, color = NazoSurface) {
            content()
        }
    }
}
