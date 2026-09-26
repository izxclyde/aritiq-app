package com.aritiq.calcnote.ui.home

import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.data.repository.SettingsRepository
import com.aritiq.calcnote.domain.Folder
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.domain.NoteProcessor
import com.aritiq.calcnote.lock.LockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.datetime.Clock

// ponytail: fixed, not tunable. Raise only if search feels laggy on a large library.
private const val SEARCH_DEBOUNCE_MS = 250L

class HomeViewModel(
    private val repo: NoteRepository,
    private val settingsRepo: SettingsRepository,
    private val exportService: ExportService,
    private val folderRepo: FolderRepository,
    private val lockManager: LockManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val snackbarHostState = SnackbarHostState()
    private var recentJob: Job? = null
    private var searchJob: Job? = null

    // Cancels the query listener, the search, and any in-flight load. Call when leaving the screen.
    fun cancel() {
        recentJob?.cancel()
        searchJob?.cancel()
        scope.cancel()
    }

    fun load() {
        scope.launch {
            recentJob?.cancel()
            val pinned = repo.pinned().filter { it.folderId != LOCKED_FOLDER_ID }
            val archived = repo.archived().filter { it.folderId != LOCKED_FOLDER_ID }
            val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            val tags = repo.allTags()
            val vm = ViewMode.fromString(settingsRepo.get("viewMode") ?: "")
            val so = SortOrder.fromString(settingsRepo.get("sortOrder") ?: "")
            _state.value = _state.value.copy(
                viewMode = vm, sortOrder = so, archived = archived,
                folders = folders, allTags = tags,
            )
            recentJob = repo.recent(50, 0)
                .map { list -> list.filter { it.folderId != LOCKED_FOLDER_ID } }
                .onEach { recent ->
                    val sorted = sort(recent, so)
                    val grouped = if (so == SortOrder.ALPHABETICAL) {
                        sorted.map { GroupedItem.NoteItem(it) }
                    } else groupByMonth(sorted, so == SortOrder.OLDEST_FIRST)
                    _state.value = _state.value.copy(
                        recent = sorted, pinned = pinned, archived = archived,
                        sortedGroupedRecent = grouped,
                        totals = totalsFor(pinned) + totalsFor(recent) + totalsFor(archived),
                        loading = false,
                    )
                }
                .launchIn(scope)
        }
    }

    /**
     * Folder and tag are two views of the same list, so only one can be active: picking either
     * clears the other, and "All" clears both. Folding them into an orthogonal pair would need
     * every path below to re-filter on the other axis.
     */
    fun selectFolder(folderId: String?) {
        recentJob?.cancel()
        _state.value = _state.value.copy(selectedFolderId = folderId, selectedTag = null)
        applyFilters()
    }

    fun selectTag(tag: String?) {
        recentJob?.cancel()
        _state.value = _state.value.copy(selectedTag = tag, selectedFolderId = null)
        applyFilters()
    }

    /** Re-runs whichever filter is active, or reloads the unfiltered list. */
    private fun applyFilters() {
        val s = _state.value
        if (s.query.isNotBlank()) {
            onSearch(s.query)
            return
        }
        val tag = s.selectedTag
        val folder = s.selectedFolderId
        if (tag != null) {
            scope.launch { showFiltered(repo.selectByTag(tag)) }
        } else if (folder != null) {
            scope.launch { showFiltered(repo.selectByFolder(folder)) }
        } else {
            load()
        }
    }

    /** A filtered view shows no pinned section and no archive, so both are dropped. */
    private suspend fun showFiltered(notes: List<Note>) {
        val so = _state.value.sortOrder
        val sorted = sort(notes, so)
        val grouped = if (so == SortOrder.ALPHABETICAL) {
            sorted.map { GroupedItem.NoteItem(it) }
        } else groupByMonth(sorted, so == SortOrder.OLDEST_FIRST)
        _state.value = _state.value.copy(
            recent = sorted, pinned = emptyList(), archived = emptyList(),
            sortedGroupedRecent = grouped, totals = totalsFor(notes), loading = false,
        )
    }

    fun setViewMode(mode: ViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
        scope.launch { settingsRepo.set("viewMode", mode.name) }
    }

    fun setSortOrder(order: SortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
        scope.launch {
            settingsRepo.set("sortOrder", order.name)
            val recent = _state.value.recent
            val sorted = sort(recent, order)
            val grouped = if (order == SortOrder.ALPHABETICAL) {
                sorted.map { GroupedItem.NoteItem(it) }
            } else groupByMonth(sorted, order == SortOrder.OLDEST_FIRST)
            _state.value = _state.value.copy(recent = sorted, sortedGroupedRecent = grouped)
        }
    }

    fun onSearch(q: String) {
        _state.value = _state.value.copy(query = q)
        if (q.isBlank()) {
            applyFilters()
            return
        }
        // Debounced, and the previous search is cancelled: the old code ran a full-table
        // LIKE '%q%' scan per keystroke and let racing searches land out of order.
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = repo.search(q).filter { it.folderId != LOCKED_FOLDER_ID }
            val folderId = _state.value.selectedFolderId
            val filtered = if (folderId != null) results.filter { it.folderId == folderId } else results
            _state.value = _state.value.copy(searchResults = filtered, totals = totalsFor(filtered))
        }
    }

    fun togglePinned(note: Note) {
        scope.launch {
            repo.setPinned(note.id, !note.isPinned)
            reload()
        }
    }

    fun toggleFavorite(note: Note) {
        scope.launch {
            repo.setFavorite(note.id, !note.favorite)
            reload()
        }
    }

    fun archive(note: Note) {
        scope.launch {
            repo.setArchived(note.id, true)
            reload()
        }
    }

    fun restore(note: Note) {
        scope.launch {
            repo.setArchived(note.id, false)
            reload()
        }
    }

    /** Archive with an Undo snackbar; reverting restores the note to the active list. */
    fun archiveWithUndo(note: Note) {
        scope.launch {
            repo.setArchived(note.id, true)
            reload()
            val result = snackbarHostState.showSnackbar(
                message = "Note archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repo.setArchived(note.id, false)
                reload()
            }
        }
    }

    /** Restore with an Undo snackbar; reverting re-archives the note. */
    fun restoreWithUndo(note: Note) {
        scope.launch {
            repo.setArchived(note.id, false)
            reload()
            val result = snackbarHostState.showSnackbar(
                message = "Note restored",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repo.setArchived(note.id, true)
                reload()
            }
        }
    }

    fun toggleShowArchived() {
        _state.value = _state.value.copy(showArchived = !_state.value.showArchived)
    }

    fun delete(id: String) {
        scope.launch {
            repo.delete(id)
            reload()
        }
    }

    fun titleOf(content: String): String = NoteProcessor.titleOf(content)

    fun moveToLocked(noteIds: Set<String>) {
        if (!lockManager.isAvailable()) return
        scope.launch {
            val now = Clock.System.now()
            for (id in noteIds) {
                val note = repo.getById(id) ?: continue
                repo.upsert(note.copy(folderId = LOCKED_FOLDER_ID, updatedAt = now, isPinned = false))
            }
            reload()
        }
    }

    /** Live totals per note id, for the conditional Σ chip on cards. Zero totals are omitted. */
    private fun totalsFor(notes: List<Note>): Map<String, Double> =
        notes.associate { it.id to NoteProcessor.liveTotal(it.content) }.filterValues { it != 0.0 }

    private fun sort(notes: List<Note>, order: SortOrder): List<Note> = when (order) {
        SortOrder.NEWEST_FIRST -> notes.sortedByDescending { it.createdAt }
        SortOrder.OLDEST_FIRST -> notes.sortedBy { it.createdAt }
        SortOrder.RECENTLY_EDITED -> notes.sortedByDescending { it.updatedAt }
        SortOrder.ALPHABETICAL -> notes.sortedBy { it.title.lowercase() }
    }

    private fun reload() {
        applyFilters()
    }

    fun toggleSelectMode() {
        _state.value = _state.value.copy(
            isSelecting = !_state.value.isSelecting,
            selectedIds = emptySet(),
        )
    }

    fun exitSelectMode() {
        _state.value = _state.value.copy(isSelecting = false, selectedIds = emptySet())
    }

    fun toggleSelection(id: String) {
        val current = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (id in current) current - id else current + id,
        )
    }

    fun selectAll() {
        val q = _state.value.query
        val all = if (q.isNotBlank()) {
            _state.value.searchResults.map { it.id }.toSet()
        } else {
            _state.value.recent.map { it.id }.toSet() +
                _state.value.pinned.map { it.id }
        }
        _state.value = _state.value.copy(selectedIds = all)
    }

    suspend fun exportSelectedJson(): String? {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return null
        return exportService.exportSelectedJson(ids)
    }

    data class UiState(
        val recent: List<Note> = emptyList(),
        val pinned: List<Note> = emptyList(),
        val archived: List<Note> = emptyList(),
        val showArchived: Boolean = false,
        val sortedGroupedRecent: List<GroupedItem> = emptyList(),
        val query: String = "",
        val searchResults: List<Note> = emptyList(),
        val viewMode: ViewMode = ViewMode.DETAILED_LIST,
        val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
        val loading: Boolean = true,
        val isSelecting: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val folders: List<Folder> = emptyList(),
        val selectedFolderId: String? = null,
        val allTags: List<String> = emptyList(),
        val selectedTag: String? = null,
        val totals: Map<String, Double> = emptyMap(),
    )
}
