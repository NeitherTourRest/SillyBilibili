package com.example.sillybilibili.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.model.Video
import com.example.sillybilibili.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoContextMenu(
    video: Video,
    onDismiss: () -> Unit,
    onRequestAssignCategory: (Video) -> Unit,
    onRequestDelete: (Video) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = DarkTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                maxLines = 2
            )

            HorizontalDivider(
                color = DarkDivider,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            MenuItem(
                icon = Icons.Default.Bookmark,
                label = "分配分类",
                iconTint = NeonCyan,
                onClick = {
                    onDismiss()
                    onRequestAssignCategory(video)
                }
            )

            HorizontalDivider(
                color = DarkDivider,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            MenuItem(
                icon = Icons.Default.Delete,
                label = "删除视频",
                iconTint = NeonRed,
                labelColor = NeonRed,
                onClick = {
                    onDismiss()
                    onRequestDelete(video)
                }
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color = Color(0xFFE8E8F0),
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AssignCategoryDialog(
    video: Video,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAssign: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                "分配分类",
                fontWeight = FontWeight.Bold,
                color = CyberVermilion
            )
        },
        text = {
            Column {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkTextSecondary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                categories.forEach { category ->
                    TextButton(
                        onClick = { onAssign(category.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFE8E8F0)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(category.color))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(category.name, fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider(color = DarkDivider, modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = { onAssign(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = NeonRed
                    )
                ) {
                    Text("移除分类")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF606080)
                )
            ) {
                Text("取消")
            }
        },
        confirmButton = {}
    )
}

