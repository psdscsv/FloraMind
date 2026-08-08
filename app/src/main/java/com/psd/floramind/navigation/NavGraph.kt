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
            "practice/{packageName}/{questionId}",
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("questionId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: "内置题库"
            val questionId = backStackEntry.arguments?.getInt("questionId") ?: 0
            PracticeScreen(packageName, questionId, repository, navController)
        }
        composable("mistakes") {
            MistakeScreen(repository, navController)
        }
        composable("settings") {
            SettingsScreen(repository)
        }
        // 新增：题库包列表（已集成在MainScreen的Tab中，但也可单独路由，此处保留）
        composable("packages") {
            PackageListScreen(repository, navController)
        }
        // 新增：包内题目列表
        composable(
            "package_questions/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            QuestionListByPackageScreen(packageName, repository, navController)
        }
    }
}