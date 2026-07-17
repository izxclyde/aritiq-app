package com.aritiq.calcnote.data.repository

import com.aritiq.calcnote.data.db.AritiqDatabase
import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.db.LOCKED_FOLDER_NAME
import com.aritiq.calcnote.data.db.Folder as FolderRow
import com.aritiq.calcnote.domain.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SqlDelightFolderRepository(
    private val db: AritiqDatabase,
) : FolderRepository {

    override suspend fun all(): List<Folder> = withContext(Dispatchers.IO) {
        db.folderQueries.selectAll().executeAsList().map(::toDomain)
    }

    override suspend fun getById(id: String): Folder? = withContext(Dispatchers.IO) {
        db.folderQueries.selectById(id).executeAsOneOrNull()?.let(::toDomain)
    }

    override suspend fun getLockedFolder(): Folder? = withContext(Dispatchers.IO) {
        db.folderQueries.selectLocked().executeAsOneOrNull()?.let(::toDomain)
    }

    override suspend fun ensureLockedFolderExists(): Folder = withContext(Dispatchers.IO) {
        val existing = db.folderQueries.selectById(LOCKED_FOLDER_ID).executeAsOneOrNull()
        if (existing != null) return@withContext toDomain(existing)
        val now = Clock.System.now().toEpochMilliseconds()
        db.folderQueries.insertOrReplace(
            id = LOCKED_FOLDER_ID,
            name = LOCKED_FOLDER_NAME,
            isLocked = 1L,
            createdAt = now,
            updatedAt = now,
        )
        val row = db.folderQueries.selectById(LOCKED_FOLDER_ID).executeAsOne()
        toDomain(row)
    }

    override suspend fun upsert(folder: Folder) {
        withContext(Dispatchers.IO) {
            db.folderQueries.insertOrReplace(
                id = folder.id,
                name = folder.name,
                isLocked = if (folder.isLocked) 1L else 0L,
                createdAt = folder.createdAt.toEpochMilliseconds(),
                updatedAt = folder.updatedAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            db.folderQueries.deleteById(id)
        }
    }

    private fun toDomain(row: FolderRow): Folder = Folder(
        id = row.id,
        name = row.name,
        isLocked = row.is_locked != 0L,
        createdAt = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
    )
}
