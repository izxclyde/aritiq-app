package com.aritiq.calcnote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aritiq.calcnote.data.export.EncryptionService
import com.aritiq.calcnote.data.export.shareExport
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ExportPasswordDialog(
    error: String?,
    title: String = "Enter export password",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank(), onClick = { onConfirm(password) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SaveExportPasswordDialog(onSave: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Save export password?") },
        text = {
            Text(
                "No export password is set. Remember the password you type — a forgotten password " +
                    "can't be recovered. Save it so future exports use the same one."
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save password") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Don't save") }
        },
    )
}

@Composable
fun rememberExportWithPassword(
    settingsViewModel: SettingsViewModel,
    encryptionService: EncryptionService,
    context: Any,
    getJson: suspend () -> String?,
    onDone: () -> Unit = {},
): () -> Unit {
    val scope = rememberCoroutineScope()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun doExport(password: String) {
        val json = pendingJson
        if (json != null) {
            shareEncryptedExport(context, json, password, encryptionService)
        }
        showPasswordDialog = false
        showSaveDialog = false
        passwordError = null
        pendingJson = null
        pendingPassword = null
        onDone()
    }

    fun onPasswordEntered(password: String) {
        scope.launch {
            when {
                settingsViewModel.verifyExportPassword(password) -> doExport(password)
                settingsViewModel.isPasswordSet() -> passwordError = "Wrong password"
                else -> {
                    pendingPassword = password
                    showPasswordDialog = false
                    showSaveDialog = true
                }
            }
        }
    }

    fun start() {
        scope.launch {
            val json = getJson()
            if (json != null) {
                pendingJson = json
                passwordError = null
                showPasswordDialog = true
            }
        }
    }

    if (showPasswordDialog) {
        ExportPasswordDialog(
            error = passwordError,
            onConfirm = ::onPasswordEntered,
            onDismiss = {
                showPasswordDialog = false
                passwordError = null
                pendingJson = null
            },
        )
    }

    if (showSaveDialog) {
        SaveExportPasswordDialog(
            onSave = {
                val password = pendingPassword
                if (password != null) {
                    settingsViewModel.setExportPassword(password)
                    doExport(password)
                } else {
                    showSaveDialog = false
                }
            },
            onSkip = {
                val password = pendingPassword
                if (password != null) doExport(password) else showSaveDialog = false
            },
        )
    }

    return ::start
}

private fun shareEncryptedExport(
    context: Any,
    json: String,
    password: String,
    encryptionService: EncryptionService,
) {
    val encrypted = encryptionService.encrypt(json, password)
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val ts = "%04d%02d%02d-%02d%02d%02d".format(now.year, now.monthNumber, now.dayOfMonth, now.hour, now.minute, now.second)
    shareExport(context, encrypted, "application/octet-stream", "aritiq-export-$ts.aritiq")
}
