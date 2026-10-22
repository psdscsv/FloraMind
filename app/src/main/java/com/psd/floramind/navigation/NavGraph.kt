package com.psd.floramind.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.session.PracticeSessionHolder
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

        // ★ 新增：筛选练习入口
        composable("practice_filtered") {
            val qs = PracticeSessionHolder.pendingQuestions
            if (qs.isEmpty()) {
                // 异常情况（例如进程重建后单例被清空），退回主界面
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val startIdx = PracticeSessionHolder.startIndex.coerceIn(0, qs.size - 1)
                val first = qs[startIdx]
                PracticeScreen(
                    packageName = first.packageName,
                    questionId = first.id,
                    repository = repository,
                    navController = navController,
                    questionsOverride = qs,
                    startIndexOverride = startIdx
                )
            }
        }

        composable(
            "mistakes_detail/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            MistakeDetailScreen(packageName, repository, navController)
        }

        composable("settings") { SettingsScreen(repository) }

        composable("packages") { PackageListScreen(repository, navController) }

        composable(
            "package_questions/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            QuestionListByPackageScreen(packageName, repository, navController)
        }
    }
}