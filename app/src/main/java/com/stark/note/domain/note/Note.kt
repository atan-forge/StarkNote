package com.stark.note.domain.note

import kotlinx.datetime.Instant

data class Note(
    val id: String,
    val title: String?,
    val body: String,
    val noteType: NoteType,
    val isLocked: Boolean,
    val bodyIv: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ListItem(
    val id: String,
    val noteId: String,
    val content: String,
    val isChecked: Boolean,
    val position: Int,
    val createdAt: Instant
)
