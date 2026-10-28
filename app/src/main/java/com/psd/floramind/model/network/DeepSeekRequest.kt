package com.psd.floramind.model.network

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val temperature: Double = 0.5,
    val max_tokens: Int = 5000
)

data class Message(
    val role: String,
    val content: String
)