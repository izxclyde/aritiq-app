package com.aritiq.calcnote.ui.home

import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.data.repository.SettingsRepository
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.domain.NoteProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: NoteRepository,
    private val settingsRepo: SettingsRepository,
    private val exportService: ExportService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var recentJob: Job? = null

    fun load() {
        scope.launch {
            recentJob?.cancel()
            val pinned = repo.pinned()
            val archived = repo.archived()
            val vm = ViewMode.fromString(settingsRepo.get("viewMode") ?: "")
            val so = SortOrder.fromString(settingsRepo.get("sortOrder") ?: "")
            _state.value = _state.value.copy(viewMode = vm, sortOrder = so, archived = archived)
            recentJob = repo.recent(50, 0)
                .onEach { recent ->
                    val sorted = sort(recent, so)
                    val grouped = if (so == SortOrder.ALPHABETICAL) {
                        sorted.map { GroupedItem.NoteItem(it) }
                    } else groupByMonth(sorted, so == SortOrder.OLDEST_FIRST)
                    _state.value = _state.value.copy(
                        recent = sorted, pinned = pinned, archived = archived, sortedGroupedRecent = grouped, loading = false,
                    )
                }
                .launchIn(scope)
        }
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
        if (q.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(searchResults = repo.search(q))
        }
    }

    fun togglePinned(note: Note) {
        scope.launch {
            repo.setPinned(note.id, !note.isPinned)
            load()
        }
    }

    fun archive(note: Note) {
        scope.launch {
            repo.setArchived(note.id, true)
            load()
        }
    }

    fun restore(note: Note) {
        scope.launch {
            repo.setArchived(note.id, false)
            load()
        }
    }

    fun toggleShowArchived() {
        _state.value = _state.value.copy(showArchived = !_state.value.showArchived)
    }

    fun delete(id: String) {
        scope.launch {
            repo.delete(id)
            load()
        }
    }

    fun titleOf(content: String): String = NoteProcessor.titleOf(content)

    private fun sort(notes: List<Note>, order: SortOrder): List<Note> = when (order) {
        SortOrder.NEWEST_FIRST -> notes.sortedByDescending { it.createdAt }
        SortOrder.OLDEST_FIRST -> notes.sortedBy { it.createdAt }
        SortOrder.RECENTLY_EDITED -> notes.sortedByDescending { it.updatedAt }
        SortOrder.ALPHABETICAL -> notes.sortedBy { it.title.lowercase() }
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
        val all = _state.value.recent.map { it.id }.toSet() +
            _state.value.pinned.map { it.id }
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
    )
}
