package com.stark.note.domain.lock

import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockUseCaseTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun incompleteLockedNoteFailsWithoutChangingStoredContent() {
        val note = note(body = "ciphertext", isLocked = true, bodyIv = null)

        val result = LockUseCase(FakeCipher()).decryptNote(note)

        assertTrue(result is DecryptNoteResult.Failure)
        assertEquals(note, (result as DecryptNoteResult.Failure).note)
    }

    @Test
    fun emptyLockedNoteCanBeOpened() {
        val result = LockUseCase(FakeCipher()).decryptNote(note(body = "", isLocked = true, bodyIv = null))

        assertTrue(result is DecryptNoteResult.Success)
        assertTrue(!(result as DecryptNoteResult.Success).note.isLocked)
    }

    @Test
    fun decryptFailurePreservesEncryptedNote() {
        val note = note(body = "ciphertext", isLocked = true, bodyIv = "iv")

        val result = LockUseCase(FailingCipher()).decryptNote(note)

        assertTrue(result is DecryptNoteResult.Failure)
        assertEquals(note, (result as DecryptNoteResult.Failure).note)
    }

    @Test
    fun invalidIvPreservesEncryptedNote() {
        val note = note(body = "ciphertext", isLocked = true, bodyIv = "invalid-iv")

        val result = LockUseCase(InvalidIvCipher()).decryptNote(note)

        assertTrue(result is DecryptNoteResult.Failure)
        assertEquals(note, (result as DecryptNoteResult.Failure).note)
    }

    private fun note(body: String, isLocked: Boolean = false, bodyIv: String? = null) = Note(
        id = "note",
        title = "Title",
        body = body,
        noteType = NoteType.NORMAL,
        isLocked = isLocked,
        bodyIv = bodyIv,
        createdAt = now,
        updatedAt = now
    )

    private class FakeCipher : NoteBodyCipher {
        override fun encrypt(plainText: String): Pair<String, String> = "ciphertext" to "iv"

        override fun decrypt(encryptedText: String, iv: String): String = "plain text"
    }

    private class FailingCipher : NoteBodyCipher {
        override fun encrypt(plainText: String): Pair<String, String> = "ciphertext" to "iv"

        override fun decrypt(encryptedText: String, iv: String): String = error("corrupt")
    }

    private class InvalidIvCipher : NoteBodyCipher {
        override fun encrypt(plainText: String): Pair<String, String> = "ciphertext" to "iv"

        override fun decrypt(encryptedText: String, iv: String): String = error("invalid iv")
    }
}
