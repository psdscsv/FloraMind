package com.psd.floramind.model

import androidx.room.Entity
import com.google.gson.annotations.SerializedName

@Entity(tableName = "questions", primaryKeys = ["packageName", "id"])
data class Question(
    val packageName: String = "内置题库",  // 新增，默认内置题库
    val id: Int,
    val type: QuestionType,
    val stem: String,
    val options: List<String>? = null,
    @SerializedName("correctAnswer") val correctAnswer: String? = null,
    val keywords: List<String>? = null,
    val imageName: String? = null,        // 相对路径，如 "images/leaf.png"
    val labels: List<LabelPoint>? = null
)

enum class QuestionType {
    DEFINITION, FILL_BLANK, CHOICE, SHORT_ANSWER, DIAGRAM, ESSAY
}