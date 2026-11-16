package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.practiceKey
import com.psd.floramind.repository.MistakeEntry
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.session.PracticeSessionHolder
import com.psd.floramind.viewmodel.MistakeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var pendingDelete by remember { mutableStateOf<MistakeEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onBack = rememberBackAction(navController)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packageName.substringAfterLast('/')) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        enabled = mistakes.isNotEmpty(),
                        onClick = {
                            // 只刷本题库的错题
                            PracticeSessionHolder.pendingQuestions =
                                mistakes.map { it.question }
                            PracticeSessionHolder.startIndex = 0
                            navController.navigate("practice_filtered")
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "刷这些错题")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mistakes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("该题库下暂无错题")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "做错会记进题库包的 practice.json，答对后自动移出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "点开可重新作答，右上角可以连刷这些错题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(mistakes, key = { it.question.practiceKey }) { entry ->
                        val record = entry.record
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(
                                        "practice/${Uri.encode(entry.question.packageName)}/${entry.question.id}"
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            entry.question.type.label(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "错 ${record.wrong} 次 / 对 ${record.right} 次",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formatTime(record.lastTime),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        entry.question.stem,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    record.lastAnswer?.takeIf { it.isNotBlank() }?.let { answer ->
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "你上次答: $answer",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { pendingDelete = entry }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "移出错题本",
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
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("移出错题本") },
            text = {
                Text(
                    "会清空这道题（题号 ${entry.question.id}）的做题记录，" +
                            "题库包里对应的记录也会一起删掉。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMistake(entry.question.packageName, entry.question.id) {
                        scope.launch { snackbarHostState.showSnackbar("已移出错题本") }
                    }
                    pendingDelete = null
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
