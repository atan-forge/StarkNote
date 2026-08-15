package com.stark.note.data.mapper

import com.stark.note.data.db.entity.ListItemEntity
import com.stark.note.data.db.entity.NoteEntity
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    noteType = noteType,
    isLocked = isLocked,
    bodyIv = bodyIv,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    noteType = noteType,
    isLocked = isLocked,
    bodyIv = bodyIv,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ListItemEntity.toDomain(): ListItem = ListItem(
    id = id,
    noteId = noteId,
    content = content,
    isChecked = isChecked,
    position = position,
    createdAt = createdAt
)

fun ListItem.toEntity(): ListItemEntity = ListItemEntity(
    id = id,
    noteId = noteId,
    content = content,
    isChecked = isChecked,
    position = position,
    createdAt = createdAt
)
