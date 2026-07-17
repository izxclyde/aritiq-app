package com.aritiq.calcnote.ui.editor

import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Folder
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.domain.NoteProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class EditorViewModel(
    private val repo: NoteRepository,
    private val exportService: ExportService,
    private val folderRepo: FolderRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun open(noteId: String?) {
        scope.launch {
            val folders = folderRepo.all()
            if (noteId == null) {
                _state.value = UiState(loaded = true, folders = folders)
            } else {
                val note = repo.getById(noteId)
                _state.value = if (note != null) UiState.from(note).copy(loaded = true, folders = folders) else UiState(loaded = true, folders = folders)
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(title = newTitle, titleEdited = true)
    }

    fun updateText(text: String) {
        val current = _state.value
        _state.value = current.copy(
            text = text,
            title = if (current.titleEdited) current.title else NoteProcessor.titleOf(text),
            currentSum = NoteProcessor.liveTotal(text),
            stats = NoteProcessor.stats(text),
        )
    }

    fun setFolderId(folderId: String?) {
        _state.value = _state.value.copy(folderId = folderId)
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val now = Clock.System.now()
            val id = now.toEpochMilliseconds().toString(16) + "-" + (0..Int.MAX_VALUE).random().toString(16)
            folderRepo.upsert(Folder(id = id, name = name, createdAt = now, updatedAt = now))
            _state.value = _state.value.copy(folderId = id, folders = folderRepo.all())
        }
    }

    suspend fun save(): String {
        val s = _state.value
        val now = Clock.System.now()
        val id = s.id ?: generateId()
        val note = Note(
            id = id,
            title = if (s.title.isNotBlank()) s.title else NoteProcessor.titleOf(s.text),
            content = s.text,
            createdAt = s.createdAt ?: now,
            updatedAt = now,
            isPinned = s.isPinned,
            isArchived = false,
            favorite = false,
            folderId = s.folderId,
        )
        repo.upsert(note)
        _state.value = s.copy(id = id, createdAt = note.createdAt, updatedAt = note.updatedAt)
        return id
    }

    suspend fun exportJson(): String? {
        val id = _state.value.id ?: return null
        return exportService.exportNoteJson(id)
    }

    suspend fun delete() {
        val id = _state.value.id ?: return
        repo.delete(id)
    }

    private fun generateId(): String =
        Clock.System.now().toEpochMilliseconds().toString(16) + "-" + (0..Int.MAX_VALUE).random().toString(16)

    data class UiState(
        val id: String? = null,
        val text: String = "",
        val title: String = "",
        val createdAt: Instant? = null,
        val updatedAt: Instant? = null,
        val isPinned: Boolean = false,
        val folderId: String? = null,
        val folders: List<Folder> = emptyList(),
        val currentSum: Double = 0.0,
        val stats: NoteProcessor.Stats = NoteProcessor.Stats(0, 0),
        val loaded: Boolean = false,
        val titleEdited: Boolean = false,
    ) {
        companion object {
            fun from(note: Note): UiState = UiState(
                id = note.id,
                text = note.content,
                title = note.title,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                isPinned = note.isPinned,
                folderId = note.folderId,
                currentSum = NoteProcessor.liveTotal(note.content),
                stats = NoteProcessor.stats(note.content),
                titleEdited = note.title.isNotBlank(),
            )
        }
    }
}
