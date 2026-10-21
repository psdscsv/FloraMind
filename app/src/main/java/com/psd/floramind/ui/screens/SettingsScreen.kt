package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: QuestionRepository) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    val viewModel = remember { SettingsViewModel(dataStore) }
    val userName by viewModel.userName.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }

    // ★ 新增：协程作用域 & Snackbar
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Text("当前用户名: $userName")
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新用户名") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                if (newName.isNotBlank()) {
                    viewModel.updateUserName(newName)
                    newName = ""
                }
            }) {
                Text("更新用户名")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("API Key: ${if (apiKey.isNotEmpty()) apiKey.take(4) + "..." else "未设置"}")
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("新 API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                if (newKey.isNotBlank()) {
                    viewModel.updateApiKey(newKey)
                    newKey = ""
                }
            }) {
                Text("更新 API Key")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ★ 恢复内置题库按钮
            Button(onClick = {
                scope.launch {
                    repository.reloadBuiltInPackages()
                    snackbarHostState.showSnackbar("内置题库已恢复")
                }
            }) {
                Text("恢复内置题库")
            }
        }
    }
}