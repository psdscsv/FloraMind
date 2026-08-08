package com.psd.floramind.network

import android.util.Log
import com.google.gson.Gson
import com.psd.floramind.model.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeepSeekScorer(private val apiKey: String) {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeepSeekApi::class.java)

    suspend fun evaluateDefinition(
        term: String,
        userAnswer: String,
        standardAnswer: String
    ): GradingResult = withContext(Dispatchers.IO) {
        val prompt = """
            你是一位植物学教授。请根据以下标准答案，评价学生的名词解释。
            术语：$term
            标准答案：$standardAnswer
            学生答案：$userAnswer
            请给出0-100的分数，并提供反馈。返回严格的JSON格式：
            {"score": 整数, "feedback": "评语", "matchedPoints": ["要点1"], "missingPoints": ["要点2"], "suggestions": "改进建议"}
        """.trimIndent()
        callApi(prompt)
    }

    suspend fun evaluateEssay(
        question: String,
        userAnswer: String,
        keywords: List<String>
    ): GradingResult = withContext(Dispatchers.IO) {
        val prompt = """
            你是一位植物学教授。根据以下问题评价学生的回答。
            问题：$question
            关键词：${keywords.joinToString(", ")}
            学生答案：$userAnswer
            请给出0-100的分数，关注是否涵盖关键词。返回JSON：{"score": ..., "feedback": "...", "matchedPoints": [...], "missingPoints": [...], "suggestions": "..."}
        """.trimIndent()
        callApi(prompt)
    }

    private suspend fun callApi(prompt: String): GradingResult {
        val request = DeepSeekRequest(
            messages = listOf(
                Message("system", "你是一个严格的植物学评分专家。只返回JSON，不要有其他文本。"),
                Message("user", prompt)
            )
        )
        val response = api.chatCompletion("Bearer $apiKey", request)
        val content = response.choices?.firstOrNull()?.message?.content ?: "{}"
        return try {
            Gson().fromJson(content, GradingResult::class.java)
        } catch (e: Exception) {
            GradingResult(0, "评分解析失败", emptyList(), emptyList(), "请重试")
        }
    }
}