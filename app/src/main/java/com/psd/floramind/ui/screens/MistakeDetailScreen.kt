package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.StudyRecord
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.MistakeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeDetailScreen(
    packageName: String,
    repository: QuestionRepository,
    navController: NavController
) {
    val viewModel = remember(packageName) { MistakeViewModel(repository) }
    LaunchedEffect(packageName) { viewModel.loadMistakes(packageName) }
    val mistakes by viewModel.mistakes.collectAsState()

    var pendingDelete by remember { mutableStateOf<StudyRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packageName.substringAfterLast('/')) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mistakes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该题库下暂无错题")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mistakes, key = { it.id }) { record ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(
                                        "practice/${Uri.encode(record.packageName)}/${record.questionId}"
                                    )
                                },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "题号: ${record.questionId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "你的答案: ${record.userAnswer}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                    Text(
                                        "得分: ${record.score}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (record.score >= 60)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(onClick = { pendingDelete = record }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除错题记录") },
            text = { Text("确定要从错题本中删除题号 ${record.questionId} 的记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMistake(record) {
                        scope.launch {
                            snackbarHostState.showSnackbar("已删除")
                        }
                    }
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}