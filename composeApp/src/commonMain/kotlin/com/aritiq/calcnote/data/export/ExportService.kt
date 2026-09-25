package com.aritiq.calcnote.data.export

import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class ExportService(
    private val repo: NoteRepository,
    private val folderRepo: FolderRepository,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun exportAllJson(): String {
        val all = repo.all()
        // one tag query for the whole library instead of one per note
        val tagsByNote = repo.tagsForNotes(all.map { it.id })
        val notes = all.map { NoteExport.fromDomain(it, tagsByNote[it.id].orEmpty()) }
        val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            .map { FolderExport.fromDomain(it) }
        val envelope = AritiqExport(
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            notes = notes,
            folders = folders,
        )
        return json.encodeToString(AritiqExport.serializer(), envelope)
    }

    suspend fun exportNoteJson(noteId: String): String? {
        val note = repo.getById(noteId) ?: return null
        return json.encodeToString(NoteExport.serializer(), NoteExport.fromDomain(note, repo.tagsForNote(note.id)))
    }

    suspend fun exportSelectedJson(noteIds: List<String>): String {
        val selected = noteIds.mapNotNull { repo.getById(it) }
        val tagsByNote = repo.tagsForNotes(selected.map { it.id })
        val notes = selected.map { NoteExport.fromDomain(it, tagsByNote[it.id].orEmpty()) }
        val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            .map { FolderExport.fromDomain(it) }
        val envelope = AritiqExport(
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            notes = notes,
            folders = folders,
        )
        return json.encodeToString(AritiqExport.serializer(), envelope)
    }
}
