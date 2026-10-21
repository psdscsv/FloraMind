package com.psd.floramind.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psd.floramind.model.StudyRecord
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MistakeViewModel(private val repository: QuestionRepository) : ViewModel() {

    // 一级：错题所属的题库包
    private val _mistakePackages = MutableStateFlow<List<String>>(emptyList())
    val mistakePackages: StateFlow<List<String>> = _mistakePackages

    // 二级：某题库下的错题记录
    private val _mistakes = MutableStateFlow<List<StudyRecord>>(emptyList())
    val mistakes: StateFlow<List<StudyRecord>> = _mistakes

    fun loadMistakePackages() {
        viewModelScope.launch {
            _mistakePackages.value = repository.getMistakePackages()
        }
    }

    fun loadMistakes(packageName: String) {
        viewModelScope.launch {
            _mistakes.value = repository.getMistakesByPackage(packageName)
        }
    }

    fun deleteMistake(record: StudyRecord, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteMistake(record.id)
            // 刷新当前错题列表 & 一级栏目
            _mistakes.value = repository.getMistakesByPackage(record.packageName)
            _mistakePackages.value = repository.getMistakePackages()
            onDone()
        }
    }
}