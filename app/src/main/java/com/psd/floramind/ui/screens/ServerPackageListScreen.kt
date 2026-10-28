package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.network.ServerPackage
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.repository.ServerRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPackageListScreen(
    serverRepository: ServerRepository,
    questionRepository: QuestionRepository,
    navController: NavController
) {
    var packages by remember { mutableStateOf<List<ServerPackage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        isLoading = true
        errorMsg = null
        serverRepository.listPackages()
            .onSuccess { packages = it }
            .onFailure { errorMsg = it.message ?: "加载失败" }
        isLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器题库") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                errorMsg != null -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "加载失败：$errorMsg",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { refresh() } }) { Text("重试") }
                }

                packages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("服务器暂无题库")
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(packages, key = { it.id }) { pkg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pkg.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (!pkg.description.isNullOrBlank()) {
                                        Text(
                                            pkg.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                    Text(
                                        "${pkg.questionCount} 题 · " +
                                                "${formatSize(pkg.fileSize)} · " +
                                                "下载 ${pkg.downloadCount} 次",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        buildString {
                                            if (!pkg.uploader.isNullOrBlank()) {
                                                append("上传者: ${pkg.uploader}  ")
                                            }
                                            append(formatTime(pkg.uploadTime))
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (downloadingId == pkg.id) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                downloadingId = pkg.id
                                                serverRepository.downloadPackage(pkg)
                                                    .onSuccess { file ->
                                                        val res = questionRepository
                                                            .importZipToFolder(
                                                                Uri.fromFile(file),
                                                                ""
                                                            )
                                                        snackbar = res.error
                                                            ?: "下载并导入成功：${res.importedQuestionCount} 题"
                                                        file.delete()
                                                    }
                                                    .onFailure {
                                                        snackbar = "下载失败: ${it.message}"
                                                    }
                                                downloadingId = null
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = "下载并导入"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            snackbar?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { snackbar = null }) { Text("关闭") }
                    }
                ) { Text(it) }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatTime(ts: Long): String {
    if (ts <= 0) return ""
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ts * 1000))
}