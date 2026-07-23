package com.example.sillybilibili.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.sillybilibili.ui.pages.categories.CategoriesPage
import com.example.sillybilibili.ui.pages.guide.GuidePage
import com.example.sillybilibili.ui.pages.exported.ExportedPage
import com.example.sillybilibili.ui.pages.home.HomePage
import com.example.sillybilibili.ui.pages.home.VideoDetailPage
import com.example.sillybilibili.ui.pages.home.VideoListPage
import com.example.sillybilibili.ui.pages.player.PlayerPage
import com.example.sillybilibili.ui.pages.scan.ScanPage
import com.example.sillybilibili.ui.pages.settings.SettingsPage

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Categories : Screen("categories")
    object Guide : Screen("guide")
    object Scan : Screen("scan")
    object Settings : Screen("settings")
    object VideoList : Screen("videolist/{categoryId}") {
        fun createRoute(categoryId: Long?) = "videolist/${categoryId ?: -1}"
    }
    object VideoDetail : Screen("videodetail/{videoId}") {
        fun createRoute(videoId: Long) = "videodetail/$videoId"
    }
    object Player : Screen("player/{filePath}/{title}") {
        fun createRoute(filePath: String, title: String) = "player/${Uri.encode(filePath)}/${Uri.encode(title)}"
    }
    object Exported : Screen("exported")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomePage(
                onNavigateToVideoList = { categoryId -> navController.navigate(Screen.VideoList.createRoute(categoryId)) },
                onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToGuide = { navController.navigate(Screen.Guide.route) },
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToVideoDetail = { videoId -> navController.navigate(Screen.VideoDetail.createRoute(videoId)) },
                onNavigateToExported = { navController.navigate(Screen.Exported.route) }
            )
        }

        composable(Screen.Categories.route) {
            CategoriesPage(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVideoList = { categoryId -> navController.navigate(Screen.VideoList.createRoute(categoryId)) }
            )
        }

        composable(Screen.Guide.route) {
            GuidePage(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Scan.route) {
            ScanPage(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsPage(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.VideoList.route, arguments = listOf(navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments?.getLong("categoryId") ?: -1L
            VideoListPage(
                categoryId = if (id == -1L) null else id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVideoDetail = { videoId -> navController.navigate(Screen.VideoDetail.createRoute(videoId)) }
            )
        }

        composable(route = Screen.VideoDetail.route, arguments = listOf(navArgument("videoId") { type = NavType.LongType })) { entry ->
            VideoDetailPage(
                videoId = entry.arguments?.getLong("videoId") ?: 0L,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { path, title -> navController.navigate(Screen.Player.createRoute(path, title)) }
            )
        }

        composable(route = Screen.Player.route, arguments = listOf(
            navArgument("filePath") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType }
        )) { entry ->
            val filePath = Uri.decode(entry.arguments?.getString("filePath") ?: "")
            val title = Uri.decode(entry.arguments?.getString("title") ?: "")
            PlayerPage(
                filePath = filePath,
                videoTitle = title,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Exported.route) {
            ExportedPage(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { path, title -> navController.navigate(Screen.Player.createRoute(path, title)) }
            )
        }
    }
}
