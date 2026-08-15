package com.stark.note

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.fragment.app.FragmentActivity
import com.stark.note.ui.navigation.StarkNavigation
import com.stark.note.ui.theme.StarkNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : FragmentActivity() {
    private val backupFileBridge: BackupFileBridgeViewModel by viewModels()

    fun backupFileBridge(): BackupFileBridgeViewModel = backupFileBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            StarkNoteTheme {
                StarkNavigation()
            }
        }
    }

    fun openBackupImportPicker(): Boolean {
        return runCatching {
            backupFileBridge.markImportPending()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            }
            startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
        }.onFailure {
            backupFileBridge.clearPendingImport()
        }.isSuccess
    }

    suspend fun openBackupExportPicker(fileName: String, encryptedText: String): Boolean {
        return try {
            if (!backupFileBridge.prepareExport(encryptedText)) return false
            backupFileBridge.markExportPending()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
            true
        } catch (e: ActivityNotFoundException) {
            backupFileBridge.clearPendingExport()
            false
        } catch (e: IllegalStateException) {
            backupFileBridge.clearPendingExport()
            false
        } catch (e: Exception) {
            backupFileBridge.clearPendingExport()
            false
        }
    }

    @Deprecated("Deprecated Android callback required for low request code compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_IMPORT_BACKUP -> {
                backupFileBridge.completeImport(data?.data.takeIf { resultCode == Activity.RESULT_OK })
            }
            REQUEST_EXPORT_BACKUP -> {
                backupFileBridge.completeExport(data?.data.takeIf { resultCode == Activity.RESULT_OK })
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStop() {
        super.onStop()
        (application as StarkNoteApp).securitySession.lock()
    }

    companion object {
        private const val REQUEST_IMPORT_BACKUP = 4100
        private const val REQUEST_EXPORT_BACKUP = 4101
    }
}

class BackupFileBridgeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val _importUri = MutableStateFlow<Uri?>(null)
    val importUri: StateFlow<Uri?> = _importUri

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri

    private var importPending = savedStateHandle[KEY_IMPORT_PENDING] ?: false
    private var exportPending = savedStateHandle[KEY_EXPORT_PENDING] ?: false

    private val pendingExportFile: File
        get() = File(getApplication<Application>().filesDir, PENDING_EXPORT_FILE)

    init {
        if (!exportPending) {
            pendingExportFile.delete()
        }
    }

    fun markImportPending() {
        importPending = true
        savedStateHandle[KEY_IMPORT_PENDING] = true
        _importUri.value = null
    }

    fun completeImport(uri: Uri?) {
        if (importPending) {
            _importUri.value = uri
        }
        importPending = false
        savedStateHandle[KEY_IMPORT_PENDING] = false
    }

    fun consumeImportUri() {
        _importUri.value = null
    }

    fun clearPendingImport() {
        importPending = false
        savedStateHandle[KEY_IMPORT_PENDING] = false
        _importUri.value = null
    }

    suspend fun prepareExport(encryptedText: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val temporaryFile = File(pendingExportFile.parentFile, "$PENDING_EXPORT_FILE.tmp")
                temporaryFile.writeText(encryptedText, Charsets.UTF_8)
                if (pendingExportFile.exists()) {
                    pendingExportFile.delete()
                }
                check(temporaryFile.renameTo(pendingExportFile))
                true
            }.getOrElse {
                false
            }
        }
    }

    fun markExportPending() {
        exportPending = true
        savedStateHandle[KEY_EXPORT_PENDING] = true
        _exportUri.value = null
    }

    fun completeExport(uri: Uri?) {
        if (exportPending) {
            _exportUri.value = uri
        }
        if (uri == null) {
            clearPendingExport()
        }
        exportPending = false
        savedStateHandle[KEY_EXPORT_PENDING] = false
    }

    suspend fun readPendingExport(uri: Uri): Pair<Uri, String>? {
        val encryptedText = withContext(Dispatchers.IO) {
            runCatching {
                if (!pendingExportFile.exists()) return@runCatching null
                pendingExportFile.readText(Charsets.UTF_8)
            }.getOrNull()
        } ?: return null
        return uri to encryptedText
    }

    fun clearPendingExport() {
        pendingExportFile.delete()
        _exportUri.value = null
        exportPending = false
        savedStateHandle[KEY_EXPORT_PENDING] = false
    }

    companion object {
        private const val KEY_IMPORT_PENDING = "backup_import_pending"
        private const val KEY_EXPORT_PENDING = "backup_export_pending"
        private const val PENDING_EXPORT_FILE = "pending_backup_export.json"
    }
}
