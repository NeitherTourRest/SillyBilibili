// ============================================================
// VideoDao.kt — 视频数据库的"访问接口"
// ============================================================
// DAO = Data Access Object（数据访问对象）。
// Room 会自动实现这个接口，你只需要写 SQL 查询语句。
// 每个函数都对应一条 SQL 操作（增/删/改/查）。
//
// 被 VideoRepositoryImpl 调用 → 被 ViewModel 调用 → 被 UI 使用。
//
// 注意两种返回类型：
//   Flow<List<VideoEntity>> — 返回后可观察（数据库变化时自动通知 UI）
// ============================================================

package com.example.sillybilibili.data.local.dao

// Room 全套注解：
//   @Dao = 标记这是数据访问接口
//   @Query = 自定义 SQL 查询语句
//   @Insert = 自动生成插入 SQL
//   @Update = 自动生成更新 SQL
//   @Delete = 自动生成删除 SQL
import androidx.room.*
// VideoEntity = 数据库表对应的数据类，DAO 操作的就是这个类型的对象
import com.example.sillybilibili.data.local.entity.VideoEntity
// Flow = Kotlin 协程的"数据流"，可以持续观察数据库变化
// 当 DAO 方法返回 Flow 时，数据一变 UI 自动刷新
import kotlinx.coroutines.flow.Flow

@Dao  // Room 注解：标记这是数据访问接口
interface VideoDao {

    // --- 查询（返回 Flow = 可观察变化） ---

    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE categoryId = :categoryId ORDER BY addedAt DESC")
    fun getVideosByCategory(categoryId: Long): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE categoryId IS NULL ORDER BY addedAt DESC")
    fun getUncategorizedVideos(): Flow<List<VideoEntity>>

    // 模糊搜索：匹配 title、ownerName、avid（avid 转成文本后匹配）
    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR ownerName LIKE '%' || :query || '%' OR CAST(avid AS TEXT) LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE categoryId = :categoryId AND (title LIKE '%' || :query || '%' OR ownerName LIKE '%' || :query || '%' OR CAST(avid AS TEXT) LIKE '%' || :query || '%') ORDER BY addedAt DESC")
    fun searchVideosInCategory(categoryId: Long, query: String): Flow<List<VideoEntity>>

    // --- 查询（suspend = 一次性，不持续观察） ---

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE path = :path")
    suspend fun getVideoByPath(path: String): VideoEntity?

    // --- 分页查询（LIMIT + OFFSET） ---

