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
        val LAST_READ_ANN_ID = stringPreferencesKey("last_read_ann_id")
        val LAST_READ_ANN_UPDATED = longPreferencesKey("last_read_ann_updated")
    }
    /** 最近一次已读公告的 id */
    val lastReadAnnIdFlow: Flow<String> = dataStore.data
        .map { it[LAST_READ_ANN_ID] ?: "" }

    /** 最近一次已读公告的更新时间戳 */
    val lastReadAnnUpdatedFlow: Flow<Long> = dataStore.data
        .map { it[LAST_READ_ANN_UPDATED] ?: 0L }
    val userNameFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[USER_NAME] ?: "用户" }

    /** 记录公告已读（同 id + updated_time 视为已读，不再弹出） */
    suspend fun markAnnouncementRead(id: String, updatedTime: Long) {
        dataStore.edit {
            it[LAST_READ_ANN_ID] = id
            it[LAST_READ_ANN_UPDATED] = updatedTime
        }
    }
    val apiKeyFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[API_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.API_KEY
        }

    /** 服务器地址，空字符串表示未配置 */
    val serverUrlFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[SERVER_URL] ?: "http://8.138.243.178:80/" }

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