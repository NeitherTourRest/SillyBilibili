// ============================================================
// CategoryDao.kt — 分类数据库访问接口
// ============================================================
// Room DAO，操作 categories 表（增/删/改/查）。
// 被 CategoryRepositoryImpl 调用 → 被 ViewModel 调用 → 被 UI 使用。
// ============================================================

package com.example.sillybilibili.data.local.dao

// Room 全套注解：@Dao / @Query / @Insert / @Update / @Delete
import androidx.room.*
// CategoryEntity = 分类表对应的数据类
import com.example.sillybilibili.data.local.entity.CategoryEntity
// Flow = 可观察的数据流，数据库变化时自动通知 UI
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY createdAt DESC")
    fun getAllCategories(): Flow<List<CategoryEntity>>  // 返回 Flow = 可观察变化

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)
}
