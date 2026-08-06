package com.psd.floramind.viewmodel

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

    private val _gradingResult = MutableStateFlow<GradingResult?>(null)
    val gradingResult: StateFlow<GradingResult?> = _gradingResult.asStateFlow()

    private var scorer: DeepSeekScorer? = if (apiKey.isNotEmpty()) DeepSeekScorer(apiKey) else null

    init {
        loadQuestions()
    }

    fun setApiKey(key: String) {
        apiKey = key
        scorer = if (key.isNotEmpty()) DeepSeekScorer(key) else null
    }

    fun loadQuestions() {
        viewModelScope.launch {
            _questions.value = repository.getAllQuestions()
        }
    }

    fun setCurrentIndex(index: Int) {
        if (index in 0 until _questions.value.size) {
            _currentIndex.value = index
            _gradingResult.value = null
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

    fun submitCurrentAnswer() {
        val question = _questions.value.getOrNull(_currentIndex.value) ?: return
        val userAnswer = getUserAnswerForCurrent()
        if (userAnswer.isBlank()) return

        viewModelScope.launch {
            val result = when (question.type) {
                QuestionType.CHOICE, QuestionType.FILL_BLANK -> {
                    val correct = question.correctAnswer?.equals(userAnswer, ignoreCase = true) == true
                    GradingResult(
                        score = if (correct) 100 else 0,
                        feedback = if (correct) "完全正确" else "答案错误",
                        matchedPoints = if (correct) listOf("答案匹配") else emptyList(),
                        missingPoints = if (!correct) listOf("正确答案是 ${question.correctAnswer}") else emptyList(),
                        suggestions = if (!correct) "请记住正确答案" else ""
                    )
                }
                QuestionType.DEFINITION -> {
                    scorer?.evaluateDefinition(
                        question.stem,
                        userAnswer,
                        question.correctAnswer ?: ""
                    ) ?: GradingResult(0, "未配置 API Key", emptyList(), emptyList(), "")
                }
                QuestionType.SHORT_ANSWER, QuestionType.ESSAY -> {
                    scorer?.evaluateEssay(
                        question.stem,
                        userAnswer,
                        question.keywords ?: emptyList()
                    ) ?: GradingResult(0, "未配置 API Key", emptyList(), emptyList(), "")
                }
                else -> GradingResult(0, "暂不支持此题型", emptyList(), emptyList(), "")
            }
            _gradingResult.value = result
            val record = StudyRecord(
                questionId = question.id,
                userAnswer = userAnswer,
                score = result.score,
                isCorrect = result.score >= 60
            )
            repository.saveRecord(record)
        }
    }

    fun submitDiagramAnswers(labelAnswers: Map<String, String>) {
        val question = _questions.value.getOrNull(_currentIndex.value) ?: return
        val labels = question.labels ?: return
        var correctCount = 0
        val feedbacks = mutableListOf<String>()
        labels.forEachIndexed { index, label ->
            val userAns = labelAnswers[index.toString()] ?: ""
            if (userAns.equals(label.correctAnswer, ignoreCase = true)) {
                correctCount++
            } else {
                feedbacks.add("标签 ${index+1} 错误，应为 ${label.correctAnswer}")
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
            questionId = question.id,
            userAnswer = labelAnswers.values.joinToString("; "),
            score = score,
            isCorrect = score >= 60
        )
        viewModelScope.launch {
            repository.saveRecord(record)
        }
    }
}