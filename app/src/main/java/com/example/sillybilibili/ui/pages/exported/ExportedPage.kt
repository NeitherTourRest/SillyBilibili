package com.example.sillybilibili.ui.pages.exported

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.components.AssignCategoryDialog
import com.example.sillybilibili.ui.components.FastScrollBar
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurface
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.NeonCyan
import com.example.sillybilibili.ui.theme.NeonGreen
import com.example.sillybilibili.ui.theme.NeonRed
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportedPage(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    viewModel: ExportedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoreFilters by remember { mutableStateOf(false) }
    var categoryTarget by remember { mutableStateOf<Video?>(null) }
    var showBatchCategory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val categoryById = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            ExportedTopBar(
                uiState = uiState,
                onBack = { if (uiState.isSelectionMode) viewModel.clearSelection() else onNavigateBack() },
                onStartSelection = viewModel::enterSelectionMode,
                onRefresh = { viewModel.refreshExternalChanges() },
                onBatchCategory = { showBatchCategory = true },
                onBatchDelete = { viewModel.showDeleteConfirm(viewModel.selectedVideos()) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (!uiState.isSelectionMode) {
                ExportedSearchAndFilters(
                    state = uiState,
                    onQueryChange = viewModel::updateQuery,
                    onFilterClick = { showMoreFilters = true },
                    onCategorySelected = { category -> viewModel.updateFilter { it.copy(category = category) } },
                    onReset = viewModel::resetFilter
                )
            }

            ExportedLibrarySummary(uiState)

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.16f))
                }
                uiState.videos.isEmpty() -> ExportedEmptyState(
                    hasActiveFilter = uiState.filter.isActive,
                    onReset = viewModel::resetFilter
                )
                else -> Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.videos, key = { it.id }, contentType = { "exported-video" }) { video ->
                            LaunchedEffect(video.id, video.coverPath, video.exportedPath) {
                                viewModel.requestCover(video)
                            }
                            ExportedVideoCard(
                                video = video,
                                category = categoryById[video.categoryId],
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = video.id in uiState.selectedIds,
                                onPlay = { video.exportedPath?.let { path -> onNavigateToPlayer(path, video.title) } },
                                onSelect = { viewModel.toggleSelection(video.id) },
                                onLongClick = { viewModel.enterSelectionMode(video.id) },
                                onAssignCategory = { categoryTarget = video },
                                onRename = { viewModel.showRename(video) },
                                onDelete = { viewModel.showDeleteConfirm(video) }
                            )
                        }
                    }
                    FastScrollBar(
                        listState = listState,
                        itemCount = uiState.videos.size,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 12.dp)
                    )
                }
            }
        }
    }

    if (showMoreFilters) {
        ExportedMoreFiltersSheet(
            filter = uiState.filter,
            onChange = viewModel::updateFilter,
            onDismiss = { showMoreFilters = false }
        )
    }
    categoryTarget?.let { video ->
        AssignCategoryDialog(
            video = video,
            categories = uiState.categories,
            onDismiss = { categoryTarget = null },
            onAssign = { categoryId ->
                viewModel.assignVideoToCategory(video.id, categoryId)
                categoryTarget = null
            }
        )
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
    if (uiState.deleteConfirmVideos.isNotEmpty()) {
        val count = uiState.deleteConfirmVideos.size
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            containerColor = DarkSurface,
            shape = MaterialTheme.shapes.large,
            title = { Text(if (count == 1) "删除导出文件" else "删除 $count 个导出文件", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (count == 1) "将删除磁盘中的 MP4 文件。若原缓存仍在，视频会继续保留在扫描库中。"
                    else "将删除磁盘中的 $count 个 MP4 文件；保留原缓存的视频仍可在扫描库中找到。",
                    color = DarkTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExported() }) { Text("删除", color = NeonRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("取消") } }
        )
    }
    uiState.renameTarget?.let { video ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            containerColor = DarkSurface,
            shape = MaterialTheme.shapes.large,
            title = { Text("重命名导出文件", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(video.title, color = DarkTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = uiState.renameInput,
                        onValueChange = viewModel::updateRenameInput,
                        singleLine = true,
                        suffix = { Text(".mp4") },
                        label = { Text("文件名") },
                        colors = exportedTextFieldColors()
                    )
                    Text("仅修改导出的 MP4 文件名，不影响原缓存标题。", style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::renameExported) { Text("保存") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRename) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportedTopBar(
    uiState: ExportedUiState,
    onBack: () -> Unit,
    onStartSelection: () -> Unit,
    onRefresh: () -> Unit,
    onBatchCategory: () -> Unit,
    onBatchDelete: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(if (uiState.isSelectionMode) "已选择 ${uiState.selectedIds.size} 项" else "已导出视频", fontWeight = FontWeight.Bold)
                if (!uiState.isSelectionMode) Text("可独立管理的 MP4 文件", style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = DarkTextPrimary) }
        },
        actions = {
            if (uiState.isSelectionMode) {
                IconButton(onClick = onBatchCategory, enabled = uiState.selectedIds.isNotEmpty()) { Icon(Icons.Default.Category, "批量分类") }
                IconButton(onClick = onBatchDelete, enabled = uiState.selectedIds.isNotEmpty()) { Icon(Icons.Default.Delete, "批量删除", tint = NeonRed) }
            } else {
                TextButton(onClick = onStartSelection, enabled = uiState.videos.isNotEmpty()) { Text("多选") }
                IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                    if (uiState.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CyberVermilion)
                    else Icon(Icons.Default.Refresh, "刷新外部改动")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, titleContentColor = DarkTextPrimary)
    )
}

@Composable
private fun ExportedSearchAndFilters(
    state: ExportedUiState,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onCategorySelected: (ExportedCategoryFilter) -> Unit,
    onReset: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("搜索标题、UP、AV/CID 或导出文件名", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.filter.query.isNotBlank()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, "清空搜索") }
                    IconButton(onClick = onFilterClick) { Icon(Icons.Default.FilterList, "更多筛选", tint = if (state.filter.isActive) CyberVermilion else DarkTextSecondary) }
                }
            },
            colors = exportedTextFieldColors()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 2.dp)) {
            item { CategoryFilterChip("全部", state.filter.category == ExportedCategoryFilter.ALL) { onCategorySelected(ExportedCategoryFilter.ALL) } }
            item { CategoryFilterChip("未分类", state.filter.category == ExportedCategoryFilter.UNCATEGORIZED) { onCategorySelected(ExportedCategoryFilter.UNCATEGORIZED) } }
            items(state.categories, key = { it.id }) { category ->
                CategoryFilterChip(
                    label = category.name,
                    selected = state.filter.category == ExportedCategoryFilter.Category(category.id),
                    color = Color(category.color)
                ) { onCategorySelected(ExportedCategoryFilter.Category(category.id)) }
            }
            if (state.filter.isActive) item { AssistChip(onClick = onReset, label = { Text("重置") }, leadingIcon = { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }, colors = AssistChipDefaults.assistChipColors(labelColor = CyberVermilion, leadingIconContentColor = CyberVermilion) ) }
        }
    }
}

