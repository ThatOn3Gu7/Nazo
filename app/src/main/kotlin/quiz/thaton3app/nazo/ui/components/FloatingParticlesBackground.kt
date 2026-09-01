package quiz.thaton3app.nazo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    AmbientBackground(modifier = modifier, style = "shapes", touchRipplesEnabled = true)
}
