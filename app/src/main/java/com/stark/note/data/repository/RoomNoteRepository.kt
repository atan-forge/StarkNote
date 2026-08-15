package com.stark.note.data.repository

import androidx.room.withTransaction
import com.stark.note.data.db.NoteDatabase
import com.stark.note.data.db.dao.ListItemDao
import com.stark.note.data.db.dao.NoteDao
import com.stark.note.data.mapper.toDomain
import com.stark.note.data.mapper.toEntity
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.repository.ImportedItemIdConflictException
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNoteRepository(
    private val database: NoteDatabase,
    private val noteDao: NoteDao,
    private val listItemDao: ListItemDao
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getNoteById(id: String): Note? {
        return noteDao.getNoteById(id)?.toDomain()
    }

    override suspend fun insertNote(note: Note) {
        noteDao.insertNote(note.toEntity())
    }

    override suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEntity())
    }

    override suspend fun deleteNoteAndItems(note: Note) {
        database.withTransaction {
            listItemDao.deleteItemsForNote(note.id)
            noteDao.deleteNote(note.toEntity())
        }
    }

    override suspend fun deleteLockedNotes() {
        database.withTransaction {
            noteDao.getLockedNotes().forEach { note ->
                listItemDao.deleteItemsForNote(note.id)
                noteDao.deleteNote(note)
            }
        }
    }

    override fun getItemsForNote(noteId: String): Flow<List<ListItem>> {
        return listItemDao.getItemsForNote(noteId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertItems(items: List<ListItem>) {
        listItemDao.insertItems(items.map { it.toEntity() })
    }

    override suspend fun addChecklistItem(note: Note, item: ListItem) {
        database.withTransaction {
            upsertNote(note)
            listItemDao.insertItems(listOf(item.toEntity()))
        }
    }

    override suspend fun updateChecklistItem(note: Note, item: ListItem) {
        database.withTransaction {
            listItemDao.updateItem(item.toEntity())
            upsertNote(note)
        }
    }

    override suspend fun deleteChecklistItem(note: Note, item: ListItem) {
        database.withTransaction {
            listItemDao.deleteItem(item.toEntity())
            upsertNote(note)
        }
    }

    override suspend fun clearCheckedChecklistItems(note: Note) {
        database.withTransaction {
            listItemDao.deleteCheckedItemsForNote(note.id)
            upsertNote(note)
        }
    }

    override suspend fun replaceImportedData(notes: List<Note>, items: List<ListItem>) {
        database.withTransaction {
            val importedNoteIds = notes.map { it.id }
            val conflictingItems = if (items.isEmpty()) {
                emptyList()
            } else {
                listItemDao.getItemsByIds(items.map { it.id })
                    .filter { it.noteId !in importedNoteIds }
            }
            if (conflictingItems.isNotEmpty()) {
                throw ImportedItemIdConflictException()
            }

            importedNoteIds.forEach { noteId ->
                listItemDao.deleteItemsForNote(noteId)
            }
            notes.forEach { note ->
                noteDao.insertNote(note.toEntity())
            }

            if (items.isNotEmpty()) {
                listItemDao.insertItems(items.map { it.toEntity() })
            }
        }
    }

    private suspend fun upsertNote(note: Note) {
        if (noteDao.getNoteById(note.id) == null) {
            noteDao.insertNote(note.toEntity())
        } else {
            noteDao.updateNote(note.toEntity())
        }
    }
}
