// ============================================================
// CategoryRepository.kt — 分类仓库接口
// ============================================================
// 定义分类数据操作的接口。具体实现在 CategoryRepositoryImpl.kt。
// 被 HomeViewModel / VideoListViewModel / CategoriesPage 使用。
// ============================================================

package com.example.sillybilibili.domain.repository

// Category = 分类数据模型
import com.example.sillybilibili.domain.model.Category
// Flow = 可观察数据流
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>  // 可观察的全部分类
    suspend fun getCategoryById(id: Long): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(id: Long)
}