    @Query("SELECT * FROM videos ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getVideosPaginated(limit: Int, offset: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE categoryId = :categoryId ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getVideosByCategoryPaginated(categoryId: Long, limit: Int, offset: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR ownerName LIKE '%' || :query || '%' OR CAST(avid AS TEXT) LIKE '%' || :query || '%' ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun searchVideosPaginated(query: String, limit: Int, offset: Int): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE categoryId = :categoryId AND (title LIKE '%' || :query || '%' OR ownerName LIKE '%' || :query || '%' OR CAST(avid AS TEXT) LIKE '%' || :query || '%') ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun searchVideosInCategoryPaginated(categoryId: Long, query: String, limit: Int, offset: Int): List<VideoEntity>

    // --- 统计查询 ---

    @Query("SELECT COUNT(*) FROM videos WHERE sourceAvailable = 1")
    suspend fun getTotalVideoCount(): Int

    // 获取所有 avId，用于扫描去重
    @Query("SELECT avid FROM videos")
    suspend fun getAllAvIds(): List<Long>

    // 路径是缓存视频的实际唯一标识；同一个 av 可以有多个 cid（分 P）。
    @Query("SELECT path FROM videos")
    suspend fun getAllVideoPaths(): List<String>

    // --- 统一过滤+搜索+分页查询 ---
    // 这是最核心的复杂查询，把搜索、过滤、分类、分页全部合并到一条 SQL 里。
    // 每个参数都是可空的：如果传 null，对应的 WHERE 条件被跳过。
    // 被 HomeViewModel.loadFirstPage() / loadMore() / goToPage() 调用。
    @Query("""
        SELECT * FROM videos 
        WHERE sourceAvailable = 1
        AND (:query IS NULL OR title LIKE '%' || :query || '%' ESCAPE '\' OR ownerName LIKE '%' || :query || '%' ESCAPE '\' OR CAST(avid AS TEXT) LIKE '%' || :query || '%' ESCAPE '\')
        AND (:qualityFilter IS NULL OR quality LIKE '%' || :qualityFilter || '%')
        AND (:isPortrait IS NULL OR (CASE WHEN height > width THEN 1 ELSE 0 END) = :isPortrait)
        AND (:minDuration IS NULL OR duration >= :minDuration)
        AND (:maxDuration IS NULL OR duration < :maxDuration)
        AND (:minSize IS NULL OR size >= :minSize)
        AND (:maxSize IS NULL OR size < :maxSize)
        AND (:minAddedAt IS NULL OR addedAt >= :minAddedAt)
        AND (:hasCover IS NULL OR (CASE WHEN coverPath IS NOT NULL THEN 1 ELSE 0 END) = :hasCover)
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY addedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredVideosPaginated(
        query: String?,        // 搜索关键词（null = 不搜索）
        qualityFilter: String?, // 画质过滤（"1080P"、null = 不限）
        isPortrait: Int?,       // 方向过滤（1=竖屏, 0=横屏, null=不限）
        minDuration: Long?,     // 最短时长（毫秒）
        maxDuration: Long?,     // 最长时长（毫秒）
        minSize: Long?,         // 最小文件大小（字节）
        maxSize: Long?,         // 最大文件大小（字节）
        minAddedAt: Long?,      // 最早添加时间
        hasCover: Int?,         // 是否有封面（1=有, 0=无, null=不限）
        categoryId: Long?,      // 分类 ID（null = 不限分类）
        limit: Int,             // 每页多少条（PAGE_SIZE = 20）
        offset: Int             // 跳过前 offset 条
    ): List<VideoEntity>

    // --- 写入操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)  // 冲突时覆盖
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE path = :path")
    suspend fun deleteVideoByPath(path: String)

    @Query("DELETE FROM videos WHERE categoryId = :categoryId")
    suspend fun deleteVideosByCategory(categoryId: Long)

    @Query("SELECT COUNT(*) FROM videos WHERE categoryId = :categoryId AND sourceAvailable = 1")
    suspend fun getVideoCountByCategory(categoryId: Long): Int
    @Query("DELETE FROM videos")
    suspend fun deleteAllVideos()

    @Query("UPDATE videos SET sourceAvailable = 1, sourceLastSeenAt = :scanTimestamp WHERE path IN (:paths)")
    suspend fun markSourcesSeen(paths: List<String>, scanTimestamp: Long)

    @Query("UPDATE videos SET sourceAvailable = 1, sourceLastSeenAt = :scanTimestamp WHERE path = :path")
    suspend fun markSourceSeen(path: String, scanTimestamp: Long)

    @Query("UPDATE videos SET sourceAvailable = 0 WHERE path LIKE :directoryPrefix || '%' AND sourceLastSeenAt < :scanTimestamp")
    suspend fun markSourcesMissingInDirectory(directoryPrefix: String, scanTimestamp: Long)

    @Query("DELETE FROM videos WHERE sourceAvailable = 0 AND exportedPath IS NULL")
    suspend fun deleteMissingUnexportedVideos()

    @Query("SELECT path FROM videos WHERE path LIKE :directoryPrefix || '%' AND sourceAvailable = 1")
    suspend fun getAvailableSourcePathsInDirectory(directoryPrefix: String): List<String>

    /** Atomically applies a successful scan so an app/process interruption cannot leave half a sync. */
    @Transaction
    suspend fun syncCacheDirectory(
        directoryPrefix: String,
        videos: List<VideoEntity>,
        seenPaths: List<String>,
        scanTimestamp: Long,
        allowMissingSourceReconciliation: Boolean
    ) {
        if (videos.isNotEmpty()) insertVideos(videos)
        seenPaths.chunked(800).forEach { paths -> markSourcesSeen(paths, scanTimestamp) }
        if (allowMissingSourceReconciliation) {
            markSourcesMissingInDirectory(directoryPrefix, scanTimestamp)
            deleteMissingUnexportedVideos()
        }
    }

    @Query("SELECT * FROM videos WHERE exportedPath IS NOT NULL")
    suspend fun getExportedVideosOnce(): List<VideoEntity>

    // 获取所有已导出为 MP4 的视频
    @Query("SELECT * FROM videos WHERE exportedPath IS NOT NULL ORDER BY addedAt DESC")
    fun getExportedVideos(): Flow<List<VideoEntity>>
}
