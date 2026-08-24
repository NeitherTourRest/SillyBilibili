package com.example.sillybilibili.ui.pages.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.ui.components.AppTopBar
import com.example.sillybilibili.ui.components.CategoryCard
import com.example.sillybilibili.ui.components.ColorPicker
import com.example.sillybilibili.ui.components.SearchBar
import com.example.sillybilibili.ui.theme.CategoryRed
import com.example.sillybilibili.ui.theme.CyberVermilion
import com.example.sillybilibili.ui.theme.DarkBackground
import com.example.sillybilibili.ui.theme.DarkCard
import com.example.sillybilibili.ui.theme.DarkDivider
import com.example.sillybilibili.ui.theme.DarkSurface
import com.example.sillybilibili.ui.theme.DarkSurfaceVariant
import com.example.sillybilibili.ui.theme.DarkTextPrimary
import com.example.sillybilibili.ui.theme.DarkTextSecondary
import com.example.sillybilibili.ui.theme.DarkTextTertiary
import com.example.sillybilibili.ui.theme.NeonRed

@Composable
fun CategoriesPage(
    onNavigateBack: () -> Unit,
    onNavigateToVideoList: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
    val filteredCategories = remember(uiState.categories, uiState.searchQuery) {
        val query = uiState.searchQuery.trim()
        if (query.isEmpty()) uiState.categories
        else uiState.categories.filter { it.name.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "分类管理", subtitle = "整理并快速定位本地视频", onNavigateBack = onNavigateBack) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "新建分类", tint = CyberVermilion)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (uiState.categories.isEmpty()) {
            EmptyCategories(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onCreate = { showAddDialog = true }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    placeholder = "搜索分类",
                    verticalPadding = 10.dp
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "category-summary") { CategorySummary(uiState.categories) }
                    if (filteredCategories.isEmpty()) {
                        item(key = "category-empty-search") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Text("没有匹配的分类", color = DarkTextSecondary)
                            }
                        }
                    } else {
                        items(filteredCategories, key = { it.id }) { category ->
                            CategoryCard(
                                category = category,
                                onClick = { onNavigateToVideoList(category.id) },
                                onEditClick = { editingCategory = category },
                                onDeleteClick = { deleteTarget = category }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingCategory != null) {
        CategoryEditorDialog(
            category = editingCategory,
            validateName = viewModel::validateCategoryName,
            onDismiss = { showAddDialog = false; editingCategory = null },
            onSave = { name, color ->
                val saved = editingCategory?.let { viewModel.updateCategory(it.id, name, color) }
                    ?: viewModel.addCategory(name, color)
                if (saved) {
                    showAddDialog = false
                    editingCategory = null
                }
            }
        )
    }
    deleteTarget?.let { category ->
        DeleteCategoryDialog(
            category = category,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteCategory(category)
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun CategorySummary(categories: List<Category>) {
    val assignedVideoCount = categories.sumOf { it.videoCount }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkDivider)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Category, null, tint = CyberVermilion, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${categories.size} 个分类", color = DarkTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("已归类 $assignedVideoCount 个可用视频", color = DarkTextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Text("点按查看", color = DarkTextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyCategories(modifier: Modifier, onCreate: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Category, null, Modifier.size(64.dp), tint = Color(0xFF404060))
            Text("还没有分类", style = MaterialTheme.typography.titleMedium, color = DarkTextSecondary, fontWeight = FontWeight.Bold)
            Text("创建分类后可在视频卡片中批量归类", style = MaterialTheme.typography.bodyMedium, color = DarkTextTertiary)
            TextButton(onClick = onCreate) { Text("创建第一个分类", color = CyberVermilion) }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    category: Category?,
    validateName: (String, Long?) -> String?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    val isEditing = category != null
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var selectedColor by remember(category?.id) { mutableStateOf(if (category != null) Color(category.color) else CategoryRed) }
    val nameError = validateName(name, category?.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (isEditing) "编辑分类" else "新建分类", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CATEGORY_NAME_MAX_LENGTH) },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isNotBlank() && nameError != null,
                    supportingText = {
                        Text(
                            nameError ?: "最多 $CATEGORY_NAME_MAX_LENGTH 个字符",
                            color = if (nameError != null) NeonRed else DarkTextTertiary
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberVermilion,
                        unfocusedBorderColor = DarkDivider,
                        errorBorderColor = NeonRed,
                        cursorColor = CyberVermilion,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
                ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedColor.hashCode().toLong()) },
                enabled = nameError == null,
                colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion),
                shape = MaterialTheme.shapes.medium
            ) { Text(if (isEditing) "保存" else "创建", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DarkTextSecondary) } }
    )
}

@Composable
private fun DeleteCategoryDialog(category: Category, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = MaterialTheme.shapes.large,
        title = { Text("删除分类？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (category.videoCount > 0) {
                    "“${category.name}”中当前显示的 ${category.videoCount} 个视频会变为未分类；不会删除缓存或已导出的文件。"
                } else {
                    "不会删除任何缓存或已导出的文件。"
                },
                color = DarkTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除分类", color = NeonRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DarkTextSecondary) } }
    )
}
