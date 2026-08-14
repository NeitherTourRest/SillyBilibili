package com.example.sillybilibili.ui.pages.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.ui.components.CategoryCard
import com.example.sillybilibili.ui.components.ColorPicker
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesPage(
    onNavigateBack: () -> Unit,
    onNavigateToVideoList: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(title = "分类管理", subtitle = "整理你的视频收藏", onNavigateBack = onNavigateBack) {
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "新建分类", tint = CyberVermilion) }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (uiState.categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Category, null, Modifier.size(64.dp), tint = Color(0xFF404060))
                    Text("还没有分类", style = MaterialTheme.typography.titleMedium, color = DarkTextSecondary, fontWeight = FontWeight.Bold)
                    Text("创建分类，快速定位常看的视频", style = MaterialTheme.typography.bodyMedium, color = DarkTextTertiary)
                    TextButton(onClick = { showAddDialog = true }) { Text("创建第一个分类") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.categories) { category ->
                    CategoryCard(category = category, onClick = { onNavigateToVideoList(category.id) }, onEditClick = { editingCategory = category }, onDeleteClick = { viewModel.deleteCategory(category.id) })
                }
            }
        }
    }

    if (showAddDialog || editingCategory != null) {
        val isEditing = editingCategory != null
        var name by remember(isEditing) { mutableStateOf(editingCategory?.name ?: "") }
        var selectedColor by remember(isEditing) { mutableStateOf(if (isEditing) Color(editingCategory!!.color) else CategoryRed) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingCategory = null },
            containerColor = DarkSurface,
            iconContentColor = CyberVermilion,
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEditing) "编辑分类" else "新建分类", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("分类名称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberVermilion, unfocusedBorderColor = DarkDivider,
                            cursorColor = CyberVermilion, focusedTextColor = Color(0xFFE8E8F0),
                            unfocusedTextColor = Color(0xFFE8E8F0), focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                    ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        if (isEditing) viewModel.updateCategory(editingCategory!!.id, name, selectedColor.hashCode().toLong())
                        else viewModel.addCategory(name, selectedColor.hashCode().toLong())
                        showAddDialog = false; editingCategory = null
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion), shape = MaterialTheme.shapes.medium) {
                    Text(if (isEditing) "保存" else "创建", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingCategory = null }) { Text("取消") } }
        )
    }
}
