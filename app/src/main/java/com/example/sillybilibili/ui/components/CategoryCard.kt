package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.ui.theme.*

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit, onEditClick: () -> Unit, onDeleteClick: () -> Unit, modifier: Modifier = Modifier) {
    val catColor = Color(category.color)
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(0.dp)).background(catColor))
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(0.dp)).background(catColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(category.name.take(1), style = MaterialTheme.typography.titleMedium, color = catColor, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleSmall, color = Color(0xFFF0F0F8), fontWeight = FontWeight.SemiBold)
                Text("${category.videoCount} videos", style = MaterialTheme.typography.labelMedium, color = catColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = onEditClick) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF606080)) }
            IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, "Delete", tint = NeonRed) }
        }
    }
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        CategoryColors.forEach { color ->
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(0.dp))
                .background(if (color == selectedColor) color else color.copy(alpha = 0.4f))
                .clickable { onColorSelected(color) }, contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(0.dp)).background(Color.White.copy(alpha = 0.5f)))
                }
            }
        }
    }
}
