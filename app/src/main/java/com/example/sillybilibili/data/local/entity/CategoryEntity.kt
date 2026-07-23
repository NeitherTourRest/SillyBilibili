// ============================================================
// CategoryEntity.kt — 分类的数据库表结构
// ============================================================
// Room 数据库的 "categories" 表。
// 当用户在 CategoriesPage 创建/编辑分类时，数据存成 CategoryEntity。
// 读出来时转成 Category（Domain 模型）供 UI 使用。
// ============================================================

package com.example.sillybilibili.data.local.entity

// @Entity = Room 注解，标记这是一个数据库表
import androidx.room.Entity
// @PrimaryKey = 主键标记
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)  // 自增主键
    val id: Long = 0,
    val name: String,                // 分类名称（如 "音乐"）
    val color: Long,                 // 颜色值（存为 Long，如 0xFFFB7299）
    val createdAt: Long = System.currentTimeMillis()  // 创建时间
)
