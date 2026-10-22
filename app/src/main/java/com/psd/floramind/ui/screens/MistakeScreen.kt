package com.psd.floramind.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.viewmodel.MistakeViewModel

@Composable
fun MistakeScreen(repository: QuestionRepository, navController: NavController) {
    val viewModel = remember { MistakeViewModel(repository) }
    LaunchedEffect(Unit) { viewModel.loadMistakePackages() }
    val packages by viewModel.mistakePackages.collectAsState()

    if (packages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("暂无错题", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("错题本", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(packages, key = { it }) { pkg ->
            val count by produceState(initialValue = 0, key1 = pkg) {
                value = repository.getMistakeCount(pkg)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // ★ 包路径含 '/'，必须 encode
                        navController.navigate("mistakes_detail/${Uri.encode(pkg)}")
                    },
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // 显示末段名（完整路径太长）
                        Text(
                            text = pkg.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$count 道错题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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