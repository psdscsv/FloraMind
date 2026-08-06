package com.psd.floramind.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: Int,
    val type: QuestionType,
    val stem: String,
    val options: List<String>? = null,
    @SerializedName("correctAnswer") val correctAnswer: String? = null,
    val keywords: List<String>? = null,
    val imageName: String? = null,
    val labels: List<LabelPoint>? = null
)

enum class QuestionType {
    DEFINITION, FILL_BLANK, CHOICE, SHORT_ANSWER, DIAGRAM, ESSAY
}