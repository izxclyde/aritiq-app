package com.aritiq.calcnote.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.NoteProcessor
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.theme.PaperGrainOverlay
import com.aritiq.calcnote.ui.theme.editorTextStyle
import com.aritiq.calcnote.ui.theme.paperColorScheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor body. Plain multiline text, monospace.
 *
 *  - The editable text is the single source of truth. We never rewrite it during typing, so
 *    the IME/keyboard never resets.
 *  - When the note's last non-empty line is a total trigger (`total` or `total =`), a separate
 *    NON-editable `Text` renders `total = <live sum>` directly under it — same font, same left
 *    margin, same ruled paper — so it reads as the next line of the page while never living
 *    inside the user's text field. The number is always live (derived from [UiState.currentSum]).
 *  - Bare keyword (`total`) auto-appends `=` on commit; a filled `total = 6000` written by the
 *    user is left untouched.
 *  - The bottom status bar shows the live Σ and word/char count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navigator: Navigator,
    noteId: String?,
) {
    val repo = koinInject<NoteRepository>()
    val vm = remember { EditorViewModel(repo) }
    LaunchedEffect(noteId) { vm.open(noteId) }
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Local text mirror keyed by noteId. Stays empty until state.loaded flips; we then
    // copy state.text into it exactly once. Using TextFieldValue to preserve cursor
    // position and IME state across recompositions (fixes keyboard reset bug).
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        bottomBar = { StatusBar(state) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .drawBehind {
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
                        },
                ) {
                    Column {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { v ->
                                if (!hasSynced) return@BasicTextField
                                textFieldValue = v
                                vm.updateText(v.text)
                            },
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
                                text = "= ${formatTotal(state.currentSum)}",
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
                PaperGrainOverlay(modifier = Modifier.fillMaxSize())
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
}

@Composable
private fun StatusBar(state: EditorViewModel.UiState) {
    Surface(tonalElevation = 1.dp, color = paperColorScheme().surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Σ ${formatTotal(state.currentSum)}",
                style = MaterialTheme.typography.titleSmall,
                color = paperColorScheme().primary,
            )
            Text(
                "${state.stats.words}w · ${state.stats.characters}c",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatTotal(v: Double): String {
    val rounded = "%.2f".format(v)
    return if (rounded.endsWith(".00")) rounded.dropLast(3) else rounded
}
