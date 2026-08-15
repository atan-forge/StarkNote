package com.stark.note.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stark.note.domain.export.ExportBackupUseCase
import com.stark.note.domain.export.ExportResult
import com.stark.note.domain.export.ImportBackupUseCase
import com.stark.note.domain.export.ImportResult
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class BackupDialogState {
    NONE,
    EXPORT_PASSWORD,
    IMPORT_CONFIRMATION,
    IMPORT_PASSWORD
}

data class BackupUiState(
    val dialog: BackupDialogState = BackupDialogState.NONE,
    val pendingImportText: String? = null,
    val errorMessage: String? = null,
    val inProgress: Boolean = false
)

class SettingsViewModel(
    private val repository: NoteRepository,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase
) : ViewModel() {
    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

    fun showExportPassword() {
        _backupUiState.value = BackupUiState(dialog = BackupDialogState.EXPORT_PASSWORD)
    }

    fun showImportConfirmation(encryptedText: String) {
        _backupUiState.value = BackupUiState(
            dialog = BackupDialogState.IMPORT_CONFIRMATION,
            pendingImportText = encryptedText
        )
    }

    fun showImportPassword() {
        _backupUiState.value = _backupUiState.value.copy(
            dialog = BackupDialogState.IMPORT_PASSWORD,
            errorMessage = null
        )
    }

    fun dismissBackupDialog() {
        _backupUiState.value = BackupUiState()
    }

    suspend fun hasLockedNotes(): Boolean = withContext(Dispatchers.IO) {
        repository.getAllNotes().first().any { it.isLocked }
    }

    suspend fun hasNotes(): Boolean = withContext(Dispatchers.IO) {
        repository.getAllNotes().first().isNotEmpty()
    }

    suspend fun exportBackup(password: CharArray): ExportResult {
        _backupUiState.value = _backupUiState.value.copy(inProgress = true, errorMessage = null)
        val result = withContext(Dispatchers.IO) { exportBackupUseCase.export(password) }
        _backupUiState.value = when (result) {
            is ExportResult.Success -> BackupUiState()
            is ExportResult.Failure -> _backupUiState.value.copy(
                inProgress = false,
                errorMessage = result.message
            )
        }
        return result
    }

    suspend fun importBackup(password: CharArray): ImportResult? {
        val encryptedText = _backupUiState.value.pendingImportText ?: return null
        _backupUiState.value = _backupUiState.value.copy(inProgress = true, errorMessage = null)
        val result = withContext(Dispatchers.IO) { importBackupUseCase.`import`(encryptedText, password) }
        _backupUiState.value = when (result) {
            is ImportResult.Success -> BackupUiState()
            is ImportResult.Failure -> _backupUiState.value.copy(
                inProgress = false,
                errorMessage = result.message
            )
        }
        return result
    }

    companion object {
        fun provideFactory(
            repository: NoteRepository,
            exportBackupUseCase: ExportBackupUseCase,
            importBackupUseCase: ImportBackupUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, exportBackupUseCase, importBackupUseCase) as T
            }
        }
    }
}
