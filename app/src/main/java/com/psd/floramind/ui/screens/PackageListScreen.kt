package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.repository.FolderItem
import com.psd.floramind.repository.PackageItem
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.repository.ServerRepository
import kotlinx.coroutines.launch

/** 长按菜单的目标项 */
private data class MenuTarget(
    val path: String,
    val isFolder: Boolean,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageListScreen(
    repository: QuestionRepository,
    serverRepository: ServerRepository,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------- 上传者名字 ----------
    val dataStore = remember { DataStoreManager(context) }
    var uploaderName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        dataStore.userNameFlow.collect { uploaderName = it }
    }

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
    var itemToRename by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // ★ 长按菜单 / 移动目标选择
    var actionTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var movingItem by remember { mutableStateOf<MenuTarget?>(null) }

    // ★ 上传对话框状态
    var uploadingPkg by remember { mutableStateOf<PackageItem?>(null) }
    var uploadDescription by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { navController.navigate("server_packages") }) {
                        Icon(Icons.Default.Cloud, "服务器题库")
                    }
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
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
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

                    // ================= 文件夹 =================
                    items(folders, key = { "folder_${it.path}" }) { folder ->
                        Box {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            actionTarget = null
                                            currentFolderPath = folder.path
                                        },
                                        onLongClick = {
                                            actionTarget = MenuTarget(
                                                folder.path, true, folder.name
                                            )
                                        }
                                    ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder, null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${folder.packageCount} 个题库",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // 文件夹长按菜单
                            DropdownMenu(
                                expanded = actionTarget?.path == folder.path &&
                                        actionTarget?.isFolder == true,
                                onDismissRequest = { actionTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        itemToRename = folder.path to true
                                        renameText = folder.name
                                        actionTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("移动到…") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        movingItem = MenuTarget(
                                            folder.path, true, folder.name
                                        )
                                        actionTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete, null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        itemToDelete = folder.path to true
                                        actionTarget = null
                                    }
                                )
                            }
                        }
                    }

                    // ================= 题库包 =================
                    items(packages, key = { "pkg_${it.fullPath}" }) { pkg ->
                        Box {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            actionTarget = null
                                            navController.navigate(
                                                "package_questions/${Uri.encode(pkg.fullPath)}"
                                            )
                                        },
                                        onLongClick = {
                                            actionTarget = MenuTarget(
                                                pkg.fullPath, false, pkg.name
                                            )
                                        }
                                    ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook, null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${pkg.questionCount} 题",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // 题库长按菜单
                            DropdownMenu(
                                expanded = actionTarget?.path == pkg.fullPath &&
                                        actionTarget?.isFolder == false,
                                onDismissRequest = { actionTarget = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        itemToRename = pkg.fullPath to false
                                        renameText = pkg.name
                                        actionTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("移动到…") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        movingItem = MenuTarget(
                                            pkg.fullPath, false, pkg.name
                                        )
                                        actionTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("上传到服务器") },
                                    leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                                    onClick = {
                                        uploadingPkg = pkg
                                        uploadDescription = ""
                                        actionTarget = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete, null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        itemToDelete = pkg.fullPath to false
                                        actionTarget = null
                                    }
                                )
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
                            val path = if (currentFolderPath.isEmpty()) name
                            else "$currentFolderPath/$name"
                            val res = repository.createFolder(path)
                            snackbarMessage = res.exceptionOrNull()?.message
                                ?: "文件夹已创建"
                            refreshTrigger++
                            showCreateFolder = false
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false }) { Text("取消") }
            }
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
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            }
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
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("取消") }
            }
        )
    }

    // ====== 删除确认 ======
    itemToDelete?.let { (path, isFolder) ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = {
                Text(
                    "将永久删除 ${path}${if (isFolder) "（含所有子题库）" else ""}，" +
                            "此操作不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (isFolder) repository.deleteFolder(path)
                        else repository.deletePackage(path)
                        refreshTrigger++
                        itemToDelete = null
                        snackbarMessage = "已删除"
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }

    // ====== ★ 移动：进入目标目录后确认 ======
    movingItem?.let { item ->
        MoveTargetDialog(
            item = item,
            repository = repository,
            onDismiss = { movingItem = null },
            onConfirm = { targetFolder ->
                scope.launch {
                    val res = repository.moveItem(item.path, targetFolder)
                    snackbarMessage = res.exceptionOrNull()?.message ?: "已移动"
                    refreshTrigger++
                    movingItem = null
                }
            }
        )
    }

    // ====== 上传到服务器对话框 ======
    uploadingPkg?.let { pkg ->
        AlertDialog(
            onDismissRequest = { if (!isUploading) uploadingPkg = null },
            title = { Text("上传到服务器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("题库: ${pkg.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "题目数: ${pkg.questionCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uploaderName.isNotBlank()) {
                        Text(
                            "上传者: $uploaderName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = uploadDescription,
                        onValueChange = { uploadDescription = it },
                        label = { Text("描述（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUploading,
                        maxLines = 3
                    )
                    if (isUploading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "正在压缩并上传...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isUploading,
                    onClick = {
                        val p = pkg
                        isUploading = true
                        scope.launch {
                            try {
                                val file = repository.exportFolderToZip(p.fullPath)
                                if (file == null) {
                                    snackbarMessage = "压缩失败，无法上传"
                                    return@launch
                                }
                                serverRepository.uploadPackage(
                                    zipFile = file,
                                    name = p.name,
                                    description = uploadDescription.trim(),
                                    uploader = uploaderName
                                )
                                    .onSuccess { snackbarMessage = it }
                                    .onFailure {
                                        snackbarMessage = "上传失败: ${it.message}"
                                    }
                                file.delete()
                            } catch (e: Exception) {
                                snackbarMessage = "上传异常: ${e.message}"
                            } finally {
                                isUploading = false
                                uploadingPkg = null
                            }
                        }
                    }
                ) { Text("上传") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUploading,
                    onClick = { uploadingPkg = null }
                ) { Text("取消") }
            }
        )
    }
}

/**
 * 移动目标选择对话框：
 *  - 顶部显示当前浏览位置
 *  - 列表只展示可进入的子文件夹（自动排除被移动项自身）
 *  - 点击文件夹"进入"，点"移动到此处"才真正执行
 */
@Composable
private fun MoveTargetDialog(
    item: MenuTarget,
    repository: QuestionRepository,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var browsePath by remember { mutableStateOf("") }
    var subFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(browsePath) {
        isLoading = true
        val (f, _) = repository.getFolderChildren(browsePath)
        // 排除自身：避免用户"进入自己"
        subFolders = f.filter { it.path != item.path }
        isLoading = false
    }

    val parentOfItem = item.path.substringBeforeLast('/', "")
    val isSelfOrChild = browsePath == item.path ||
            (item.isFolder && browsePath.startsWith("${item.path}/"))
    val isSameParent = browsePath == parentOfItem

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动「${item.name}」") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "目标位置：${browsePath.ifEmpty { "根目录" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))

                if (browsePath.isNotEmpty()) {
                    TextButton(onClick = {
                        browsePath = browsePath.substringBeforeLast('/', "")
                    }) { Text("⬆ 返回上级") }
                }

                when {
                    isLoading -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        Alignment.Center
                    ) { CircularProgressIndicator(Modifier.size(24.dp)) }

                    subFolders.isEmpty() -> Text(
                        "（没有可进入的子文件夹）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(subFolders, key = { it.path }) { f ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { browsePath = f.path }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(f.name, modifier = Modifier.weight(1f))
                                Text(
                                    "${f.packageCount} 个题库",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isSelfOrChild) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ 不能移动到自身或其子目录",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (isSameParent) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已在当前目录，无需移动",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSelfOrChild && !isSameParent,
                onClick = { onConfirm(browsePath) }
            ) { Text("移动到此处") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}