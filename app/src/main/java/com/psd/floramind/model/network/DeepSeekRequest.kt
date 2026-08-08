package com.psd.floramind.model.network

data class DeepSeekRequest(
    val model: String = "deepseek-v4-flash",
    val messages: List<Message>,
    val temperature: Double = 0.5,
    val max_tokens: Int = 500
)

data class Message(
    val role: String,
    val content: String
)