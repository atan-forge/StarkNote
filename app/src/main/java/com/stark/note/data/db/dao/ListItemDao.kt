package com.stark.note.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stark.note.data.db.entity.ListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_items WHERE note_id = :noteId ORDER BY position ASC")
    fun getItemsForNote(noteId: String): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<String>): List<ListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ListItemEntity>)

    @Update
    suspend fun updateItem(item: ListItemEntity)

    @Delete
    suspend fun deleteItem(item: ListItemEntity)

    @Query("DELETE FROM list_items WHERE note_id = :noteId")
    suspend fun deleteItemsForNote(noteId: String)

    @Query("DELETE FROM list_items WHERE note_id = :noteId AND is_checked = 1")
    suspend fun deleteCheckedItemsForNote(noteId: String)
}
