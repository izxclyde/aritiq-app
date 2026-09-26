package com.aritiq.calcnote.ui.editor

import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Folder
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.domain.NoteProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var computeJob: Job? = null

    // ponytail: fixed delay. Shorter feels laggy on the readout, longer delays the total.
    private companion object { const val COMPUTE_DEBOUNCE_MS = 120L }

    // The screen owns a remember{}-scoped VM, so its SupervisorJob would otherwise outlive
    // every visit to the editor. Call when leaving.
    fun cancel() {
        computeJob?.cancel()
        scope.cancel()
    }

    fun open(noteId: String?) {
        scope.launch {
            val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            val allTags = repo.allTags()
            if (noteId == null) {
                _state.value = UiState(loaded = true, folders = folders, allTags = allTags)
            } else {
                val note = repo.getById(noteId)
                val tags = repo.tagsForNote(noteId)
                _state.value = if (note != null) {
                    UiState.from(note).copy(loaded = true, folders = folders, allTags = allTags, tags = tags)
                } else {
                    UiState(loaded = true, folders = folders, allTags = allTags)
                }
            }
        }
    }

    /**
     * Toggles one tag in the draft. Tags live in note_tag rather than on the note row, so they are
     * written in [save] alongside everything else: a brand-new note has no id to link against yet,
     * and the editor already treats back-and-save as the commit point.
     */
    fun toggleTag(name: String) {
        val tag = name.trim()
        if (tag.isEmpty()) return
        val current = _state.value.tags
        _state.value = _state.value.copy(
            tags = if (tag in current) current - tag else current + tag,
            allTags = if (tag in current || tag in _state.value.allTags) {
                _state.value.allTags
            } else {
                (_state.value.allTags + tag).sorted()
            },
        )
    }

    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(title = newTitle, titleEdited = true)
    }

    fun updateText(text: String) {
        val current = _state.value
        // Text and title update synchronously: the field is the source of truth and the title
        // derivation is a single line scan.
        _state.value = current.copy(
            text = text,
            title = if (current.titleEdited) current.title else NoteProcessor.titleOf(text),
        )
        // liveTotal + stats re-parse the entire document (up to 3 parses per line), so they are
        // debounced and run off the UI thread instead of blocking every keystroke.
        computeJob?.cancel()
        computeJob = scope.launch {
            delay(COMPUTE_DEBOUNCE_MS)
            val sum = withContext(Dispatchers.Default) { NoteProcessor.liveTotal(text) }
            val stats = withContext(Dispatchers.Default) { NoteProcessor.stats(text) }
            // drop the result if the user kept typing while we were computing
            if (_state.value.text == text) {
                _state.value = _state.value.copy(currentSum = sum, stats = stats)
            }
        }
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
        // Unconditional: an empty list is how a user removes the last tag, and setTags unlinks
        // whatever is left. Guarding this on isNotEmpty would strand the old links.
        repo.setTags(id, s.tags)
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
        val tags: List<String> = emptyList(),
        val allTags: List<String> = emptyList(),
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
