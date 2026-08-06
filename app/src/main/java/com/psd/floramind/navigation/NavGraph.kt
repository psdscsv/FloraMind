package com.psd.floramind.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.ui.screens.*

@Composable
fun NavGraph(repository: QuestionRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(navController, repository)
        }
        composable(
            "practice/{questionId}",
            arguments = listOf(navArgument("questionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getInt("questionId") ?: 0
            PracticeScreen(questionId, repository, navController)
        }
        composable("mistakes") {
            MistakeScreen(repository, navController)
        }
        composable("settings") {
            SettingsScreen(repository)
        }
    }
}