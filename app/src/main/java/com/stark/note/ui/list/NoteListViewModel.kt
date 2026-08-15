package com.stark.note.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stark.note.domain.note.Note
import com.stark.note.domain.note.repository.NoteRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

class NoteListViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { allNotes ->
                _notes.value = allNotes
                _isReady.value = true
            }
        }
    }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NoteListViewModel(repository) as T
            }
        }
    }
}
