package com.psd.floramind.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.psd.floramind.BuildConfig
import com.psd.floramind.model.QuestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

class DataStoreManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        // ---------- 基础设置 ----------
        val USER_NAME = stringPreferencesKey("user_name")
        val API_KEY = stringPreferencesKey("api_key")
        val SERVER_URL = stringPreferencesKey("server_url")

        // ---------- 题型筛选 ----------
        val SELECTED_TYPES = stringPreferencesKey("selected_question_types")

        // ---------- 公告已读 ----------
        val LAST_READ_ANN_ID = stringPreferencesKey("last_read_ann_id")
        val LAST_READ_ANN_UPDATED = longPreferencesKey("last_read_ann_updated")
    }

    // ============================================================
    //                        基础设置
    // ============================================================

    val userNameFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[USER_NAME] ?: "用户" }

    // DataStore 没有值或为空时，回退到打包时写入的 BuildConfig.API_KEY
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
            val trimmed = url.trim()
            if (trimmed.isEmpty()) preferences.remove(SERVER_URL)
            else preferences[SERVER_URL] = trimmed
        }
    }

    // ============================================================
    //                        题型筛选
    // ============================================================

    /**
     * 已选题型集合。
     * 空集合表示"未选"，语义上等同全选；调用方自行转换。
     * 反序列化时过滤无效 name（版本升级删枚举时不出错）。
     */
    val selectedTypesFlow: Flow<Set<QuestionType>> = dataStore.data
        .map { preferences ->
            val raw = preferences[SELECTED_TYPES] ?: ""
            if (raw.isBlank()) emptySet()
            else raw.split(",")
                .mapNotNull { name ->
                    QuestionType.entries.firstOrNull { it.name == name }
                }
                .toSet()
        }

    /** 保存题型筛选。空集合时移除 key，下次读回仍是空（=全选） */
    suspend fun saveSelectedTypes(types: Set<QuestionType>) {
        dataStore.edit { preferences ->
            if (types.isEmpty()) {
                preferences.remove(SELECTED_TYPES)
            } else {
                preferences[SELECTED_TYPES] =
                    types.joinToString(",") { it.name }
            }
        }
    }

    // ============================================================
    //                        公告已读
    // ============================================================

    /** 最近一次已读公告的 id，空字符串表示从未读过 */
    val lastReadAnnIdFlow: Flow<String> = dataStore.data
        .map { preferences -> preferences[LAST_READ_ANN_ID] ?: "" }

    /** 最近一次已读公告的更新时间戳，0 表示从未读过 */
    val lastReadAnnUpdatedFlow: Flow<Long> = dataStore.data
        .map { preferences -> preferences[LAST_READ_ANN_UPDATED] ?: 0L }

    /** 记录公告已读（同 id + updated_time 视为已读，不再弹出） */
    suspend fun markAnnouncementRead(id: String, updatedTime: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_READ_ANN_ID] = id
            preferences[LAST_READ_ANN_UPDATED] = updatedTime
        }
    }
}