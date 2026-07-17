package com.aritiq.calcnote.data.repository

import com.aritiq.calcnote.domain.Folder

interface FolderRepository {
    suspend fun all(): List<Folder>
    suspend fun getById(id: String): Folder?
    suspend fun upsert(folder: Folder)
    suspend fun delete(id: String)
}
