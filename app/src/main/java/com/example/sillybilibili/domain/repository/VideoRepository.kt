// ============================================================
// VideoRepository.kt — 视频仓库接口（Domain 层）
// ============================================================
// 仓库（Repository）是数据来源的统一"出入口"。
// ViewModel 只跟仓库接口打交道，不关心具体实现（是 Room 还是网络）。
// 这样换数据源时不需要改 ViewModel 的代码。
//
// 被 HomeViewModel / VideoListViewModel / VideoDetailViewModel 调用。
// 具体实现在 VideoRepositoryImpl.kt。
// ============================================================

package com.example.sillybilibili.domain.repository

// Video = 我们自己定义的视频数据模型（Domain 层最核心的数据类）
import com.example.sillybilibili.domain.model.Video
// Flow = Kotlin 协程的数据流。用于"可观察"的查询——数据一变，订阅者自动收到新数据
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getAllVideos(): Flow<List<Video>>            // 全部视频（可观察）
    fun getVideosByCategory(categoryId: Long): Flow<List<Video>>  // 按分类筛选
    fun getUncategorizedVideos(): Flow<List<Video>>  // 未分类的视频
    suspend fun getVideoById(id: Long): Video?       // 按 ID 查一个视频
    suspend fun getVideoByPath(path: String): Video?

    fun searchVideos(query: String): Flow<List<Video>>       // 全局搜索
    fun searchVideosInCategory(categoryId: Long, query: String): Flow<List<Video>>

    suspend fun insertVideo(video: Video): Long
    suspend fun insertVideos(videos: List<Video>)
    suspend fun updateVideo(video: Video)
    suspend fun deleteVideo(video: Video)
    suspend fun deleteVideoByPath(path: String)
    suspend fun deleteAllVideos()

    // 以下 4 个分页方法仍保留（被 VideoListViewModel 使用）
    suspend fun getAllVideosPaginated(page: Int, pageSize: Int): List<Video>
    suspend fun getVideosByCategoryPaginated(categoryId: Long, page: Int, pageSize: Int): List<Video>
    suspend fun searchVideosPaginated(query: String, page: Int, pageSize: Int): List<Video>
    suspend fun searchVideosInCategoryPaginated(categoryId: Long, query: String, page: Int, pageSize: Int): List<Video>

    suspend fun getTotalVideoCount(): Int
    suspend fun getAllAvIds(): List<Long>  // 获取所有 avid（用于扫描去重）
    suspend fun getAllVideoPaths(): List<String> // 缓存文件路径，用于按分 P 增量扫描
    suspend fun reconcileCacheDirectory(directoryPrefix: String, seenPaths: List<String>, scanTimestamp: Long)
    suspend fun syncCacheDirectory(
        directoryPrefix: String,
        scannedVideos: List<Video>,
        seenPaths: List<String>,
        scanTimestamp: Long,
        allowMissingSourceReconciliation: Boolean
    )
    suspend fun getAvailableSourcePathsInDirectory(directoryPrefix: String): List<String>
    suspend fun getExportedVideosOnce(): List<Video>

    fun getExportedVideos(): Flow<List<Video>>  // 已转换MP4的视频

    // 统一过滤+搜索+分页（核心方法，替代上面4个分页方法）
    suspend fun getFilteredVideosPaginated(
        query: String?,
        qualityFilter: String?,
        isPortrait: Int?,
        minDuration: Long?,
        maxDuration: Long?,
        minSize: Long?,
        maxSize: Long?,
        minAddedAt: Long?,
        hasCover: Int?,
        categoryId: Long?,
        page: Int,
        pageSize: Int
    ): List<Video>
}
