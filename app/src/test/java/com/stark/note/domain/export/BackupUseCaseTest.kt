package com.stark.note.domain.export

import com.stark.note.domain.lock.NoteBodyCrypto
import com.stark.note.domain.lock.DecryptNoteResult
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString

class BackupUseCaseTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun encryptedBackupRoundTripPreservesNotesAndChecklistItems() = runBlocking {
        val sourceRepository = FakeNoteRepository(
            notes = listOf(
                note(id = "normal", body = "plain body"),
                note(id = "locked", body = "locked:secret body", isLocked = true, bodyIv = "iv"),
                note(id = "list", body = "", noteType = NoteType.LIST)
            ),
            items = listOf(item(id = "item-1", noteId = "list", content = "milk"))
        )
        val crypto = FakeNoteBodyCrypto()
        val exportResult = ExportBackupUseCase(sourceRepository, crypto).export("backup-pass".toCharArray())

        assertTrue(exportResult is ExportResult.Success)

        val targetRepository = FakeNoteRepository()
        val importResult = ImportBackupUseCase(targetRepository, crypto).`import`(
            (exportResult as ExportResult.Success).encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Success)
        assertEquals(3, targetRepository.notes.size)
        assertEquals("plain body", targetRepository.notes.first { it.id == "normal" }.body)
        val importedLocked = targetRepository.notes.first { it.id == "locked" }
        assertTrue(importedLocked.isLocked)
        val decryptedLocked = crypto.decryptNote(importedLocked)
        assertTrue(decryptedLocked is DecryptNoteResult.Success)
        assertEquals("secret body", (decryptedLocked as DecryptNoteResult.Success).note.body)
        assertEquals(listOf("milk"), targetRepository.itemsFor("list").map { it.content })
    }

    @Test
    fun shortBackupPasswordIsRejectedOnExport() = runBlocking {
        val repository = FakeNoteRepository(notes = listOf(note(id = "normal", body = "body")))

        val exportResult = ExportBackupUseCase(repository, FakeNoteBodyCrypto()).export("short".toCharArray())

        assertTrue(exportResult is ExportResult.Failure)
        assertEquals(
            ExportFailureReason.PASSWORD_TOO_SHORT,
            (exportResult as ExportResult.Failure).reason
        )
    }

    @Test
    fun emptyNoteDatabaseIsRejectedOnExport() = runBlocking {
        val repository = FakeNoteRepository()

        val exportResult = ExportBackupUseCase(repository, FakeNoteBodyCrypto()).export("backup-pass".toCharArray())

        assertTrue(exportResult is ExportResult.Failure)
        assertEquals(
            ExportFailureReason.NO_NOTES,
            (exportResult as ExportResult.Failure).reason
        )
    }

    @Test
    fun wrongBackupPasswordFailsWithoutMutatingDatabase() = runBlocking {
        val sourceRepository = FakeNoteRepository(notes = listOf(note(id = "exported", body = "body")))
        val crypto = FakeNoteBodyCrypto()
        val exportResult = ExportBackupUseCase(sourceRepository, crypto).export("right-pass".toCharArray())
                as ExportResult.Success
        val targetRepository = FakeNoteRepository(notes = listOf(note(id = "existing", body = "keep")))

        val importResult = ImportBackupUseCase(targetRepository, crypto).`import`(
            exportResult.encryptedText,
            "wrong-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(
            ImportFailureReason.WRONG_PASSWORD_OR_CORRUPT_FILE,
            (importResult as ImportResult.Failure).reason
        )
        assertEquals(0, targetRepository.replaceImportedDataCalls)
        assertEquals(listOf("existing"), targetRepository.notes.map { it.id })
    }

    @Test
    fun corruptBackupFailsWithoutMutatingDatabase() = runBlocking {
        val targetRepository = FakeNoteRepository(notes = listOf(note(id = "existing", body = "keep")))

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            "{not-json",
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(
            ImportFailureReason.WRONG_PASSWORD_OR_CORRUPT_FILE,
            (importResult as ImportResult.Failure).reason
        )
        assertEquals(0, targetRepository.replaceImportedDataCalls)
        assertEquals(listOf("existing"), targetRepository.notes.map { it.id })
    }

    @Test
    fun emptyBackupPayloadFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(notes = emptyList()),
            expectedReason = ImportFailureReason.EMPTY_BACKUP
        )
    }

    @Test
    fun duplicateNoteIdsFailWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(
                    backupNote(id = "same", body = "one"),
                    backupNote(id = "same", body = "two")
                )
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun duplicateItemIdsFailWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(backupNote(id = "list", type = NoteType.LIST.name)),
                items = listOf(
                    backupItem(id = "same", noteId = "list"),
                    backupItem(id = "same", noteId = "list")
                )
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun itemPointingToMissingNoteFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(backupNote(id = "list", type = NoteType.LIST.name)),
                items = listOf(backupItem(id = "item", noteId = "missing"))
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun itemPointingToNormalNoteFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(backupNote(id = "normal", type = NoteType.NORMAL.name)),
                items = listOf(backupItem(id = "item", noteId = "normal"))
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun negativeChecklistPositionFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(backupNote(id = "list", type = NoteType.LIST.name)),
                items = listOf(backupItem(id = "item", noteId = "list", position = -1))
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun unknownNoteTypeFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(notes = listOf(backupNote(id = "bad", type = "UNKNOWN"))),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun invalidTimestampFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(notes = listOf(backupNote(id = "bad", createdAt = "not-a-date"))),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun invalidExportTimestampFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                exportedAt = "not-a-date",
                notes = listOf(backupNote(id = "bad"))
            ),
            expectedReason = ImportFailureReason.INVALID_CONTENT
        )
    }

    @Test
    fun unsupportedBackupKdfReturnsUnsupportedVersion() = runBlocking {
        val envelope = SecureBackupCipher.encrypt(
            backupJson.encodeToString(backupPayload(notes = listOf(backupNote(id = "note")))),
            "backup-pass".toCharArray()
        ).copy(kdf = BackupKdf("unsupported", 200_000, 256))
        val targetRepository = FakeNoteRepository(notes = listOf(note(id = "existing", body = "keep")))

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            backupJson.encodeToString(envelope),
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(ImportFailureReason.UNSUPPORTED_VERSION, (importResult as ImportResult.Failure).reason)
        assertEquals(0, targetRepository.replaceImportedDataCalls)
    }

    @Test
    fun malformedBackupEnvelopeFailsWithoutMutatingDatabase() = runBlocking {
        val envelope = SecureBackupCipher.encrypt(
            backupJson.encodeToString(backupPayload(notes = listOf(backupNote(id = "note")))),
            "backup-pass".toCharArray()
        ).copy(ciphertext = "not-base64")
        val targetRepository = FakeNoteRepository(notes = listOf(note(id = "existing", body = "keep")))

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            backupJson.encodeToString(envelope),
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(ImportFailureReason.WRONG_PASSWORD_OR_CORRUPT_FILE, (importResult as ImportResult.Failure).reason)
        assertEquals(0, targetRepository.replaceImportedDataCalls)
    }

    @Test
    fun lockedChecklistImportFailsWithoutMutatingDatabase() = runBlocking {
        assertInvalidPayloadFails(
            payload = backupPayload(
                notes = listOf(backupNote(id = "locked-list", type = NoteType.LIST.name, isLocked = true))
            ),
            expectedReason = ImportFailureReason.LOCKED_CHECKLIST_UNSUPPORTED
        )
    }

    @Test
    fun unsupportedBackupVersionReturnsClearFailure() = runBlocking {
        val envelope = SecureBackupCipher.encrypt(
            backupJson.encodeToString(
                BackupPayload(
                    version = 2,
                    exportedAt = now.toString(),
                    notes = emptyList(),
                    items = emptyList()
                )
            ),
            "backup-pass".toCharArray()
        )
        val encryptedText = backupJson.encodeToString(envelope)
        val targetRepository = FakeNoteRepository()

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(
            ImportFailureReason.UNSUPPORTED_VERSION,
            (importResult as ImportResult.Failure).reason
        )
        assertEquals(0, targetRepository.replaceImportedDataCalls)
    }

    @Test
    fun unsupportedEnvelopeVersionReturnsClearFailure() = runBlocking {
        val envelope = SecureBackupCipher.encrypt(
            backupJson.encodeToString(
                BackupPayload(
                    version = 1,
                    exportedAt = now.toString(),
                    notes = emptyList(),
                    items = emptyList()
                )
            ),
            "backup-pass".toCharArray()
        ).copy(version = 2)
        val encryptedText = backupJson.encodeToString(envelope)
        val targetRepository = FakeNoteRepository()

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(
            ImportFailureReason.UNSUPPORTED_VERSION,
            (importResult as ImportResult.Failure).reason
        )
        assertEquals(0, targetRepository.replaceImportedDataCalls)
    }

    @Test
    fun sameIdChecklistImportReplacesExistingItemsCleanly() = runBlocking {
        val sourceRepository = FakeNoteRepository(
            notes = listOf(note(id = "list", body = "", noteType = NoteType.LIST)),
            items = listOf(item(id = "new-item", noteId = "list", content = "new"))
        )
        val crypto = FakeNoteBodyCrypto()
        val exportResult = ExportBackupUseCase(sourceRepository, crypto).export("backup-pass".toCharArray())
                as ExportResult.Success
        val targetRepository = FakeNoteRepository(
            notes = listOf(note(id = "list", title = "old", body = "", noteType = NoteType.LIST)),
            items = listOf(item(id = "old-item", noteId = "list", content = "old"))
        )

        val importResult = ImportBackupUseCase(targetRepository, crypto).`import`(
            exportResult.encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Success)
        assertEquals("new", targetRepository.itemsFor("list").single().content)
        assertEquals("list", targetRepository.notes.single().id)
    }

    @Test
    fun replacingChecklistWithNormalNoteRemovesOldItems() = runBlocking {
        val sourceRepository = FakeNoteRepository(
            notes = listOf(note(id = "shared", body = "normal", noteType = NoteType.NORMAL))
        )
        val crypto = FakeNoteBodyCrypto()
        val exportResult = ExportBackupUseCase(sourceRepository, crypto).export("backup-pass".toCharArray())
            as ExportResult.Success
        val targetRepository = FakeNoteRepository(
            notes = listOf(note(id = "shared", body = "", noteType = NoteType.LIST)),
            items = listOf(item(id = "old-item", noteId = "shared", content = "stale"))
        )

        val importResult = ImportBackupUseCase(targetRepository, crypto).`import`(
            exportResult.encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Success)
        assertEquals(NoteType.NORMAL, targetRepository.notes.single().noteType)
        assertEquals(emptyList<ListItem>(), targetRepository.itemsFor("shared"))
    }

    @Test
    fun conflictingItemIdDoesNotReplaceUnrelatedChecklistItem() = runBlocking {
        val sourceRepository = FakeNoteRepository(
            notes = listOf(note(id = "imported-list", body = "", noteType = NoteType.LIST)),
            items = listOf(item(id = "shared-item", noteId = "imported-list", content = "imported"))
        )
        val crypto = FakeNoteBodyCrypto()
        val exportResult = ExportBackupUseCase(sourceRepository, crypto).export("backup-pass".toCharArray())
            as ExportResult.Success
        val targetRepository = FakeNoteRepository(
            notes = listOf(
                note(id = "existing-list", body = "", noteType = NoteType.LIST),
                note(id = "keep", body = "keep")
            ),
            items = listOf(item(id = "shared-item", noteId = "existing-list", content = "keep"))
        )

        val importResult = ImportBackupUseCase(targetRepository, crypto).`import`(
            exportResult.encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(ImportFailureReason.INVALID_CONTENT, (importResult as ImportResult.Failure).reason)
        assertEquals(listOf("keep"), targetRepository.itemsFor("existing-list").map { it.content })
        assertEquals(listOf("existing-list", "keep"), targetRepository.notes.map { it.id })
    }

    @Test
    fun lockedChecklistExportIsRejected() = runBlocking {
        val repository = FakeNoteRepository(
            notes = listOf(note(id = "locked-list", body = "", noteType = NoteType.LIST, isLocked = true))
        )

        val exportResult = ExportBackupUseCase(repository, FakeNoteBodyCrypto())
            .export("backup-pass".toCharArray())

        assertTrue(exportResult is ExportResult.Failure)
        assertEquals(
            ExportFailureReason.LOCKED_CHECKLIST_UNSUPPORTED,
            (exportResult as ExportResult.Failure).reason
        )
    }

    @Test
    fun lockedNoteDecryptFailureRejectsExport() = runBlocking {
        val repository = FakeNoteRepository(
            notes = listOf(note(id = "locked", body = "locked:secret", isLocked = true, bodyIv = "iv"))
        )

        val exportResult = ExportBackupUseCase(repository, FakeNoteBodyCrypto(failingDecryptIds = setOf("locked")))
            .export("backup-pass".toCharArray())

        assertTrue(exportResult is ExportResult.Failure)
        assertEquals(
            ExportFailureReason.LOCKED_NOTE_DECRYPTION_FAILED,
            (exportResult as ExportResult.Failure).reason
        )
    }

    @Test
    fun deleteNoteAndItemsRemovesChecklistItems() = runBlocking {
        val listNote = note(id = "list", body = "", noteType = NoteType.LIST)
        val repository = FakeNoteRepository(
            notes = listOf(listNote),
            items = listOf(item(id = "item", noteId = "list", content = "old"))
        )

        repository.deleteNoteAndItems(listNote)

        assertEquals(emptyList<Note>(), repository.notes)
        assertEquals(emptyList<ListItem>(), repository.itemsFor("list"))
    }

    @Test
    fun deleteLockedNotesRemovesOnlyLockedNotes() = runBlocking {
        val repository = FakeNoteRepository(
            notes = listOf(
                note(id = "normal", body = "keep"),
                note(id = "checklist", body = "", noteType = NoteType.LIST),
                note(id = "locked", body = "secret", isLocked = true)
            ),
            items = listOf(item(id = "item", noteId = "checklist", content = "keep"))
        )

        repository.deleteLockedNotes()

        assertEquals(listOf("normal", "checklist"), repository.notes.map { it.id })
        assertEquals(listOf("keep"), repository.itemsFor("checklist").map { it.content })
    }

    @Test
    fun deleteLockedNotesRemovesItemsForLockedListsOnly() = runBlocking {
        val repository = FakeNoteRepository(
            notes = listOf(
                note(id = "open-list", body = "", noteType = NoteType.LIST),
                note(id = "locked-list", body = "", noteType = NoteType.LIST, isLocked = true)
            ),
            items = listOf(
                item(id = "open-item", noteId = "open-list", content = "keep"),
                item(id = "locked-item", noteId = "locked-list", content = "delete")
            )
        )

        repository.deleteLockedNotes()

        assertEquals(listOf("open-list"), repository.notes.map { it.id })
        assertEquals(listOf("keep"), repository.itemsFor("open-list").map { it.content })
        assertEquals(emptyList<ListItem>(), repository.itemsFor("locked-list"))
    }

    private fun note(
        id: String,
        title: String? = id,
        body: String,
        noteType: NoteType = NoteType.NORMAL,
        isLocked: Boolean = false,
        bodyIv: String? = null
    ) = Note(
        id = id,
        title = title,
        body = body,
        noteType = noteType,
        isLocked = isLocked,
        bodyIv = bodyIv,
        createdAt = now,
        updatedAt = now
    )

    private fun item(
        id: String,
        noteId: String,
        content: String,
        position: Int = 0
    ) = ListItem(
        id = id,
        noteId = noteId,
        content = content,
        isChecked = false,
        position = position,
        createdAt = now
    )

    private suspend fun assertInvalidPayloadFails(
        payload: BackupPayload,
        expectedReason: ImportFailureReason
    ) {
        val encryptedText = encryptedPayload(payload)
        val targetRepository = FakeNoteRepository(notes = listOf(note(id = "existing", body = "keep")))

        val importResult = ImportBackupUseCase(targetRepository, FakeNoteBodyCrypto()).`import`(
            encryptedText,
            "backup-pass".toCharArray()
        )

        assertTrue(importResult is ImportResult.Failure)
        assertEquals(expectedReason, (importResult as ImportResult.Failure).reason)
        assertEquals(0, targetRepository.replaceImportedDataCalls)
        assertEquals(listOf("existing"), targetRepository.notes.map { it.id })
    }

    private fun encryptedPayload(payload: BackupPayload): String {
        val envelope = SecureBackupCipher.encrypt(
            backupJson.encodeToString(payload),
            "backup-pass".toCharArray()
        )
        return backupJson.encodeToString(envelope)
    }

    private fun backupPayload(
        version: Int = 1,
        exportedAt: String = now.toString(),
        notes: List<BackupNoteData>,
        items: List<BackupItemData> = emptyList()
    ) = BackupPayload(
        version = version,
        exportedAt = exportedAt,
        notes = notes,
        items = items
    )

    private fun backupNote(
        id: String,
        title: String? = id,
        body: String = "",
        type: String = NoteType.NORMAL.name,
        isLocked: Boolean = false,
        createdAt: String = now.toString(),
        updatedAt: String = now.toString()
    ) = BackupNoteData(
        id = id,
        title = title,
        body = body,
        type = type,
        isLocked = isLocked,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun backupItem(
        id: String,
        noteId: String,
        content: String = "item",
        position: Int = 0,
        createdAt: String = now.toString()
    ) = BackupItemData(
        id = id,
        noteId = noteId,
        content = content,
        isChecked = false,
        position = position,
        createdAt = createdAt
    )
}

private class FakeNoteBodyCrypto(
    private val failingDecryptIds: Set<String> = emptySet()
) : NoteBodyCrypto {
    override fun encryptNote(note: Note, plaintextBody: String): Note {
        return note.copy(body = "locked:$plaintextBody", isLocked = true, bodyIv = "fake-iv")
    }

    override fun decryptNote(note: Note): DecryptNoteResult {
        if (note.id in failingDecryptIds) {
            return DecryptNoteResult.Failure(note)
        }
        return if (note.isLocked && note.body.startsWith("locked:")) {
            DecryptNoteResult.Success(note.copy(body = note.body.removePrefix("locked:"), isLocked = false))
        } else {
            DecryptNoteResult.Success(note)
        }
    }
}

private class FakeNoteRepository(
    notes: List<Note> = emptyList(),
    items: List<ListItem> = emptyList()
) : NoteRepository {
    private val notesFlow = MutableStateFlow(notes)
    private val itemMap = items.groupBy { it.noteId }
        .mapValues { (_, value) -> value.toMutableList() }
        .toMutableMap()

    val notes: List<Note>
        get() = notesFlow.value

    var replaceImportedDataCalls = 0
        private set

    override fun getAllNotes(): Flow<List<Note>> = notesFlow

    override suspend fun getNoteById(id: String): Note? = notesFlow.value.firstOrNull { it.id == id }

    override suspend fun insertNote(note: Note) {
        notesFlow.value = notesFlow.value.filterNot { it.id == note.id } + note
    }

    override suspend fun updateNote(note: Note) = insertNote(note)

    override suspend fun deleteNoteAndItems(note: Note) {
        notesFlow.value = notesFlow.value.filterNot { it.id == note.id }
        itemMap.remove(note.id)
    }

    override suspend fun deleteLockedNotes() {
        notes.filter { it.isLocked }.forEach { deleteNoteAndItems(it) }
    }

    override fun getItemsForNote(noteId: String): Flow<List<ListItem>> = flowOf(itemsFor(noteId))

    override suspend fun insertItems(items: List<ListItem>) {
        items.forEach { item ->
            val current = itemMap.getOrPut(item.noteId) { mutableListOf() }
            current.removeAll { it.id == item.id }
            current += item
        }
    }

    override suspend fun addChecklistItem(note: Note, item: ListItem) {
        insertNote(note)
        insertItems(listOf(item))
    }

    override suspend fun updateChecklistItem(note: Note, item: ListItem) {
        insertNote(note)
        insertItems(listOf(item))
    }

    override suspend fun deleteChecklistItem(note: Note, item: ListItem) {
        insertNote(note)
        itemMap[item.noteId]?.removeAll { it.id == item.id }
    }

    override suspend fun clearCheckedChecklistItems(note: Note) {
        insertNote(note)
        itemMap[note.id]?.removeAll { it.isChecked }
    }

    override suspend fun replaceImportedData(notes: List<Note>, items: List<ListItem>) {
        replaceImportedDataCalls++
        val importedNoteIds = notes.map { it.id }
        val conflicts = itemMap.values.flatten().filter { existing ->
            items.any { it.id == existing.id } && existing.noteId !in importedNoteIds
        }
        if (conflicts.isNotEmpty()) {
            throw com.stark.note.domain.note.repository.ImportedItemIdConflictException()
        }
        importedNoteIds.forEach { itemMap.remove(it) }
        notes.forEach { insertNote(it) }
        insertItems(items)
    }

    fun itemsFor(noteId: String): List<ListItem> {
        return itemMap[noteId].orEmpty().sortedBy { it.position }
    }
}
