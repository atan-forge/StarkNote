package com.stark.note.domain.export

import com.stark.note.domain.lock.NoteBodyCrypto
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.domain.note.repository.ImportedItemIdConflictException
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString

class ImportBackupUseCase(
    private val repository: NoteRepository,
    private val noteBodyCrypto: NoteBodyCrypto
) {
    suspend fun `import`(encryptedText: String, password: CharArray): ImportResult {
        if (encryptedText.isBlank()) {
            return ImportResult.Failure(
                reason = ImportFailureReason.EMPTY_FILE,
                message = "Backup file is empty."
            )
        }
        if (password.isEmpty()) {
            return ImportResult.Failure(
                reason = ImportFailureReason.EMPTY_PASSWORD,
                message = "Backup password is required."
            )
        }

        val envelope = decodeEnvelope(encryptedText) ?: return wrongPasswordOrCorrupt()
        val plaintextPayload = try {
            decryptPayload(envelope, password) ?: return wrongPasswordOrCorrupt()
        } catch (e: UnsupportedBackupEnvelopeException) {
            return unsupportedVersion(e)
        } catch (e: UnsupportedBackupFormatException) {
            return unsupportedVersion(e)
        }
        val payload = decodePayload(plaintextPayload) ?: return wrongPasswordOrCorrupt()

        return runCatching {
            validatePayloadShape(payload)
            val notes = payload.notes.map { it.toNote() }
            val noteTypesById = notes.associate { it.id to it.noteType }
            val items = payload.items.map { it.toListItem(noteTypesById) }
            repository.replaceImportedData(notes, items)
            notes.size to items.size
        }.fold(
            onSuccess = { (noteCount, itemCount) -> ImportResult.Success(noteCount = noteCount, itemCount = itemCount) },
            onFailure = { e ->
                when (e) {
                    is UnsupportedBackupEnvelopeException,
                    is UnsupportedBackupVersionException -> ImportResult.Failure(
                        reason = ImportFailureReason.UNSUPPORTED_VERSION,
                        message = "This backup cannot be opened by this version of StarkNote.",
                        cause = e
                    )
                    is EmptyImportBackupException -> ImportResult.Failure(
                        reason = ImportFailureReason.EMPTY_BACKUP,
                        message = "There are no notes in this backup.",
                        cause = e
                    )
                    is ImportedLockedChecklistException -> ImportResult.Failure(
                        reason = ImportFailureReason.LOCKED_CHECKLIST_UNSUPPORTED,
                        message = "Locked checklists are not supported in backups yet.",
                        cause = e
                    )
                    is InvalidBackupContentException,
                    is ImportedItemIdConflictException -> ImportResult.Failure(
                        reason = ImportFailureReason.INVALID_CONTENT,
                        message = "Backup content is invalid.",
                        cause = e
                    )
                    else -> ImportResult.Failure(
                        reason = ImportFailureReason.UNKNOWN,
                        message = "Import failed. Please try again.",
                        cause = e
                    )
                }
            }
        )
    }

    private fun decodeEnvelope(encryptedText: String): BackupEnvelope? {
        return runCatching {
            backupJson.decodeFromString<BackupEnvelope>(encryptedText)
        }.getOrNull()
    }

    private fun decryptPayload(envelope: BackupEnvelope, password: CharArray): String? {
        return runCatching {
            SecureBackupCipher.decrypt(envelope, password)
        }.getOrElse { e ->
            if (e is UnsupportedBackupEnvelopeException || e is UnsupportedBackupFormatException) {
                throw e
            }
            null
        }
    }

    private fun decodePayload(plaintextPayload: String): BackupPayload? {
        return runCatching {
            backupJson.decodeFromString<BackupPayload>(plaintextPayload)
        }.getOrNull()
    }

    private fun wrongPasswordOrCorrupt(): ImportResult.Failure {
        return ImportResult.Failure(
            reason = ImportFailureReason.WRONG_PASSWORD_OR_CORRUPT_FILE,
            message = "Wrong password or corrupt backup file."
        )
    }

    private fun unsupportedVersion(cause: Throwable): ImportResult.Failure {
        return ImportResult.Failure(
            reason = ImportFailureReason.UNSUPPORTED_VERSION,
            message = "This backup cannot be opened by this version of StarkNote.",
            cause = cause
        )
    }

    private fun validatePayloadShape(payload: BackupPayload) {
        if (payload.version != 1) {
            throw UnsupportedBackupVersionException()
        }
        parseInstant(payload.exportedAt)
        if (payload.notes.isEmpty()) {
            throw EmptyImportBackupException()
        }
        if (payload.notes.any { it.id.isBlank() || it.createdAt.isBlank() || it.updatedAt.isBlank() }) {
            throw InvalidBackupContentException()
        }
        if (payload.items.any {
                it.id.isBlank() ||
                    it.noteId.isBlank() ||
                    it.content.isBlank() ||
                    it.createdAt.isBlank() ||
                    it.position < 0
            }) {
            throw InvalidBackupContentException()
        }
        if (payload.notes.map { it.id }.distinct().size != payload.notes.size) {
            throw InvalidBackupContentException()
        }
        if (payload.items.map { it.id }.distinct().size != payload.items.size) {
            throw InvalidBackupContentException()
        }
        if (payload.notes.any { it.type == NoteType.LIST.name && it.isLocked }) {
            throw ImportedLockedChecklistException()
        }
    }

    private fun BackupNoteData.toNote(): Note {
        val noteType = NoteType.entries.firstOrNull { it.name == type }
            ?: throw InvalidBackupContentException()
        val plainNote = Note(
            id = id,
            title = title,
            body = body,
            noteType = noteType,
            isLocked = false,
            bodyIv = null,
            createdAt = parseInstant(createdAt),
            updatedAt = parseInstant(updatedAt)
        )
        return if (isLocked) {
            noteBodyCrypto.encryptNote(plainNote, body)
        } else {
            plainNote
        }
    }

    private fun BackupItemData.toListItem(noteTypesById: Map<String, NoteType>): ListItem {
        val noteType = noteTypesById[noteId] ?: throw InvalidBackupContentException()
        if (noteType != NoteType.LIST) {
            throw InvalidBackupContentException()
        }
        return ListItem(
            id = id,
            noteId = noteId,
            content = content,
            isChecked = isChecked,
            position = position,
            createdAt = parseInstant(createdAt)
        )
    }

    private fun parseInstant(value: String): Instant {
        return runCatching {
            Instant.parse(value)
        }.getOrElse {
            throw InvalidBackupContentException()
        }
    }
}

private class UnsupportedBackupVersionException : IllegalStateException()

private class ImportedLockedChecklistException : IllegalStateException()

private class InvalidBackupContentException : IllegalStateException()

private class EmptyImportBackupException : IllegalStateException()
