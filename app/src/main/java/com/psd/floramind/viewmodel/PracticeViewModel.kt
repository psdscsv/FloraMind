package com.psd.floramind.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psd.floramind.model.*
import com.psd.floramind.model.network.GradingResult
import com.psd.floramind.network.DeepSeekScorer
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PracticeViewModel(
    private val repository: QuestionRepository,
    private var apiKey: String
) : ViewModel() {

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _userAnswers = MutableStateFlow<MutableMap<Int, String>>(mutableMapOf())
    val userAnswers: StateFlow<Map<Int, String>> = _userAnswers.asStateFlow()

    private val _fillBlankAnswers = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val fillBlankAnswers: StateFlow<Map<Int, List<String>>> = _fillBlankAnswers.asStateFlow()

    private val _gradingResult = MutableStateFlow<GradingResult?>(null)
    val gradingResult: StateFlow<GradingResult?> = _gradingResult.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 提交中状态（供 UI 禁用按钮 / 显示进度）
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // 仅在 apiKey 变化时重建；调用方"使用前"通过 currentScorer() 获取最新实例
    private var scorer: DeepSeekScorer? = if (apiKey.isNotBlank()) DeepSeekScorer(apiKey) else null
    private var currentPackageName = ""

    /**
     * 更新 API Key。
     * 允许传入空串：仅表示"当前无 key"，不会因为 Flow 重发空值而破坏已存在的 scorer
     * —— 因为 scorer 只在本次真正收到非空 key 时才被替换。
     */
    fun setApiKey(key: String) {
        if (key == apiKey) return
        apiKey = key
        if (key.isNotBlank()) {
            scorer = DeepSeekScorer(key)
        } else {
            scorer = null
        }
    }

    /** ★ 使用前检查：保证拿到非空 scorer（apiKey 已就绪但实例尚未创建时补建） */
    private fun currentScorer(): DeepSeekScorer? {
        if (scorer == null && apiKey.isNotBlank()) {
            scorer = DeepSeekScorer(apiKey)
        }
        return scorer
    }

    suspend fun loadQuestionsForPackage(packageName: String) {
        currentPackageName = packageName
        _isLoading.value = true
        try {
            val list = repository.getQuestionsByPackage(packageName)
            _questions.value = list
            Log.d("PracticeVM", "Loaded ${list.size} questions for package '$packageName'")
        } catch (e: Exception) {
            Log.e("PracticeVM", "Failed to load questions", e)
            _questions.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun loadQuestionsFromList(list: List<Question>) {
        _isLoading.value = true
        try {
            _questions.value = list
            _gradingResult.value = null
            Log.d("PracticeVM", "Loaded ${list.size} questions from filter session")
        } finally {
            _isLoading.value = false
        }
    }

    fun setCurrentIndex(index: Int) {
        if (index in 0 until _questions.value.size) {
            _currentIndex.value = index
            _gradingResult.value = null
        } else {
            Log.w("PracticeVM", "Invalid index $index, size=${_questions.value.size}")
        }
    }

    fun updateUserAnswer(answer: String) {
        val id = _questions.value.getOrNull(_currentIndex.value)?.id ?: return
        _userAnswers.value[id] = answer
    }

    fun getUserAnswerForCurrent(): String {
        val id = _questions.value.getOrNull(_currentIndex.value)?.id ?: return ""
        return _userAnswers.value[id] ?: ""
    }

    fun updateFillBlankAnswer(questionId: Int, blankIndex: Int, text: String) {
        val current = _fillBlankAnswers.value[questionId]?.toMutableList() ?: mutableListOf()
        while (current.size <= blankIndex) current.add("")
        current[blankIndex] = text
        _fillBlankAnswers.value = _fillBlankAnswers.value + (questionId to current)
    }

    fun getFillBlankAnswers(questionId: Int): List<String> =
        _fillBlankAnswers.value[questionId] ?: emptyList()

    /** 供 UI 判断当前题是否需要 API Key */
    fun currentQuestionNeedsApiKey(): Boolean {
        val q = _questions.value.getOrNull(_currentIndex.value) ?: return false
        return q.type == QuestionType.DEFINITION ||
                q.type == QuestionType.SHORT_ANSWER ||
                q.type == QuestionType.ESSAY
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    fun submitCurrentAnswer() {
        if (_isSubmitting.value) return
        val question = _questions.value.getOrNull(_currentIndex.value) ?: return

        if (question.type == QuestionType.FILL_BLANK) {
            submitFillBlank(question)
            return
        }

        val userAnswer = getUserAnswerForCurrent()
        if (userAnswer.isBlank()) return

        _isSubmitting.value = true
        _gradingResult.value = null

        viewModelScope.launch {
            try {
                val result = when (question.type) {
                    QuestionType.CHOICE -> {
                        val correct = question.correctAnswer
                            ?.equals(userAnswer.trim(), ignoreCase = true) == true
                        GradingResult(
                            score = if (correct) 100 else 0,
                            feedback = if (correct) "完全正确" else "答案错误",
                            matchedPoints = if (correct) listOf("答案匹配") else emptyList(),
                            missingPoints = if (!correct) listOf("正确答案是 ${question.correctAnswer}") else emptyList(),
                            suggestions = if (!correct) "请记住正确答案" else ""
                        )
                    }

                    QuestionType.DEFINITION -> {
                        currentScorer()?.evaluateDefinition(
                            question.stem,
                            userAnswer,
                            question.correctAnswer ?: ""
                        ) ?: GradingResult(0, "未配置 API Key", emptyList(), emptyList(), "请在设置中填写")
                    }

                    QuestionType.SHORT_ANSWER, QuestionType.ESSAY -> {
                        currentScorer()?.evaluateEssay(
                            question.stem,
                            userAnswer,
                            question.keywords ?: emptyList()
                        ) ?: GradingResult(0, "未配置 API Key", emptyList(), emptyList(), "请在设置中填写")
                    }

                    else -> GradingResult(0, "暂不支持此题型", emptyList(), emptyList(), "")
                }
                _gradingResult.value = result
                repository.saveRecord(
                    StudyRecord(
                        packageName = question.packageName,
                        questionId = question.id,
                        userAnswer = userAnswer,
                        score = result.score,
                        isCorrect = result.score >= 60
                    )
                )
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private fun submitFillBlank(question: Question) {
        val stemParts = question.stem.split(Regex("_{2,}"))
        val blankCount = stemParts.size - 1
        val userList = getFillBlankAnswers(question.id)

        if (userList.all { it.isBlank() }) {
            _gradingResult.value = GradingResult(
                score = 0,
                feedback = "请至少填写一个空",
                matchedPoints = emptyList(),
                missingPoints = emptyList(),
                suggestions = ""
            )
            return
        }

        val result: GradingResult
        val combinedUserAnswer = userList.joinToString("; ")

        if (blankCount <= 0) {
            val u = userList.firstOrNull()?.trim() ?: ""
            val correct = question.correctAnswer?.equals(u, ignoreCase = true) == true
            result = GradingResult(
                score = if (correct) 100 else 0,
                feedback = if (correct) "正确" else "错误",
                matchedPoints = if (correct) listOf("匹配") else emptyList(),
                missingPoints = if (!correct) listOf("正确答案: ${question.correctAnswer}") else emptyList(),
                suggestions = ""
            )
        } else {
            val correctList = question.correctAnswer
                ?.split(";", "；", "|")
                ?.map { it.trim() }
                ?: emptyList()

            var correctCount = 0
            val wrongs = mutableListOf<String>()
            for (i in 0 until blankCount) {
                val u = userList.getOrNull(i)?.trim() ?: ""
                val c = correctList.getOrNull(i)?.trim() ?: ""
                if (u.equals(c, ignoreCase = true)) {
                    correctCount++
                } else {
                    wrongs.add("第 ${i + 1} 空：你填「$u」，应为「$c」")
                }
            }
            val score = if (blankCount > 0) correctCount * 100 / blankCount else 0
            result = GradingResult(
                score = score,
                feedback = if (score == 100) "全部正确"
                else "${blankCount - correctCount}/$blankCount 空错误",
                matchedPoints = if (correctCount > 0) listOf("正确 $correctCount 空") else emptyList(),
                missingPoints = wrongs,
                suggestions = if (score < 100) "复习相关知识点" else ""
            )
        }

        _gradingResult.value = result
        viewModelScope.launch {
            repository.saveRecord(
                StudyRecord(
                    packageName = question.packageName,
                    questionId = question.id,
                    userAnswer = combinedUserAnswer,
                    score = result.score,
                    isCorrect = result.score >= 60
                )
            )
        }
    }

    fun submitDiagramAnswers(labelAnswers: Map<String, String>) {
        if (_isSubmitting.value) return
        val question = _questions.value.getOrNull(_currentIndex.value) ?: return
        val labels = question.labels ?: return

        _isSubmitting.value = true
        try {
            var correctCount = 0
            val feedbacks = mutableListOf<String>()
            labels.forEachIndexed { index, label ->
                val userAns = labelAnswers[index.toString()] ?: ""
                if (userAns.equals(label.correctAnswer, ignoreCase = true)) {
                    correctCount++
                } else {
                    feedbacks.add("标签 ${index + 1} 错误，应为 ${label.correctAnswer}")
                }
            }
            val score = if (labels.isNotEmpty()) (correctCount * 100 / labels.size) else 0
            val result = GradingResult(
                score = score,
                feedback = if (score == 100) "全部正确" else "有 ${labels.size - correctCount} 个标签错误",
                matchedPoints = if (correctCount > 0) listOf("正确标签数: $correctCount") else emptyList(),
                missingPoints = feedbacks,
                suggestions = "复习对应结构"
            )
            _gradingResult.value = result
            val record = StudyRecord(
                packageName = question.packageName,
                questionId = question.id,
                userAnswer = labelAnswers.values.joinToString("; "),
                score = score,
                isCorrect = score >= 60
            )
            viewModelScope.launch { repository.saveRecord(record) }
        } finally {
            _isSubmitting.value = false
        }
    }
}