package com.example.sillybilibili.ui.pages.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.components.AssignCategoryDialog
import com.example.sillybilibili.ui.components.CategoryChip
import com.example.sillybilibili.ui.components.FilterSheet
import com.example.sillybilibili.ui.components.SearchBar
import com.example.sillybilibili.ui.components.VideoCard
import com.example.sillybilibili.ui.components.VideoContextMenu
import com.example.sillybilibili.ui.pages.scan.ScanViewModel
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import kotlinx.coroutines.launch

@Composable
fun HomePage(
    onNavigateToVideoList: (Long?) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToPlayer: (Video, List<Video>) -> Unit,
    onNavigateToExported: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var contextMenuVideo by remember { mutableStateOf<Video?>(null) }
    var assignDialogVideo by remember { mutableStateOf<Video?>(null) }
    var deleteConfirmVideo by remember { mutableStateOf<Video?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(FilterState()) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val scanViewModel: ScanViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    val scanUiState by scanViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshVideos() }
    LaunchedEffect(uiState.currentPage) {
        // A page owns only 40 cards. Resetting here prevents Compose from retaining the
        // former page's tail position and immediately advancing again.
        listState.scrollToItem(0)
    }
    LaunchedEffect(listState, uiState.currentPage, uiState.videos.size, uiState.hasMoreData) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
            if (shouldPrefetchHomePage(lastIndex, uiState.videos.size, uiState.hasMoreData)) {
                viewModel.prefetchNextPage()
            }
            if (shouldAdvanceHomePage(lastIndex, uiState.videos.size, uiState.hasMoreData)) {
                viewModel.loadMore()
            }
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.home_subtitle)) {
                IconButton(onClick = { filterDraft = uiState.filterState; showFilterSheet = true }) {
                    BadgedBox(badge = { if (uiState.filterState.isActive || uiState.searchQuery.isNotBlank()) Badge(containerColor = CyberVermilion) }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                }
                IconButton(onClick = onNavigateToScan) { Icon(Icons.Default.YoutubeSearchedFor, contentDescription = stringResource(R.string.scan_videos)) }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.exported_videos)) }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { showMoreMenu = false; onNavigateToExported() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_management)) }, leadingIcon = { Icon(Icons.Default.Category, null) }, onClick = { showMoreMenu = false; onNavigateToCategories() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.user_guide)) }, leadingIcon = { Icon(Icons.Default.MenuBook, null) }, onClick = { showMoreMenu = false; onNavigateToGuide() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showMoreMenu = false; onNavigateToSettings() })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (scanUiState.isScanning && scanUiState.scanProgress != null) {
                val progress = scanUiState.scanProgress!!
                Surface(color = CyberVermilion.copy(alpha = 0.12f), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("正在扫描缓存", style = MaterialTheme.typography.labelLarge, color = CyberVermilion)
                            Text("发现 ${progress.foundVideoCount} 个视频", style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
                        }
                        LinearProgressIndicator(
                            progress = { progress.processedFolders.toFloat() / progress.totalFolders.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = CyberVermilion,
                            trackColor = CyberVermilion.copy(alpha = 0.16f)
                        )
                    }
                }
            }

            SearchBar(query = uiState.searchQuery, onQueryChange = viewModel::updateSearchQuery, placeholder = "搜索本地视频")
            if (uiState.categories.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNavigateToCategories) { Text("管理") }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null); scope.launch { snackbarHostState.showSnackbar("已显示全部视频") } },
                            label = { Text("全部") },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberVermilion.copy(alpha = 0.2f), selectedLabelColor = CyberVermilion, containerColor = DarkSurfaceVariant, labelColor = DarkTextSecondary),
                            border = FilterChipDefaults.filterChipBorder(borderColor = DarkDivider, selectedBorderColor = CyberVermilion, enabled = true, selected = uiState.selectedCategoryId == null)
                        )
                    }
                    items(uiState.categories) { category ->
                        CategoryChip(category.name, Color(category.color), category.videoCount, uiState.selectedCategoryId == category.id, onClick = {
                            viewModel.selectCategory(category.id)
                            scope.launch { snackbarHostState.showSnackbar("已切换到「${category.name}」") }
                        })
                    }
                }
            }

            val firstVisible = (listState.firstVisibleItemIndex + 1).coerceAtMost(uiState.videos.size)
            val lastVisible = ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex) + 1)
                .coerceAtMost(uiState.videos.size)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (uiState.videos.isEmpty()) "视频库" else "第 ${uiState.currentPage + 1} 页 · ${uiState.videos.size} 个视频",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.filterState.isActive) {
                        Text("已启用筛选", style = MaterialTheme.typography.labelMedium, color = CyberVermilion)
                    } else if (uiState.hasMoreData) {
                        Surface(shape = RoundedCornerShape(12.dp), color = DarkSurfaceVariant) {
                            Text("下滑自动进入下一页", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = DarkTextSecondary)
                        }
                    }
                }
                if (uiState.videos.isNotEmpty()) {
                    Text(
                        "本页第 $firstVisible–$lastVisible 条 · ${if (uiState.hasMoreData) "下一页已预载" else "已到最后一页"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkTextTertiary
                    )
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CyberVermilion) }
                uiState.videos.isEmpty() -> EmptyVideoLibrary(onScan = onNavigateToScan)
                else -> Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.videos, key = { it.id }, contentType = { "video" }) { video ->
                            VideoCard(video, onClick = { onNavigateToPlayer(video, uiState.videos) }, onLongClick = { contextMenuVideo = video }, onCoverRequested = viewModel::requestCover, onOnlineStatusRequested = viewModel::requestOnlineStatus)
                        }
                        if (uiState.isLoadingMore) item(key = "home-switching-page", contentType = "loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion) } }
                    }
                }
            }
        }
    }

    contextMenuVideo?.let { VideoContextMenu(it, onDismiss = { contextMenuVideo = null }, onRequestAssignCategory = { assignDialogVideo = it }, onRequestDelete = { deleteConfirmVideo = it }) }
    assignDialogVideo?.let { video -> AssignCategoryDialog(video, uiState.categories, onDismiss = { assignDialogVideo = null }, onAssign = { categoryId -> viewModel.assignVideoToCategory(video.id, categoryId); assignDialogVideo = null; scope.launch { snackbarHostState.showSnackbar("分类已更新") } }) }
    deleteConfirmVideo?.let { video ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmVideo = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("从列表移除视频") },
            text = { Text("这不会删除原始缓存文件。") },
            confirmButton = { TextButton(onClick = { viewModel.deleteVideo(video); deleteConfirmVideo = null; scope.launch { snackbarHostState.showSnackbar("视频已从列表移除") } }) { Text("移除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirmVideo = null }) { Text("取消") } }
        )
    }
    if (showFilterSheet) FilterSheet(currentFilter = filterDraft, onDraftFilterChange = { filterDraft = it }, onApplyFilter = { filter -> viewModel.applyFilter(filter); showFilterSheet = false; scope.launch { snackbarHostState.showSnackbar(if (filter.isActive) "筛选条件已应用" else "筛选已重置") } }, onDismiss = { showFilterSheet = false })
}

@Composable
private fun EmptyVideoLibrary(onScan: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = DarkSurfaceVariant) { Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.padding(20.dp).size(38.dp), tint = CyberVermilion) }
            Text("还没有本地视频", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("扫描 B 站缓存目录后，视频会显示在这里。", color = DarkTextTertiary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Button(onClick = onScan) { Icon(Icons.Default.YoutubeSearchedFor, null); Spacer(Modifier.width(6.dp)); Text("开始扫描") }
        }
    }
}
