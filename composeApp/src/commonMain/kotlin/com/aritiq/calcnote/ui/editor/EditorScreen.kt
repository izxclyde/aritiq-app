package com.aritiq.calcnote.ui.editor

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.export.EncryptionService
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.NoteProcessor
import com.aritiq.calcnote.ui.components.rememberExportWithPassword
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import com.aritiq.calcnote.ui.theme.editorTextStyle
import com.aritiq.calcnote.ui.theme.paperColorScheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor body. Plain multiline text, monospace.
 *
 *  - The editable text is the single source of truth for everything at/under the cursor. The
 *    only programmatic rewrite is a stale filled total (`total = old`) when the user edits
 *    strictly above that line ([NoteProcessor.refreshFilledTotal]) — the cursor region is
 *    never touched, so the IME/keyboard stays stable.
 *  - When the note's last non-empty line is a total trigger (`total` or `total =`), a separate
 *    NON-editable `Text` renders `total = <live sum>` directly under it — same font, same left
 *    margin, same ruled paper — so it reads as the next line of the page while never living
 *    inside the user's text field. The number is always live (derived from [UiState.currentSum]).
 *  - The ruled paper is drawn from the text layout's real baselines, not a fixed spacing guess,
 *    so lines and text stay aligned at any density or font scale.
 *  - The bottom status bar shows the live Σ (tap to copy) and word/char count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navigator: Navigator,
    noteId: String?,
) {
    val repo = koinInject<NoteRepository>()
    val exportService = koinInject<ExportService>()
    val folderRepo = koinInject<FolderRepository>()
    val encryptionService = remember { EncryptionService() }
    val vm = remember { EditorViewModel(repo, exportService, folderRepo) }
    // remember{}-scoped VM owns a SupervisorJob; without this every editor visit leaks a scope.
    DisposableEffect(vm) { onDispose { vm.cancel() } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportWithPassword = rememberExportWithPassword(
        settingsViewModel = koinInject<SettingsViewModel>(),
        encryptionService = encryptionService,
        context = context,
        getJson = { vm.exportJson() },
    )
    LaunchedEffect(noteId) { vm.open(noteId) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Local text mirror keyed by noteId. Stays empty until state.loaded flips; we then
    // copy state.text into it exactly once. Using TextFieldValue to preserve cursor
    // position and IME state across recompositions (fixes keyboard reset bug).
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var textFieldValue by remember(noteId) { mutableStateOf(TextFieldValue("")) }
    var hasSynced by remember(noteId) { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.id) {
        if (state.loaded && !hasSynced) {
            textFieldValue = TextFieldValue(
                text = state.text,
                selection = androidx.compose.ui.text.TextRange(state.text.length),
            )
            hasSynced = true
        }
    }

    val showReadout = hasSynced && NoteProcessor.isTotalTriggerLine(textFieldValue.text)

    // Swipe-back / hardware back / predictive back all route through here. A truly empty
    // note (no characters at all) is discarded; anything else (even just whitespace) is saved.
    val handleBack: () -> Unit = {
        scope.launch {
            if (textFieldValue.text.isNotEmpty() || state.title.isNotEmpty()) {
                vm.save()
            }
            navigator.pop()
        }
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = state.title,
                        onValueChange = { vm.updateTitle(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back & save")
                    }
                },
                actions = {
                    if (state.folderId != LOCKED_FOLDER_ID) {
                    Box {
                        IconButton(onClick = { showFolderMenu = true }) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = "Assign folder",
                                tint = if (state.folderId != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showFolderMenu, onDismissRequest = { showFolderMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("No folder") },
                                trailingIcon = if (state.folderId == null) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                onClick = { vm.setFolderId(null); showFolderMenu = false },
                            )
                            state.folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name) },
                                    trailingIcon = if (state.folderId == folder.id) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = { vm.setFolderId(folder.id); showFolderMenu = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Create new folder") },
                                onClick = { showFolderMenu = false; showCreateFolderDialog = true },
                            )
                        }
                    }
                    }
                    Box {
                        IconButton(onClick = { showTagMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Label,
                                contentDescription = "Assign tags",
                                tint = if (state.tags.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showTagMenu, onDismissRequest = { showTagMenu = false }) {
                            if (state.allTags.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No tags yet") },
                                    onClick = {},
                                )
                            }
                            state.allTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag) },
                                    trailingIcon = if (tag in state.tags) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = { vm.toggleTag(tag) },
                                )
                            }
                            if (state.tags.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Clear all tags") },
                                    onClick = { state.tags.forEach { vm.toggleTag(it) } },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Create new tag") },
                                onClick = { showTagMenu = false; showCreateTagDialog = true },
                            )
                        }
                    }
                    if (state.id != null) {
                        IconButton(onClick = { exportWithPassword() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export")
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        bottomBar = { StatusBar(state) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The ruled-paper drawBehind is remembered: an inline lambda allocates a new instance
            // per recomposition, invalidating the draw layer and repainting every line per keystroke.
            val ruledPaper = remember {
                Modifier.drawBehind {
                    val lineSpacing = 24.sp.toPx()
                    val strokeW = 1.dp.toPx()
                    val marginX = 40.dp.toPx()

                    // Horizontal ruled lines — span full page width
                    var y = lineSpacing * 0.75f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFFA0988E),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeW,
                        )
                        y += lineSpacing
                    }

                    // Vertical margin line
                    drawLine(
                        color = Color(0xFFC47070),
                        start = Offset(marginX, 0f),
                        end = Offset(marginX, size.height),
                        strokeWidth = strokeW * 1.5f,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Ruled paper tracks the text layout's real baselines: per-line pixel rounding
                // then cancels out instead of accumulating into drift on long notes.
                var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                // Remembered so the Modifier chain isn't reallocated every recomposition. Safe
                // despite capturing textLayout: the draw lambda reads that snapshot state, so the
                // draw phase still invalidates whenever the layout changes.
                val ruledPaper = remember {
                    Modifier.drawBehind {
                        val layout = textLayout ?: return@drawBehind
                        val strokeW = 1.dp.toPx()
                        val marginX = 40.dp.toPx()
                        val pitch = if (layout.lineCount > 1) {
                            val p = layout.getLineBaseline(1) - layout.getLineBaseline(0)
                            if (p > 0f) p else 24.sp.toPx()
                        } else {
                            24.sp.toPx()
                        }
                        // Horizontal ruled lines — at each real text baseline
                        var y = layout.getLineBaseline(0)
                        while (y < size.height) {
                            drawLine(
                                color = Color(0xFFA0988E),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeW,
                            )
                            y += pitch
                        }
                        // Vertical margin line
                        drawLine(
                            color = Color(0xFFC47070),
                            start = Offset(marginX, 0f),
                            end = Offset(marginX, size.height),
                            strokeWidth = strokeW * 1.5f,
                        )
                    }
                }
                BoxWithConstraints(
                    modifier = ruledPaper
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Column {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { v ->
                                if (!hasSynced) return@BasicTextField
                                var applied = v
                                // Live-refresh a stale filled total (`total = old`), but only
                                // when the user is editing strictly above that line so the
                                // cursor/IME region is never rewritten underneath them.
                                val refresh = NoteProcessor.refreshFilledTotal(v.text)
                                if (refresh != null && v.selection.end < refresh.fromIndex) {
                                    applied = TextFieldValue(refresh.newContent, selection = v.selection)
                                }
                                textFieldValue = applied
                                vm.updateText(applied.text)
                            },
                            onTextLayout = { textLayout = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = this@BoxWithConstraints.maxHeight)
                                .padding(start = 44.dp),
                            textStyle = editorTextStyle().copy(
                                color = paperColorScheme().onSurface,
                            ),
                            cursorBrush = SolidColor(paperColorScheme().primary),
                        )
                        if (showReadout) {
                            // Computed total renders as an indented sub-line under the keyword:
                            //   total
                            //         = 6000
                    Text(
                        "= ${NoteProcessor.formatTotal(state.currentSum)}",
                                style = editorTextStyle().copy(
                                    color = paperColorScheme().primary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 44.dp + 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch { vm.delete(); navigator.pop() }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.createFolder(name) }
                    showCreateFolderDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCreateTagDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("New tag") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Tag name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { vm.toggleTag(name); showCreateTagDialog = false },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTagDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusBar(state: EditorViewModel.UiState) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showCheatSheet by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }
    Surface(tonalElevation = 1.dp, color = paperColorScheme().surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Σ ${NoteProcessor.formatTotal(state.currentSum)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = paperColorScheme().primary,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            clipboard.setText(AnnotatedString(NoteProcessor.formatTotal(state.currentSum)))
                            copied = true
                        },
                        // Long-press for the grammar: tap is already spoken for by copy, and the
                        // sample note can only point somewhere.
                        onLongClick = { showCheatSheet = true },
                    ),
                )
                if (copied) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Copied",
                        style = MaterialTheme.typography.labelSmall,
                        color = paperColorScheme().onSurfaceVariant,
                    )
                }
            }
            Text(
                "${state.stats.words}w · ${state.stats.characters}c",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (showCheatSheet) {
        CheatSheetDialog(onDismiss = { showCheatSheet = false })
    }
}

/**
 * The calc grammar, in the order NoteProcessor actually parses it. Every example here is one the
 * parser has a test for, so the sheet cannot drift into documenting syntax that does not work.
 */
@Composable
private fun CheatSheetDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Syntax") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Plain text contributes nothing. These lines add up:",
                    style = MaterialTheme.typography.bodySmall,
                )
                CheatRow("12.50", "a bare number")
                CheatRow("milk 12.5", "a label then a number")
                CheatRow("200 + 15%", "a whole-line expression")
                CheatRow("sqrt(16)", "functions work")
                CheatRow("grocery 1000 * 2", "arithmetic after a label")
                CheatRow("Rent = 1200", "assign a name, reuse it below")
                HorizontalDivider()
                Text(
                    "Close the note with a total line:",
                    style = MaterialTheme.typography.bodySmall,
                )
                CheatRow("Total =", "sums everything above it")
                CheatRow("total = 6000", "rewrites itself when the numbers change")
                Text(
                    "Closing keywords: " + NoteProcessor.totalKeywords.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun CheatRow(example: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            example,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = editorTextStyle().fontFamily),
            color = paperColorScheme().primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(description, style = MaterialTheme.typography.bodySmall)
    }
}
