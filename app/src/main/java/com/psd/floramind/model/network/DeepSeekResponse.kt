package com.psd.floramind.model.network

data class DeepSeekResponse(
    val choices: List<Choice>? = null
)

data class Choice(
    val message: MessageResponse? = null
)

data class MessageResponse(
    val content: String? = null
)