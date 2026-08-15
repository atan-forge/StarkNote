package com.stark.note.domain.lock

import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.repository.NoteRepository
import com.stark.note.platform.storage.CredentialResult
import com.stark.note.platform.storage.PinCredentialStore
import com.stark.note.platform.storage.PinVerifyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinResetCoordinatorTest {
    @Test
    fun successfulResetMarksPendingBeforeDeletionAndClearsCredentialLast() = runBlocking {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events)
        val repository = FakeRepository(events)

        val result = PinResetCoordinator(repository, store).reset()

        assertTrue(result is PinResetResult.Success)
        assertEquals(listOf("begin", "delete", "finish"), events)
        assertFalse(store.pending)
    }

    @Test
    fun interruptedResetResumesDeletionAndCredentialClear() = runBlocking {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events).apply { pending = true }
        val repository = FakeRepository(events)

        val result = PinResetCoordinator(repository, store).resumeIfNeeded()

        assertTrue(result is PinResetResult.Success)
        assertEquals(listOf("delete", "finish"), events)
    }

    @Test
    fun deletionFailureKeepsResetMarkerForRetry() = runBlocking {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events)
        val repository = FakeRepository(events, failDelete = true)

        val result = PinResetCoordinator(repository, store).reset()

        assertTrue(result is PinResetResult.Failure)
        assertTrue(store.pending)
        assertEquals(listOf("begin", "delete"), events)
    }

    private class FakeCredentialStore(private val events: MutableList<String>) : PinCredentialStore {
        var pending = false

        override suspend fun savePin(pin: String) = CredentialResult.Success(Unit)
        override suspend fun verifyPin(pin: String) = CredentialResult.Success(PinVerifyResult.Success)
        override suspend fun hasPin() = CredentialResult.Success(true)
        override suspend fun beginPinReset(): CredentialResult<Unit> {
            events += "begin"
            pending = true
            return CredentialResult.Success(Unit)
        }
        override suspend fun finishPinReset(): CredentialResult<Unit> {
            events += "finish"
            pending = false
            return CredentialResult.Success(Unit)
        }
        override suspend fun isPinResetPending() = CredentialResult.Success(pending)
    }

    private class FakeRepository(
        private val events: MutableList<String>,
        private val failDelete: Boolean = false
    ) : NoteRepository {
        override fun getAllNotes(): Flow<List<Note>> = flowOf(emptyList())
        override suspend fun getNoteById(id: String): Note? = null
        override suspend fun insertNote(note: Note) = Unit
        override suspend fun updateNote(note: Note) = Unit
        override suspend fun deleteNoteAndItems(note: Note) = Unit
        override suspend fun deleteLockedNotes() {
            events += "delete"
            if (failDelete) error("failed")
        }
        override fun getItemsForNote(noteId: String): Flow<List<ListItem>> = flowOf(emptyList())
        override suspend fun insertItems(items: List<ListItem>) = Unit
        override suspend fun addChecklistItem(note: Note, item: ListItem) = Unit
        override suspend fun updateChecklistItem(note: Note, item: ListItem) = Unit
        override suspend fun deleteChecklistItem(note: Note, item: ListItem) = Unit
        override suspend fun clearCheckedChecklistItems(note: Note) = Unit
        override suspend fun replaceImportedData(notes: List<Note>, items: List<ListItem>) = Unit
    }
}
