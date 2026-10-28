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
            请给出0-100的分数，并提供反馈。只返回一个 JSON 对象，不要 Markdown 代码块，不要任何解释性文字。
            JSON 结构：{"score": 整数, "feedback": "评语", "matchedPoints": ["要点1"], "missingPoints": ["要点2"], "suggestions": "改进建议"}
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
            请给出0-100的分数，关注是否涵盖关键词。只返回一个 JSON 对象，不要 Markdown 代码块，不要任何解释性文字。
            JSON 结构：{"score": ..., "feedback": "...", "matchedPoints": [...], "missingPoints": [...], "suggestions": "..."}
        """.trimIndent()
        callApi(prompt)
    }

    private suspend fun callApi(prompt: String): GradingResult {
        val request = DeepSeekRequest(
            messages = listOf(
                Message("system", "你是一个严格的植物学评分专家。只返回 JSON，不要 Markdown，不要其他文本。"),
                Message("user", prompt)
            )
        )

        val response = try {
            api.chatCompletion("Bearer $apiKey", request)
        } catch (e: Exception) {
            Log.e("DeepSeekScorer", "network error", e)
            return GradingResult(0, "网络请求失败：${e.message ?: "未知错误"}",
                emptyList(), emptyList(), "请检查网络后重试")
        }

        val content = response.choices?.firstOrNull()?.message?.content
        if (content.isNullOrBlank()) {
            return GradingResult(0, "AI 返回为空", emptyList(), emptyList(), "请重试")
        }

        // ★ 多层兜底提取 JSON
        val jsonText = extractJson(content)
        return try {
            val parsed = Gson().fromJson(jsonText, GradingResult::class.java)
            // 兜底字段
            if (parsed == null) {
                GradingResult(0, "评分解析失败", emptyList(), emptyList(), "请重试")
            } else {
                parsed.copy(
                    feedback = parsed.feedback.ifBlank { "已评分" },
                    suggestions = parsed.suggestions ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("DeepSeekScorer", "parse failed, raw=$content", e)
            // 最后兜底：从原始内容里抠数字作为分数
            val score = Regex("""score["']?\s*[:：]\s*(\d+)""")
                .find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""\b(\d{1,3})\b""")
                    .find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 0
            GradingResult(
                score = score.coerceIn(0, 100),
                feedback = "评分结果包含非标准格式，已尽力解析。原始返回片段：${
                    content.take(80).replace("\n", " ")
                }",
                matchedPoints = emptyList(),
                missingPoints = emptyList(),
                suggestions = "如需精确反馈请重试"
            )
        }
    }

    /**
     * 从 LLM 返回文本里提取 JSON 对象：
     * 1. 若有 ```json ... ``` 代码块，取块内内容
     * 2. 否则取第一个 { 到最后一个 } 之间的子串
     * 3. 再退化则返回原文（让 Gson 报错，走兜底）
     */
    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()

        // 1. ```json ... ``` 或 ``` ... ```
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        fenceRegex.find(trimmed)?.let { m ->
            val inner = m.groupValues[1].trim()
            if (inner.startsWith("{")) return inner
        }

        // 2. 第一个 { 到最后一个 }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }

        return trimmed
    }
}