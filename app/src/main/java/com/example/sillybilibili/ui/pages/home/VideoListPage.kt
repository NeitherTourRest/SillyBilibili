package com.example.sillybilibili.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.components.*
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListPage(categoryId: Long?, onNavigateBack: () -> Unit, onNavigateToPlayer: (Video, List<Video>) -> Unit, viewModel: VideoListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showBatchCategory by remember { mutableStateOf(false) }

    val latestOnNavigate by rememberUpdatedState(onNavigateToPlayer)
    val latestVideos by rememberUpdatedState(uiState.videos)
    val categoryById = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    LaunchedEffect(categoryId) { viewModel.setCategoryId(categoryId) }
    LaunchedEffect(listState) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { if (it != null && it >= uiState.videos.size - 5) viewModel.loadMore() } }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isSelectionMode) "已选择 ${uiState.selectedIds.size} 项" else uiState.categoryName,
                subtitle = if (uiState.isSelectionMode) null else "按分类浏览视频",
                onNavigateBack = if (uiState.isSelectionMode) viewModel::exitSelectionMode else onNavigateBack
            ) {
                if (uiState.isSelectionMode) {
                    TextButton(onClick = viewModel::toggleSelectAllFiltered) { Text("全选", color = CyberVermilion, fontWeight = FontWeight.SemiBold) }
                    IconButton(onClick = viewModel::exitSelectionMode) { Icon(Icons.Default.Close, "完成选择", tint = DarkTextSecondary) }
                } else {
                    TextButton(onClick = viewModel::enterSelectionMode, enabled = uiState.videos.isNotEmpty()) { Text("多选", color = CyberVermilion, fontWeight = FontWeight.SemiBold) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.isSelectionMode) {
                BatchActionBar(
                    hasSelection = uiState.selectedIds.isNotEmpty(),
                    onAssignCategory = { showBatchCategory = true },
                    onConvertToMp4 = viewModel::batchConvertToMp4,
                    onRefreshStatus = viewModel::batchRefreshOnlineStatus,
                    onCheckIntegrity = viewModel::batchCheckIntegrity,
                    progress = uiState.batchProgress
                )
            } else {
                uiState.batchProgress?.let { BatchConversionStatusBanner(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                SearchBar(query = uiState.searchQuery, onQueryChange = viewModel::updateSearchQuery, placeholder = "搜索此分类")
            }
            if (uiState.isLoading) SkeletonVideoList(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp))
            else if (uiState.videos.isEmpty()) EmptyStatePanel(
                icon = Icons.Filled.FolderOpen,
                title = "这个分类暂时没有视频",
                subtitle = if (uiState.searchQuery.isNotBlank()) "没有找到匹配的搜索关键词。" else "去首页看看其他分类，或扫描更多缓存视频。",
                accent = NeonPurple
            )
            else Column(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).rangeSelectDrag(uiState.isSelectionMode, listState, viewModel::selectRange), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.videos, key = { it.id }, contentType = { "video" }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { latestOnNavigate(video, latestVideos) },
                            onLongClick = { viewModel.startSelectionFromLongPress(video.id) },
                            onCoverRequested = viewModel::requestCover,
                            onOnlineStatusRequested = viewModel::requestOnlineStatus,
                            selectionMode = uiState.isSelectionMode,
                            selected = video.id in uiState.selectedIds,
                            onSelect = { viewModel.toggleSelection(video.id) },
                            category = categoryById[video.categoryId]
                        )
                    }
                    if (uiState.isLoadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)) } }
                }
                if (uiState.videos.isNotEmpty() && uiState.hasMoreData && !uiState.isLoadingMore) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text("上滑加载更多", style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary)
                    }
                }
            }
        }
    }
    if (showBatchCategory) {
        BatchAssignCategoryDialog(
            categories = uiState.categories,
            selectedCount = uiState.selectedIds.size,
            onDismiss = { showBatchCategory = false },
            onAssign = { categoryId ->
                viewModel.assignSelectedToCategory(categoryId)
                showBatchCategory = false
            }
        )
    }
}
