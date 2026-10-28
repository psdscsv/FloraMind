package com.psd.floramind.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.psd.floramind.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

class DataStoreManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val API_KEY = stringPreferencesKey("api_key")
        val SERVER_URL = stringPreferencesKey("server_url")
    }

    val userNameFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[USER_NAME] ?: "用户" }

    val apiKeyFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[API_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.API_KEY
        }

    /** 服务器地址，空字符串表示未配置 */
    val serverUrlFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[SERVER_URL] ?: "" }

    suspend fun saveUserName(name: String) {
        dataStore.edit { preferences -> preferences[USER_NAME] = name }
    }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { preferences -> preferences[API_KEY] = key }
    }

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_URL] = url.trim()
        }
    }
}