package com.stark.note.domain.export

import com.stark.note.domain.lock.NoteBodyCrypto
import com.stark.note.domain.lock.DecryptNoteResult
import com.stark.note.domain.note.NoteType
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportBackupUseCase(
    private val repository: NoteRepository,
    private val noteBodyCrypto: NoteBodyCrypto
) {
    suspend fun export(password: CharArray): ExportResult {
        if (password.isEmpty()) {
            return ExportResult.Failure(
                reason = ExportFailureReason.EMPTY_PASSWORD,
                message = "Backup password is required."
            )
        }
        if (password.size < MIN_BACKUP_PASSWORD_LENGTH) {
            return ExportResult.Failure(
                reason = ExportFailureReason.PASSWORD_TOO_SHORT,
                message = "Use at least 8 characters for backup password."
            )
        }

        return runCatching {
            val notes = repository.getAllNotes().first()
            if (notes.isEmpty()) {
                throw EmptyBackupException()
            }
            if (notes.any { it.noteType == NoteType.LIST && it.isLocked }) {
                throw LockedChecklistBackupException()
            }

            val noteDataList = notes.map { note ->
                val exportBody = if (note.isLocked) {
                    when (val decrypted = noteBodyCrypto.decryptNote(note)) {
                        is DecryptNoteResult.Success -> decrypted.note.body
                        is DecryptNoteResult.Failure -> throw LockedNoteDecryptionException()
                    }
                } else {
                    note.body
                }

                BackupNoteData(
                    id = note.id,
                    title = note.title,
                    body = exportBody,
                    type = note.noteType.name,
                    isLocked = note.isLocked,
                    createdAt = note.createdAt.toString(),
                    updatedAt = note.updatedAt.toString()
                )
            }

            val itemDataList = mutableListOf<BackupItemData>()
            notes.filter { it.noteType == NoteType.LIST }.forEach { note ->
                repository.getItemsForNote(note.id).first().forEach { item ->
                    itemDataList += BackupItemData(
                        id = item.id,
                        noteId = item.noteId,
                        content = item.content,
                        isChecked = item.isChecked,
                        position = item.position,
                        createdAt = item.createdAt.toString()
                    )
                }
            }

            val payload = BackupPayload(
                version = 1,
                exportedAt = Clock.System.now().toString(),
                notes = noteDataList,
                items = itemDataList
            )
            val plaintextPayload = backupJson.encodeToString(payload)
            val envelope = SecureBackupCipher.encrypt(plaintextPayload, password)
            backupJson.encodeToString(envelope)
        }.fold(
            onSuccess = { encryptedText -> ExportResult.Success(encryptedText) },
            onFailure = { e ->
                when (e) {
                    is EmptyBackupException -> ExportResult.Failure(
                        reason = ExportFailureReason.NO_NOTES,
                        message = "No notes to export.",
                        cause = e
                    )
                    is LockedChecklistBackupException -> ExportResult.Failure(
                        reason = ExportFailureReason.LOCKED_CHECKLIST_UNSUPPORTED,
                        message = "Locked checklists are not supported in backups yet.",
                        cause = e
                    )
                    is LockedNoteDecryptionException -> ExportResult.Failure(
                        reason = ExportFailureReason.LOCKED_NOTE_DECRYPTION_FAILED,
                        message = "A locked note could not be decrypted. Unlock notes and try again.",
                        cause = e
                    )
                    else -> ExportResult.Failure(
                        reason = ExportFailureReason.UNKNOWN,
                        message = "Export failed. Please try again.",
                        cause = e
                    )
                }
            }
        )
    }
}

internal val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private class LockedChecklistBackupException : IllegalStateException()

private class LockedNoteDecryptionException : IllegalStateException()

private class EmptyBackupException : IllegalStateException()

internal const val MIN_BACKUP_PASSWORD_LENGTH = 8
