package com.aritiq.calcnote.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.export.shareExport
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.components.EmptyState
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigator: Navigator) {
    val repo = koinInject<NoteRepository>()
    val settingsRepo = koinInject<com.aritiq.calcnote.data.repository.SettingsRepository>()
    val exportService = koinInject<ExportService>()
    val folderRepo = koinInject<FolderRepository>()
    val lockManager = koinInject<LockManager>()
    val vm = remember { HomeViewModel(repo, settingsRepo, exportService, folderRepo, lockManager) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()

    var showViewMenu by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            if (state.isSelecting) {
                TopAppBar(
                    title = { Text("${state.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { vm.exitSelectMode() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val json = vm.exportSelectedJson()
                                if (json != null) {
                                    shareExport(context, json, "application/json", "aritiq-export.json")
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export selected as JSON")
                        }
                        if (lockManager.isAvailable()) {
                            IconButton(onClick = {
                                vm.moveToLocked(state.selectedIds)
                                vm.exitSelectMode()
                            }) {
                                Icon(Icons.Filled.Lock, contentDescription = "Move to Locked folder")
                            }
                        }
                    },
                )
            } else {
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
                                        text = { Text(mode.label) },
                                        leadingIcon = if (state.viewMode == mode) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null,
                                        onClick = { vm.setViewMode(mode) },
                                    )
                                }
                                HorizontalDivider()
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        leadingIcon = if (state.sortOrder == order) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null,
                                        onClick = { vm.setSortOrder(order) },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Manage folders") },
                                    onClick = { showViewMenu = false; navigator.navigate(Route.ManageFolders) },
                                )
                            }
                        }
                        IconButton(onClick = { navigator.navigate(Route.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navigator.navigate(Route.Editor(null)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
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

            FolderChipRow(state, vm)

            Box(Modifier.weight(1f).fillMaxWidth()) {
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

            if (lockManager.isAvailable()) {
                LockedRow(lockManager, navigator)
                Spacer(Modifier.height(80.dp))
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
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "No results",
            )
        }
        return
    }
    when (state.viewMode) {
        ViewMode.SIMPLE_LIST -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(state.searchResults, key = { it.id }) { note ->
                SwipeToArchive(note = note, onArchive = { vm.archive(note) }) {
                    NoteRowSimple(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
                }
            }
        }
        ViewMode.DETAILED_LIST -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(state.searchResults, key = { it.id }) { note ->
                SwipeToArchive(note = note, onArchive = { vm.archive(note) }) {
                    NoteRowDetailed(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
                }
            }
        }
        ViewMode.SMALL_GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.searchResults, key = { it.id }) { note ->
                NoteCardSmall(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
            }
        }
        ViewMode.LARGE_GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.searchResults, key = { it.id }) { note ->
                NoteCardLarge(
                    note = note,
                    onOpen = { navigator.navigate(Route.Editor(note.id)) },
                    onTogglePin = { vm.togglePinned(note) },
                    vm = vm,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleList(state: HomeViewModel.UiState, vm: HomeViewModel, navigator: Navigator) {
    if (state.pinned.isEmpty() && state.sortedGroupedRecent.isEmpty() && state.archived.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.EditNote,
                title = "No notes yet",
                actionLabel = "Create note",
                onAction = { navigator.navigate(Route.Editor(null)) },
            )
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
            EmptyState(
                icon = Icons.Outlined.EditNote,
                title = "No notes yet",
                actionLabel = "Create note",
                onAction = { navigator.navigate(Route.Editor(null)) },
            )
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
            Icon(
                if (state.showArchived) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (state.showArchived) "Collapse archived" else "Expand archived",
                modifier = Modifier.size(20.dp),
            )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Archive", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
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
            EmptyState(
                icon = Icons.Outlined.EditNote,
                title = "No notes yet",
                actionLabel = "Create note",
                onAction = { navigator.navigate(Route.Editor(null)) },
            )
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
                    NoteCardSmall(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, vm = vm)
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
            EmptyState(
                icon = Icons.Outlined.EditNote,
                title = "No notes yet",
                actionLabel = "Create note",
                onAction = { navigator.navigate(Route.Editor(null)) },
            )
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
                    NoteCardLarge(note = note, onOpen = { navigator.navigate(Route.Editor(note.id)) }, onTogglePin = { vm.togglePinned(note) }, vm = vm)
                }
            }
            ArchivedSectionColumn(state, vm, navigator)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NoteRowSimple(note: Note, onOpen: () -> Unit, vm: HomeViewModel) {
    val state = vm.state.collectAsState().value
    val selected = note.id in state.selectedIds
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (state.isSelecting) vm.toggleSelection(note.id) else onOpen() },
                onLongClick = { if (!state.isSelecting) { vm.toggleSelectMode(); vm.toggleSelection(note.id) } },
            ),
        tonalElevation = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (state.isSelecting) 8.dp else 16.dp)) {
            if (state.isSelecting) {
                Checkbox(checked = selected, onCheckedChange = { vm.toggleSelection(note.id) })
            }
            Text(
                text = note.title.ifBlank { "Untitled" },
                modifier = Modifier.padding(vertical = 12.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NoteRowDetailed(note: Note, onOpen: () -> Unit, vm: HomeViewModel) {
    val state = vm.state.collectAsState().value
    val selected = note.id in state.selectedIds
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (state.isSelecting) vm.toggleSelection(note.id) else onOpen() },
                onLongClick = { if (!state.isSelecting) { vm.toggleSelectMode(); vm.toggleSelection(note.id) } },
            ),
        leadingContent = if (state.isSelecting) {
            { Checkbox(checked = selected, onCheckedChange = { vm.toggleSelection(note.id) }) }
        } else null,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = {
            Column {
                Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(
                    formatDate(note.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = { vm.toggleFavorite(note) }) {
                    Icon(
                        if (note.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (note.favorite) "Unfavorite" else "Favorite",
                        tint = if (note.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { vm.togglePinned(note) }) {
                    Icon(
                        if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NoteCardSmall(note: Note, onOpen: () -> Unit, vm: HomeViewModel?) {
    val state = vm?.state?.collectAsState()?.value
    val selected = state?.let { note.id in it.selectedIds } ?: false
    val isSelecting = state?.isSelecting ?: false
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelecting) vm?.toggleSelection(note.id) else onOpen() },
                onLongClick = { if (!isSelecting) { vm?.toggleSelectMode(); vm?.toggleSelection(note.id) } },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NoteCardLarge(note: Note, onOpen: () -> Unit, onTogglePin: () -> Unit, vm: HomeViewModel?) {
    val state = vm?.state?.collectAsState()?.value
    val selected = state?.let { note.id in it.selectedIds } ?: false
    val isSelecting = state?.isSelecting ?: false
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelecting) vm?.toggleSelection(note.id) else onOpen() },
                onLongClick = { if (!isSelecting) { vm?.toggleSelectMode(); vm?.toggleSelection(note.id) } },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onTogglePin) {
                    Icon(
                        if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(note.content.lineSequence().firstOrNull { it.isNotBlank() && it.trim() != note.title } ?: "", maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LockedRow(lockManager: LockManager, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Surface(
        onClick = {
            scope.launch {
                if (lockManager.authenticate(context)) {
                    navigator.navigate(Route.LockedFolder)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Locked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Tap to unlock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun FolderChipRow(state: HomeViewModel.UiState, vm: HomeViewModel) {
    if (state.folders.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        )
        FilterChip(
            selected = state.selectedFolderId == null,
            onClick = { vm.selectFolder(null) },
            label = { Text("All") },
            colors = chipColors,
        )
        state.folders.forEach { folder ->
            FilterChip(
                selected = state.selectedFolderId == folder.id,
                onClick = { vm.selectFolder(folder.id) },
                label = { Text(folder.name) },
                colors = chipColors,
            )
        }
    }
}

private fun formatDate(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$month ${dt.dayOfMonth}"
}
