package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.Question
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.launch

@Composable
fun MainScreen(navController: NavController, repository: QuestionRepository) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.School, contentDescription = "练习") },
                    label = { Text("练习") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "题库") },
                    label = { Text("题库") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Book, contentDescription = "错题本") },
                    label = { Text("错题本") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PracticeStartScreen(repository, navController)
                2 -> MistakeScreen(repository, navController)
                3 -> SettingsScreen(repository)
                1 -> QuestionListScreen(repository)
            }
        }
    }
}

@Composable
fun PracticeStartScreen(repository: QuestionRepository, navController: NavController) {
    val context = LocalContext.current
    var questions by remember { mutableStateOf<List<Question>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            repository.loadQuestionsFromAssets(context)
            questions = repository.getAllQuestions()
        } catch (e: Exception) {
            questions = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("动植智记 - 练习", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("加载题库中...")
            }
            questions.isNullOrEmpty() -> {
                Text("题库为空，请检查 assets/question_bank.json 是否存在且格式正确")
            }
            else -> {
                val list = questions!!
                Button(
                    onClick = {
                        if (list.isNotEmpty()) {
                            val randomId = list.random().id
                            navController.navigate("practice/$randomId")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("随机练习")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (list.isNotEmpty()) {
                            navController.navigate("practice/${list.first().id}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从第一题开始")
                }
            }
        }
    }
}

// ---------- 🟢 新增：题库列表页面 ----------
@Composable
fun QuestionListScreen(repository: QuestionRepository) {
    var questions by remember { mutableStateOf<List<Question>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            questions = repository.getAllQuestions()
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        questions.isNullOrEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("题库为空")
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(questions!!, key = { it.id }) { question ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                                    text = question.type.name,
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
                            // 可选的额外信息（如正确答案）
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