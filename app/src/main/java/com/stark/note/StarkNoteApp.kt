package com.stark.note

import android.app.Application
import com.stark.note.data.crypto.CryptoService
import com.stark.note.data.db.NoteDatabase
import com.stark.note.data.repository.RoomNoteRepository
import com.stark.note.domain.lock.LockUseCase
import com.stark.note.domain.lock.PinResetCoordinator
import com.stark.note.domain.lock.SecuritySession
import com.stark.note.domain.note.repository.NoteRepository
import com.stark.note.platform.storage.SecurePreferences

class StarkNoteApp : Application() {
    val noteRepository: NoteRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val database = NoteDatabase.getDatabase(this)
        RoomNoteRepository(
            database = database,
            noteDao = database.noteDao(),
            listItemDao = database.listItemDao()
        )
    }

    val securePreferences: SecurePreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecurePreferences(this)
    }

    val securitySession: SecuritySession by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecuritySession()
    }

    val lockUseCase: LockUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LockUseCase(CryptoService())
    }

    val pinResetCoordinator: PinResetCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PinResetCoordinator(noteRepository, securePreferences)
    }
}
