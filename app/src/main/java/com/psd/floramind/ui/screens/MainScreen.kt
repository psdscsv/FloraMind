package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.model.PracticeRecord
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
import androidx.compose.runtime.saveable.rememberSaveable
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    repository: QuestionRepository,
    serverRepository: com.psd.floramind.repository.ServerRepository
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
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

// ===== 练习：范围筛选 + 顺序 =====
enum class PracticeOrder(val label: String) {
    SEQUENTIAL("顺序"),
    RANDOM("随机"),
    WRONG_ONLY("只刷错题"),
    UNSEEN("只刷未做")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeStartScreen(repository: QuestionRepository, navController: NavController) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }

    var allQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var allPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var allRecords by remember {
        mutableStateOf<Map<String, Map<Int, PracticeRecord>>>(emptyMap())
    }
    var isLoading by remember { mutableStateOf(true) }

    // 题库筛选（本会话，不入库；空 = 全部）
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 题型筛选：初始为空集合（= 全选语义），持久化到 DataStore
    var selectedTypes by remember { mutableStateOf<Set<QuestionType>>(emptySet()) }
    var typesLoaded by remember { mutableStateOf(false) }

    // 顺序：顺序 / 随机 / 只刷错题 / 只刷未做
    var orderName by rememberSaveable { mutableStateOf(PracticeOrder.SEQUENTIAL.name) }
    var showPackageDialog by remember { mutableStateOf(false) }

    val order = runCatching { PracticeOrder.valueOf(orderName) }
        .getOrDefault(PracticeOrder.SEQUENTIAL)

    LaunchedEffect(Unit) {
        val saved = dataStore.selectedTypesFlow.first()
        selectedTypes = saved
        typesLoaded = true
    }

    LaunchedEffect(selectedTypes, typesLoaded) {
        if (typesLoaded) dataStore.saveSelectedTypes(selectedTypes)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        allQuestions = repository.getAllQuestions()
        allPackages = repository.getAllPackages()
        allRecords = repository.getAllPracticeRecords()
        isLoading = false
    }

    val effectiveTypes: Set<QuestionType> =
        if (selectedTypes.isEmpty()) QuestionType.entries.toSet() else selectedTypes

    val scopedQuestions = remember(allQuestions, selectedPackages, effectiveTypes) {
        allQuestions.filter { q ->
            (selectedPackages.isEmpty() || q.packageName in selectedPackages) &&
                    q.type in effectiveTypes
        }
    }

    // 只刷错题 / 只刷未做 需要先知道每题的做题记录（记录存在题库包里）
    val orderedQuestions = remember(scopedQuestions, orderName, allRecords) {
        when (order) {
            PracticeOrder.SEQUENTIAL, PracticeOrder.RANDOM -> scopedQuestions
            PracticeOrder.WRONG_ONLY -> scopedQuestions.filter { q ->
                allRecords[q.packageName]?.get(q.id)?.isMistake == true
            }
            PracticeOrder.UNSEEN -> scopedQuestions.filter { q ->
                allRecords[q.packageName]?.get(q.id)?.isDone != true
            }
        }
    }

    val totals = remember(allRecords) {
        var done = 0
        var right = 0
        var wrong = 0
        allRecords.values.forEach { perPackage ->
            perPackage.values.forEach { record ->
                if (record.isDone) done++
                right += record.right
                wrong += record.wrong
            }
        }
        Triple(done, right, wrong)
    }

    val scopeSummary = when {
        selectedPackages.isEmpty() -> "全部（${allPackages.size} 个）"
        selectedPackages.size == 1 -> selectedPackages.first().substringAfterLast('/')
        else -> "${selectedPackages.size} 个题库"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("练习", style = MaterialTheme.typography.headlineSmall)
            Text(
                "题库共 ${allQuestions.size} 题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PracticeStatsBar(
            done = totals.first,
            right = totals.second,
            wrong = totals.third,
            scope = scopedQuestions.size
        )

        if (isLoading || !typesLoaded) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // ---------- 题库范围 ----------
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
                    Text("题库范围", style = MaterialTheme.typography.titleMedium)
                    Text(
                        scopeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = { showPackageDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("选择题库")
                }
            }
        }

        // ---------- 题型 ----------
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
                    if (selectedTypes.isNotEmpty()) {
                        TextButton(onClick = { selectedTypes = emptySet() }) { Text("全选题型") }
                    }
                }
                Text(
                    if (selectedTypes.isEmpty()) "当前：全部题型（不勾选 = 全选）"
                    else "当前：已选 ${selectedTypes.size}/${QuestionType.entries.size} 种",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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

        // ---------- 顺序 ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("顺序", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PracticeOrder.entries.forEach { option ->
                        FilterChip(
                            selected = order == option,
                            onClick = { orderName = option.name },
                            label = { Text(option.label) }
                        )
                    }
                }
                Text(
                    "选择题点选即判定；其他题型先看答案再自我判定；" +
                            "做题记录写在题库包里，导出时会一起带走。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 范围统计 ----------
        Text(
            "当前范围：${orderedQuestions.size} 题",
            style = MaterialTheme.typography.titleMedium,
            color = if (orderedQuestions.isEmpty())
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )
        if (orderedQuestions.isEmpty()) {
            Text(
                when (order) {
                    PracticeOrder.WRONG_ONLY -> "这个范围里没有错题，换个题库范围或切回「顺序」。"
                    PracticeOrder.UNSEEN -> "这个范围里的题都做过了，切回「顺序」或「只刷错题」看看。"
                    else -> "请至少选择一个题库或题型。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ---------- 开始练习 ----------
        Button(
            onClick = {
                val list = if (order == PracticeOrder.RANDOM) {
                    orderedQuestions.shuffled()
                } else {
                    orderedQuestions
                }
                if (list.isEmpty()) return@Button
                PracticeSessionHolder.pendingQuestions = list
                PracticeSessionHolder.startIndex = 0
                navController.navigate("practice_filtered")
            },
            enabled = orderedQuestions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (order) {
                    PracticeOrder.RANDOM -> "开始练习（随机顺序 · ${orderedQuestions.size} 题）"
                    PracticeOrder.WRONG_ONLY -> "开始刷错题（${orderedQuestions.size} 题）"
                    PracticeOrder.UNSEEN -> "开始刷未做（${orderedQuestions.size} 题）"
                    PracticeOrder.SEQUENTIAL -> "开始练习（从头 · ${orderedQuestions.size} 题）"
                }
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeStatsBar(done: Int, right: Int, wrong: Int, scope: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatPill("已做", done.toString())
        StatPill("答对", right.toString())
        StatPill("答错", wrong.toString())
        StatPill("正确率", if (right + wrong > 0) "${right * 100 / (right + wrong)}%" else "-")
        StatPill("当前范围", scope.toString())
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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