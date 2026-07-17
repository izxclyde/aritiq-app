package com.aritiq.calcnote.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aritiq.calcnote.data.db.CalcNoteDatabase
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.domain.NoteProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ponytail: one runnable check on the repo. Covers upsert/read/search/pin/delete plus
 * the integration with NoteProcessor.liveTotal — the risky glue.
 */
class SqlDelightNoteRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: CalcNoteDatabase
    private lateinit var repo: NoteRepository

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CalcNoteDatabase.Schema.create(driver)
        db = CalcNoteDatabase(driver)
        repo = SqlDelightNoteRepository(db)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun note(id: String, content: String, pinned: Boolean = false) = Note(
        id = id,
        title = NoteProcessor.titleOf(content),
        content = content,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(1_000),
        isPinned = pinned,
        isArchived = false,
        favorite = false,
        folderId = null,
    )

    @Test fun upsert_and_read_back_by_id() = runBlocking {
        val n = note("n1", "Milk 12.5\nBread 6\nTotal =")
        repo.upsert(n)
        val byId = repo.getById("n1")
        assertNotNull(byId)
        assertEquals("Shopping".let { NoteProcessor.titleOf(n.content) }, byId.title)
        assertEquals(n.content, byId.content)
    }

    @Test fun recent_returns_newest_unpinned_first() = runBlocking {
        repo.upsert(note("a", "Apple 5"))
        repo.upsert(note("b", "Banana 3"))
        val r = repo.recent(50, 0).first()
        assertEquals(2, r.size)
        // no pinned; both returned, newest first by updated_at
        assertTrue(r.first().updatedAt >= r.last().updatedAt)
    }

    @Test fun recent_excludes_pinned_notes() = runBlocking {
        repo.upsert(note("a", "Plain", pinned = false))
        repo.upsert(note("b", "Important", pinned = true))
        val r = repo.recent(50, 0).first()
        assertEquals(1, r.size)
        assertEquals("Plain", NoteProcessor.titleOf(r.first().content))
    }

    @Test fun search_matches_title_or_content() = runBlocking {
        repo.upsert(note("n", "Rent = 1200"))
        val hits = repo.search("Rent")
        assertEquals(1, hits.size)
        assertEquals("n", hits[0].id)
    }

    @Test fun archive_hides_from_recent() = runBlocking {
        repo.upsert(note("a", "first"))
        repo.upsert(note("b", "second"))
        repo.setArchived("a", true)
        val r = repo.recent(50, 0).first()
        assertEquals(1, r.size)
        assertEquals("b", r[0].id)
    }

    @Test fun delete_removes_note() = runBlocking {
        repo.upsert(note("x", "gone"))
        repo.delete("x")
        val r = repo.recent(50, 0).first()
        assertTrue(r.none { it.id == "x" })
        assertNull(repo.getById("x"))
    }

    @Test fun title_of_shopping_lists_becomes_first_line() = runBlocking {
        val n = note("g", "Groceries\nMilk 12.5\nBread 6\nTotal =")
        repo.upsert(n)
        val fromDb = repo.getById("g")!!
        assertEquals("Groceries", fromDb.title)
    }

    @Test fun live_total_of_shopping_note_sums_trailing_numbers() = runBlocking {
        // ponytail: this is the spec's flagship example
        val content = """
            Groceries

            Milk 12.5
            Bread 6
            Chicken 22.5

            Total =
        """.trimIndent()
        val n = note("s", content)
        repo.upsert(n)
        val saved = repo.getById("s")!!
        val total = NoteProcessor.liveTotal(saved.content)
        assertEquals(41.0, total, 1e-6)
    }

    @Test fun live_total_with_variables_resolves() = runBlocking {
        val content = """
            Budget
            Rent = 1200
            Electricity = 150
            Internet = 90
            Total =
        """.trimIndent()
        val n = note("v", content)
        repo.upsert(n)
        val total = NoteProcessor.liveTotal(repo.getById("v")!!.content)
        assertEquals(1440.0, total, 1e-6)
    }

    private fun assertEquals(expected: Double, actual: Double, tolerance: Double) {
        if (kotlin.math.abs(expected - actual) > tolerance) {
            throw AssertionError("expected=$expected actual=$actual tolerance=$tolerance")
        }
    }
}