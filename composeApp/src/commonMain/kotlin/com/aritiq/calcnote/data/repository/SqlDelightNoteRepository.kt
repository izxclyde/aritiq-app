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

    override suspend fun search(query: String): List<Note> {
        return withContext(Dispatchers.IO) {
            db.noteQueries.searchByText(query).executeAsList().map(::toDomain)
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

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            db.noteQueries.setFavorite(if (favorite) 1L else 0L, now(), id)
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    override suspend fun tagsForNote(noteId: String): List<String> {
        return withContext(Dispatchers.IO) {
            db.tagQueries.tagsForNote(noteId).executeAsList().map { it.name }
        }
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