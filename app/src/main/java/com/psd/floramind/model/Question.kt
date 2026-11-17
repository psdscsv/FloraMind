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
    val labels: List<LabelPoint>? = null,
    /** 题库包自定义题型 id（type = CUSTOM 时使用，对应包内 question_types.json） */
    val customType: String? = null,
    /** 自定义题型的多个小问（type = CUSTOM 时使用） */
    val parts: List<QuestionPart>? = null
)

enum class QuestionType {
    DEFINITION, FILL_BLANK, CHOICE, SHORT_ANSWER, DIAGRAM, ESSAY,

    /** 题库包通过 question_types.json 自定义的题型 */
    CUSTOM
}