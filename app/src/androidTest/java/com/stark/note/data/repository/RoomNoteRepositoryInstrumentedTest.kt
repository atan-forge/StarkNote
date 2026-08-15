package com.stark.note.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stark.note.data.db.NoteDatabase
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RoomNoteRepositoryInstrumentedTest {
    private lateinit var database: NoteDatabase
    private lateinit var repository: RoomNoteRepository
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomNoteRepository(
            database = database,
            noteDao = database.noteDao(),
            listItemDao = database.listItemDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteNoteAndItemsRemovesChecklistItems() = runBlocking {
        val note = Note(
            id = "list",
            title = "Groceries",
            body = "",
            noteType = NoteType.LIST,
            isLocked = false,
            bodyIv = null,
            createdAt = now,
            updatedAt = now
        )
        val item = ListItem(
            id = "item",
            noteId = note.id,
            content = "Milk",
            isChecked = false,
            position = 0,
            createdAt = now
        )

        repository.insertNote(note)
        repository.insertItems(listOf(item))
        repository.deleteNoteAndItems(note)

        assertEquals(emptyList<Note>(), repository.getAllNotes().first())
        assertEquals(emptyList<ListItem>(), repository.getItemsForNote(note.id).first())
    }

    @Test
    fun replaceImportedDataReplacesSameIdChecklistItems() = runBlocking {
        val oldNote = listNote("list", "Old")
        val newNote = listNote("list", "New")
        val oldItem = listItem("old-item", "list", "Old")
        val newItem = listItem("new-item", "list", "New")

        repository.insertNote(oldNote)
        repository.insertItems(listOf(oldItem))

        repository.replaceImportedData(listOf(newNote), listOf(newItem))

        assertEquals("New", repository.getAllNotes().first().single().title)
        assertEquals(listOf("New"), repository.getItemsForNote("list").first().map { it.content })
    }

    @Test
    fun currentSchemaCreatesCleanly() = runBlocking {
        assertEquals(emptyList<Note>(), repository.getAllNotes().first())
    }

    @Test
    fun notesAreOrderedByLastModified() = runBlocking {
        repository.insertNote(listNote("older", "Older").copy(updatedAt = Instant.parse("2026-01-01T00:00:00Z")))
        repository.insertNote(listNote("newer", "Newer").copy(updatedAt = Instant.parse("2026-01-03T00:00:00Z")))
        repository.insertNote(listNote("middle", "Middle").copy(updatedAt = Instant.parse("2026-01-02T00:00:00Z")))

        assertEquals(listOf("newer", "middle", "older"), repository.getAllNotes().first().map { it.id })
    }

    @Test
    fun checklistMutationUpdatesItemAndNoteTogether() = runBlocking {
        val note = listNote("list", "List")
        val item = listItem("item", note.id, "Item")

        repository.addChecklistItem(note, item)
        val updated = note.copy(updatedAt = Instant.parse("2026-01-02T00:00:00Z"))
        repository.updateChecklistItem(updated, item.copy(isChecked = true))

        assertEquals(updated.updatedAt, repository.getNoteById(note.id)?.updatedAt)
        assertEquals(true, repository.getItemsForNote(note.id).first().single().isChecked)
    }

    @Test
    fun importRejectsItemIdCollisionWithUnrelatedNoteWithoutMutation() = runBlocking {
        val existing = listNote("existing", "Existing")
        repository.insertNote(existing)
        repository.insertItems(listOf(listItem("shared-item", existing.id, "Existing item")))
        val imported = listNote("imported", "Imported")

        try {
            repository.replaceImportedData(
                listOf(imported),
                listOf(listItem("shared-item", imported.id, "Imported item"))
            )
            fail("Expected item ID collision")
        } catch (_: com.stark.note.domain.note.repository.ImportedItemIdConflictException) {
        }

        assertEquals(listOf("existing"), repository.getAllNotes().first().map { it.id })
        assertEquals("Existing item", repository.getItemsForNote(existing.id).first().single().content)
    }

    private fun listNote(id: String, title: String): Note {
        return Note(
            id = id,
            title = title,
            body = "",
            noteType = NoteType.LIST,
            isLocked = false,
            bodyIv = null,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun listItem(id: String, noteId: String, content: String): ListItem {
        return ListItem(
            id = id,
            noteId = noteId,
            content = content,
            isChecked = false,
            position = 0,
            createdAt = now
        )
    }
}