@Composable
private fun CategoryFilterChip(label: String, selected: Boolean, color: Color? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = color?.let { chipColor -> { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(chipColor)) } },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyberVermilion.copy(alpha = 0.18f),
            selectedLabelColor = CyberVermilion
        )
    )
}

@Composable
private fun ExportedLibrarySummary(state: ExportedUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${state.videos.size} / ${state.totalExportedCount} 个 MP4", fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Text(if (state.filter.isActive) "当前筛选结果" else "已导出文件库", style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary)
            }
            Text(formatSize(state.totalSize), style = MaterialTheme.typography.labelLarge, color = NeonCyan)
        }
    }
}

@Composable
private fun ExportedEmptyState(hasActiveFilter: Boolean, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.VideoLibrary, null, Modifier.size(58.dp), tint = DarkSurfaceVariant)
            Text(if (hasActiveFilter) "没有匹配的导出视频" else "还没有导出的视频", color = DarkTextPrimary, fontWeight = FontWeight.Bold)
            Text(if (hasActiveFilter) "可清除搜索或筛选条件后重试。" else "在播放页转换为 MP4 后，文件会集中显示在这里。", color = DarkTextSecondary)
            if (hasActiveFilter) TextButton(onClick = onReset) { Text("清除筛选") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExportedVideoCard(
    video: Video,
    category: Category?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onAssignCategory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = if (isSelectionMode) onSelect else onPlay,
            onLongClick = onLongClick
        ),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) CyberVermilion.copy(alpha = 0.15f) else DarkCard),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { onSelect() })
            Box(
                modifier = Modifier.width(if (video.isVertical) 64.dp else 118.dp).aspectRatio(video.previewAspectRatio).clip(RoundedCornerShape(14.dp)).background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (video.displayCoverPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(video.displayCoverPath).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else Icon(Icons.Default.PlayArrow, null, tint = CyberVermilion)
                if (video.duration > 0L) {
                    Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp), color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(7.dp)) {
                        Text(video.formattedDuration, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(File(video.exportedPath.orEmpty()).name, style = MaterialTheme.typography.labelSmall, color = DarkTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.quality.ifBlank { video.resolutionLabel.ifBlank { "MP4" } }, color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                    Text(if (video.exportedSize > 0L) formatSize(video.exportedSize) else video.formattedSize, color = DarkTextSecondary, style = MaterialTheme.typography.labelSmall)
                    if (!video.sourceAvailable) Text("仅导出", color = NeonGreen, style = MaterialTheme.typography.labelSmall)
                }
                category?.let {
                    AssistChip(
                        onClick = onAssignCategory,
                        label = { Text(it.name, maxLines = 1) },
                        leadingIcon = { Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(it.color))) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(it.color).copy(alpha = 0.14f), labelColor = DarkTextSecondary),
                        modifier = Modifier.height(28.dp)
                    )
                } ?: TextButton(onClick = onAssignCategory, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) { Text("添加分类", style = MaterialTheme.typography.labelSmall) }
            }
            if (!isSelectionMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "播放", tint = CyberVermilion) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "更多操作", tint = DarkTextSecondary) }
                        androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("编辑分类") }, onClick = { showMenu = false; onAssignCategory() }, leadingIcon = { Icon(Icons.Default.Category, null) })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("重命名文件") }, onClick = { showMenu = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            androidx.compose.material3.DropdownMenuItem(text = { Text("删除导出文件", color = NeonRed) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = NeonRed) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportedMoreFiltersSheet(filter: ExportedLibraryFilter, onChange: ((ExportedLibraryFilter) -> ExportedLibraryFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("更多筛选与排序", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ExportedFilterGroup(
                title = "源缓存状态",
                options = ExportedSourceFilter.entries,
                selected = filter.source,
                label = { source ->
                    when (source) {
                        ExportedSourceFilter.ALL -> "全部"
                        ExportedSourceFilter.CACHE_AVAILABLE -> "缓存仍在"
                        ExportedSourceFilter.EXPORTED_ONLY -> "仅保留 MP4"
                    }
                },
                onSelected = { source -> onChange { current -> current.copy(source = source) } }
            )
            ExportedFilterGroup(
                title = "画面方向",
                options = ExportedOrientationFilter.entries,
                selected = filter.orientation,
                label = { orientation ->
                    when (orientation) {
                        ExportedOrientationFilter.ALL -> "全部"
                        ExportedOrientationFilter.LANDSCAPE -> "横屏"
                        ExportedOrientationFilter.PORTRAIT -> "竖屏"
                    }
                },
                onSelected = { orientation -> onChange { current -> current.copy(orientation = orientation) } }
            )
            ExportedFilterGroup(
                title = "排序",
                options = ExportedSort.entries,
                selected = filter.sort,
                label = { sort ->
                    when (sort) {
                        ExportedSort.EXPORTED_RECENT -> "最近导出/改动"
                        ExportedSort.TITLE -> "标题 A–Z"
                        ExportedSort.FILE_SIZE -> "文件大小"
                    }
                },
                onSelected = { sort -> onChange { current -> current.copy(sort = sort) } }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun <T> ExportedFilterGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = DarkTextSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option -> FilterChip(selected = selected == option, onClick = { onSelected(option) }, label = { Text(label(option)) }) }
        }
    }
}

@Composable
private fun BatchAssignCategoryDialog(categories: List<Category>, selectedCount: Int, onDismiss: () -> Unit, onAssign: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("为 $selectedCount 个视频设置分类", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                categories.forEach { category ->
                    TextButton(onClick = { onAssign(category.id) }, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(category.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(category.name, Modifier.weight(1f), color = DarkTextPrimary)
                    }
                }
                HorizontalDivider(color = DarkDivider)
                TextButton(onClick = { onAssign(null) }, modifier = Modifier.fillMaxWidth()) { Text("移除分类", color = NeonRed) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun exportedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberVermilion.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = DarkTextPrimary,
    unfocusedTextColor = DarkTextPrimary,
    focusedPlaceholderColor = DarkTextTertiary,
    unfocusedPlaceholderColor = DarkTextTertiary,
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedLeadingIconColor = CyberVermilion,
    unfocusedLeadingIconColor = DarkTextSecondary
)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
