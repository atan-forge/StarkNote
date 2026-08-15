package com.stark.note.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stark.note.data.db.dao.ListItemDao
import com.stark.note.data.db.dao.NoteDao
import com.stark.note.data.db.entity.ListItemEntity
import com.stark.note.data.db.entity.NoteEntity

@Database(
    entities = [NoteEntity::class, ListItemEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun listItemDao(): ListItemDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = create(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun create(context: Context): NoteDatabase {
            return Room.databaseBuilder(
                context,
                NoteDatabase::class.java,
                "note_database"
            ).build()
        }
    }
}
