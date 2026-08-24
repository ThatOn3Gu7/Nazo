package quiz.thaton3app.nazo.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shown when the device OS theme no longer matches the applied launcher icon.
 * Offers to relaunch now (to swap the icon immediately) or continue (the icon is
 * swapped automatically when the app exits), so the running session is never disrupted.
 */
@Composable
fun IconThemeDialog(
    darkTarget: Boolean,
    onRelaunch: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onContinue,
        title = { Text("Update app icon?") },
        text = {
            Text(
                if (darkTarget) {
                    "Your phone is now in dark mode. Relaunch the app to switch the " +
                        "icon to the dark-green variant?"
                } else {
                    "Your phone is now in light mode. Relaunch the app to switch the " +
                        "icon to the light-green variant?"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onRelaunch) { Text("Relaunch") }
        },
        dismissButton = {
            TextButton(onClick = onContinue) { Text("Not now") }
        },
    )
}
