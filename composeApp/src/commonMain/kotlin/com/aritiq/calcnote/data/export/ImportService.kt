package com.aritiq.calcnote.data.export

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
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Auto-detect JSON vs CSV by content prefix. Strips BOM if present. */
    suspend fun import(content: String, mode: ImportMode = ImportMode.MERGE): ImportResult {
        val cleaned = content.trim().removePrefix("\uFEFF")
        return if (cleaned.startsWith("{")) {
            importJson(cleaned, mode)
        } else if (cleaned.startsWith("title,content") || cleaned.startsWith('"')) {
            importCsv(cleaned)
        } else {
            ImportResult(0, 0, listOf("Unrecognized format — expected JSON (starts with {) or CSV (starts with 'title,content' or '\"')"))
        }
    }

    suspend fun importJson(content: String, mode: ImportMode): ImportResult {
        val envelope = try {
            json.decodeFromString(AritiqExport.serializer(), content)
        } catch (e: Exception) {
            return ImportResult(0, 0, listOf("Invalid JSON: ${e.message}"))
        }

        val existing = if (mode == ImportMode.MERGE) {
            repo.all().map { it.id }.toSet()
        } else {
            emptySet()
        }

        var imported = 0
        var skipped = 0

        if (mode == ImportMode.REPLACE) {
            val all = repo.all()
            for (note in all) repo.delete(note.id)
        }

        for (noteExport in envelope.notes) {
            try {
                if (mode == ImportMode.MERGE && noteExport.id in existing) {
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
        val lines = cleaned.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return ImportResult(0, 0, listOf("CSV is empty or missing header"))
        val header = lines.first().trim().removeSurrounding("\"").lowercase()
        if (header != "title,content") {
            return ImportResult(0, 0, listOf("CSV must have 'title,content' header, got '$header'"))
        }

        var imported = 0
        val now = Clock.System.now()

        for (i in 1 until lines.size) {
            try {
                val (title, content) = parseCsvLine(lines[i])
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
                return ImportResult(imported, 0, listOf("Error importing CSV line ${i + 1}: ${e.message}"))
            }
        }
        return ImportResult(imported, 0, emptyList())
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
                val csb = StringBuilder(); var j = 1
                while (j < rest.length) {
                    if (rest[j] == '"' && j + 1 < rest.length && rest[j + 1] == '"') {
                        csb.append('"'); j += 2
                    } else if (rest[j] == '"') {
                        j++; break
                    } else {
                        csb.append(rest[j]); j++
                    }
                }
                csb.toString()
            } else rest
            Pair(title, content)
        } else {
            val comma = line.indexOf(',')
            if (comma < 0) Pair(line.trim(), "") else Pair(line.substring(0, comma).trim(), line.substring(comma + 1).trim())
        }
    }

    private fun generateId(): String =
        Clock.System.now().toEpochMilliseconds().toString(16) + "-" + (0..Int.MAX_VALUE).random().toString(16)
}
