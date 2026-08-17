package com.aritiq.calcnote.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aritiq.calcnote.data.update.UpdateInfo
import com.aritiq.calcnote.data.update.canRequestPackageInstalls
import com.aritiq.calcnote.data.update.downloadAndInstallUpdate
import com.aritiq.calcnote.data.update.openUnknownAppSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UpdateAvailableDialog(update: UpdateInfo?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update available") },
            text = { Text("A new version of Aritiq is available. Download and install it?") },
            confirmButton = {
                TextButton(
                    enabled = !installing,
                    onClick = {
                        if (!canRequestPackageInstalls(context)) {
                            showPermissionDialog = true
                        } else {
                            installing = true
                            scope.launch(Dispatchers.IO) {
                                downloadAndInstallUpdate(context, info)
                                installing = false
                                onDismiss()
                            }
                        }
                    },
                ) {
                    Text(if (installing) "Downloading..." else "Install")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            },
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Allow app installs") },
            text = {
                Text(
                    "To install updates, Aritiq needs permission to install apps " +
                        "from unknown sources. You'll be taken to Settings to grant it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openUnknownAppSources(context)
                    showPermissionDialog = false
                }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
            },
        )
    }
}