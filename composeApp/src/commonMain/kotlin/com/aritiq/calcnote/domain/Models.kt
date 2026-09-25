package com.aritiq.calcnote.domain

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

/**
 * Domain Note model — independent of SQLDelight-generated types so domain code never
 * knows what `AritiqDatabase` is. Mapping happens in the repository.
 */
// All fields are vals of stable types, so the Compose compiler can skip equality checks on
// every note in the list. Without this these classes are treated as unstable and force
// recomposition of every row on each emission.
@Immutable
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

@Immutable
data class Folder(
    val id: String,
    val name: String,
    val isLocked: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Immutable
data class Tag(
    val id: String,
    val name: String,
    val createdAt: Instant,
)