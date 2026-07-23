// ============================================================
// CategoryRepositoryImpl.kt — 分类仓库实现
// ============================================================
// 实现 CategoryRepository 接口。
// 每次获取分类时还会查询该分类下有多少视频（videoCount），
// 这样 UI 上显示"游戏 (12)"这样的计数。
// ============================================================

package com.example.sillybilibili.data.repository

// CategoryDao / VideoDao = DAO 接口
import com.example.sillybilibili.data.local.dao.CategoryDao
import com.example.sillybilibili.data.local.dao.VideoDao
// CategoryEntity = 分类表数据类
import com.example.sillybilibili.data.local.entity.CategoryEntity
// Category = 分类数据模型（Domain 层）
import com.example.sillybilibili.domain.model.Category
// CategoryRepository = 分类仓库接口
import com.example.sillybilibili.domain.repository.CategoryRepository
// Flow / map = 可观察数据流 + 转换操作符
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
// @Inject = Hilt 依赖注入
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val videoDao: VideoDao  // 需要 VideoDao 来查分类的视频数量
) : CategoryRepository {

    // 获取全部分类，并为每个分类查询其下的视频数量
    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { entity ->
                val count = videoDao.getVideoCountByCategory(entity.id)
                entity.toDomain(count)
            }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)?.let { entity ->
            val count = videoDao.getVideoCountByCategory(entity.id)
            entity.toDomain(count)
        }
    }

    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(id: Long) {
        categoryDao.deleteCategoryById(id)
    }

    // Entity ↔ Domain 转换
    private fun CategoryEntity.toDomain(videoCount: Int = 0) = Category(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt,
        videoCount = videoCount
    )

    private fun Category.toEntity() = CategoryEntity(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt
    )
}
