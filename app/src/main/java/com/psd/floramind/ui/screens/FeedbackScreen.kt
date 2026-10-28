package com.psd.floramind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.repository.ServerRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    serverRepository: ServerRepository,
    navController: NavController
) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    var userName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        dataStore.userNameFlow.collect { userName = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("意见反馈") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("意见反馈", style = MaterialTheme.typography.headlineSmall)
            Text(
                "遇到问题或有建议？欢迎告诉我们！",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("反馈内容") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10
            )
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("联系方式（选填）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (content.isBlank()) return@Button
                    isSubmitting = true
                    scope.launch {
                        val version = try {
                            context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .versionName ?: ""
                        } catch (_: Exception) { "" }

                        serverRepository.submitFeedback(
                            userName = userName.ifBlank { null },
                            content = content.trim(),
                            contact = contact.ifBlank { null },
                            appVersion = version
                        )
                            .onSuccess {
                                snackbarHostState.showSnackbar(it)
                                content = ""
                                contact = ""
                            }
                            .onFailure {
                                snackbarHostState.showSnackbar(
                                    "提交失败: ${it.message}"
                                )
                            }
                        isSubmitting = false
                    }
                },
                enabled = content.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isSubmitting) "提交中..." else "提交反馈")
            }
        }
    }
}