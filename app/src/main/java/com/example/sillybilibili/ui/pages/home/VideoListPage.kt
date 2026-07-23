package com.example.sillybilibili.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun VideoListPage(categoryId: Long?, onNavigateBack: () -> Unit, onNavigateToVideoDetail: (Long) -> Unit, viewModel: VideoListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var contextMenuVideo by remember { mutableStateOf<Video?>(null) }
    var assignDialogVideo by remember { mutableStateOf<Video?>(null) }
    var deleteConfirmVideo by remember { mutableStateOf<Video?>(null) }

    LaunchedEffect(categoryId) { viewModel.setCategoryId(categoryId) }
    LaunchedEffect(listState) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { if (it != null && it >= uiState.videos.size - 5) viewModel.loadMore() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(uiState.categoryName, fontWeight = FontWeight.Bold, color = Color.White) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.8f)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(CyberVermilion, CyberGold, NeonPurple))))
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            SearchBar(query = uiState.searchQuery, onQueryChange = viewModel::updateSearchQuery, placeholder = "Search...")
            if (uiState.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CyberVermilion, trackColor = CyberVermilion.copy(alpha = 0.1f)) }
            else if (uiState.videos.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No videos", color = Color(0xFF404060), fontWeight = FontWeight.Bold) }
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
}
