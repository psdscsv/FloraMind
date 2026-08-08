package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
fun PackageListScreen(repository: QuestionRepository, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPackageName by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        packages = repository.getAllPackages()
        isLoading = false
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val count = repository.importPackageFromZip(it, importPackageName.ifBlank { "未命名题库" })
                    snackbarMessage = "成功导入 $count 道题目"
                    packages = repository.getAllPackages()
                } catch (e: Exception) {
                    snackbarMessage = "导入失败: ${e.message}"
                }
                showImportDialog = false
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "导入题库")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                packages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("暂无题库包，点击右下角 + 导入")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(packages) { pkg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("package_questions/$pkg") },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = pkg, style = MaterialTheme.typography.titleMedium)
                                val count by produceState(0) {
                                    value = repository.getQuestionCount(pkg)
                                }
                                Text(text = "$count 题", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            snackbarMessage?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) { Text("关闭") }
                    }
                ) {
                    Text(it)
                }
            }
        }
    }

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
                        placeholder = { Text("留空则使用文件名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importPackageName.isBlank()) {
                        snackbarMessage = "请输入题库名称"
                        return@TextButton
                    }
                    importLauncher.launch(arrayOf("application/zip"))
                }) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }
}