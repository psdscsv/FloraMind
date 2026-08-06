package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.launch

@Composable
fun MainScreen(navController: NavController, repository: QuestionRepository) {
    var selectedTab by remember { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("练习") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("错题本") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PracticeStartScreen(repository, navController)
                1 -> MistakeScreen(repository, navController)
                2 -> SettingsScreen(repository)
            }
        }
    }
}

@Composable
fun PracticeStartScreen(repository: QuestionRepository, navController: NavController) {
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<com.psd.floramind.model.Question>?>(null) }
    LaunchedEffect(Unit) {
        scope.launch {
            questions = repository.getAllQuestions()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("植物智记 - 练习", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val list = questions
            if (!list.isNullOrEmpty()) {
                val randomId = list.random().id
                navController.navigate("practice/$randomId")
            }
        }) {
            Text("随机练习")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (!questions.isNullOrEmpty()) {
                navController.navigate("practice/${questions!!.first().id}")
            }
        }) {
            Text("从第一题开始")
        }
    }
}