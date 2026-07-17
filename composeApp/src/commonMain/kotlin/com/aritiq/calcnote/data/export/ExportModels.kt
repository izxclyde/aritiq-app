package com.aritiq.calcnote.data.export

import com.aritiq.calcnote.domain.Note
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AritiqExport(
    val version: Int = 1,
    val exportedAt: Long,
    val notes: List<NoteExport>,
)

@Serializable
data class NoteExport(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val favorite: Boolean = false,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val drawing: String? = null,
) {
    companion object {
        fun fromDomain(note: Note, tags: List<String>): NoteExport = NoteExport(
            id = note.id,
            title = note.title,
            content = note.content,
            createdAt = note.createdAt.toEpochMilliseconds(),
            updatedAt = note.updatedAt.toEpochMilliseconds(),
            isPinned = note.isPinned,
            isArchived = note.isArchived,
            favorite = note.favorite,
            folderId = note.folderId,
            tags = tags,
        )
    }

    fun toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        isPinned = isPinned,
        isArchived = isArchived,
        favorite = favorite,
        folderId = folderId,
    )
}
