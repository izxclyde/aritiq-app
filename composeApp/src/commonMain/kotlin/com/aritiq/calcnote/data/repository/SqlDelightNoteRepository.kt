package com.aritiq.calcnote.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aritiq.calcnote.data.db.AritiqDatabase
import com.aritiq.calcnote.data.db.Note as NoteRow
import com.aritiq.calcnote.domain.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Maps between SQLDelight-generated rows and the domain [Note] model. The DB row type is
 * referenced by aliasing the generated `Note` to `NoteRow` to avoid colliding with the
 * domain `Note`.
 */
class SqlDelightNoteRepository(
    private val db: AritiqDatabase,
) : NoteRepository {

    override fun recent(limit: Int, offset: Int): Flow<List<Note>> {
        return db.noteQueries.selectRecent(limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.map(::toDomain) }
    }

    override suspend fun all(): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectAll().executeAsList().map(::toDomain)
        }
    }

    override suspend fun pinned(): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectPinned().executeAsList().map(::toDomain)
        }
    }

    override suspend fun archived(): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectArchived().executeAsList().map(::toDomain)
        }
    }

    /**
     * Substring search, plus a second pass that ignores digit grouping so "1500" finds "1,500".
     * Notes are typed by hand and both spellings end up in the same library.
     *
     * The grouped pass cannot be a SQL expression -- SQLDelight's grammar will not accept REPLACE
     * on the left of LIKE, because REPLACE is its own keyword for INSERT OR REPLACE. So the
     * candidates are prefetched with a single-digit LIKE (a digit is the one substring guaranteed
     * to survive grouping) and compared in memory. That keeps the scan off the full table, which
     * is the whole point of the debounce in HomeViewModel.
     */
    override suspend fun search(query: String): List<Note> {
        return withContext(Dispatchers.IO) {
            val byText = db.noteQueries.searchByText(query).executeAsList()
            val needle = groupedNeedle(query)
            if (needle == null) return@withContext byText.map(::toDomain)
            val candidates = db.noteQueries.searchByText(needle.first().toString()).executeAsList()
            val extra = candidates.filter {
                stripSeparators(it.content).contains(needle) || stripSeparators(it.title).contains(needle)
            }
            (byText + extra).distinctBy { it.id }.map(::toDomain)
        }
    }

    override suspend fun getById(id: String): Note? {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectById(id).executeAsOneOrNull()?.let(::toDomain)
        }
    }

    override suspend fun upsert(note: Note) {
        withContext(Dispatchers.IO) {
            db.noteQueries.insertOrReplace(
                id = note.id,
                title = note.title,
                content = note.content,
                createdAt = note.createdAt.toEpochMilliseconds(),
                updatedAt = note.updatedAt.toEpochMilliseconds(),
                isPinned = if (note.isPinned) 1L else 0L,
                isArchived = if (note.isArchived) 1L else 0L,
                favorite = if (note.favorite) 1L else 0L,
                folderId = note.folderId,
            )
        }
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            db.noteQueries.deleteById(id)
        }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        withContext(Dispatchers.IO) {
            db.noteQueries.setPinned(if (pinned) 1L else 0L, now(), id)
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        withContext(Dispatchers.IO) {
            db.noteQueries.setArchived(if (archived) 1L else 0L, now(), id)
        }
    }

    override suspend fun selectByFolder(folderId: String): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectByFolder(folderId).executeAsList().map(::toDomain)
        }
    }

    override suspend fun selectByFolderExcluding(excludedId: String): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.selectByFolderExcluding(excludedId).executeAsList().map(::toDomain)
        }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            db.noteQueries.setFavorite(if (favorite) 1L else 0L, now(), id)
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * The separator-free form of [query] when the query is a bare number, else null.
     *
     * Fires for plain digit strings too, not just grouped ones: "1500" has to reach "1,500", and
     * the plain LIKE is exactly what fails to make that hop. Needs at least one digit left after
     * stripping -- a bare "." strips to empty, and an empty needle matches every note.
     */
    private fun groupedNeedle(query: String): String? {
        if (query.isEmpty() || !query.all { it.isDigit() || it == ',' || it == '.' }) return null
        return stripSeparators(query).takeIf { it.isNotEmpty() }
    }

    private fun stripSeparators(s: String): String = s.replace(",", "").replace(".", "")

    override suspend fun tagsForNote(noteId: String): List<String> {
        return withContext(Dispatchers.IO) {
            db.tagQueries.tagsForNote(noteId).executeAsList().map { it.name }
        }
    }

    override suspend fun tagsForNotes(noteIds: List<String>): Map<String, List<String>> {
        if (noteIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            db.tagQueries.tagsForNotes(noteIds)
                .executeAsList()
                .groupBy({ it.note_id }, { it.name })
        }
    }

    override suspend fun setTags(noteId: String, tags: List<String>) {
        withContext(Dispatchers.IO) {
            val wanted = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val current = db.tagQueries.tagsForNote(noteId).executeAsList().map { it.name }.toSet()
            for (name in current - wanted.toSet()) {
                db.tagQueries.unlinkNoteTag(noteId, name)
            }
            for (name in wanted) {
                if (name in current) continue
                // A tag's id is its name: `tag.name` is UNIQUE, so this needs no id generator and
                // turns every name lookup into selectById. Renaming a tag is a future concern and
                // would need the id decoupled from the name.
                if (db.tagQueries.selectById(name).executeAsOneOrNull() == null) {
                    db.tagQueries.insertOrReplace(id = name, name = name, createdAt = now())
                }
                db.tagQueries.linkNoteTag(noteId, name)
            }
        }
    }

    override suspend fun allTags(): List<String> = withContext(Dispatchers.IO) {
        db.tagQueries.selectAll().executeAsList().map { it.name }
    }

    override suspend fun selectByTag(tag: String): List<Note> = withContext(Dispatchers.IO) {
        db.noteQueries.selectByTag(tag).executeAsList().map(::toDomain)
    }

    private fun toDomain(row: NoteRow): Note = Note(
        id = row.id,
        title = row.title,
        content = row.content,
        createdAt = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
        isPinned = row.is_pinned != 0L,
        isArchived = row.is_archived != 0L,
        favorite = row.favorite != 0L,
        folderId = row.folder_id,
    )
}