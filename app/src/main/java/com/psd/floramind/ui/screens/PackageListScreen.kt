package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.psd.floramind.repository.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageListScreen(repository: QuestionRepository, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentFolderPath by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var packages by remember { mutableStateOf<List<PackageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // 对话框状态
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // path to isFolder
    var renameText by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var itemToMove by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var moveTarget by remember { mutableStateOf("") }

    suspend fun refresh() {
        isLoading = true
        val (f, p) = repository.getFolderChildren(currentFolderPath)
        folders = f; packages = p
        isLoading = false
    }

    LaunchedEffect(currentFolderPath, refreshTrigger) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val res = repository.importZipToFolder(it, currentFolderPath)
                snackbarMessage = res.error ?: "成功导入 ${res.importedQuestionCount} 道题目"
                refreshTrigger++
                showImport = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val file = repository.exportFolderToZip(currentFolderPath)
                if (file != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    snackbarMessage = "导出成功"
                } else snackbarMessage = "导出失败"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentFolderPath.isEmpty()) "题库管理"
                        else currentFolderPath.substringAfterLast('/')
                    )
                },
                navigationIcon = {
                    if (currentFolderPath.isNotEmpty()) {
                        IconButton(onClick = {
                            currentFolderPath = currentFolderPath.substringBeforeLast('/', "")
                        }) { Icon(Icons.Default.ArrowBack, "返回上级") }
                    }
                },
                actions = {
                    IconButton(onClick = { newFolderName = ""; showCreateFolder = true }) {
                        Icon(Icons.Default.CreateNewFolder, "新建文件夹")
                    }
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.FileUpload, "导入")
                    }
                    IconButton(onClick = {
                        exportLauncher.launch("题库_${System.currentTimeMillis()}.zip")
                    }) { Icon(Icons.Default.FileDownload, "导出") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                folders.isEmpty() && packages.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("当前文件夹为空，点击右上角导入或新建")
                    }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 面包屑
                    if (currentFolderPath.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { currentFolderPath = "" }) { Text("根目录") }
                                currentFolderPath.split('/').forEachIndexed { idx, seg ->
                                    Text("/")
                                    TextButton(onClick = {
                                        currentFolderPath = currentFolderPath
                                            .split('/').take(idx + 1).joinToString("/")
                                    }) { Text(seg) }
                                }
                            }
                        }
                    }

                    items(folders, key = { "folder_${it.path}" }) { folder ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { currentFolderPath = folder.path },
                                    onLongClick = {
                                        itemToRename = folder.path to true
                                        renameText = folder.name
                                    }
                                ),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${folder.packageCount} 个题库",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { itemToMove = folder.path to true; moveTarget = "" }) {
                                    Icon(Icons.Default.DriveFileMove, "移动")
                                }
                                IconButton(onClick = { itemToDelete = folder.path to true }) {
                                    Icon(Icons.Default.Delete, "删除",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    items(packages, key = { "pkg_${it.fullPath}" }) { pkg ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("package_questions/${Uri.encode(pkg.fullPath)}")
                                    },
                                    onLongClick = {
                                        itemToRename = pkg.fullPath to false
                                        renameText = pkg.name
                                    }
                                ),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${pkg.questionCount} 题",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { itemToMove = pkg.fullPath to false; moveTarget = currentFolderPath }) {
                                    Icon(Icons.Default.DriveFileMove, "移动")
                                }
                                IconButton(onClick = { itemToDelete = pkg.fullPath to false }) {
                                    Icon(Icons.Default.Delete, "删除",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            snackbarMessage?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = { TextButton(onClick = { snackbarMessage = null }) { Text("关闭") } }
                ) { Text(it) }
            }
        }
    }

    // ====== 新建文件夹对话框 ======
    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newFolderName.trim()
                    if (name.isNotEmpty() && !name.contains('/')) {
                        scope.launch {
                            val path = if (currentFolderPath.isEmpty()) name else "$currentFolderPath/$name"
                            repository.createFolder(path)
                            refreshTrigger++
                            showCreateFolder = false
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("取消") } }
        )
    }

    // ====== 导入对话框 ======
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入题库") },
            text = {
                Text(
                    "将导入到当前文件夹：${currentFolderPath.ifEmpty { "根目录" }}\n" +
                            "ZIP 内可以包含多级目录，每层有 questions.json 即视为一个题库包。"
                )
            },
            confirmButton = {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                    Text("选择 ZIP 文件")
                }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } }
        )
    }

    // ====== 重命名对话框 ======
    itemToRename?.let { (path, isFolder) ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text(if (isFolder) "重命名文件夹" else "重命名题库") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新名称（不要包含 /）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty() && !name.contains('/')) {
                        scope.launch {
                            val res = if (isFolder) repository.renameFolder(path, name)
                            else repository.renamePackage(path, name)
                            snackbarMessage = res.exceptionOrNull()?.message ?: "已重命名"
                            refreshTrigger++
                            itemToRename = null
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { itemToRename = null }) { Text("取消") } }
        )
    }

    // ====== 删除确认 ======
    itemToDelete?.let { (path, isFolder) ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = { Text("将永久删除 ${path}${if (isFolder) "（含所有子题库）" else ""}，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (isFolder) repository.deleteFolder(path) else repository.deletePackage(path)
                        refreshTrigger++
                        itemToDelete = null
                        snackbarMessage = "已删除"
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("取消") } }
        )
    }

    // ====== 移动对话框（简化为输入目标路径） ======
    itemToMove?.let { (path, _) ->
        AlertDialog(
            onDismissRequest = { itemToMove = null },
            title = { Text("移动到文件夹") },
            text = {
                Column {
                    Text("当前：$path")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = moveTarget,
                        onValueChange = { moveTarget = it },
                        label = { Text("目标文件夹路径（空=根目录）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val res = repository.moveItem(path, moveTarget.trim())
                        snackbarMessage = res.exceptionOrNull()?.message ?: "已移动"
                        refreshTrigger++
                        itemToMove = null
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { itemToMove = null }) { Text("取消") } }
        )
    }
}