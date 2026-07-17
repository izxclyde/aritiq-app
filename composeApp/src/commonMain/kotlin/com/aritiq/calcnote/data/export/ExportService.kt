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
        val notes = repo.all()
            .filter { it.folderId != LOCKED_FOLDER_ID }
            .map { NoteExport.fromDomain(it, repo.tagsForNote(it.id)) }
        val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            .map { FolderExport.fromDomain(it) }
        val envelope = AritiqExport(
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            notes = notes,
            folders = folders,
        )
        return json.encodeToString(AritiqExport.serializer(), envelope)
    }

    suspend fun exportAllCsv(): String = buildCsv(repo.all().filter { it.folderId != LOCKED_FOLDER_ID })

    suspend fun exportNoteJson(noteId: String): String {
        val note = repo.getById(noteId) ?: return "{}"
        if (note.folderId == LOCKED_FOLDER_ID) return "{}"
        val export = NoteExport.fromDomain(note, repo.tagsForNote(note.id))
        return json.encodeToString(NoteExport.serializer(), export)
    }

    suspend fun exportSelectedJson(noteIds: List<String>): String {
        val notes = noteIds.mapNotNull { repo.getById(it) }
            .filter { it.folderId != LOCKED_FOLDER_ID }
            .map { NoteExport.fromDomain(it, repo.tagsForNote(it.id)) }
        val folders = folderRepo.all().filter { it.id != LOCKED_FOLDER_ID }
            .map { FolderExport.fromDomain(it) }
        val envelope = AritiqExport(
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            notes = notes,
            folders = folders,
        )
        return json.encodeToString(AritiqExport.serializer(), envelope)
    }

    suspend fun exportSelectedCsv(noteIds: List<String>): String {
        val notes = noteIds.mapNotNull { repo.getById(it) }
            .filter { it.folderId != LOCKED_FOLDER_ID }
        return buildCsv(notes)
    }

    private fun buildCsv(notes: List<com.aritiq.calcnote.domain.Note>): String {
        val sb = StringBuilder()
        sb.appendLine("title,content")
        for (note in notes) {
            sb.appendLine("${csvEscape(note.title)},${csvEscape(note.content)}")
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
