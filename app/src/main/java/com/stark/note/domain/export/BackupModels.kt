package com.stark.note.domain.export

import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val version: Int,
    val kdf: BackupKdf,
    val salt: String,
    val iv: String,
    val ciphertext: String
)

@Serializable
data class BackupKdf(
    val algorithm: String,
    val iterations: Int,
    val keyLengthBits: Int
)

@Serializable
internal data class BackupPayload(
    val version: Int,
    val exportedAt: String,
    val notes: List<BackupNoteData>,
    val items: List<BackupItemData>
)

@Serializable
internal data class BackupNoteData(
    val id: String,
    val title: String?,
    val body: String,
    val type: String,
    val isLocked: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class BackupItemData(
    val id: String,
    val noteId: String,
    val content: String,
    val isChecked: Boolean,
    val position: Int,
    val createdAt: String
)

sealed class ExportResult {
    data class Success(val encryptedText: String) : ExportResult()
    data class Failure(
        val reason: ExportFailureReason,
        val message: String,
        val cause: Throwable? = null
    ) : ExportResult()
}

sealed class ImportResult {
    data class Success(val noteCount: Int, val itemCount: Int) : ImportResult()
    data class Failure(
        val reason: ImportFailureReason,
        val message: String,
        val cause: Throwable? = null
    ) : ImportResult()
}

enum class ExportFailureReason {
    EMPTY_PASSWORD,
    PASSWORD_TOO_SHORT,
    NO_NOTES,
    LOCKED_CHECKLIST_UNSUPPORTED,
    LOCKED_NOTE_DECRYPTION_FAILED,
    UNKNOWN
}

enum class ImportFailureReason {
    EMPTY_FILE,
    EMPTY_PASSWORD,
    EMPTY_BACKUP,
    WRONG_PASSWORD_OR_CORRUPT_FILE,
    UNSUPPORTED_VERSION,
    LOCKED_CHECKLIST_UNSUPPORTED,
    INVALID_CONTENT,
    UNKNOWN
}
