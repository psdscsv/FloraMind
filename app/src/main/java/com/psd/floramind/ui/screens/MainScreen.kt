package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.launch

@Composable
fun MainScreen(navController: NavController, repository: QuestionRepository) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repository.loadBuiltInQuestionsIfEmpty()
    }

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
                1 -> PackageListScreen(repository, navController)
                2 -> MistakeScreen(repository, navController)
                3 -> SettingsScreen(repository)
            }
        }
    }
}

@Composable
fun PracticeStartScreen(repository: QuestionRepository, navController: NavController) {
    var questions by remember { mutableStateOf<List<com.psd.floramind.model.Question>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        questions = repository.getAllQuestions()
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("启动后如果没有内置题库，可以在设置里恢复。\n因为没有放图片，所以识图题是空白的。另外由于部分填空题的答案是“略”，题库录入时并没有对此处理，所以看到超多空的填空题可以不管。", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("加载题库中...")
            }
            questions.isNullOrEmpty() -> {
                Text("题库为空，请检查 assets/question_bank.json 是否存在")
            }
            else -> {
                val list = questions!!
                Button(
                    onClick = {
                        if (list.isNotEmpty()) {
                            val random = list.random()
                            navController.navigate("practice/${Uri.encode(random.packageName)}/${random.id}")
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
                            val first = list.first()
                            navController.navigate("practice/${Uri.encode(first.packageName)}/${first.id}")
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