package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.CustomQuestionType
import com.psd.floramind.model.Question
import com.psd.floramind.model.QuestionType
import com.psd.floramind.repository.QuestionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListByPackageScreen(
    packageName: String,
    repository: QuestionRepository,
    navController: NavController
) {
    var questions by remember { mutableStateOf<List<Question>?>(null) }
    var customTypes by remember { mutableStateOf<Map<String, CustomQuestionType>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    val onBack = rememberBackAction(navController)

    LaunchedEffect(packageName) {
        isLoading = true
        questions = repository.getQuestionsByPackage(packageName)
        customTypes = repository.getCustomTypeMap(packageName)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packageName.substringAfterLast('/')) },
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
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                questions.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("该题库包暂无题目")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (customTypes.isNotEmpty()) {
                        item {
                            Text(
                                "本包自定义题型：" + customTypes.values.joinToString("、") { it.safeName() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    items(questions!!, key = { "${it.packageName}_${it.id}" }) { question ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // ★ 关键：包路径含 '/', 必须 encode
                                    navController.navigate(
                                        "practice/${Uri.encode(question.packageName)}/${question.id}"
                                    )
                                },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ID: ${question.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = question.displayTypeName(customTypes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = question.stem,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (question.type == QuestionType.CUSTOM &&
                                    !question.parts.isNullOrEmpty()
                                ) {
                                    Text(
                                        text = "共 ${question.parts!!.size} 个小问",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else {
                                    question.correctAnswer?.let {
                                        Text(
                                            text = "正确答案: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}