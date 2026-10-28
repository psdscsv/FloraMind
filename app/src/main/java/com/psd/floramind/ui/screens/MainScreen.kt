package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.Question
import com.psd.floramind.model.QuestionType
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.session.PracticeSessionHolder
import androidx.compose.ui.platform.LocalContext
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.model.network.Announcement
import com.psd.floramind.repository.ServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    repository: QuestionRepository,
    serverRepository: com.psd.floramind.repository.ServerRepository
) {
    var selectedTab by remember { mutableStateOf(0) }
    // ★ 公告状态
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    var announcement by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(Unit) {
        repository.loadBuiltInQuestionsIfEmpty()
    }

    // ★ 启动时拉取公告
    LaunchedEffect(Unit) {
        val url = dataStore.serverUrlFlow.first()
        if (url.isBlank()) return@LaunchedEffect

        serverRepository.getAnnouncement()
            .onSuccess { ann ->
                if (ann != null) {
                    val lastId = dataStore.lastReadAnnIdFlow.first()
                    val lastUpdated = dataStore.lastReadAnnUpdatedFlow.first()
                    if (ann.id != lastId || ann.updatedTime != lastUpdated) {
                        announcement = ann
                    }
                }
            }
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
                1 -> PackageListScreen(repository, serverRepository, navController)
                2 -> MistakeScreen(repository, navController)
                3 -> SettingsScreen(repository, serverRepository, navController)
            }
        }
    }
    // ★ 弹窗
    announcement?.let { ann ->
        AnnouncementDialog(
            announcement = ann,
            onClose = {
                scope.launch {
                    dataStore.markAnnouncementRead(ann.id, ann.updatedTime)
                }
                announcement = null
            }
        )
    }
}

// ===== 题型中文名 =====
private fun QuestionType.label(): String = when (this) {
    QuestionType.DEFINITION -> "名词解释"
    QuestionType.FILL_BLANK -> "填空"
    QuestionType.CHOICE -> "选择"
    QuestionType.SHORT_ANSWER -> "简答"
    QuestionType.DIAGRAM -> "填图"
    QuestionType.ESSAY -> "论述"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeStartScreen(repository: QuestionRepository, navController: NavController) {
    var allQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var allPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 筛选状态
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) } // 空 = 全部
    var selectedTypes by remember {
        mutableStateOf<Set<QuestionType>>(QuestionType.entries.toSet()) // 默认全选
    }
    var showPackageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        allQuestions = repository.getAllQuestions()
        allPackages = repository.getAllPackages()
        isLoading = false
    }

    val filteredQuestions = remember(allQuestions, selectedPackages, selectedTypes) {
        allQuestions.filter { q ->
            (selectedPackages.isEmpty() || q.packageName in selectedPackages) &&
                    q.type in selectedTypes
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("筛选练习范围", style = MaterialTheme.typography.headlineSmall)
        Text(
            "提示：简答/论述/名词解释题会调用deepseek，因此点击提交后不要切题，几秒后会出现反馈；题库和题型默认全选，点击对应按钮可以取消；错题会自动记入错题本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLoading) {
            Box(Modifier
                .fillMaxWidth()
                .padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // ---------- 题型筛选 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("题型", style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = {
                            selectedTypes = QuestionType.entries.toSet()
                        }) { Text("全选") }
                        TextButton(onClick = { selectedTypes = emptySet() }) { Text("清空") }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QuestionType.entries.forEach { type ->
                        FilterChip(
                            selected = type in selectedTypes,
                            onClick = {
                                selectedTypes = if (type in selectedTypes) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                            },
                            label = { Text(type.label()) }
                        )
                    }
                }
            }
        }

        // ---------- 题库筛选 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("题库", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            selectedPackages.isEmpty() -> "全部（${allPackages.size} 个）"
                            selectedPackages.size == 1 -> selectedPackages.first()
                                .substringAfterLast('/')

                            else -> "${selectedPackages.size} 个"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { showPackageDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择题库范围")
                }
            }
        }

        // ---------- 结果统计 ----------
        Text(
            "筛选结果：${filteredQuestions.size} 道题目",
            style = MaterialTheme.typography.titleMedium,
            color = if (filteredQuestions.isEmpty())
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )

        if (filteredQuestions.isEmpty()) {
            Text(
                "请至少选择一种题型和一个题库",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.weight(1f))

        // ---------- 开始按钮 ----------
        Button(
            onClick = {
                val list = filteredQuestions
                if (list.isEmpty()) return@Button
                PracticeSessionHolder.pendingQuestions = list
                PracticeSessionHolder.startIndex = list.indices.random()
                navController.navigate("practice_filtered")
            },
            enabled = filteredQuestions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始练习（随机）")
        }

        Button(
            onClick = {
                val list = filteredQuestions
                if (list.isEmpty()) return@Button
                PracticeSessionHolder.pendingQuestions = list
                PracticeSessionHolder.startIndex = 0
                navController.navigate("practice_filtered")
            },
            enabled = filteredQuestions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始练习（顺序）")
        }
    }

    // ---------- 题库选择对话框 ----------
    if (showPackageDialog) {
        PackageSelectorDialog(
            allPackages = allPackages,
            selected = selectedPackages,
            onDismiss = { showPackageDialog = false },
            onConfirm = {
                selectedPackages = it
                showPackageDialog = false
            }
        )
    }
}

@Composable
fun PackageSelectorDialog(
    allPackages: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var localSelected by remember { mutableStateOf(selected) }
    val sortedPackages = remember(allPackages) { allPackages.sorted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择题库范围") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        localSelected = sortedPackages.toSet()
                    }) { Text("全选") }
                    TextButton(onClick = { localSelected = emptySet() }) { Text("清空") }
                }
                Text(
                    "不勾选任何项 = 全部题库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(sortedPackages, key = { it }) { pkg ->
                        val depth = pkg.count { it == '/' }
                        val checked = pkg in localSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    localSelected = if (checked) localSelected - pkg
                                    else localSelected + pkg
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { c ->
                                    localSelected = if (c) localSelected + pkg
                                    else localSelected - pkg
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "   ".repeat(depth) + pkg.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localSelected) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}