// ============================================================
// Category.kt — 分类数据模型
// ============================================================
// 用户创建的自定义分类。比如"游戏"、"音乐"、"教程"等。
// 视频可以分配到某个分类，方便管理。
// 被 CategoriesPage.kt（分类管理页面）和 HomePage.kt（分类筛选）使用。
// ============================================================

package com.example.sillybilibili.domain.model

import androidx.compose.runtime.Immutable

// data class 自动生成 equals/hashCode/toString/copy
@Immutable
data class Category(
    val id: Long = 0,               // 数据库主键
    val name: String,               // 分类名称（如 "游戏"、"音乐"）
    val color: Long,                // 分类颜色（存为 Long，用 Color(category.color) 转成颜色对象）
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间戳
    val videoCount: Int = 0         // 该分类下有多少个视频（用于 UI 显示计数）
)
