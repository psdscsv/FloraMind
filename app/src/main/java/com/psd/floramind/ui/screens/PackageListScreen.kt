package com.psd.floramind.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.repository.QuestionRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageListScreen(repository: QuestionRepository, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPackageName by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // 刷新触发器：改变时强制刷新题目数量
    var refreshTrigger by remember { mutableStateOf(0) }

    // 删除模式状态
    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val packageName = importPackageName.ifBlank { "未命名题库" }
                    Log.d("PackageList", "Importing package: $packageName from $uri")
                    val count = repository.importPackageFromZip(it, packageName)
                    snackbarMessage = if (count > 0) "成功导入 $count 道题目" else "导入失败，请检查压缩包格式"
                    // 刷新列表和数量
                    packages = repository.getAllPackages()
                    refreshTrigger++
                } catch (e: Exception) {
                    Log.e("PackageList", "Import failed", e)
                    snackbarMessage = "导入失败: ${e.message}"
                }
                showImportDialog = false
                importPackageName = ""
            }
        }
    }

    // 加载包列表
    LaunchedEffect(Unit) {
        isLoading = true
        packages = repository.getAllPackages()
        isLoading = false
    }

    // 执行删除
    fun performDelete() {
        scope.launch {
            val toDelete = selectedPackages.toList() // 复制一份，避免并发修改
            toDelete.forEach { pkg ->
                repository.deletePackage(pkg)
            }
            selectedPackages = emptySet()
            isDeleteMode = false
            packages = repository.getAllPackages()
            refreshTrigger++
            snackbarMessage = "已删除 ${toDelete.size} 个题库包"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库包") },
                actions = {
                    IconButton(onClick = { isDeleteMode = !isDeleteMode }) {
                        Icon(
                            if (isDeleteMode) Icons.Default.Close else Icons.Default.Delete,
                            contentDescription = if (isDeleteMode) "退出删除模式" else "删除题库"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isDeleteMode) {
                FloatingActionButton(onClick = { showImportDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "导入题库")
                }
            }
        }
        // 注意：移除了 snackbarHost 参数
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                packages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("暂无题库包，点击右下角 + 导入")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packages, key = { it }) { pkg ->
                            val isSelected = selectedPackages.contains(pkg)
                            val count by produceState(initialValue = 0, key1 = pkg, key2 = refreshTrigger) {
                                value = repository.getQuestionCount(pkg)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDeleteMode) {
                                        navController.navigate("package_questions/$pkg")
                                    },
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isDeleteMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedPackages = if (it) {
                                                    selectedPackages + pkg
                                                } else {
                                                    selectedPackages - pkg
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = pkg, style = MaterialTheme.typography.titleMedium)
                                        Text(text = "$count 题", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!isDeleteMode) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 删除模式底部按钮
                    if (isDeleteMode && selectedPackages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Button(
                                onClick = { performDelete() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("删除选中的 ${selectedPackages.size} 个题库包")
                            }
                        }
                    }
                }
            }

            // Snackbar 覆盖层（始终显示）
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }

    // 导入对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入题库包") },
            text = {
                Column {
                    Text("请选择 ZIP 压缩包，内部需包含 questions.json 及图片文件夹。")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPackageName,
                        onValueChange = { importPackageName = it },
                        label = { Text("题库包名称（可选）") },
                        placeholder = { Text("留空则使用默认名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Log.d("PackageList", "Confirm button clicked, launching file picker")
                    importLauncher.launch(arrayOf("application/zip"))
                }) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importPackageName = ""
                }) {
                    Text("取消")
                }
            }
        )
    }
}