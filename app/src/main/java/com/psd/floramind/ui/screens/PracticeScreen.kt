package com.psd.floramind.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.model.Question
import com.psd.floramind.model.QuestionType
import com.psd.floramind.model.network.GradingResult
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.PracticeViewModel

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

    // ★ 监听 apiKey 变化，仅非空时同步给 ViewModel
    LaunchedEffect(Unit) {
        dataStore.apiKeyFlow.collect { key ->
            apiKey = key
            if (key.isNotBlank()) {
                viewModel.setApiKey(key)
            }
        }
    }

    LaunchedEffect(packageName, questionId, questionsOverride, startIndexOverride) {
        if (questionsOverride != null && questionsOverride.isNotEmpty()) {
            viewModel.loadQuestionsFromList(questionsOverride)
            val idx = startIndexOverride?.coerceIn(0, questionsOverride.size - 1) ?: 0
            viewModel.setCurrentIndex(idx)
        } else {
            viewModel.loadQuestionsForPackage(packageName)
            val qs = viewModel.questions.value
            val index = qs.indexOfFirst { it.id == questionId }
            if (index >= 0) {
                viewModel.setCurrentIndex(index)
            } else if (qs.isNotEmpty()) {
                viewModel.setCurrentIndex(0)
            }
        }
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val gradingResult by viewModel.gradingResult.collectAsState()
    val userAnswers by viewModel.userAnswers.collectAsState()

    // ★ 修改 4：提交中的 UI 状态由 ViewModel 控制
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    var diagramAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCoordinateDialog by remember { mutableStateOf(false) }
    var clickedPoint by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("练习") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                        val q = question
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (q.type != QuestionType.FILL_BLANK) {
                                item {
                                    Text(
                                        text = q.stem,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            item {
                                when (q.type) {
                                    QuestionType.CHOICE -> ChoiceInput(q, viewModel)
                                    QuestionType.FILL_BLANK -> FillBlankInput(q, viewModel)
                                    QuestionType.DEFINITION,
                                    QuestionType.SHORT_ANSWER,
                                    QuestionType.ESSAY ->
                                        EssayInput(q, viewModel, userAnswers)

                                    QuestionType.DIAGRAM -> DiagramInput(
                                        q = q,
                                        packageName = packageName,
                                        repository = repository,
                                        diagramAnswers = diagramAnswers,
                                        onDiagramAnswerChange = { newMap -> diagramAnswers = newMap },
                                        onShowDialog = { point ->
                                            clickedPoint = point
                                            showCoordinateDialog = true
                                        },
                                        viewModel = viewModel
                                    )
                                }
                            }

                            item {
                                val needApiKey = q.type == QuestionType.DEFINITION ||
                                        q.type == QuestionType.SHORT_ANSWER ||
                                        q.type == QuestionType.ESSAY
                                val apiKeyMissing = needApiKey && apiKey.isBlank()

                                Button(
                                    onClick = {
                                        if (q.type == QuestionType.DIAGRAM) {
                                            viewModel.submitDiagramAnswers(diagramAnswers)
                                        } else {
                                            viewModel.submitCurrentAnswer()
                                        }
                                    },
                                    enabled = !isSubmitting && !apiKeyMissing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = LocalContentColor.current
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("评分中...")
                                    } else {
                                        Text(
                                            when {
                                                apiKeyMissing -> "请先在设置中配置 API Key"
                                                else -> "提交答案"
                                            }
                                        )
                                    }
                                }
                            }

                            gradingResult?.let { result ->
                                item { ResultCard(result) }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = {
                                            val prev = currentIndex - 1
                                            if (prev >= 0) {
                                                diagramAnswers = emptyMap()
                                                viewModel.setCurrentIndex(prev)
                                            }
                                        },
                                        enabled = currentIndex > 0 && !isSubmitting
                                    ) { Text("上一题") }
                                    Button(
                                        onClick = {
                                            val next = currentIndex + 1
                                            if (next < questions.size) {
                                                diagramAnswers = emptyMap()
                                                viewModel.setCurrentIndex(next)
                                            }
                                        },
                                        enabled = currentIndex < questions.size - 1 && !isSubmitting
                                    ) { Text("下一题") }
                                }
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

// ===== 以下子组件保持不变 =====

@Composable
private fun ChoiceInput(q: Question, viewModel: PracticeViewModel) {
    val options = q.options ?: emptyList()
    var selected by remember(q.id) { mutableStateOf<String?>(null) }
    Column {
        options.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == option,
                    onClick = {
                        selected = option
                        viewModel.updateUserAnswer(option)
                    }
                )
                Text(option, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FillBlankInput(q: Question, viewModel: PracticeViewModel) {
    val blankRegex = remember { Regex("_{2,}") }
    val parts = remember(q.id, q.stem) { q.stem.split(blankRegex) }
    val blankCount = parts.size - 1

    val allAnswers by viewModel.fillBlankAnswers.collectAsState()
    val answers = allAnswers[q.id] ?: emptyList()

    if (blankCount <= 0) {
        val legacy = answers.firstOrNull() ?: ""
        OutlinedTextField(
            value = legacy,
            onValueChange = { viewModel.updateFillBlankAnswer(q.id, 0, it) },
            label = { Text("填入答案") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (part.isNotBlank()) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (index < blankCount) {
                val value = answers.getOrNull(index) ?: ""
                OutlinedTextField(
                    value = value,
                    onValueChange = { newVal ->
                        viewModel.updateFillBlankAnswer(q.id, index, newVal)
                    },
                    label = { Text("第 ${index + 1} 空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EssayInput(
    q: Question,
    viewModel: PracticeViewModel,
    userAnswers: Map<Int, String>
) {
    var text by remember(q.id) { mutableStateOf(userAnswers[q.id] ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            viewModel.updateUserAnswer(it)
        },
        label = { Text("你的回答") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        maxLines = 8
    )
}

@Composable
private fun DiagramInput(
    q: Question,
    packageName: String,
    repository: QuestionRepository,
    diagramAnswers: Map<String, String>,
    onDiagramAnswerChange: (Map<String, String>) -> Unit,
    onShowDialog: (Offset) -> Unit,
    viewModel: PracticeViewModel
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
                    .pointerInput(q.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.first().position
                                val size = size
                                val x = position.x / size.width.toFloat()
                                val y = position.y / size.height.toFloat()
                                onShowDialog(Offset(x, y))
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

        val labels = q.labels ?: emptyList()
        labels.forEachIndexed { index, _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}: ")
                OutlinedTextField(
                    value = diagramAnswers[index.toString()] ?: "",
                    onValueChange = { newVal ->
                        onDiagramAnswerChange(diagramAnswers + (index.toString() to newVal))
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
    labels: List<com.psd.floramind.model.LabelPoint>,
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
                Text("点击位置: (${String.format("%.2f", clickedPoint.x)}, ${String.format("%.2f", clickedPoint.y)})")
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