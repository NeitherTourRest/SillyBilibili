package com.example.sillybilibili.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkTextSecondary

data class BottomTab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

/** 底部导航的四个一级页面（路由与 AppNavHost 中的 Screen 保持一致）。 */
val bottomTabs = listOf(
    BottomTab("home", R.string.tab_home, Icons.Default.VideoLibrary),
    BottomTab("exported", R.string.tab_exported, Icons.Default.FolderOpen),
    BottomTab("scan", R.string.tab_scan, Icons.Default.YoutubeSearchedFor),
    BottomTab("settings", R.string.tab_settings, Icons.Default.Settings)
)

@Composable
fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(68.dp),
        containerColor = DarkCard,
        tonalElevation = 0.dp
    ) {
        bottomTabs.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onNavigate(tab.route) },
                icon = { Icon(tab.icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberVermilion,
                    selectedTextColor = CyberVermilion,
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unselectedIconColor = DarkTextSecondary,
                    unselectedTextColor = DarkTextSecondary
                )
            )
        }
    }
}
