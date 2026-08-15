package com.stark.note.domain.note.repository

import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: String): Note?
    suspend fun insertNote(note: Note)
    suspend fun updateNote(note: Note)
    suspend fun deleteNoteAndItems(note: Note)
    suspend fun deleteLockedNotes()

    fun getItemsForNote(noteId: String): Flow<List<ListItem>>
    suspend fun insertItems(items: List<ListItem>)
    suspend fun addChecklistItem(note: Note, item: ListItem)
    suspend fun updateChecklistItem(note: Note, item: ListItem)
    suspend fun deleteChecklistItem(note: Note, item: ListItem)
    suspend fun clearCheckedChecklistItems(note: Note)
    suspend fun replaceImportedData(notes: List<Note>, items: List<ListItem>)
}

class ImportedItemIdConflictException : IllegalArgumentException()
