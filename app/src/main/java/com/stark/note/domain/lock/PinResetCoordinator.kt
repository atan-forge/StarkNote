package com.stark.note.domain.lock

import com.stark.note.domain.note.repository.NoteRepository
import com.stark.note.platform.storage.CredentialResult
import com.stark.note.platform.storage.PinCredentialStore

sealed class PinResetResult {
    object Success : PinResetResult()
    data class Failure(val lockedNotesDeleted: Boolean, val cause: Throwable? = null) : PinResetResult()
}

class PinResetCoordinator(
    private val repository: NoteRepository,
    private val credentialStore: PinCredentialStore
) {
    suspend fun reset(): PinResetResult {
        return when (val pending = credentialStore.beginPinReset()) {
            is CredentialResult.Failure -> PinResetResult.Failure(lockedNotesDeleted = false, pending.cause)
            is CredentialResult.Success -> completeReset()
        }
    }

    suspend fun resumeIfNeeded(): PinResetResult {
        return when (val pending = credentialStore.isPinResetPending()) {
            is CredentialResult.Failure -> PinResetResult.Failure(lockedNotesDeleted = false, pending.cause)
            is CredentialResult.Success -> if (pending.value) completeReset() else PinResetResult.Success
        }
    }

    private suspend fun completeReset(): PinResetResult {
        val deletion = runCatching { repository.deleteLockedNotes() }
        if (deletion.isFailure) {
            return PinResetResult.Failure(lockedNotesDeleted = false, deletion.exceptionOrNull())
        }
        return when (val cleared = credentialStore.finishPinReset()) {
            is CredentialResult.Success -> PinResetResult.Success
            is CredentialResult.Failure -> PinResetResult.Failure(lockedNotesDeleted = true, cleared.cause)
        }
    }
}
