package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.MistakeViewModel

@Composable
fun MistakeScreen(repository: QuestionRepository, navController: NavController) {
    val viewModel = remember { MistakeViewModel(repository) }
    LaunchedEffect(Unit) { viewModel.loadMistakes() }
    val mistakes by viewModel.mistakes.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (mistakes.isEmpty()) {
            item {
                Text("暂无错题", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            items(mistakes) { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = {
                        navController.navigate("practice/${record.packageName}/${record.questionId}")
                    }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("问题ID: ${record.questionId}")
                        Text("你的答案: ${record.userAnswer}")
                        Text("得分: ${record.score}")
                        Button(onClick = {
                            navController.navigate("practice/${record.packageName}/${record.questionId}")
                        }) {
                            Text("重新练习")
                        }
                    }
                }
            }
        }
    }
}