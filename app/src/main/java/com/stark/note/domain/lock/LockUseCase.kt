package com.stark.note.domain.lock

import com.stark.note.domain.note.Note

interface NoteBodyCrypto {
    fun encryptNote(note: Note, plaintextBody: String): Note
    fun decryptNote(note: Note): DecryptNoteResult
}

interface NoteBodyCipher {
    fun encrypt(plainText: String): Pair<String, String>
    fun decrypt(encryptedText: String, iv: String): String
}

sealed class DecryptNoteResult {
    data class Success(val note: Note) : DecryptNoteResult()
    data class Failure(val note: Note, val cause: Throwable? = null) : DecryptNoteResult()
}

class LockUseCase(
    private val cryptoService: NoteBodyCipher
) : NoteBodyCrypto {
    override fun encryptNote(note: Note, plaintextBody: String): Note {
        if (plaintextBody.isEmpty()) {
            return note.copy(body = "", isLocked = true, bodyIv = null)
        }

        val (encryptedBody, iv) = cryptoService.encrypt(plaintextBody)
        return note.copy(
            body = encryptedBody,
            bodyIv = iv,
            isLocked = true
        )
    }

    override fun decryptNote(note: Note): DecryptNoteResult {
        if (!note.isLocked) {
            return DecryptNoteResult.Success(note)
        }
        if (note.body.isEmpty() && note.bodyIv == null) {
            return DecryptNoteResult.Success(note.copy(isLocked = false))
        }
        if (note.body.isEmpty() || note.bodyIv.isNullOrBlank()) {
            return DecryptNoteResult.Failure(
                note,
                IllegalStateException("Locked note encryption data is incomplete")
            )
        }

        return try {
            val decryptedBody = cryptoService.decrypt(note.body, note.bodyIv)
            DecryptNoteResult.Success(
                note.copy(
                    body = decryptedBody,
                    isLocked = false
                )
            )
        } catch (e: Exception) {
            DecryptNoteResult.Failure(note, e)
        }
    }
}
