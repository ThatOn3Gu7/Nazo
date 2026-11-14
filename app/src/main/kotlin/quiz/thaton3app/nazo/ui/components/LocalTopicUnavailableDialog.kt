package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import quiz.thaton3app.nazo.ui.theme.NazoTextSecondary

/** Explicit recovery when a requested topic has no built-in questions. */
@Composable
fun LocalTopicUnavailableDialog(
    topic: String,
    onChangeTopic: () -> Unit,
    onUseAnyTopic: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onChangeTopic,
        title = { Text("No local questions found") },
        text = {
            Text(
                text = "Nazo's built-in bank has no questions matching “$topic”. " +
                    "Choose another topic, or explicitly start an all-anime quiz.",
                style = MaterialTheme.typography.bodyMedium,
                color = NazoTextSecondary,
            )
        },
        confirmButton = {
            NazoPrimaryButton(
                onClick = onChangeTopic,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change topic")
            }
        },
        dismissButton = {
            NazoSecondaryButton(
                onClick = onUseAnyTopic,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use any topic")
            }
        },
    )
}
