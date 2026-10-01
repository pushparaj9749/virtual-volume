package dev.virtualvolume.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Android exposes no universal Quick Settings editor intent; explain the real manual flow. */
@Composable
fun QuickSettingsHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("One swipe away") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("1   Swipe down twice to open Quick Settings.")
                Text("2   Tap Edit, the pencil, or your phone's edit menu.")
                Text("3   Find Virtual Volume and drag it into your active tiles.")
                Spacer(Modifier.height(2.dp))
                Text("Tap the tile to turn the control on or off. Adding a tile is always your choice; the layout varies by phone.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
