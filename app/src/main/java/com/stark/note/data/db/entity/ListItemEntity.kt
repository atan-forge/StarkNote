package com.stark.note.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["note_id", "position"])]
)
data class ListItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    val content: String,
    @ColumnInfo(name = "is_checked")
    val isChecked: Boolean,
    val position: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
