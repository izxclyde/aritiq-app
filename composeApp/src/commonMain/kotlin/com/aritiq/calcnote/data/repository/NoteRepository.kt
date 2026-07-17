package com.aritiq.calcnote.data.repository

import com.aritiq.calcnote.domain.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Ponytail: ViewModels depend on this — no UseCase layer. Single-implementation interface
 * keeps it swappable for tests; we avoid defining one without a caller.
 */
interface NoteRepository {
    fun recent(limit: Int = 50, offset: Int = 0): Flow<List<Note>>
    suspend fun all(): List<Note>
    suspend fun pinned(): List<Note>
    suspend fun archived(): List<Note>
    suspend fun search(query: String): List<Note>
    suspend fun getById(id: String): Note?
    suspend fun upsert(note: Note)
    suspend fun delete(id: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun selectByFolder(folderId: String): List<Note>
    suspend fun tagsForNote(noteId: String): List<String>
}