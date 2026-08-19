package com.example.sillybilibili.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.sillybilibili.ui.components.AppBottomBar
import com.example.sillybilibili.ui.components.bottomTabs
import com.example.sillybilibili.ui.pages.categories.CategoriesPage
import com.example.sillybilibili.ui.pages.guide.GuidePage
import com.example.sillybilibili.ui.pages.exported.ExportedPage
import com.example.sillybilibili.ui.pages.home.HomePage
import com.example.sillybilibili.ui.pages.home.VideoListPage
import com.example.sillybilibili.ui.pages.player.PlayerPage
import com.example.sillybilibili.ui.pages.player.PlaybackQueueItem
import com.example.sillybilibili.ui.pages.player.PlaybackQueueStore
import com.example.sillybilibili.ui.pages.player.BackgroundPlaybackEntry
import com.example.sillybilibili.ui.pages.scan.ScanPage
import com.example.sillybilibili.ui.pages.settings.SettingsPage
import com.example.sillybilibili.ui.theme.DarkBackground

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Categories : Screen("categories")
    object Guide : Screen("guide")
    object Scan : Screen("scan")
    object Settings : Screen("settings")
    object VideoList : Screen("videolist/{categoryId}") {
        fun createRoute(categoryId: Long?) = "videolist/${categoryId ?: -1}"
    }
    object Player : Screen("player/{queueId}/{startIndex}") {
        fun createRoute(queueId: String, startIndex: Int) = "player/${Uri.encode(queueId)}/$startIndex"
    }
    object Exported : Screen("exported")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }
    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
    Box(Modifier.fillMaxSize().padding(innerPadding)) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomePage(
                onNavigateToVideoList = { categoryId -> navController.navigate(Screen.VideoList.createRoute(categoryId)) },
                onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToGuide = { navController.navigate(Screen.Guide.route) },
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToPlayer = { video, videos ->
                    val queue = PlaybackQueueStore.replace(
                        videos.map { PlaybackQueueItem(it.id, it.title, it.path, it.audioPath, it.displayCoverPath) },
                        video.id
                    )
                    navController.navigate(Screen.Player.createRoute(queue.id, queue.selectedIndex))
                },
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
                onNavigateToPlayer = { video, videos ->
                    val queue = PlaybackQueueStore.replace(
                        videos.map { PlaybackQueueItem(it.id, it.title, it.path, it.audioPath, it.displayCoverPath) },
                        video.id
                    )
                    navController.navigate(Screen.Player.createRoute(queue.id, queue.selectedIndex))
                }
            )
        }

        composable(route = Screen.Player.route, arguments = listOf(
            navArgument("queueId") { type = NavType.StringType },
            navArgument("startIndex") { type = NavType.IntType }
        )) { entry ->
            PlayerPage(
                queueId = Uri.decode(entry.arguments?.getString("queueId") ?: ""),
                initialIndex = entry.arguments?.getInt("startIndex") ?: 0,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Exported.route) {
            ExportedPage(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { path, title ->
                    val queue = PlaybackQueueStore.prepareSingle(path, title)
                    navController.navigate(Screen.Player.createRoute(queue.id, queue.selectedIndex))
                }
            )
        }
    }
    BackgroundPlaybackEntry(
        visible = backStackEntry?.destination?.route != Screen.Player.route,
        modifier = androidx.compose.ui.Modifier.align(Alignment.BottomCenter),
        onOpenPlayer = {
            PlaybackQueueStore.currentQueue()?.let { queue ->
                navController.navigate(Screen.Player.createRoute(queue.id, queue.selectedIndex)) { launchSingleTop = true }
            }
        }
    )
    }
    }
}
