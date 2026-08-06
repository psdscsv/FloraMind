package com.psd.floramind.model.network

data class GradingResult(
    val score: Int,
    val feedback: String,
    val matchedPoints: List<String>,
    val missingPoints: List<String>,
    val suggestions: String
)