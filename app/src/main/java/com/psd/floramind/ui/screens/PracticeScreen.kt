package com.psd.floramind.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.model.LabelPoint
import com.psd.floramind.model.PracticeRecord
import com.psd.floramind.model.Question
import com.psd.floramind.model.QuestionPart
import com.psd.floramind.model.QuestionType
import com.psd.floramind.model.network.GradingResult
import com.psd.floramind.model.practiceKey
import com.psd.floramind.model.resolvedParts
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.PracticeViewModel

// 与参考页面一致的判对/判错配色
private val CorrectContainer = Color(0xFFE8F6EE)
private val CorrectBorderColor = Color(0xFF4F9F74)
private val CorrectContent = Color(0xFF1C6344)
private val WrongContainer = Color(0xFFFCECEB)
private val WrongBorderColor = Color(0xFFD4807A)
private val WrongContent = Color(0xFFA33B34)
private val AnswerContainer = Color(0xFFF8FAF7)
private val AnswerBorderColor = Color(0xFFC9D8CC)
private val AnswerLabelColor = Color(0xFF6D8A78)

private val AI_GRADABLE_TYPES = setOf(
    QuestionType.DEFINITION,
    QuestionType.SHORT_ANSWER,
    QuestionType.ESSAY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    packageName: String,
    questionId: Int,
    repository: QuestionRepository,
    navController: NavController,
    questionsOverride: List<Question>? = null,
    startIndexOverride: Int? = null
) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    var apiKey by remember { mutableStateOf("") }

    // ★ 先创建 ViewModel（初始 apiKey 传空串，后续通过 setApiKey 更新）
    val viewModel = remember { PracticeViewModel(repository, "") }

    LaunchedEffect(Unit) {
        dataStore.apiKeyFlow.collect { key ->
            apiKey = key
            if (key.isNotBlank()) viewModel.setApiKey(key)
        }
    }

    LaunchedEffect(packageName, questionId, questionsOverride, startIndexOverride) {
        if (questionsOverride != null && questionsOverride.isNotEmpty()) {
            viewModel.loadQuestionsFromList(questionsOverride)
            val idx = startIndexOverride?.coerceIn(0, questionsOverride.size - 1) ?: 0
            viewModel.setCurrentIndex(idx)
        } else {
            viewModel.loadQuestionsForPackage(packageName)
            val loaded = viewModel.questions.value
            val index = loaded.indexOfFirst { it.id == questionId }
            viewModel.setCurrentIndex(if (index >= 0) index else 0)
        }
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val gradingResult by viewModel.gradingResult.collectAsState()
    val userAnswers by viewModel.userAnswers.collectAsState()
    val fillBlankAnswers by viewModel.fillBlankAnswers.collectAsState()
    val partAnswers by viewModel.partAnswers.collectAsState()
    val customTypes by viewModel.customTypes.collectAsState()
    val records by viewModel.records.collectAsState()
    val revealed by viewModel.revealed.collectAsState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val attemptRecorded by viewModel.attemptRecorded.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    var diagramAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCoordinateDialog by remember { mutableStateOf(false) }
    var clickedPoint by remember { mutableStateOf<Offset?>(null) }
    val onBack = rememberBackAction(navController)

    // 换题时清空填图作答
    LaunchedEffect(currentIndex) { diagramAnswers = emptyMap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("练习")
                        if (questions.isNotEmpty() && currentIndex in questions.indices) {
                            Text(
                                "第 ${currentIndex + 1} / ${questions.size} 题",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("加载题目中...")
                    }
                }

                questions.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("该题库包暂无题目")
                    }
                }

                else -> {
                    val question = questions.getOrNull(currentIndex)
                    if (question == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("当前题目不存在")
                        }
                    } else {
                        val questionKey = question.practiceKey
                        val record = records[questionKey]
                        val currentAnswer = userAnswers[questionKey] ?: ""
                        val customType = customTypes["${question.packageName}#${question.customType.orEmpty()}"]
                        val typeName = if (question.type == QuestionType.CUSTOM) {
                            question.customTypeName(customType)
                        } else {
                            question.type.label()
                        }
                        val customParts = if (question.type == QuestionType.CUSTOM) {
                            question.resolvedParts()
                        } else {
                            emptyList()
                        }
                        val customPartAnswers = partAnswers[questionKey] ?: emptyList()
                        val customIsParts = customType?.isSingleLayout != true
                        val aiGradable = question.type in AI_GRADABLE_TYPES ||
                                (question.type == QuestionType.CUSTOM && customType?.useAiGrading == true)
                        val hasAnswer = if (question.type == QuestionType.CUSTOM && customIsParts) {
                            customParts.indices.any {
                                !customPartAnswers.getOrNull(it).isNullOrBlank()
                            }
                        } else {
                            currentAnswer.isNotBlank()
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                QuestionHeader(
                                    question = question,
                                    record = record,
                                    index = currentIndex,
                                    total = questions.size,
                                    typeName = typeName
                                )
                            }

                            if (question.type != QuestionType.FILL_BLANK) {
                                item {
                                    Text(
                                        text = question.stem,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            item {
                                when (question.type) {
                                    QuestionType.CHOICE -> ChoiceOptions(
                                        question = question,
                                        selectedOption = selectedOption,
                                        revealed = revealed,
                                        onSelect = { viewModel.selectOption(it) }
                                    )

                                    QuestionType.FILL_BLANK -> FillBlankInput(
                                        question = question,
                                        answers = fillBlankAnswers[questionKey] ?: emptyList(),
                                        onAnswerChange = { index, text ->
                                            viewModel.updateFillBlankAnswer(questionKey, index, text)
                                        }
                                    )

                                    QuestionType.DIAGRAM -> DiagramInput(
                                        q = question,
                                        repository = repository,
                                        diagramAnswers = diagramAnswers,
                                        onDiagramAnswerChange = { diagramAnswers = it },
                                        onShowDialog = { point ->
                                            clickedPoint = point
                                            showCoordinateDialog = true
                                        }
                                    )

                                    QuestionType.CUSTOM -> if (customIsParts) {
                                        CustomPartsInput(
                                            parts = customParts,
                                            answers = customPartAnswers,
                                            onAnswerChange = { index, text ->
                                                viewModel.updatePartAnswer(questionKey, index, text)
                                            }
                                        )
                                    } else {
                                        EssayInput(
                                            text = currentAnswer,
                                            onTextChange = { viewModel.updateUserAnswer(it) }
                                        )
                                    }

                                    else -> EssayInput(
                                        text = currentAnswer,
                                        onTextChange = { viewModel.updateUserAnswer(it) }
                                    )
                                }
                            }

                            if (revealed) {
                                item {
                                    AnswerBlock(
                                        question = question,
                                        showParts = question.type == QuestionType.CUSTOM &&
                                                (customIsParts || question.parts.orEmpty().isNotEmpty())
                                    )
                                }
                            }

                            if (gradingResult != null) {
                                item { ResultCard(gradingResult!!) }
                            }

                            if (!revealed && !attemptRecorded) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.revealAnswer() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("显示答案")
                                        }
                                        if (aiGradable && !isSubmitting) {
                                            Button(
                                                onClick = { viewModel.submitCurrentAnswer() },
                                                enabled = apiKey.isNotBlank() && hasAnswer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("AI 评分")
                                            }
                                            if (apiKey.isBlank()) {
                                                Text(
                                                    "未配置 API Key：可直接显示答案后自我判定",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isSubmitting) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("AI 评分中...", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (question.type == QuestionType.DIAGRAM) {
                                            Button(
                                                onClick = { viewModel.submitDiagramAnswers(diagramAnswers) },
                                                enabled = !isSubmitting,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("提交判定")
                                            }
                                        }
                                    }
                                }
                            }

                            if (revealed && !attemptRecorded) {
                                item {
                                    SelfJudgeRow(
                                        onCorrect = {
                                            viewModel.selfJudge(
                                                correct = true,
                                                answerOverride = if (question.type == QuestionType.DIAGRAM)
                                                    diagramAnswers.values.joinToString("; ") else null
                                            )
                                        },
                                        onWrong = {
                                            viewModel.selfJudge(
                                                correct = false,
                                                answerOverride = if (question.type == QuestionType.DIAGRAM)
                                                    diagramAnswers.values.joinToString("; ") else null
                                            )
                                        }
                                    )
                                }
                            }

                            if (attemptRecorded) {
                                item {
                                    Text(
                                        "已记录本题结果。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CorrectContent
                                    )
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.movePrevious() },
                                        enabled = currentIndex > 0 && !isSubmitting,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("← 上一题") }
                                    Button(
                                        onClick = { viewModel.moveNext() },
                                        enabled = currentIndex < questions.size - 1 && !isSubmitting,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("下一题 →") }
                                }
                            }

                            item {
                                TextButton(
                                    onClick = {
                                        diagramAnswers = emptyMap()
                                        viewModel.resetCurrent()
                                    }
                                ) { Text("重置本题记录") }
                            }
                        }
                    }
                }
            }

            if (showCoordinateDialog && clickedPoint != null) {
                val labels = questions.getOrNull(currentIndex)?.labels ?: emptyList()
                DiagramLabelDialog(
                    clickedPoint = clickedPoint!!,
                    labels = labels,
                    onDismiss = { showCoordinateDialog = false },
                    onSave = { index, text ->
                        diagramAnswers = diagramAnswers + (index.toString() to text)
                        showCoordinateDialog = false
                    }
                )
            }
        }
    }
}

