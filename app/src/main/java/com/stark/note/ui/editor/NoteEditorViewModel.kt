package com.stark.note.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stark.note.R
import com.stark.note.domain.lock.DecryptNoteResult
import com.stark.note.domain.lock.LockUseCase
import com.stark.note.domain.lock.SecuritySession
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID

data class SearchMatch(
    val start: Int,
    val end: Int
)

internal object NoteSearch {
    fun findMatches(text: String, query: String): List<SearchMatch> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        val matches = mutableListOf<SearchMatch>()
        var startIndex = 0
        while (startIndex <= text.length - normalizedQuery.length) {
            val index = text.indexOf(normalizedQuery, startIndex, ignoreCase = true)
            if (index == -1) break
            matches += SearchMatch(index, index + normalizedQuery.length)
            startIndex = index + normalizedQuery.length
        }
        return matches
    }
}

sealed class EditorUiEvent {
    object SetupPinRequired : EditorUiEvent()
    object AuthRequired : EditorUiEvent()
    data class Message(val resourceId: Int) : EditorUiEvent()
}

class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val lockUseCase: LockUseCase,
    private val securitySession: SecuritySession,
    private val hasPin: () -> Boolean,
    private val noteId: String?
) : ViewModel() {
    private val currentNoteId = noteId ?: UUID.randomUUID().toString()
    private val saveMutex = Mutex()
    private var searchUpdateJob: Job? = null
    private var hasPersistedNote = false
    private var hasUnsavedChanges = false
    private var decryptInProgress = false
    private var isNewDraft = false
    private var hiddenLockedDraft: String? = null

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _loadError = MutableStateFlow<Int?>(null)
    val loadError: StateFlow<Int?> = _loadError.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _searchMatches = MutableStateFlow<List<SearchMatch>>(emptyList())
    val searchMatches: StateFlow<List<SearchMatch>> = _searchMatches.asStateFlow()

    private val _selectedSearchMatchIndex = MutableStateFlow(-1)
    val selectedSearchMatchIndex: StateFlow<Int> = _selectedSearchMatchIndex.asStateFlow()

    private val _decryptFailed = MutableStateFlow(false)
    val decryptFailed: StateFlow<Boolean> = _decryptFailed.asStateFlow()

    private val _contentLoading = MutableStateFlow(false)
    val contentLoading: StateFlow<Boolean> = _contentLoading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<EditorUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    val isUnlocked: StateFlow<Boolean> = securitySession.isUnlocked

    init {
        viewModelScope.launch {
            try {
                val existingNote = noteId?.let { repository.getNoteById(it) }
                if (existingNote != null) {
                    hasPersistedNote = true
                    existingNote.let { loadedNote ->
                        _note.value = loadedNote
                        _title.value = loadedNote.title ?: ""
                        if (!loadedNote.isLocked) {
                            _body.value = loadedNote.body
                        } else if (isUnlocked.value) {
                            revealLockedNote(loadedNote)
                        }
                    }
                } else {
                    isNewDraft = true
                    createNewNote()
                }
            } catch (e: Exception) {
                _loadError.value = R.string.could_not_open_note
            } finally {
                _isReady.value = true
            }
        }

        viewModelScope.launch {
            isUnlocked.collect { unlocked ->
                val currentNote = _note.value
                if (unlocked && currentNote?.isLocked == true && _body.value.isEmpty()) {
                    val retainedDraft = hiddenLockedDraft
                    if (retainedDraft != null) {
                        hiddenLockedDraft = null
                        _body.value = retainedDraft
                        hasUnsavedChanges = true
                        scheduleSearchUpdate()
                    } else {
                        revealLockedNote(currentNote)
                    }
                } else if (!unlocked && currentNote?.isLocked == true && (hasUnsavedChanges || _body.value.isNotEmpty())) {
                    viewModelScope.launch {
                        val draft = _body.value
                        if (!saveNow()) {
                            hiddenLockedDraft = draft
                        }
                        _body.value = ""
                        scheduleSearchUpdate()
                    }
                }
            }
        }

        @OptIn(FlowPreview::class)
        combine(_title, _body) { t, b -> t to b }
            .debounce(1000L)
            .onEach { saveSafely() }
            .launchIn(viewModelScope)
    }

    private fun createNewNote() {
        val now = Clock.System.now()
        _note.value = Note(
            id = currentNoteId,
            title = null,
            body = "",
            noteType = NoteType.NORMAL,
            isLocked = false,
            bodyIv = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun onTitleChanged(newTitle: String) {
        if (!_isReady.value) return
        _title.value = newTitle
        hasUnsavedChanges = true
    }

    fun onBodyChanged(newBody: String) {
        if (!_isReady.value) return
        if (_note.value?.isLocked == true && (!isUnlocked.value || _decryptFailed.value)) return
        _body.value = newBody
        hasUnsavedChanges = true
        scheduleSearchUpdate()
    }

    fun onFindQueryChanged(query: String) {
        if (!_isReady.value) return
        _findQuery.value = query
        scheduleSearchUpdate()
    }

    fun onNextSearchMatch() {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return
        val current = _selectedSearchMatchIndex.value
        _selectedSearchMatchIndex.value = if (current >= matches.lastIndex) 0 else current + 1
    }

    fun onPreviousSearchMatch() {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return
        val current = _selectedSearchMatchIndex.value
        _selectedSearchMatchIndex.value = if (current <= 0) matches.lastIndex else current - 1
    }

    suspend fun deleteNote(): Boolean {
        if (!_isReady.value || _contentLoading.value) return false
        val currentNote = _note.value ?: return true
        if (!hasPersistedNote) return true
        return saveMutex.withLock {
            runCatching {
                repository.deleteNoteAndItems(currentNote)
                true
            }.getOrElse {
            emitMessage(R.string.could_not_delete_note)
                false
            }
        }
    }

    fun onToggleLockRequested() {
        if (!_isReady.value || _contentLoading.value) return
        val currentNote = _note.value ?: return
        if (currentNote.isLocked) {
            if (isUnlocked.value && !_decryptFailed.value) {
                viewModelScope.launch {
                    if (saveNow()) {
                        securitySession.lock()
                        _body.value = ""
                        emitMessage(R.string.note_hidden)
                    }
                }
            } else {
                viewModelScope.launch { _uiEvent.emit(EditorUiEvent.AuthRequired) }
            }
        } else {
            if (hasPin()) {
                performLock()
            } else {
                viewModelScope.launch { _uiEvent.emit(EditorUiEvent.SetupPinRequired) }
            }
        }
    }

    fun onAuthSuccess() {
        securitySession.unlock()
    }

    private fun performLock() {
        viewModelScope.launch {
            saveMutex.withLock {
                val currentNote = _note.value ?: return@withLock
                val body = _body.value
                runCatching {
                    val lockedNote = withContext(Dispatchers.Default) {
                        lockUseCase.encryptNote(currentNote, body)
                    }
                    if (hasPersistedNote) {
                        repository.updateNote(lockedNote)
                    } else {
                        repository.insertNote(lockedNote)
                        hasPersistedNote = true
                    }
                    _note.value = lockedNote
                    _decryptFailed.value = false
                    hasUnsavedChanges = false
                    securitySession.lock()
                    _body.value = ""
                    emitMessage(R.string.note_locked)
                }.onFailure {
                    emitMessage(R.string.could_not_save_note)
                }
            }
        }
    }

    fun onPinSetupComplete() {
        if (_note.value?.isLocked == false) {
            performLock()
        }
    }

    fun onRemoveEncryption() {
        val currentNote = _note.value ?: return
        if (!_isReady.value || _contentLoading.value || !currentNote.isLocked || !isUnlocked.value || _decryptFailed.value) return

        val decryptedNote = currentNote.copy(
            body = _body.value,
            isLocked = false,
            bodyIv = null
        )
        viewModelScope.launch {
            saveMutex.withLock {
                runCatching {
                    repository.updateNote(decryptedNote)
                    _note.value = decryptedNote
                    _decryptFailed.value = false
                    hasUnsavedChanges = false
                    emitMessage(R.string.lock_removed)
                }.onFailure {
                    emitMessage(R.string.could_not_save_note)
                }
            }
        }
    }

    suspend fun saveNow(): Boolean {
        if (!_isReady.value || _contentLoading.value) return false
        return runCatching { saveNote() }.getOrElse {
            emitMessage(R.string.could_not_save_note)
            false
        }
    }

    private suspend fun saveSafely() {
        runCatching { saveNote() }.onFailure {
            emitMessage(R.string.could_not_save_note)
        }
    }

    private suspend fun saveNote(): Boolean = saveMutex.withLock {
        if (!_isReady.value || _contentLoading.value) return@withLock false
        val currentNote = _note.value ?: return@withLock true
        if (isNewDraft && hasPersistedNote && hasUnsavedChanges && _title.value.isBlank() && _body.value.isBlank()) {
            repository.deleteNoteAndItems(currentNote)
            hasPersistedNote = false
            hasUnsavedChanges = false
            return@withLock true
        }
        if (hasPersistedNote && !hasUnsavedChanges) return@withLock true
        if (currentNote.isLocked && _decryptFailed.value) return@withLock true
        if (currentNote.isLocked && !isUnlocked.value && !hasUnsavedChanges) return@withLock true
        if (!hasPersistedNote && _title.value.isBlank() && _body.value.isBlank()) return@withLock true

        val now = Clock.System.now()
        val title = _title.value.ifBlank { null }
        val body = _body.value
        val noteToSave = withContext(Dispatchers.Default) {
            if (currentNote.isLocked) {
                lockUseCase.encryptNote(currentNote.copy(title = title, updatedAt = now), body)
            } else {
                currentNote.copy(title = title, body = body, updatedAt = now)
            }
        }
        if (hasPersistedNote) {
            repository.updateNote(noteToSave)
        } else {
            repository.insertNote(noteToSave)
            hasPersistedNote = true
        }
        _note.value = noteToSave
        hasUnsavedChanges = false
        true
    }

    private suspend fun revealLockedNote(note: Note) {
        if (decryptInProgress) return
        decryptInProgress = true
        _contentLoading.value = true
        try {
            when (val result = withContext(Dispatchers.Default) { lockUseCase.decryptNote(note) }) {
                is DecryptNoteResult.Success -> {
                    _decryptFailed.value = false
                    _body.value = result.note.body
                    hasUnsavedChanges = false
                    scheduleSearchUpdate()
                }
                is DecryptNoteResult.Failure -> {
                    _decryptFailed.value = true
                    _body.value = ""
                    emitMessage(R.string.content_could_not_open)
                }
            }
        } finally {
            decryptInProgress = false
            _contentLoading.value = false
        }
    }

    private fun emitMessage(resourceId: Int) {
        viewModelScope.launch {
            _uiEvent.emit(EditorUiEvent.Message(resourceId))
        }
    }

    private fun scheduleSearchUpdate() {
        searchUpdateJob?.cancel()
        searchUpdateJob = viewModelScope.launch {
            val bodySnapshot = _body.value
            val querySnapshot = _findQuery.value
            if (querySnapshot.trim().isEmpty()) {
                _searchMatches.value = emptyList()
                _selectedSearchMatchIndex.value = -1
                return@launch
            }
            delay(100)
            val matches = withContext(Dispatchers.Default) {
                NoteSearch.findMatches(bodySnapshot, querySnapshot)
            }
            if (bodySnapshot == _body.value && querySnapshot == _findQuery.value) {
                _searchMatches.value = matches
                _selectedSearchMatchIndex.value = if (matches.isEmpty()) -1 else 0
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: NoteRepository,
            lockUseCase: LockUseCase,
            securitySession: SecuritySession,
            hasPin: () -> Boolean,
            noteId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NoteEditorViewModel(repository, lockUseCase, securitySession, hasPin, noteId) as T
            }
        }
    }
}
