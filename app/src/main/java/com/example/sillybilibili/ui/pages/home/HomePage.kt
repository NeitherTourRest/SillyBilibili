package com.example.sillybilibili.ui.pages.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.components.*
import com.example.sillybilibili.ui.pages.scan.ScanViewModel
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    onNavigateToVideoList: (Long?) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToVideoDetail: (Long) -> Unit,
    onNavigateToExported: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var contextMenuVideo by remember { mutableStateOf<Video?>(null) }
    var assignDialogVideo by remember { mutableStateOf<Video?>(null) }
    var deleteConfirmVideo by remember { mutableStateOf<Video?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(FilterState()) }

    // Activity-scoped ScanViewModel — survives navigation, shows progress on HomePage
    val scanViewModel: ScanViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
    val scanUiState by scanViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshVideos() }
    LaunchedEffect(listState) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { if (it != null && it >= uiState.videos.size - 5) viewModel.loadMore() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("\u26A1", color = CyberGold); Spacer(Modifier.width(4.dp)); Text("SILLY BILIBILI", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White) } },
                actions = {
                    IconButton(onClick = { filterDraft = uiState.filterState; showFilterSheet = true }) { BadgedBox(badge = { if (uiState.filterState.isActive || uiState.searchQuery.isNotBlank()) Badge(containerColor = CyberVermilion) }) { Icon(Icons.Default.FilterList, "Filter", tint = Color.White.copy(alpha = 0.8f)) } }
                    IconButton(onClick = onNavigateToScan) { Icon(Icons.Default.YoutubeSearchedFor, "Scan", tint = Color.White.copy(alpha = 0.8f)) }
                    IconButton(onClick = onNavigateToExported) { Icon(Icons.Default.FolderOpen, "Exported", tint = Color.White.copy(alpha = 0.8f)) }
                    IconButton(onClick = onNavigateToGuide) { Icon(Icons.Default.MenuBook, "Guide", tint = Color.White.copy(alpha = 0.8f)) }
                    IconButton(onClick = onNavigateToCategories) { Icon(Icons.Default.Category, "Categories", tint = Color.White.copy(alpha = 0.8f)) }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.8f)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(CyberVermilion, CyberGold, NeonPurple))))
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            // Background scan progress indicator
            if (scanUiState.isScanning && scanUiState.scanProgress != null) {
                val p = scanUiState.scanProgress!!
                Surface(color = CyberVermilion.copy(alpha = 0.08f), shape = RoundedCornerShape(0.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { p.processedFolders.toFloat() / p.totalFolders.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(0.dp)),
                            color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("${p.foundVideoCount}v", style = MaterialTheme.typography.labelSmall, color = CyberVermilion, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SearchBar(query = uiState.searchQuery, onQueryChange = viewModel::updateSearchQuery, placeholder = "Search videos...")

            if (uiState.categories.isNotEmpty()) {
                LazyRow(Modifier.padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = uiState.selectedCategoryId == null, onClick = { viewModel.selectCategory(null) }, shape = RoundedCornerShape(0.dp),
                            label = { Text("All", fontWeight = if (uiState.selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberVermilion.copy(alpha = 0.15f), containerColor = DarkSurfaceVariant, selectedLabelColor = CyberVermilion, labelColor = Color(0xFFA0A0B8)),
                            border = FilterChipDefaults.filterChipBorder(borderColor = DarkDivider, selectedBorderColor = CyberVermilion, enabled = true, selected = uiState.selectedCategoryId == null))
                    }
                    items(uiState.categories) { cat -> CategoryChip(name = cat.name, color = Color(cat.color), count = cat.videoCount, selected = uiState.selectedCategoryId == cat.id, onClick = { viewModel.selectCategory(cat.id) }) }
                }
            }

            if (uiState.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)) }
            else if (uiState.videos.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VideoLibrary, null, Modifier.size(64.dp), tint = Color(0xFF404060)); Text("No videos found", color = Color(0xFF404060), fontWeight = FontWeight.Bold)
                }
            }
            else Column(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.videos, key = { it.id }) { video -> VideoCard(video = video, onClick = { onNavigateToVideoDetail(video.id) }, onLongClick = { contextMenuVideo = video }) }
                    if (uiState.isLoadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)) } }
                }
                if (uiState.videos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { val p = uiState.currentPage - 1; if (p >= 0) viewModel.goToPage(p) }, enabled = uiState.currentPage > 0, shape = RoundedCornerShape(0.dp)) { Text("< Prev", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                        Text("PAGE ${uiState.currentPage + 1}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF606080), fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { viewModel.goToPage(uiState.currentPage + 1) }, enabled = uiState.hasMoreData, shape = RoundedCornerShape(0.dp)) { Text("Next >", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                    }
                    if (uiState.hasMoreData && !uiState.isLoadingMore) TextButton(onClick = { viewModel.loadAll() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(0.dp)).background(DarkSurfaceVariant)) { Text("LOAD ALL  (${uiState.videos.size}+)", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = CyberGold) }
                }
            }
        }
    }

    if (contextMenuVideo != null) VideoContextMenu(video = contextMenuVideo!!, onDismiss = { contextMenuVideo = null }, onRequestAssignCategory = { assignDialogVideo = it }, onRequestDelete = { deleteConfirmVideo = it }, onRequestConvert = { onNavigateToVideoDetail(it.id) })
    assignDialogVideo?.let { v -> AssignCategoryDialog(video = v, categories = uiState.categories, onDismiss = { assignDialogVideo = null }, onAssign = { cid -> viewModel.assignVideoToCategory(v.id, cid); assignDialogVideo = null }) }
    deleteConfirmVideo?.let { v -> AlertDialog(onDismissRequest = { deleteConfirmVideo = null }, containerColor = DarkSurface, shape = RoundedCornerShape(0.dp), title = { Text("Delete", fontWeight = FontWeight.Bold, color = NeonRed) }, text = { Text("Delete this video?", color = Color(0xFF8080A0)) }, confirmButton = { TextButton(onClick = { viewModel.deleteVideo(v); deleteConfirmVideo = null }) { Text("Delete", color = NeonRed, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { deleteConfirmVideo = null }) { Text("Cancel", color = Color(0xFF606080)) } }) }
    if (showFilterSheet) FilterSheet(currentFilter = filterDraft, onDraftFilterChange = { filterDraft = it }, onApplyFilter = { viewModel.applyFilter(it); showFilterSheet = false }, onDismiss = { showFilterSheet = false })
}
