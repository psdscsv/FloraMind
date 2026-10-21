package com.psd.floramind.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psd.floramind.data.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStore: DataStoreManager) : ViewModel() {
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    init {
        viewModelScope.launch {
            dataStore.userNameFlow.collect { _userName.value = it }
        }
        viewModelScope.launch {
            dataStore.apiKeyFlow.collect { _apiKey.value = it }
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            dataStore.saveUserName(name)
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            dataStore.saveApiKey(key)
        }
    }
}