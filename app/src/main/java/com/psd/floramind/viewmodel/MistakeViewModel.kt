package com.psd.floramind.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psd.floramind.model.StudyRecord
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MistakeViewModel(private val repository: QuestionRepository) : ViewModel() {
    private val _mistakes = MutableStateFlow<List<StudyRecord>>(emptyList())
    val mistakes: StateFlow<List<StudyRecord>> = _mistakes

    fun loadMistakes() {
        viewModelScope.launch {
            _mistakes.value = repository.getMistakes()
        }
    }
}