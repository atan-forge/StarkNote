package com.stark.note.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stark.note.R
import com.stark.note.domain.note.ListItem
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.NoteType
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID

class ListEditorViewModel(
    private val repository: NoteRepository,
    private val noteId: String?
) : ViewModel() {
    private val saveMutex = Mutex()
    private var hasPersistedNote = false
    private var hasUnsavedChanges = false
    private var isNewDraft = false

    private val currentNoteId = noteId ?: UUID.randomUUID().toString()

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _loadError = MutableStateFlow<Int?>(null)
    val loadError: StateFlow<Int?> = _loadError.asStateFlow()

    private val _uiEvent = MutableSharedFlow<Int>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _newItemContent = MutableStateFlow("")
    val newItemContent: StateFlow<String> = _newItemContent.asStateFlow()

    val items: StateFlow<List<ListItem>> = repository.getItemsForNote(currentNoteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            try {
                val existingNote = noteId?.let { repository.getNoteById(it) }
                if (existingNote != null) {
                    hasPersistedNote = true
                    existingNote.let { loadedNote ->
                        _note.value = loadedNote
                        _title.value = loadedNote.title ?: ""
                    }
                } else {
                    isNewDraft = true
                    prepareNewListNote()
                }
            } catch (e: Exception) {
                _loadError.value = R.string.could_not_open_checklist
            } finally {
                _isReady.value = true
            }
        }

        @OptIn(FlowPreview::class)
        _title.debounce(1000L)
            .onEach { saveSafely() }
            .launchIn(viewModelScope)
    }

    private fun prepareNewListNote() {
        val now = Clock.System.now()
        _note.value = Note(
            id = currentNoteId,
            title = null,
            body = "",
            noteType = NoteType.LIST,
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

    fun onNewItemContentChanged(content: String) {
        if (!_isReady.value) return
        _newItemContent.value = content
    }

    fun onAddItem() {
        if (!_isReady.value || _note.value == null) return
        val content = _newItemContent.value.trim()
        if (content.isEmpty()) return

        val currentItems = items.value
        val nextPosition = (currentItems.maxOfOrNull { it.position } ?: -1) + 1

        val newItem = ListItem(
            id = UUID.randomUUID().toString(),
            noteId = currentNoteId,
            content = content,
            isChecked = false,
            position = nextPosition,
            createdAt = Clock.System.now()
        )

        viewModelScope.launch {
            saveMutex.withLock {
                runCatching {
                    val updatedNote = updatedNote()
                    repository.addChecklistItem(updatedNote, newItem)
                    _note.value = updatedNote
                    hasPersistedNote = true
                    hasUnsavedChanges = false
                    _newItemContent.value = ""
                }.onFailure {
                    emitMessage(R.string.could_not_add_item)
                }
            }
        }
    }

    fun onToggleItem(item: ListItem) {
        if (!_isReady.value) return
        viewModelScope.launch {
            saveMutex.withLock {
                runCatching {
                    val updatedNote = updatedNote()
                    repository.updateChecklistItem(updatedNote, item.copy(isChecked = !item.isChecked))
                    _note.value = updatedNote
                    hasUnsavedChanges = false
                }.onFailure {
                    emitMessage(R.string.could_not_update_item)
                }
            }
        }
    }

    fun onDeleteItem(item: ListItem) {
        if (!_isReady.value) return
        viewModelScope.launch {
            saveMutex.withLock {
                runCatching {
                    val updatedNote = updatedNote()
                    repository.deleteChecklistItem(updatedNote, item)
                    _note.value = updatedNote
                    hasUnsavedChanges = false
                }.onFailure {
                    emitMessage(R.string.could_not_delete_item)
                }
            }
        }
    }

    suspend fun deleteNote(): Boolean {
        if (!_isReady.value) return true
        val currentNote = _note.value ?: return true
        if (!hasPersistedNote) return true
        return saveMutex.withLock {
            runCatching {
                repository.deleteNoteAndItems(currentNote)
                true
            }.getOrElse {
                emitMessage(R.string.could_not_delete_checklist)
                false
            }
        }
    }

    suspend fun clearCheckedItems(): Boolean {
        if (!_isReady.value) return true
        return saveMutex.withLock {
            runCatching {
                if (items.value.none { it.isChecked }) return@runCatching true
                val updatedNote = updatedNote()
                repository.clearCheckedChecklistItems(updatedNote)
                _note.value = updatedNote
                hasUnsavedChanges = false
                true
            }.getOrElse {
                emitMessage(R.string.could_not_clear_items)
                false
            }
        }
    }

    suspend fun saveNow(): Boolean {
        if (!_isReady.value) return true
        return runCatching { saveNote() }.getOrElse {
            emitMessage(R.string.could_not_save_checklist)
            false
        }
    }

    private suspend fun saveSafely() {
        runCatching { saveNote() }.onFailure {
            emitMessage(R.string.could_not_save_checklist)
        }
    }

    private suspend fun saveNote(): Boolean = saveMutex.withLock {
        saveNoteLocked()
    }

    private suspend fun saveNoteLocked(): Boolean {
        val currentNote = _note.value ?: return true
        val isEmptyNewDraft = isNewDraft &&
            hasPersistedNote &&
            hasUnsavedChanges &&
            _title.value.isBlank() &&
            repository.getItemsForNote(currentNoteId).first().isEmpty()
        if (isEmptyNewDraft) {
            repository.deleteNoteAndItems(currentNote)
            hasPersistedNote = false
            hasUnsavedChanges = false
            return true
        }
        if (hasPersistedNote && !hasUnsavedChanges) return true
        if (!hasPersistedNote && _title.value.isBlank()) return true
        val now = Clock.System.now()
        val updatedNote = withContext(Dispatchers.Default) {
            currentNote.copy(
                title = _title.value.ifBlank { null },
                updatedAt = now
            )
        }
        if (hasPersistedNote) {
            repository.updateNote(updatedNote)
        } else {
            repository.insertNote(updatedNote)
            hasPersistedNote = true
        }
        _note.value = updatedNote
        hasUnsavedChanges = false
        return true
    }

    private fun updatedNote(): Note {
        return requireNotNull(_note.value).copy(
            title = _title.value.ifBlank { null },
            updatedAt = Clock.System.now()
        )
    }

    private fun emitMessage(resourceId: Int) {
        viewModelScope.launch {
            _uiEvent.emit(resourceId)
        }
    }

    companion object {
        fun provideFactory(
            repository: NoteRepository,
            noteId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ListEditorViewModel(repository, noteId) as T
            }
        }
    }
}
