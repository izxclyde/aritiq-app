package com.aritiq.calcnote.data.export

import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import kotlinx.serialization.json.Json

enum class ImportMode { MERGE, REPLACE }

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
)

class ImportService(
    private val repo: NoteRepository,
    private val folderRepo: FolderRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(content: String, mode: ImportMode = ImportMode.MERGE): ImportResult {
        return importJson(content.trim().removePrefix("\uFEFF"), mode)
    }

    suspend fun importJson(content: String, mode: ImportMode): ImportResult {
        val envelope = try {
            json.decodeFromString(AritiqExport.serializer(), content)
        } catch (e: Exception) {
            return ImportResult(0, 0, listOf("Invalid JSON: ${e.message}"))
        }

        var imported = 0
        var skipped = 0

        if (mode == ImportMode.REPLACE) {
            val all = repo.all()
            for (note in all) if (note.folderId != LOCKED_FOLDER_ID) repo.delete(note.id)
            val folders = folderRepo.all()
            for (f in folders) if (f.id != LOCKED_FOLDER_ID) folderRepo.delete(f.id)
        }

        for (folderExport in envelope.folders) {
            try {
                if (folderExport.id == LOCKED_FOLDER_ID) { skipped++; continue }
                val folder = folderExport.toDomain()
                val existing = folderRepo.getById(folder.id)
                if (mode == ImportMode.MERGE && existing != null) {
                    skipped++
                    continue
                }
                folderRepo.upsert(folder)
                imported++
            } catch (e: Exception) {
                return ImportResult(imported, skipped, listOf("Error importing folder ${folderExport.id}: ${e.message}"))
            }
        }

        val existingNoteIds = if (mode == ImportMode.MERGE) {
            repo.all().map { it.id }.toSet()
        } else {
            emptySet()
        }

        for (noteExport in envelope.notes) {
            try {
                if (mode == ImportMode.MERGE && noteExport.id in existingNoteIds) {
                    skipped++
                    continue
                }
                val note = noteExport.toDomain()
                repo.upsert(note)
                imported++
            } catch (e: Exception) {
                return ImportResult(imported, skipped, listOf("Error importing note ${noteExport.id}: ${e.message}"))
            }
        }
        return ImportResult(imported, skipped, emptyList())
    }
}
