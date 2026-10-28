package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.repository.ServerRepository
import com.psd.floramind.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: QuestionRepository,
    serverRepository: ServerRepository,
    navController: NavController
) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    val viewModel = remember { SettingsViewModel(dataStore) }
    val userName by viewModel.userName.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    // 服务器地址
    var serverUrl by remember { mutableStateOf("") }
    var editingServerUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        dataStore.serverUrlFlow.collect {
            serverUrl = it
            editingServerUrl = it
        }
    }

    var newName by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // ★ 关键改动：加入 verticalScroll，让内容可上下滚动
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)

            // ---------- 用户名 ----------
            Text("当前用户名: $userName")
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = {
                if (newName.isNotBlank()) {
                    viewModel.updateUserName(newName)
                    newName = ""
                }
            }) { Text("更新用户名") }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ---------- API Key ----------
            Text("API Key: ${if (apiKey.isNotEmpty()) apiKey.take(4) + "..." else "未设置"}")
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("新 API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = {
                if (newKey.isNotBlank()) {
                    viewModel.updateApiKey(newKey)
                    newKey = ""
                }
            }) { Text("更新 API Key") }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ---------- 服务器地址 ----------
            Text(
                "题库服务器地址" + if (serverUrl.isBlank()) "（未配置）" else "",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "示例: http://10.0.2.2:5000/ （模拟器访问本机）\n" +
                        "或 http://192.168.1.10:5000/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = editingServerUrl,
                onValueChange = { editingServerUrl = it },
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        dataStore.saveServerUrl(editingServerUrl)
                        snackbarHostState.showSnackbar("服务器地址已保存")
                    }
                }) { Text("保存地址") }
                OutlinedButton(onClick = {
                    editingServerUrl = ""
                    scope.launch { dataStore.saveServerUrl("") }
                }) { Text("清空") }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ---------- 反馈 & 服务器题库 ----------
            OutlinedButton(
                onClick = { navController.navigate("server_packages") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(8.dp))
                Text("浏览服务器题库")
            }
            OutlinedButton(
                onClick = { navController.navigate("feedback") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Feedback, null)
                Spacer(Modifier.width(8.dp))
                Text("意见反馈")
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ---------- 恢复内置题库 ----------
            Button(onClick = {
                scope.launch {
                    repository.reloadBuiltInPackages()
                    snackbarHostState.showSnackbar("内置题库已恢复")
                }
            }) { Text("恢复内置题库") }

            // 底部留白，避免最后一项贴着屏幕边缘
            Spacer(Modifier.height(24.dp))
        }
    }
}