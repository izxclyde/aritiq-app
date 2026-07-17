package com.aritiq.calcnote.data.export

import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

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
                if (noteExport.folderId == LOCKED_FOLDER_ID) { skipped++; continue }
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

    suspend fun importCsv(content: String): ImportResult {
        val cleaned = content.trim().removePrefix("\uFEFF")
        val rows = splitCsvRows(cleaned)
        if (rows.isEmpty()) return ImportResult(0, 0, listOf("CSV is empty or missing header"))
        val header = rows.first().trim().removeSurrounding("\"").lowercase()
        if (header != "title,content") {
            return ImportResult(0, 0, listOf("CSV must have 'title,content' header, got '$header'"))
        }

        var imported = 0
        val now = Clock.System.now()

        for (i in 1 until rows.size) {
            try {
                val (title, content) = parseCsvLine(rows[i])
                val note = com.aritiq.calcnote.domain.Note(
                    id = generateId(),
                    title = title,
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                )
                repo.upsert(note)
                imported++
            } catch (e: Exception) {
                return ImportResult(imported, 0, listOf("Error importing row ${i + 1}: ${e.message}"))
            }
        }
        return ImportResult(imported, 0, emptyList())
    }

    private fun splitCsvRows(text: String): List<String> {
        val rows = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"') {
                inQuotes = !inQuotes; cur.append(c); i++
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                val row = cur.toString().trim()
                if (row.isNotBlank()) rows.add(row)
                cur.clear()
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                i++
            } else {
                cur.append(c); i++
            }
        }
        val last = cur.toString().trim()
        if (last.isNotBlank()) rows.add(last)
        return rows
    }

    private fun parseCsvLine(line: String): Pair<String, String> {
        return if (line.startsWith('"')) {
            val sb = StringBuilder()
            var i = 1
            while (i < line.length) {
                if (line[i] == '"' && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i += 2
                } else if (line[i] == '"') {
                    i++; break
                } else {
                    sb.append(line[i]); i++
                }
            }
            val title = sb.toString()
            val rest = if (i < line.length && line[i] == ',') line.substring(i + 1) else ""
            val content = if (rest.startsWith('"')) {
                parseCsvQuotedValue(rest, 1)
            } else rest.trim()
            Pair(title, content)
        } else {
            val comma = findUnquotedComma(line, 0)
            val title = if (comma < 0) line.trim() else line.substring(0, comma).trim()
            val rawContent = if (comma < 0) "" else line.substring(comma + 1).trim()
            val content = stripSurroundingQuotes(rawContent)
            Pair(title, content)
        }
    }

    private fun findUnquotedComma(s: String, start: Int): Int {
        var inQ = false
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '"' -> inQ = !inQ
                ',' -> if (!inQ) return i
            }
            i++
        }
        return -1
    }

    private fun parseCsvQuotedValue(s: String, start: Int): String {
        val sb = StringBuilder()
        var j = start
        while (j < s.length) {
            if (s[j] == '"' && j + 1 < s.length && s[j + 1] == '"') {
                sb.append('"'); j += 2
            } else if (s[j] == '"') {
                j++; break
            } else {
                sb.append(s[j]); j++
            }
        }
        return sb.toString()
    }

    private fun stripSurroundingQuotes(value: String): String {
        val t = value.trim()
        return if (t.startsWith('"') && t.endsWith('"')) {
            parseCsvQuotedValue(t, 1)
        } else t
    }

    private fun generateId(): String =
        Clock.System.now().toEpochMilliseconds().toString(16) + "-" + (0..Int.MAX_VALUE).random().toString(16)
}
