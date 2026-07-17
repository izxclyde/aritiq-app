package com.aritiq.calcnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import com.aritiq.calcnote.appVersion
import com.aritiq.calcnote.data.export.ImportMode
import com.aritiq.calcnote.data.export.shareExport
import com.aritiq.calcnote.ui.navigation.Navigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: Navigator) {
    BackHandler { navigator.pop() }
    val vm = koinInject<SettingsViewModel>().also { it.load() }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var importContent by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    importContent = input?.bufferedReader()?.readText() ?: ""
                    input?.close()
                    if (importContent.isBlank()) {
                        vm.importResult("File is empty")
                    } else {
                        showImportDialog = true
                    }
                } catch (e: Exception) {
                    vm.importResult("Error reading file: ${e.message}")
                }
            }
        }
    }

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

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Export", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    scope.launch { shareExport(context, vm.exportAllJson(), "application/json", "aritiq-all.json") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export All as JSON")
            }

            Spacer(Modifier.height(16.dp))
            Text("Import", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/json")) },
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
            Text("Developer: HNatividad", style = MaterialTheme.typography.bodyMedium)
            ClickableText(
                text = AnnotatedString("Website: www.hcnatividad.com"),
                style = MaterialTheme.typography.bodyMedium,
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hcnatividad.com"))
                    context.startActivity(intent)
                }
            )
            Spacer(Modifier.height(15.dp))
            HorizontalDivider()
        }
    }

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
                        val result = vm.importFromString(importContent, ImportMode.REPLACE)
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
                        val result = vm.importFromString(importContent, ImportMode.MERGE)
                        showImportDialog = false; importContent = ""; importing = false
                    }
                }) {
                    Text("Merge")
                }
            },
        )
    }
}