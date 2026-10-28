package com.psd.floramind.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.psd.floramind.repository.QuestionRepository
import com.psd.floramind.repository.ServerRepository
import com.psd.floramind.session.PracticeSessionHolder
import com.psd.floramind.ui.screens.*

@Composable
fun NavGraph(
    repository: QuestionRepository,
    serverRepository: ServerRepository
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {

        composable("main") {
            MainScreen(navController, repository, serverRepository)
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

        composable("practice_filtered") {
            val qs = PracticeSessionHolder.pendingQuestions
            if (qs.isEmpty()) {
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

        composable("settings") {
            SettingsScreen(repository, serverRepository, navController)
        }

        composable("packages") {
            PackageListScreen(repository, serverRepository, navController)
        }

        composable(
            "package_questions/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            QuestionListByPackageScreen(packageName, repository, navController)
        }

        // ★ 新增：服务器题库
        composable("server_packages") {
            ServerPackageListScreen(serverRepository, repository, navController)
        }

        // ★ 新增：意见反馈
        composable("feedback") {
            FeedbackScreen(serverRepository, navController)
        }
    }
}