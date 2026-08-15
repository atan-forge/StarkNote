package com.stark.note.data.db

import androidx.room.TypeConverter
import com.stark.note.domain.note.NoteType
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String {
        return value.name
    }

    @TypeConverter
    fun toNoteType(value: String): NoteType {
        return NoteType.valueOf(value)
    }

    @TypeConverter
    fun fromInstant(value: Instant): String {
        return value.toString()
    }

    @TypeConverter
    fun toInstant(value: String): Instant {
        return Instant.parse(value)
    }
}
