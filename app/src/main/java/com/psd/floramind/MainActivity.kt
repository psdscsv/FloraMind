package com.psd.floramind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.psd.floramind.data.DataStoreManager
import com.psd.floramind.navigation.NavGraph
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.repository.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = QuestionRepository(this)
        val dataStore = DataStoreManager(this)
        val serverRepository = ServerRepository(this, dataStore)

        CoroutineScope(Dispatchers.IO).launch {
            repository.loadBuiltInQuestionsIfEmpty()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(repository, serverRepository)
                }
            }
        }
    }
}