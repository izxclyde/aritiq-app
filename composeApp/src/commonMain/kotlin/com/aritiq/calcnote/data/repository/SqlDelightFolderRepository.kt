package com.aritiq.calcnote.data.repository

import com.aritiq.calcnote.data.db.AritiqDatabase
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

    override suspend fun upsert(folder: Folder) {
        withContext(Dispatchers.IO) {
            db.folderQueries.insertOrReplace(
                id = folder.id,
                name = folder.name,
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
        createdAt = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
    )
}
