package com.example.sillybilibili.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkTextSecondary

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/** 底部导航的四个一级页面（路由与 AppNavHost 中的 Screen 保持一致）。 */
val bottomTabs = listOf(
    BottomTab("home", "首页", Icons.Default.VideoLibrary),
    BottomTab("exported", "已导出", Icons.Default.FolderOpen),
    BottomTab("scan", "扫描", Icons.Default.YoutubeSearchedFor),
    BottomTab("settings", "设置", Icons.Default.Settings)
)

@Composable
fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(modifier = Modifier.height(72.dp), containerColor = DarkCard) {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberVermilion,
                    selectedTextColor = CyberVermilion,
                    indicatorColor = CyberVermilion.copy(alpha = 0.16f),
                    unselectedIconColor = DarkTextSecondary,
                    unselectedTextColor = DarkTextSecondary
                )
            )
        }
    }
}
