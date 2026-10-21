package com.psd.floramind.navigation

import android.net.Uri
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
    NavHost(navController, startDestination = "main") {

        composable("main") { MainScreen(navController, repository) }

        composable(
            "practice/{packageName}/{questionId}",
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("questionId") { type = NavType.IntType }
            )
        ) { backStack ->
            // Navigation 会解码 %2F -> '/'，这里直接使用即可
            val packageName = backStack.arguments?.getString("packageName") ?: "内置题库"
            val questionId = backStack.arguments?.getInt("questionId") ?: 0
            PracticeScreen(packageName, questionId, repository, navController)
        }

        composable(
            "mistakes_detail/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStack ->
            val packageName = backStack.arguments?.getString("packageName") ?: ""
            MistakeDetailScreen(packageName, repository, navController)
        }

        composable("settings") { SettingsScreen(repository) }

        composable("packages") { PackageListScreen(repository, navController) }

        composable(
            "package_questions/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStack ->
            val packageName = backStack.arguments?.getString("packageName") ?: ""
            QuestionListByPackageScreen(packageName, repository, navController)
        }
    }
}