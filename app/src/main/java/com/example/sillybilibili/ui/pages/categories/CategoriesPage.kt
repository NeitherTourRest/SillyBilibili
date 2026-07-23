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
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.8f)) } },
                actions = { IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add Category", tint = CyberGold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(CyberVermilion, CyberGold, NeonPurple))))
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (uiState.categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Category, null, Modifier.size(64.dp), tint = Color(0xFF404060))
                    Text("No categories yet", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF404060))
                    TextButton(onClick = { showAddDialog = true }) { Text("Create your first category", color = CyberGold) }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            shape = RoundedCornerShape(0.dp),
            title = { Text(if (isEditing) "Edit Category" else "New Category", fontWeight = FontWeight.Bold, color = CyberGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name", color = Color(0xFF606080)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(0.dp),
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
                }, colors = ButtonDefaults.buttonColors(containerColor = CyberVermilion), shape = RoundedCornerShape(0.dp)) {
                    Text(if (isEditing) "Save" else "Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingCategory = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF606080))) { Text("Cancel") } }
        )
    }
}
