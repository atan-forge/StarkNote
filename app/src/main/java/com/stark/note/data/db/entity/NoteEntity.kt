package com.stark.note.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stark.note.domain.note.NoteType
import kotlinx.datetime.Instant

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String?,
    val body: String,
    @ColumnInfo(name = "note_type")
    val noteType: NoteType,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean,
    @ColumnInfo(name = "body_iv")
    val bodyIv: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
)
