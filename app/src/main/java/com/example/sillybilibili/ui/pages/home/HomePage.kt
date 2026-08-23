package com.example.sillybilibili.ui.pages.home

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.R
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.components.BatchActionBar
import com.example.sillybilibili.ui.components.EmptyStatePanel
import com.example.sillybilibili.ui.components.AssignCategoryDialog
import com.example.sillybilibili.ui.components.CategoryChip
import com.example.sillybilibili.ui.components.FilterSheet
import com.example.sillybilibili.ui.components.SearchBar
import com.example.sillybilibili.ui.components.SkeletonVideoList
import com.example.sillybilibili.ui.components.VideoCard
import com.example.sillybilibili.ui.components.VideoGridCard
import com.example.sillybilibili.ui.components.rangeSelectDrag
import com.example.sillybilibili.ui.components.VideoContextMenu
import com.example.sillybilibili.ui.pages.scan.ScanViewModel
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.GlassHighlight
import com.example.sillybilibili.ui.theme.NeonPurple
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var contextMenuVideo by remember { mutableStateOf<Video?>(null) }
    var assignDialogVideo by remember { mutableStateOf<Video?>(null) }
    var deleteConfirmVideo by remember { mutableStateOf<Video?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(FilterState()) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshVideos() }
    // 用 rememberUpdatedState 持有最新回调与列表：item 内的 lambda 只捕获稳定值，
    // 卡片参数未变化时 Compose 可以直接跳过重组。
    val latestOnNavigate by rememberUpdatedState(onNavigateToPlayer)
    val latestVideos by rememberUpdatedState(uiState.videos)
    val categoryById = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    LaunchedEffect(listState, uiState.videos.size, uiState.hasMoreData, uiState.gridViewEnabled) {
        if (uiState.gridViewEnabled) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
            if (shouldPrefetchHomePage(lastIndex, uiState.videos.size, uiState.hasMoreData)) {
                viewModel.prefetchNextPage()
            }
            if (shouldAdvanceHomePage(lastIndex, uiState.videos.size, uiState.hasMoreData)) {
                viewModel.loadMore()
            }
        }
    }
    LaunchedEffect(gridState, uiState.videos.size, uiState.hasMoreData, uiState.gridViewEnabled) {
        if (!uiState.gridViewEnabled) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
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
            AppTopBar(
                title = if (uiState.isSelectionMode) stringResource(R.string.home_selected_count, uiState.selectedIds.size) else stringResource(R.string.app_name),
                titleContent = if (uiState.isSelectionMode) null else ({
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 用 PNG 前景图而非 adaptive-icon XML（后者在部分 ROM 上解码异常会闪退）
                        Icon(
                            painter = painterResource(R.drawable.sillybilibili_sbb_mark),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }),
                showSecondaryActions = true,
                secondaryActions = {
                if (uiState.isSelectionMode) {
                    TextButton(onClick = viewModel::toggleSelectAllFiltered) { Text(stringResource(R.string.select_all), color = CyberVermilion, fontWeight = FontWeight.SemiBold) }
                    IconButton(onClick = viewModel::exitSelectionMode) { Icon(Icons.Default.Close, stringResource(R.string.finish_selection), tint = DarkTextSecondary) }
                } else {
                    TextButton(onClick = viewModel::enterSelectionMode, enabled = uiState.videos.isNotEmpty()) { Text(stringResource(R.string.multi_select), color = CyberVermilion, fontWeight = FontWeight.SemiBold) }
                    IconButton(onClick = { filterDraft = uiState.filterState; showFilterSheet = true }) {
                        BadgedBox(badge = { if (uiState.filterState.isActive || uiState.searchQuery.isNotBlank()) Badge(containerColor = CyberVermilion) }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter))
                        }
                    }
                    IconButton(onClick = viewModel::toggleGridView) {
                        Icon(
                            if (uiState.gridViewEnabled) Icons.Filled.ViewAgenda else Icons.Default.GridView,
                            contentDescription = stringResource(if (uiState.gridViewEnabled) R.string.switch_to_list else R.string.switch_to_grid)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.category_management)) }, leadingIcon = { Icon(Icons.Default.Category, null) }, onClick = { showMoreMenu = false; onNavigateToCategories() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.user_guide)) }, leadingIcon = { Icon(Icons.Default.MenuBook, null) }, onClick = { showMoreMenu = false; onNavigateToGuide() })
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isSelectionMode) {
                BatchActionBar(
                    hasSelection = uiState.selectedIds.isNotEmpty(),
                    onConvertToMp4 = viewModel::batchConvertToMp4,
                    onRefreshStatus = viewModel::batchRefreshOnlineStatus,
                    onCheckIntegrity = viewModel::batchCheckIntegrity,
                    progress = uiState.batchProgress
                )
            } else {
                ScanProgressBanner()

                SearchBar(query = uiState.searchQuery, onQueryChange = viewModel::updateSearchQuery, placeholder = stringResource(R.string.search_local_videos))
            if (uiState.categories.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null); scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.showing_all_videos)) } },
                            label = { Text(stringResource(R.string.all)) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberVermilion.copy(alpha = 0.2f), selectedLabelColor = CyberVermilion, containerColor = DarkSurfaceVariant, labelColor = DarkTextSecondary),
                            border = FilterChipDefaults.filterChipBorder(borderColor = DarkDivider, selectedBorderColor = CyberVermilion, enabled = true, selected = uiState.selectedCategoryId == null)
                        )
                    }
                    items(uiState.categories, key = { it.id }) { category ->
                        CategoryChip(category.name, Color(category.color), category.videoCount, uiState.selectedCategoryId == category.id, onClick = {
                            viewModel.selectCategory(category.id)
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.switched_to_category, category.name)) }
                        })
                    }
                }
            }

            }

            when {
                uiState.isLoading -> SkeletonVideoList(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp))
                uiState.videos.isEmpty() -> {
                    val hasActiveQuery = uiState.searchQuery.isNotBlank() || uiState.filterState.isActive || uiState.selectedCategoryId != null
                    if (hasActiveQuery) {
                        EmptyStatePanel(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.no_matching_videos),
                            subtitle = stringResource(R.string.no_matching_videos_description),
                            accent = NeonPurple,
                            action = {
                                TextButton(onClick = { viewModel.updateSearchQuery(""); viewModel.clearFilter(); viewModel.selectCategory(null) }) {
                                    Text(stringResource(R.string.clear_search_and_filters), color = NeonPurple)
                                }
                            }
                        )
                    } else {
                        EmptyVideoLibrary(onScan = onNavigateToScan)
                    }
                }
                else -> AnimatedContent(
                    targetState = uiState.gridViewEnabled,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "homeViewSwitch"
                ) { grid ->
                if (grid) Box(Modifier.fillMaxSize().rangeSelectDrag(uiState.isSelectionMode, gridState, viewModel::selectRange)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(uiState.videos, key = { it.id }, contentType = { "video-grid" }) { video ->
                            VideoGridCard(
                                video = video,
                                onClick = { latestOnNavigate(video, latestVideos) },
                                onLongClick = { contextMenuVideo = video },
                                onCoverRequested = viewModel::requestCover,
                                onOnlineStatusRequested = viewModel::requestOnlineStatus,
                                selectionMode = uiState.isSelectionMode,
                                selected = video.id in uiState.selectedIds,
                                onSelect = { viewModel.toggleSelection(video.id) },
                                category = categoryById[video.categoryId]
                            )
                        }
                        if (uiState.isLoadingMore) item(key = "home-switching-page-grid", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion) } }
                    }
                } else Box(Modifier.fillMaxSize().rangeSelectDrag(uiState.isSelectionMode, listState, viewModel::selectRange)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.videos, key = { it.id }, contentType = { "video" }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { latestOnNavigate(video, latestVideos) },
                                onLongClick = { contextMenuVideo = video },
                                onCoverRequested = viewModel::requestCover,
                                onOnlineStatusRequested = viewModel::requestOnlineStatus,
                                selectionMode = uiState.isSelectionMode,
                                selected = video.id in uiState.selectedIds,
                                onSelect = { viewModel.toggleSelection(video.id) },
                                category = categoryById[video.categoryId]
                            )
                        }
                        if (uiState.isLoadingMore) item(key = "home-switching-page", contentType = "loading") { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion) } }
                    }
                }
                }
            }
        }
    }

    contextMenuVideo?.let { VideoContextMenu(it, onDismiss = { contextMenuVideo = null }, onRequestAssignCategory = { assignDialogVideo = it }, onRequestDelete = { deleteConfirmVideo = it }) }
    assignDialogVideo?.let { video -> AssignCategoryDialog(video, uiState.categories, onDismiss = { assignDialogVideo = null }, onAssign = { categoryId -> viewModel.assignVideoToCategory(video.id, categoryId); assignDialogVideo = null; scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.category_updated)) } }) }
    deleteConfirmVideo?.let { video ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmVideo = null },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.remove_video_from_list)) },
            text = { Text(stringResource(R.string.remove_video_from_list_description)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteVideo(video); deleteConfirmVideo = null; scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.video_removed_from_list)) } }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirmVideo = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showFilterSheet) FilterSheet(currentFilter = filterDraft, onDraftFilterChange = { filterDraft = it }, onApplyFilter = { filter -> viewModel.applyFilter(filter); showFilterSheet = false; scope.launch { snackbarHostState.showSnackbar(context.getString(if (filter.isActive) R.string.filters_applied else R.string.filters_reset)) } }, onDismiss = { showFilterSheet = false })
}

/**
 * 扫描进度横幅。自己持有 ScanViewModel（Activity 作用域，与 ScanPage 共享同一实例），
 * 扫描进度高频更新时只有横幅重组，主页列表与头部不会跟着重绘。
 */
@Composable
private fun ScanProgressBanner() {
    val scanViewModel: ScanViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    val scanUiState by scanViewModel.uiState.collectAsState()
    val progress = scanUiState.scanProgress
    if (!scanUiState.isScanning || progress == null) return

    Surface(color = CyberVermilion.copy(alpha = 0.12f), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.scanning_cache), style = MaterialTheme.typography.labelLarge, color = CyberVermilion)
                Text(stringResource(R.string.found_video_count, progress.foundVideoCount), style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
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

@Composable
private fun EmptyVideoLibrary(onScan: () -> Unit) {
    EmptyStatePanel(
        icon = Icons.Default.VideoLibrary,
        title = stringResource(R.string.no_local_videos),
        subtitle = stringResource(R.string.no_local_videos_description),
        action = {
            androidx.compose.material3.Button(
                onClick = onScan,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CyberVermilion)
            ) {
                Icon(Icons.Default.YoutubeSearchedFor, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start_scan), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
