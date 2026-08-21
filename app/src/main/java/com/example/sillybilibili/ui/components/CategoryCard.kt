package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.ui.theme.*

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit, onEditClick: () -> Unit, onDeleteClick: () -> Unit, modifier: Modifier = Modifier) {
    val catColor = Color(category.color)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        border = BorderStroke(1.dp, DarkDivider.copy(alpha = 0.58f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(catColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.name.take(1), style = MaterialTheme.typography.titleMedium, color = catColor, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, color = DarkTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${category.videoCount} 个视频", style = MaterialTheme.typography.labelMedium, color = DarkTextSecondary)
            }
            IconButton(onClick = onEditClick) { Icon(Icons.Default.Edit, "编辑", tint = DarkTextSecondary, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, "删除", tint = NeonRed.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        CategoryColors.forEach { color ->
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (color == selectedColor) color else color.copy(alpha = 0.4f))
                .clickable { onColorSelected(color) }, contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.75f)))
                }
            }
        }
    }
}
