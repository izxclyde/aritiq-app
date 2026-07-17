package com.aritiq.calcnote.data.export

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportServiceTest {

    @Test fun import_json_merge_skips_duplicates() = runTest {
        val repo = InMemoryNoteRepo()
        val now = kotlinx.datetime.Clock.System.now()
        repo.seed(com.aritiq.calcnote.domain.Note(id = "dup", title = "Existing", content = "keep", createdAt = now, updatedAt = now))

        val json = """{"version":1,"exportedAt":1000,"notes":[{"id":"dup","title":"Existing","content":"keep","createdAt":1000,"updatedAt":1000}]}"""
        val svc = ImportService(repo)
        val result = svc.importJson(json, ImportMode.MERGE)
        assertEquals(0, result.imported, "duplicate should be skipped")
        assertEquals(1, result.skipped)
    }

    @Test fun import_json_replace_wipes_and_adds() = runTest {
        val repo = InMemoryNoteRepo()
        val now = kotlinx.datetime.Clock.System.now()
        repo.seed(com.aritiq.calcnote.domain.Note(id = "old", title = "Old", content = "gone", createdAt = now, updatedAt = now))

        val json = """{"version":1,"exportedAt":2000,"notes":[{"id":"new1","title":"New","content":"hello","createdAt":2000,"updatedAt":2000}]}"""
        val svc = ImportService(repo)
        val result = svc.importJson(json, ImportMode.REPLACE)
        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(null, repo.getById("old"))
        assertEquals("New", repo.getById("new1")?.title)
    }

    @Test fun import_csv_adds_new_notes() = runTest {
        val repo = InMemoryNoteRepo()
        val csv = "title,content\nShopping,milk 10\ntotal,= 6000"
        val svc = ImportService(repo)
        val result = svc.importCsv(csv)
        assertEquals(2, result.imported)
    }

    @Test fun import_csv_rejects_bad_header() = runTest {
        val repo = InMemoryNoteRepo()
        val csv = "bad,header\nhello,world"
        val svc = ImportService(repo)
        val result = svc.importCsv(csv)
        assertEquals(0, result.imported)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test fun import_json_invalid_returns_error() = runTest {
        val repo = InMemoryNoteRepo()
        val svc = ImportService(repo)
        val result = svc.importJson("not json", ImportMode.MERGE)
        assertEquals(0, result.imported)
        assertTrue(result.errors.isNotEmpty())
    }
}
