package com.psd.floramind.model

import androidx.room.Embedded

data class LabelPoint(
    val x: Float,   // 0..1
    val y: Float,
    val correctAnswer: String,
    val labelId: String = ""
)