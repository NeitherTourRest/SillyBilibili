// ============================================================
// AppDatabase.kt — Room 数据库定义
// ============================================================
// Room 是 Android 官方的 SQLite 数据库框架。
// 这个类定义了数据库的"骨架"：有哪些表、当前版本号是多少。
// version = 8 adds the video publish date (pubdate) column.
// 实际建库和迁移逻辑在 DatabaseModule.kt。
// 被 DatabaseModule.kt 调用 → 全局单例。
// ============================================================

package com.example.sillybilibili.data.local

// @Database = Room 注解，标记这是一个数据库类
import androidx.room.Database
// RoomDatabase = Room 数据库的基类，提供 DAO 的访问方法
import androidx.room.RoomDatabase
// VideoDao / CategoryDao = 我们自己写的 DAO 接口，Room 会自动实现
import com.example.sillybilibili.data.local.dao.CategoryDao
import com.example.sillybilibili.data.local.dao.VideoDao
// VideoEntity / CategoryEntity = 数据库表对应的数据类
import com.example.sillybilibili.data.local.entity.CategoryEntity
import com.example.sillybilibili.data.local.entity.VideoEntity

// entities = 该数据库有哪些表（每张表对应一个 Entity 类）
// version = 数据库版本号（升级时写迁移逻辑，见 DatabaseModule.kt）
@Database(
    entities = [CategoryEntity::class, VideoEntity::class],
    version = 8,
    exportSchema = false  // 不导出 Schema 文件（开发调试用）
)
abstract class AppDatabase : RoomDatabase() {
    // Room 自动实现这两个方法，返回 DAO 实例
    abstract fun categoryDao(): CategoryDao
    abstract fun videoDao(): VideoDao
}