// ===================== 题目头部 =====================

@Composable
private fun QuestionHeader(
    question: Question,
    record: PracticeRecord?,
    index: Int,
    total: Int,
    typeName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(typeName)
            TagChip(question.packageName.substringAfterLast('/'))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "第 ${index + 1} / $total 题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "本题 对 ${record?.right ?: 0} 错 ${record?.wrong ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        record?.lastAnswer?.takeIf { it.isNotBlank() }?.let { last ->
            Text(
                "上次作答：$last",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===================== 选择题 =====================

@Composable
private fun ChoiceOptions(
    question: Question,
    selectedOption: String?,
    revealed: Boolean,
    onSelect: (String) -> Unit
) {
    val options = question.options.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        options.forEachIndexed { index, option ->
            val isAnswer = question.correctAnswer?.trim()
                ?.equals(option.trim(), ignoreCase = true) == true
            val isPicked = selectedOption?.trim() == option.trim()
            ChoiceOptionRow(
                letter = ('A' + index).toString(),
                text = option,
                revealed = revealed,
                isAnswer = isAnswer,
                isPicked = isPicked,
                onClick = { onSelect(option) }
            )
        }
        if (!revealed) {
            Text(
                "点击选项即可判定对错",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChoiceOptionRow(
    letter: String,
    text: String,
    revealed: Boolean,
    isAnswer: Boolean,
    isPicked: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        revealed && isAnswer -> CorrectContainer
        revealed && isPicked -> WrongContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when {
        revealed && isAnswer -> CorrectBorderColor
        revealed && isPicked -> WrongBorderColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = when {
        revealed && isAnswer -> CorrectContent
        revealed && isPicked -> WrongContent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dimmed = revealed && !isAnswer && !isPicked

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.55f else 1f)
            .clickable(enabled = !revealed, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$letter. ",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                textDecoration = if (revealed && isPicked && !isAnswer) {
                    TextDecoration.LineThrough
                } else {
                    null
                }
            )
            if (revealed && isAnswer) {
                Icon(Icons.Default.Check, contentDescription = null, tint = CorrectBorderColor)
            } else if (revealed && isPicked) {
                Icon(Icons.Default.Close, contentDescription = null, tint = WrongBorderColor)
            }
        }
    }
}

// ===================== 填空 / 主观题 =====================

@Composable
private fun FillBlankInput(
    question: Question,
    answers: List<String>,
    onAnswerChange: (Int, String) -> Unit
) {
    val blankRegex = remember { Regex("_{2,}") }
    val parts = remember(question.practiceKey, question.stem) { question.stem.split(blankRegex) }
    val blankCount = parts.size - 1

    if (blankCount <= 0) {
        OutlinedTextField(
            value = answers.firstOrNull() ?: "",
            onValueChange = { onAnswerChange(0, it) },
            label = { Text("填入答案") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (part.isNotBlank()) {
                Text(text = part, style = MaterialTheme.typography.bodyLarge)
            }
            if (index < blankCount) {
                OutlinedTextField(
                    value = answers.getOrNull(index) ?: "",
                    onValueChange = { newValue -> onAnswerChange(index, newValue) },
                    label = { Text("第 ${index + 1} 空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EssayInput(text: String, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("你的回答") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        maxLines = 8
    )
}

/** 自定义题型的「多小问」作答区：每个小问题干 + 独立作答框 */
@Composable
private fun CustomPartsInput(
    parts: List<QuestionPart>,
    answers: List<String>,
    onAnswerChange: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEachIndexed { index, part ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val partStem = part.stem.trim()
                if (partStem.isNotBlank()) {
                    Text(
                        text = partStem,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                OutlinedTextField(
                    value = answers.getOrNull(index) ?: "",
                    onValueChange = { newValue -> onAnswerChange(index, newValue) },
                    label = { Text("第 ${index + 1} 小问") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
            }
        }
    }
}

// ===================== 答案 / 自我判定 =====================

@Composable
private fun AnswerBlock(question: Question, showParts: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AnswerContainer,
        border = BorderStroke(1.dp, AnswerBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "答案",
                style = MaterialTheme.typography.labelSmall,
                color = AnswerLabelColor
            )
            Spacer(Modifier.height(4.dp))
            when {
                // 自定义题型的多个小问：逐个小问给出参考答案
                showParts -> {
                    val parts = question.resolvedParts()
                    parts.forEachIndexed { index, part ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        Text(
                            "（${index + 1}）" + (
                                    part.answer?.takeIf { it.isNotBlank() }
                                        ?: "（无参考答案）"
                                    ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val partKeywords = part.keywords.orEmpty().filter { it.isNotBlank() }
                        if (partKeywords.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "关键词：" + partKeywords.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                question.type == QuestionType.DIAGRAM -> {
                    val labels = question.labels.orEmpty()
                    Text(
                        if (labels.isEmpty()) "（本题暂无标注答案）"
                        else "需标注部位：" + labels.joinToString(" · ") { it.correctAnswer },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                else -> {
                    Text(
                        question.correctAnswer?.takeIf { it.isNotBlank() }
                            ?: "（本题暂无参考答案）",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val keywords = question.keywords.orEmpty().filter { it.isNotBlank() }
                    if (keywords.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "关键词：" + keywords.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelfJudgeRow(onCorrect: () -> Unit, onWrong: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "对照答案自我判定：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCorrect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CorrectContainer,
                    contentColor = CorrectContent
                ),
                border = BorderStroke(1.dp, CorrectBorderColor)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("答对了")
            }
            OutlinedButton(
                onClick = onWrong,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = WrongContainer,
                    contentColor = WrongContent
                ),
                border = BorderStroke(1.dp, WrongBorderColor)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("答错了")
            }
        }
    }
}

// ===================== 填图 =====================

@Composable
private fun DiagramInput(
    q: Question,
    repository: QuestionRepository,
    diagramAnswers: Map<String, String>,
    onDiagramAnswerChange: (Map<String, String>) -> Unit,
    onShowDialog: (Offset) -> Unit
) {
    val context = LocalContext.current
    Column {
        if (q.imageName != null) {
            val imageFile = repository.getImageFile(q.packageName, q.imageName)
            val imageUri = if (imageFile != null && imageFile.exists()) {
                imageFile
            } else {
                "file:///android_asset/${q.imageName}"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(q.practiceKey) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                val width = size.width.toFloat().takeIf { it > 0f } ?: 1f
                                val height = size.height.toFloat().takeIf { it > 0f } ?: 1f
                                onShowDialog(
                                    Offset(position.x / width, position.y / height)
                                )
                            }
                        }
                    }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(imageUri)
                            .build()
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val labels = q.labels.orEmpty()
        labels.forEachIndexed { index, _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}: ")
                OutlinedTextField(
                    value = diagramAnswers[index.toString()] ?: "",
                    onValueChange = { newValue ->
                        onDiagramAnswerChange(diagramAnswers + (index.toString() to newValue))
                    },
                    label = { Text("结构名称") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DiagramLabelDialog(
    clickedPoint: Offset,
    labels: List<LabelPoint>,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val nearestIndex = labels.indices.minByOrNull { idx ->
        val l = labels[idx]
        val dx = l.x - clickedPoint.x
        val dy = l.y - clickedPoint.y
        dx * dx + dy * dy
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标注结构") },
        text = {
            Column {
                Text(
                    "点击位置: (" + String.format("%.2f", clickedPoint.x) + ", " +
                            String.format("%.2f", clickedPoint.y) + ")"
                )
                Text("最近标签序号: ${nearestIndex?.let { it + 1 } ?: "无"}")
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("输入结构名称") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nearestIndex != null && inputText.isNotBlank()) {
                    onSave(nearestIndex, inputText)
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ===================== 评分结果 =====================

@Composable
fun ResultCard(result: GradingResult) {
    val matched = result.matchedPoints ?: emptyList()
    val missing = result.missingPoints ?: emptyList()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.score >= 60) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "得分: ${result.score}", style = MaterialTheme.typography.titleMedium)
            Text(text = "反馈: ${result.feedback}")
            if (matched.isNotEmpty()) {
                Text(text = "✅ 匹配要点: ${matched.joinToString()}")
            }
            if (missing.isNotEmpty()) {
                Text(text = "❌ 缺失要点: ${missing.joinToString()}")
            }
            if (!result.suggestions.isNullOrEmpty()) {
                Text(text = "💡 建议: ${result.suggestions}")
            }
        }
    }
}
