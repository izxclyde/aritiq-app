package com.aritiq.calcnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.aritiq.calcnote.appVersion
import androidx.compose.foundation.isSystemInDarkTheme
import com.aritiq.calcnote.data.export.EncryptionService
import com.aritiq.calcnote.data.export.ImportMode
import com.aritiq.calcnote.data.update.UpdateInfo
import com.aritiq.calcnote.data.update.checkForUpdate
import com.aritiq.calcnote.ui.components.ExportPasswordDialog
import com.aritiq.calcnote.ui.components.UpdateAvailableDialog
import com.aritiq.calcnote.ui.components.rememberExportWithPassword
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.theme.NotebookAccent
import com.aritiq.calcnote.ui.theme.dark
import com.aritiq.calcnote.ui.theme.light
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: Navigator) {  
    BackHandler { navigator.pop() }
    val vm = koinInject<SettingsViewModel>()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val encryptionService = remember { EncryptionService() }
    val exportWithPassword = rememberExportWithPassword(
        settingsViewModel = vm,
        encryptionService = encryptionService,
        context = context,
        getJson = { vm.exportAllJson() },
    )
    var showImportDialog by remember { mutableStateOf(false) }
    var importContent by remember { mutableStateOf("") }

    // Password dialogs
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Import password prompt
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importPasswordError by remember { mutableStateOf<String?>(null) }

    // Update check
    var updateAvailable by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    fun handleDecryptedContent(content: String?) {
        if (content == null) {
            vm.importResult("Could not decrypt file")
        } else if (content.isBlank()) {
            vm.importResult("File is empty")
        } else {
            importContent = content
            showImportDialog = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        vm.importResult("File is empty")
                        return@launch
                    }
                    if (encryptionService.isEncrypted(bytes)) {
                        pendingImportBytes = bytes
                        importPasswordError = null
                        showImportPasswordDialog = true
                    } else {
                        handleDecryptedContent(String(bytes, Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    vm.importResult("Error reading file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            SettingsViewModel.ThemeMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.themeMode == mode, onClick = { vm.setTheme(mode) })
                    Text(mode.name)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Accent", style = MaterialTheme.typography.titleSmall)
            val isDark = when (state.themeMode) {
                SettingsViewModel.ThemeMode.System -> isSystemInDarkTheme()
                SettingsViewModel.ThemeMode.Light -> false
                SettingsViewModel.ThemeMode.Dark -> true
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NotebookAccent.entries.forEach { accent ->
                    val selected = state.accent == accent
                    val accentScheme = if (isDark) accent.dark else accent.light
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentScheme.primary)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { vm.setAccent(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = accentScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Export Password", style = MaterialTheme.typography.titleSmall)
            if (state.passwordSet) {
                Text("Password is set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showChangePasswordDialog = true }) {
                        Text("Change")
                    }
                    OutlinedButton(onClick = { vm.clearExportPassword() }) {
                        Text("Remove")
                    }
                }
            } else {
                Text("No password set. Exports use default password.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showSetPasswordDialog = true }) {
                    Text("Set Password")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Export", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { exportWithPassword() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export All")
            }

            Spacer(Modifier.height(16.dp))
            Text("Import", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import from file")
            }
            state.importResult?.let { result ->
                Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("About", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("App: Aritiq", style = MaterialTheme.typography.bodyMedium)
            Text("Version: ${appVersion()}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    updateStatus = "Checking..."
                    scope.launch(Dispatchers.IO) {
                        val info = checkForUpdate()
                        withContext(Dispatchers.Main) {
                            if (info != null) {
                                updateStatus = null
                                updateAvailable = info
                            } else {
                                updateStatus = "You're up to date"
                            }
                        }
                    }
                },
            ) {
                Text("Check for updates")
            }
            updateStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("Developer: HNatividad", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Website: www.hcnatividad.com",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hcnatividad.com"))
                    context.startActivity(intent)
                },
            )
            Spacer(Modifier.height(15.dp))
            HorizontalDivider()
        }
    }

    // Import merge/replace dialog
    var importing by remember { mutableStateOf(false) }
    if (showImportDialog && importContent.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { if (!importing) { showImportDialog = false; importContent = "" } },
            title = { Text("Import notes") },
            text = {
                if (importing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Importing...")
                    }
                } else {
                    Text("Import with Merge (keep existing, skip duplicates) or Replace (delete all existing notes)?")
                }
            },
            confirmButton = {
                TextButton(enabled = !importing, onClick = {
                    importing = true
                    scope.launch {
                        vm.importFromString(importContent, ImportMode.REPLACE)
                        showImportDialog = false; importContent = ""; importing = false
                    }
                }) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(enabled = !importing, onClick = {
                    importing = true
                    scope.launch {
                        vm.importFromString(importContent, ImportMode.MERGE)
                        showImportDialog = false; importContent = ""; importing = false
                    }
                }) {
                    Text("Merge")
                }
            },
        )
    }

    // Set password dialog
    if (showSetPasswordDialog) {
        PasswordSetDialog(
            title = "Set Export Password",
            onConfirm = { password ->
                vm.setExportPassword(password)
                showSetPasswordDialog = false
            },
            onDismiss = { showSetPasswordDialog = false },
        )
    }

    // Change password dialog
    if (showChangePasswordDialog) {
        PasswordChangeDialog(
            onConfirm = { oldPassword, newPassword ->
                val success = vm.changeExportPassword(oldPassword, newPassword)
                if (success) {
                    showChangePasswordDialog = false
                    passwordError = null
                } else {
                    passwordError = "Wrong password"
                }
            },
            onDismiss = { showChangePasswordDialog = false; passwordError = null },
            error = passwordError,
        )
    }

    // Import password dialog (file is encrypted)
    if (showImportPasswordDialog) {
        ExportPasswordDialog(
            error = importPasswordError,
            onConfirm = { password ->
                val bytes = pendingImportBytes
                if (bytes != null) {
                    val decrypted = try {
                        encryptionService.decrypt(bytes, password)
                    } catch (e: Exception) {
                        null
                    }
                    if (decrypted != null) {
                        pendingImportBytes = null
                        showImportPasswordDialog = false
                        importPasswordError = null
                        handleDecryptedContent(decrypted)
                    } else {
                        importPasswordError = "Wrong password"
                    }
                }
            },
            onDismiss = {
                pendingImportBytes = null
                showImportPasswordDialog = false
                importPasswordError = null
            },
        )
    }

    UpdateAvailableDialog(update = updateAvailable, onDismiss = { updateAvailable = null })
}

@Composable
private fun PasswordSetDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = password.isNotBlank(), onClick = { onConfirm(password) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasswordChangeDialog(
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit,
    error: String?,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showOld by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Current password") },
                    visualTransformation = if (showOld) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOld = !showOld }) {
                            Icon(
                                if (showOld) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
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
            TextButton(
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank(),
                onClick = { onConfirm(oldPassword, newPassword) },
            ) {
                Text("Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
