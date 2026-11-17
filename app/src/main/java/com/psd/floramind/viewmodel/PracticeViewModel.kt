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

/**
 * 做题页面状态。
 *
 * 与旧版的区别：
 *  - 选择题点选即判，主观题「显示答案 + 自我判定」，AI 评分只作为可选辅助；
 *  - 每道题的作答内容、做题记录都按「包名#题号」索引，跨题库练习不会串题；
 *  - 做题记录写到题库包里的 practice.json，而不是数据库。
 */
class PracticeViewModel(
    private val repository: QuestionRepository,
    private var apiKey: String
) : ViewModel() {

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 用户输入，key = 包名#题号 */
    private val _userAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAnswers: StateFlow<Map<String, String>> = _userAnswers.asStateFlow()

    /** 填空题每空的输入，key = 包名#题号 */
    private val _fillBlankAnswers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val fillBlankAnswers: StateFlow<Map<String, List<String>>> = _fillBlankAnswers.asStateFlow()

    /** 自定义多小问题型：每个小问的输入，key = 包名#题号 */
    private val _partAnswers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val partAnswers: StateFlow<Map<String, List<String>>> = _partAnswers.asStateFlow()

    /** 本次练习涉及的自定义题型定义，key = 包名#题型id */
    private val _customTypes = MutableStateFlow<Map<String, CustomQuestionType>>(emptyMap())
    val customTypes: StateFlow<Map<String, CustomQuestionType>> = _customTypes.asStateFlow()

    /** 做题记录，key = 包名#题号 */
    private val _records = MutableStateFlow<Map<String, PracticeRecord>>(emptyMap())
    val records: StateFlow<Map<String, PracticeRecord>> = _records.asStateFlow()

    /** 当前题是否已显示答案 */
    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    /** 选择题里被点选的选项 */
    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption: StateFlow<String?> = _selectedOption.asStateFlow()

    /** 自定义多选题已勾选的选项 */
    private val _selectedOptions = MutableStateFlow<Set<String>>(emptySet())
    val selectedOptions: StateFlow<Set<String>> = _selectedOptions.asStateFlow()

    /** 当前题本次是否已经记录过（避免一道题被重复计数） */
    private val _attemptRecorded = MutableStateFlow(false)
    val attemptRecorded: StateFlow<Boolean> = _attemptRecorded.asStateFlow()

    private val _gradingResult = MutableStateFlow<GradingResult?>(null)
    val gradingResult: StateFlow<GradingResult?> = _gradingResult.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // 仅在 apiKey 变化时重建；调用方"使用前"通过 currentScorer() 获取最新实例
    private var scorer: DeepSeekScorer? = if (apiKey.isNotBlank()) DeepSeekScorer(apiKey) else null

    fun setApiKey(key: String) {
        if (key == apiKey) return
        apiKey = key
        scorer = if (key.isNotBlank()) DeepSeekScorer(key) else null
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    private fun currentScorer(): DeepSeekScorer? {
        if (scorer == null && apiKey.isNotBlank()) {
            scorer = DeepSeekScorer(apiKey)
        }
        return scorer
    }

    fun currentQuestion(): Question? = _questions.value.getOrNull(_currentIndex.value)

    fun recordOf(question: Question): PracticeRecord? = _records.value[question.practiceKey]

    // ---------------- 加载 ----------------

    suspend fun loadQuestionsForPackage(packageName: String) {
        _isLoading.value = true
        try {
            val list = repository.getQuestionsByPackage(packageName)
            _questions.value = list
            loadRecords(list)
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
            loadRecords(list)
            Log.d("PracticeVM", "Loaded ${list.size} questions from filter session")
        } finally {
            _isLoading.value = false
        }
    }

    /** 读取涉及到的题库包的做题记录 + 自定义题型定义 */
    private suspend fun loadRecords(questions: List<Question>) {
        val merged = mutableMapOf<String, PracticeRecord>()
        val types = mutableMapOf<String, CustomQuestionType>()
        questions.map { it.packageName }.distinct().forEach { pkg ->
            repository.getPracticeRecords(pkg).forEach { (questionId, record) ->
                merged["$pkg#$questionId"] = record
            }
            repository.getCustomQuestionTypes(pkg).forEach { type ->
                types["$pkg#${type.id}"] = type
            }
        }
        _records.value = merged
        _customTypes.value = types
    }

    /** 当前题所属包的自定义题型定义（不是自定义题型返回 null） */
    fun customTypeOf(question: Question): CustomQuestionType? =
        _customTypes.value["${question.packageName}#${question.customType.orEmpty()}"]

    // ---------------- 导航 ----------------

    fun setCurrentIndex(index: Int) {
        if (index in _questions.value.indices) {
            _currentIndex.value = index
            resetAttemptState()
        } else {
            Log.w("PracticeVM", "Invalid index $index, size=${_questions.value.size}")
        }
    }

    fun movePrevious() {
        val index = _currentIndex.value
        if (index > 0) setCurrentIndex(index - 1)
    }

    fun moveNext() {
        val index = _currentIndex.value
        if (index < _questions.value.size - 1) setCurrentIndex(index + 1)
    }

    private fun resetAttemptState() {
        _revealed.value = false
        _selectedOption.value = null
        _selectedOptions.value = emptySet()
        _attemptRecorded.value = false
        _gradingResult.value = null
    }

    // ---------------- 作答 ----------------

    fun updateUserAnswer(answer: String) {
        val question = currentQuestion() ?: return
        _userAnswers.value = _userAnswers.value + (question.practiceKey to answer)
    }

    fun getUserAnswer(question: Question): String =
        _userAnswers.value[question.practiceKey] ?: ""

    fun updateFillBlankAnswer(questionKey: String, blankIndex: Int, text: String) {
        val current = _fillBlankAnswers.value[questionKey]?.toMutableList() ?: mutableListOf()
        while (current.size <= blankIndex) current.add("")
        current[blankIndex] = text
        _fillBlankAnswers.value = _fillBlankAnswers.value + (questionKey to current)
    }

    fun getFillBlankAnswers(questionKey: String): List<String> =
        _fillBlankAnswers.value[questionKey] ?: emptyList()

    // ---------------- 自定义多小问 ----------------

    fun updatePartAnswer(questionKey: String, partIndex: Int, text: String) {
        val current = _partAnswers.value[questionKey]?.toMutableList() ?: mutableListOf()
        while (current.size <= partIndex) current.add("")
        current[partIndex] = text
        _partAnswers.value = _partAnswers.value + (questionKey to current)
    }

    fun getPartAnswers(questionKey: String): List<String> =
        _partAnswers.value[questionKey] ?: emptyList()

    /** 把所有小问的作答拼成一段，用于保存记录 / AI 评分 */
    private fun compositeAnswer(question: Question): String =
        question.composePartsAnswer(getPartAnswers(question.practiceKey))

    /** 参考答案（多小问逐条拼接），交给 AI 评分用 */
    private fun referenceAnswer(question: Question): String {
        if (isSingleCustomType(question) || isImageTextCustomType(question)) {
            return question.correctAnswer.orEmpty()
        }
        val parts = question.resolvedParts()
        val joined = parts.mapIndexedNotNull { index, part ->
            part.answer?.takeIf { it.isNotBlank() }?.let { "（${index + 1}）$it" }
        }
        return if (joined.isNotEmpty()) joined.joinToString("\n")
        else question.correctAnswer.orEmpty()
    }

    /** 当前题保存记录/评分时使用的作答文本 */
    private fun answerFor(question: Question): String = when {
        question.type == QuestionType.FILL_BLANK ->
            getFillBlankAnswers(question.practiceKey).joinToString("; ")

        question.type == QuestionType.CUSTOM && isMultiChoiceCustomType(question) ->
            question.options.orEmpty()
                .filter { it in _selectedOptions.value }
                .joinToString("; ")

        question.type == QuestionType.CUSTOM && isImageTextCustomType(question) ->
            getUserAnswer(question)

        question.type == QuestionType.CUSTOM && !isSingleCustomType(question) ->
            compositeAnswer(question)

        question.type == QuestionType.DIAGRAM -> ""
        else -> getUserAnswer(question)
    }

    private fun isSingleCustomType(question: Question): Boolean =
        customTypeOf(question)?.isSingleLayout == true

    private fun isMultiChoiceCustomType(question: Question): Boolean =
        customTypeOf(question)?.isMultiChoiceLayout == true

    private fun isImageTextCustomType(question: Question): Boolean =
        customTypeOf(question)?.isImageTextLayout == true

    /** 自定义多选题：勾选/取消一个选项 */
    fun toggleOption(option: String) {
        if (_revealed.value || _attemptRecorded.value) return
        val current = _selectedOptions.value
        _selectedOptions.value = if (option in current) current - option else current + option
    }

    /** 自定义多选题：提交并自动判定（选项与 correctOptions 完全一致才算对） */
    fun submitMultiChoice() {
        if (_isSubmitting.value || _revealed.value || _attemptRecorded.value) return
        val question = currentQuestion() ?: return
        val selected = _selectedOptions.value
        if (selected.isEmpty() || question.correctOptions.isNullOrEmpty()) return
        val correct = question.isMultiChoiceCorrect(selected)
        _revealed.value = true
        _attemptRecorded.value = true
        val answerText = question.options.orEmpty().filter { it in selected }.joinToString("; ")
        recordResult(question, correct, if (correct) 100 else 0, answerText)
    }

    /** 选择题：点选选项即判定 */
    fun selectOption(option: String) {
        if (_revealed.value || _attemptRecorded.value) return
        val question = currentQuestion() ?: return
        _selectedOption.value = option
        _revealed.value = true
        _attemptRecorded.value = true
        val correct = question.correctAnswer?.trim().equals(option.trim(), ignoreCase = true)
        recordResult(question, correct, if (correct) 100 else 0, option)
    }

    /** 显示答案（不记录，等用户自我判定或 AI 评分） */
    fun revealAnswer() {
        if (_revealed.value) return
        _revealed.value = true
        _attemptRecorded.value = false
    }

    /** 自我判定：主观题/填空题/填图题对照答案后由用户点「答对了 / 答错了」 */
    fun selfJudge(correct: Boolean, answerOverride: String? = null) {
        if (_attemptRecorded.value) return
        val question = currentQuestion() ?: return
        val answer = answerOverride ?: answerFor(question)
        _attemptRecorded.value = true
        recordResult(question, correct, if (correct) 100 else 0, answer)
    }

    /** 重置当前题的记录（清空作答 + 从错题本移除） */
    fun resetCurrent() {
        val question = currentQuestion() ?: return
        viewModelScope.launch {
            try {
                repository.resetPractice(question.packageName, question.id)
                _records.value = _records.value - question.practiceKey
                _userAnswers.value = _userAnswers.value - question.practiceKey
                _fillBlankAnswers.value = _fillBlankAnswers.value - question.practiceKey
                _partAnswers.value = _partAnswers.value - question.practiceKey
                _selectedOptions.value = emptySet()
                resetAttemptState()
            } catch (e: Exception) {
                Log.e("PracticeVM", "resetPractice failed", e)
            }
        }
    }

    private fun recordResult(question: Question, correct: Boolean, score: Int, userAnswer: String) {
        viewModelScope.launch {
            try {
                val record = repository.recordPractice(
                    packageName = question.packageName,
                    questionId = question.id,
                    correct = correct,
                    score = score,
                    userAnswer = userAnswer
                )
                _records.value = _records.value + (question.practiceKey to record)
            } catch (e: Exception) {
                Log.e("PracticeVM", "recordPractice failed", e)
            }
        }
    }

    // ---------------- AI 评分 ----------------

    fun submitCurrentAnswer() {
        if (_isSubmitting.value || _attemptRecorded.value) return
        val question = currentQuestion() ?: return
        // 自定义题型只有声明了 grading = "ai" 才允许 AI 评分；多选题走自动判定
        if (question.type == QuestionType.CUSTOM) {
            val definition = customTypeOf(question)
            if (definition?.useAiGrading != true || definition.isMultiChoiceLayout) return
        }
        val userAnswer = answerFor(question)
        if (userAnswer.isBlank()) return

        _isSubmitting.value = true
        _gradingResult.value = null

        viewModelScope.launch {
            try {
                val result = when (question.type) {
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

                    QuestionType.CUSTOM -> {
                        currentScorer()?.evaluateComposite(
                            question.stem,
                            referenceAnswer(question),
                            userAnswer
                        ) ?: GradingResult(0, "未配置 API Key", emptyList(), emptyList(), "请在设置中填写")
                    }

                    else -> GradingResult(0, "暂不支持此题型", emptyList(), emptyList(), "")
                }
                _gradingResult.value = result
                _revealed.value = true
                _attemptRecorded.value = true
                recordResult(question, result.score >= 60, result.score, userAnswer)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** 填图题：按标签自动比对打分 */
    fun submitDiagramAnswers(labelAnswers: Map<String, String>) {
        if (_isSubmitting.value || _attemptRecorded.value) return
        val question = currentQuestion() ?: return
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
                suggestions = if (score < 100) "复习对应结构" else ""
            )
            _gradingResult.value = result
            _revealed.value = true
            _attemptRecorded.value = true
            recordResult(
                question = question,
                correct = score >= 60,
                score = score,
                userAnswer = labelAnswers.values.joinToString("; ")
            )
        } finally {
            _isSubmitting.value = false
        }
    }
}
