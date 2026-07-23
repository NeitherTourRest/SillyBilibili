// ============================================================
// VideoEntity.kt — 视频的数据库表结构
// ============================================================
// 这是 Room 数据库的一张表叫 "videos"。
// 当 VideoScanService 扫描到一个视频，它会把数据存成 VideoEntity → 写进 SQLite。
// 读出来时，VideoRepositoryImpl 把 VideoEntity 转成 Video（Domain 模型）给 UI 用。
//
// 为什么要有 Entity / Domain 两层？
//   Entity 层和数据库表一一对应，Domain 层是给 UI 用的纯净模型。
//   这样可以避免 UI 直接和数据库结构耦合。
// ============================================================

package com.example.sillybilibili.data.local.entity

// @Entity = Room 注解，标记这个类是对应数据库的一张表
import androidx.room.Entity
// @ForeignKey = 外键约束，定义表之间的关联关系（如 videos.categoryId → categories.id）
import androidx.room.ForeignKey
// @Index = 数据库索引，加速查询（如按 path 查询时）
import androidx.room.Index
// @PrimaryKey = 主键标记（每一行的唯一 ID）
import androidx.room.PrimaryKey

// @Entity(...) 告诉 Room：这是个数据库表
// tableName = "videos" → 表名
// foreignKeys → 外键：categoryId 关联到 categories 表的 id
//   当分类被删除时（onDelete = SET_NULL），对应的视频 categoryId 被设为 null
// indices → 数据库索引：加速按分类查询和按路径查询
@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoryId"),               // 加速 SELECT WHERE categoryId = ?
        Index(value = ["path"], unique = true)  // 路径唯一，防止同一视频重复扫描
    ]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)  // 自增主键
    val id: Long = 0,
    val avid: Long,              // B站 av号
    val cid: Long,               // 分P cid
    val title: String,           // 视频标题
    val ownerName: String = "",  // UP主名
    val quality: String = "",    // 画质（"1080P"）
    val width: Int = 0,          // 视频宽度
    val height: Int = 0,         // 视频高度
    val path: String,            // video.m4s 完整路径（唯一索引）
    val audioPath: String,       // audio.m4s 完整路径
    val size: Long,              // 总大小（字节）
    val duration: Long,          // 时长（毫秒）
    val categoryId: Long? = null,// 所属分类 ID
    val coverPath: String? = null,// 封面缓存路径
    val addedAt: Long = System.currentTimeMillis(),  // 添加时间
    val exportedPath: String? = null  // 导出后的 .mp4 路径（null=未导出）
)
