package com.psd.floramind.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psd.floramind.repository.MistakeEntry
import com.psd.floramind.repository.MistakePackage
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 错题本。
 * 错题信息来自题库包里的 practice.json（最近一次做错即算错题，答对后自动移出）。
 */
class MistakeViewModel(private val repository: QuestionRepository) : ViewModel() {

    // 一级：错题所属的题库包
    private val _mistakePackages = MutableStateFlow<List<MistakePackage>>(emptyList())
    val mistakePackages: StateFlow<List<MistakePackage>> = _mistakePackages

    // 二级：某题库下的错题
    private val _mistakes = MutableStateFlow<List<MistakeEntry>>(emptyList())
    val mistakes: StateFlow<List<MistakeEntry>> = _mistakes

    fun loadMistakePackages() {
        viewModelScope.launch {
            _mistakePackages.value = repository.getMistakePackages()
        }
    }

    fun loadMistakes(packageName: String) {
        viewModelScope.launch {
            _mistakes.value = repository.getMistakeEntries(packageName)
        }
    }

    /** 重置某题的做题记录 = 移出错题本 */
    fun clearMistake(packageName: String, questionId: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetPractice(packageName, questionId)
            _mistakes.value = repository.getMistakeEntries(packageName)
            _mistakePackages.value = repository.getMistakePackages()
            onDone()
        }
    }
}
