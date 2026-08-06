package com.psd.floramind.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.model.LabelPoint
import com.psd.floramind.model.QuestionType

class Converters {
    // ---------- 枚举转换 ----------
    @TypeConverter
    fun fromQuestionType(value: QuestionType?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toQuestionType(value: Int?): QuestionType? {
        return value?.let { QuestionType.entries.getOrNull(it) }
    }

    // ---------- List<String> 转换 ----------
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(it, type)
        }
    }

    // ---------- List<LabelPoint> 转换 ----------
    @TypeConverter
    fun fromLabelPointList(value: List<LabelPoint>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toLabelPointList(value: String?): List<LabelPoint>? {
        return value?.let {
            val type = object : TypeToken<List<LabelPoint>>() {}.type
            Gson().fromJson(it, type)
        }
    }
}