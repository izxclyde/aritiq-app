package com.aritiq.calcnote.data.export

import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Folder
import com.aritiq.calcnote.domain.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryNoteRepo : NoteRepository {
    private val notes = mutableListOf<Note>()
    private val tagMap = mutableMapOf<String, List<String>>()

    fun seed(note: Note, tags: List<String> = emptyList()) {
        notes.add(note)
        tagMap[note.id] = tags
    }

    override fun recent(limit: Int, offset: Int): Flow<List<Note>> = emptyFlow()
    override suspend fun all(): List<Note> = notes.toList()
    override suspend fun pinned(): List<Note> = emptyList()
    override suspend fun archived(): List<Note> = emptyList()
    override suspend fun search(query: String): List<Note> = emptyList()
    override suspend fun getById(id: String): Note? = notes.find { it.id == id }
    override suspend fun upsert(note: Note) { notes.removeAll { it.id == note.id }; notes.add(note) }
    override suspend fun delete(id: String) { notes.removeAll { it.id == id } }
    override suspend fun selectByFolder(folderId: String): List<Note> = emptyList()
    override suspend fun setPinned(id: String, pinned: Boolean) {}
    override suspend fun setArchived(id: String, archived: Boolean) {}
    override suspend fun setFavorite(id: String, favorite: Boolean) {}
    override suspend fun tagsForNote(noteId: String): List<String> = tagMap[noteId] ?: emptyList()
}

class InMemoryFolderRepo : FolderRepository {
    override suspend fun all(): List<Folder> = emptyList()
    override suspend fun getById(id: String): Folder? = null
    override suspend fun upsert(folder: Folder) {}
    override suspend fun delete(id: String) {}
}

class ExportServiceTest {

    private val now = Clock.System.now()

    @Test fun export_all_json_includes_envelope() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(Note(id = "1", title = "Test", content = "milk 5", createdAt = now, updatedAt = now))
        val svc = ExportService(repo, InMemoryFolderRepo())
        val json = svc.exportAllJson()
        assertContains(json, "\"version\":")
        assertContains(json, "\"exportedAt\":")
        assertContains(json, "\"id\":")
        assertContains(json, "\"folders\":")
    }

    @Test fun export_single_note_json() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(Note(id = "a", title = "Single", content = "sum 10", createdAt = now, updatedAt = now))
        val svc = ExportService(repo, InMemoryFolderRepo())
        val json = svc.exportNoteJson("a")
        assertContains(json, "\"id\":\"a\"")
        assertContains(json, "\"title\":\"Single\"")
    }

    @Test fun export_selected_json() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(Note(id = "1", title = "A", content = "a", createdAt = now, updatedAt = now))
        repo.seed(Note(id = "2", title = "B", content = "b", createdAt = now, updatedAt = now))
        repo.seed(Note(id = "3", title = "C", content = "c", createdAt = now, updatedAt = now))
        val svc = ExportService(repo, InMemoryFolderRepo())
        val json = svc.exportSelectedJson(listOf("1", "3"))
        assertContains(json, "\"id\":\"1\"")
        assertContains(json, "\"id\":\"3\"")
        assertTrue { !json.contains("\"id\":\"2\"") }
    }

    @Test fun export_csv_has_header_and_rows() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(Note(id = "x", title = "Hello", content = "world", createdAt = now, updatedAt = now))
        val svc = ExportService(repo, InMemoryFolderRepo())
        val csv = svc.exportAllCsv()
        assertTrue(csv.startsWith("title,content"))
        assertContains(csv, "Hello,world")
    }

    @Test fun export_csv_escapes_commas() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(Note(id = "y", title = "Hello, World", content = "a,b,c", createdAt = now, updatedAt = now))
        val svc = ExportService(repo, InMemoryFolderRepo())
        val csv = svc.exportAllCsv()
        assertContains(csv, "\"Hello, World\"")
        assertContains(csv, "\"a,b,c\"")
    }

    @Test fun export_all_json_round_trip() = runTest {
        val repo = InMemoryNoteRepo()
        repo.seed(
            Note(id = "r1", title = "Round", content = "trip 42", createdAt = now, updatedAt = now),
            tags = listOf("test", "demo"),
        )
        val svc = ExportService(repo, InMemoryFolderRepo())
        val json = svc.exportAllJson()
    }
}
