package com.aritiq.calcnote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigator: Navigator) {
    val repo = koinInject<NoteRepository>()
    val settingsRepo = koinInject<com.aritiq.calcnote.data.repository.SettingsRepository>()
    val vm = remember { HomeViewModel(repo, settingsRepo) }
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()
    var showViewMenu by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aritiq") },
                actions = {
                    Box {
                        IconButton(onClick = { showViewMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "View options")
                        }
                        DropdownMenu(
                            expanded = showViewMenu,
                            onDismissRequest = { showViewMenu = false },
                        ) {
                            ViewMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (state.viewMode == mode) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                            } else {
                                                Spacer(Modifier.width(19.dp))
                                            }
                                            Text(mode.label)
                                        }
                                    },
                                    onClick = { vm.setViewMode(mode) },
                                )
                            }
                            HorizontalDivider()
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (state.sortOrder == order) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                            } else {
                                                Spacer(Modifier.width(19.dp))
                                            }
                                            Text(order.label)
                                        }
                                    },
                                    onClick = { vm.setSortOrder(order) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { navigator.navigate(Route.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.navigate(Route.Editor(null)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Create note")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.onSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search notes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { vm.onSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
            )

            if (state.query.isNotBlank()) {
                SearchResults(state, vm, navigator)
            } else {
                when (state.viewMode) {
                    ViewMode.SIMPLE_LIST -> SimpleList(state, vm, navigator)
                    ViewMode.DETAILED_LIST -> DetailedList(state, vm, navigator)
                    ViewMode.SMALL_GRID -> SmallGrid(state, vm, navigator)
                    ViewMode.LARGE_GRID -> LargeGrid(state, vm, navigator)
                }
            }
        }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(note.id); noteToDelete = null }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResults(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    if (state.searchResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(state.searchResults, key = { it.id }) { note ->
                SwipeToArchive(note = note, onArchive = { vm.archive(note) }) {
                    NoteRowDetailed(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleList(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    if (state.pinned.isEmpty() && state.sortedGroupedRecent.isEmpty() && state.archived.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            if (state.pinned.isNotEmpty()) {
                item { SectionHeader("Pinned") }
                items(state.pinned, key = { it.id }) { note ->
                    SwipeToArchive(note = note, onArchive = { vm.archive(note) }) {
                        NoteRowSimple(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
                    }
                }
            }
            groupedItems(state, navigator, vm) { n, onOpen ->
                SwipeToArchive(note = n, onArchive = { vm.archive(n) }) {
                    NoteRowSimple(note = n, onOpen = onOpen, vm = vm)
                }
            }
            archivedSection(state, vm, navigator)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedList(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    if (state.pinned.isEmpty() && state.sortedGroupedRecent.isEmpty() && state.archived.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            if (state.pinned.isNotEmpty()) {
                item { SectionHeader("Pinned") }
                items(state.pinned, key = { it.id }) { note ->
                    SwipeToArchive(note = note, onArchive = { vm.archive(note) }) {
                        NoteRowDetailed(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
                    }
                }
            }
            groupedItems(state, navigator, vm) { n, onOpen ->
                SwipeToArchive(note = n, onArchive = { vm.archive(n) }) {
                    NoteRowDetailed(note = n, onOpen = onOpen, vm = vm)
                }
            }
            archivedSection(state, vm, navigator)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun LazyListScope.groupedItems(
    state: HomeViewModel.UiState,
    navigator: Navigator,
    vm: HomeViewModel,
    noteContent: @Composable (Note, () -> Unit) -> Unit,
) {
    if (state.sortedGroupedRecent.isEmpty()) return
    items(state.sortedGroupedRecent, key = {
        when (it) {
            is GroupedItem.Header -> it.label
            is GroupedItem.NoteItem -> it.note.id
        }
    }) { item ->
        when (item) {
            is GroupedItem.Header -> SectionHeader(item.label)
            is GroupedItem.NoteItem -> noteContent(item.note, { navigator.navigate(Route.Editor(item.note.id)) })
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

private fun LazyListScope.archivedSection(
    state: HomeViewModel.UiState,
    vm: HomeViewModel,
    navigator: Navigator,
) {
    if (state.archived.isEmpty()) return
    item { ArchivedHeader(state, vm) }
    if (state.showArchived) {
        items(state.archived, key = { it.id + "_archived" }) { note ->
            ArchivedNoteRow(note = note, onRestore = { vm.restore(note) }, onDelete = { vm.delete(note.id) }, onOpen = { navigator.navigate(Route.Editor(note.id)) })
        }
    }
}

@Composable
private fun ArchivedSectionColumn(
    state: HomeViewModel.UiState,
    vm: HomeViewModel,
    navigator: Navigator,
) {
    if (state.archived.isEmpty()) return
    Column {
        ArchivedHeader(state, vm)
        HorizontalDivider()
        if (state.showArchived) {
            Column {
                state.archived.forEach { note ->
                    ArchivedNoteRow(note = note, onRestore = { vm.restore(note) }, onDelete = { vm.delete(note.id) }, onOpen = { navigator.navigate(Route.Editor(note.id)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ArchivedHeader(state: HomeViewModel.UiState, vm: HomeViewModel) {
    Surface(
        onClick = { vm.toggleShowArchived() },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Archived (${state.archived.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (state.showArchived) "\u25B2" else "\u25BC", style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun ArchivedNoteRow(note: Note, onRestore: () -> Unit, onDelete: () -> Unit, onOpen: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        headlineContent = {
            Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text("Archived", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row {
                TextButton(onClick = onRestore) {
                    Text("Restore", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete permanently")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToArchive(note: Note, onArchive: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onArchive(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Archive", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        content = { content() },
    )
}

@Composable
private fun SmallGrid(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    val notes = (if (state.pinned.isNotEmpty()) state.pinned else emptyList()) + state.recent
    if (notes.isEmpty() && state.archived.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet", style = MaterialTheme.typography.bodyLarge)
        }
    } else if (notes.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ArchivedSectionColumn(state, vm, navigator)
            Spacer(Modifier.height(80.dp))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCardSmall(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) })
                }
            }
            ArchivedSectionColumn(state, vm, navigator)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun LargeGrid(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    val notes = (if (state.pinned.isNotEmpty()) state.pinned else emptyList()) + state.recent
    if (notes.isEmpty() && state.archived.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No notes yet", style = MaterialTheme.typography.bodyLarge)
        }
    } else if (notes.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ArchivedSectionColumn(state, vm, navigator)
            Spacer(Modifier.height(80.dp))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCardLarge(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, onTogglePin = { vm.togglePinned(note) })
                }
            }
            ArchivedSectionColumn(state, vm, navigator)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun NoteRowSimple(note: Note, onOpen: () -> Unit, vm: HomeViewModel) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onOpen() }, tonalElevation = 0.dp) {
        Text(
            text = note.title.ifBlank { "Untitled" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    HorizontalDivider()
}

@Composable
private fun NoteRowDetailed(note: Note, onOpen: () -> Unit, vm: HomeViewModel) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Text("PIN", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = {
            Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Surface(
                modifier = Modifier.clickable { vm.togglePinned(note) },
                shape = RoundedCornerShape(8.dp),
                color = if (note.isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (note.isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(if (note.isPinned) "Unpin" else "Pin", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        },
        leadingContent = null,
    )
    HorizontalDivider()
}

@Composable
private fun NoteCardSmall(note: Note, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteCardLarge(note: Note, onOpen: () -> Unit, onTogglePin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Text("PIN", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier.clickable { onTogglePin() },
                    shape = RoundedCornerShape(8.dp),
                    color = if (note.isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (note.isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(if (note.isPinned) "Unpin" else "Pin", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
