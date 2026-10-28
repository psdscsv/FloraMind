package com.psd.floramind.model.network

data class GradingResult(
    val score: Int = 0,
    val feedback: String = "",
    val matchedPoints: List<String>? = null,
    val missingPoints: List<String>? = null,
    val suggestions: String? = ""
)