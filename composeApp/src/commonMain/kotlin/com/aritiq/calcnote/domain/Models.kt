package com.aritiq.calcnote.domain

import kotlinx.datetime.Instant

/**
 * Domain Note model — independent of SQLDelight-generated types so domain code never
 * knows what `AritiqDatabase` is. Mapping happens in the repository.
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val favorite: Boolean = false,
    val folderId: String? = null,
)

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Tag(
    val id: String,
    val name: String,
    val createdAt: Instant,
)