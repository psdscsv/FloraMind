package com.psd.floramind.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_records")
data class StudyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String = "内置题库",
    val questionId: Int,
    val userAnswer: String,
    val score: Int,
    val isCorrect: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)