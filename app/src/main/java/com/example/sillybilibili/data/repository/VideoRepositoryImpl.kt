// ============================================================
// VideoRepositoryImpl.kt — 视频仓库接口的具体实现
// ============================================================
// 这是 VideoRepository 接口的真正代码。
// 它做的事情很简单：调用 VideoDao 的方法，把结果从 Entity 转成 Domain 模型。
// @Inject constructor 让 Hilt 自动注入 VideoDao。
// ============================================================

package com.example.sillybilibili.data.repository

// VideoDao = 视频表的数据库操作接口
import com.example.sillybilibili.data.local.dao.VideoDao
// VideoEntity = 视频表的数据类（数据库层）
import com.example.sillybilibili.data.local.entity.VideoEntity
// Video = 视频数据模型（Domain 层，UI 层用的）
import com.example.sillybilibili.domain.model.Video
// VideoRepository = 仓库接口
import com.example.sillybilibili.domain.repository.VideoRepository
// Flow = 可观察数据流
import kotlinx.coroutines.flow.Flow
// map = Flow 的转换操作符，把 List<Entity> 转为 List<Domain>
import kotlinx.coroutines.flow.map
// @Inject = Hilt 注解：标记构造函数，让 Hilt 自动提供依赖
import javax.inject.Inject

// @Inject constructor = Hilt 自动提供依赖（VideoDao）
class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao
) : VideoRepository {

    // 每个方法都是"委托"模式：
    // 调用 DAO → 把结果 .map { it.toDomain() } 转成 Domain 模型 → 返回

    override fun getAllVideos(): Flow<List<Video>> {
        return videoDao.getAllVideos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getVideosByCategory(categoryId: Long): Flow<List<Video>> {
        return videoDao.getVideosByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUncategorizedVideos(): Flow<List<Video>> {
        return videoDao.getUncategorizedVideos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVideoById(id: Long): Video? {
        return videoDao.getVideoById(id)?.toDomain()
    }

    override suspend fun getVideoByPath(path: String): Video? {
        return videoDao.getVideoByPath(path)?.toDomain()
    }

    override fun searchVideos(query: String): Flow<List<Video>> {
        return videoDao.searchVideos(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchVideosInCategory(categoryId: Long, query: String): Flow<List<Video>> {
        return videoDao.searchVideosInCategory(categoryId, query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertVideo(video: Video): Long {
        return videoDao.insertVideo(video.toEntity())
    }

    override suspend fun insertVideos(videos: List<Video>) {
        videoDao.insertVideos(videos.map { it.toEntity() })
    }

    override suspend fun updateVideo(video: Video) {
        videoDao.updateVideo(video.toEntity())
    }

    override suspend fun deleteVideo(video: Video) {
        videoDao.deleteVideo(video.toEntity())
    }

    override suspend fun deleteVideoByPath(path: String) {
        videoDao.deleteVideoByPath(path)
    }

    override suspend fun deleteAllVideos() {
        videoDao.deleteAllVideos()
    }

    // 分页方法：计算 offset = page * pageSize，委托 DAO
    override suspend fun getAllVideosPaginated(page: Int, pageSize: Int): List<Video> {
        return videoDao.getVideosPaginated(pageSize, page * pageSize).map { it.toDomain() }
    }

    override suspend fun getVideosByCategoryPaginated(categoryId: Long, page: Int, pageSize: Int): List<Video> {
        return videoDao.getVideosByCategoryPaginated(categoryId, pageSize, page * pageSize).map { it.toDomain() }
    }

    override suspend fun searchVideosPaginated(query: String, page: Int, pageSize: Int): List<Video> {
        return videoDao.searchVideosPaginated(query, pageSize, page * pageSize).map { it.toDomain() }
    }

    override suspend fun searchVideosInCategoryPaginated(categoryId: Long, query: String, page: Int, pageSize: Int): List<Video> {
        return videoDao.searchVideosInCategoryPaginated(categoryId, query, pageSize, page * pageSize).map { it.toDomain() }
    }

    override suspend fun getTotalVideoCount(): Int {
        return videoDao.getTotalVideoCount()
    }

    override suspend fun getAllAvIds(): List<Long> {
        return videoDao.getAllAvIds()
    }

    override suspend fun getAllVideoPaths(): List<String> {
        return videoDao.getAllVideoPaths()
    }

    override suspend fun reconcileCacheDirectory(directoryPrefix: String, seenPaths: List<String>, scanTimestamp: Long) {
        // SQLite supports a bounded number of bind parameters; keep large libraries safe.
        seenPaths.chunked(800).forEach { paths -> videoDao.markSourcesSeen(paths, scanTimestamp) }
        videoDao.markSourcesMissingInDirectory(directoryPrefix, scanTimestamp)
        videoDao.deleteMissingUnexportedVideos()
    }

    override suspend fun syncCacheDirectory(
        directoryPrefix: String,
        scannedVideos: List<Video>,
        seenPaths: List<String>,
        scanTimestamp: Long,
        allowMissingSourceReconciliation: Boolean
    ) {
        videoDao.syncCacheDirectory(
            directoryPrefix = directoryPrefix,
            videos = scannedVideos.map { it.toEntity() },
            seenPaths = seenPaths,
            scanTimestamp = scanTimestamp,
            allowMissingSourceReconciliation = allowMissingSourceReconciliation
        )
    }

    override suspend fun getAvailableSourcePathsInDirectory(directoryPrefix: String): List<String> =
        videoDao.getAvailableSourcePathsInDirectory(directoryPrefix)

    override suspend fun getExportedVideosOnce(): List<Video> =
        videoDao.getExportedVideosOnce().map { it.toDomain() }

    override fun getExportedVideos(): Flow<List<Video>> {
        return videoDao.getExportedVideos().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // 统一过滤+搜索+分页方法
    override suspend fun getFilteredVideosPaginated(
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
    ): List<Video> {
        return videoDao.getFilteredVideosPaginated(
            query = query?.escapeForLike(),
            qualityFilter = qualityFilter,
            isPortrait = isPortrait,
            minDuration = minDuration,
            maxDuration = maxDuration,
            minSize = minSize,
            maxSize = maxSize,
            minAddedAt = minAddedAt,
            hasCover = hasCover,
            categoryId = categoryId,
            limit = pageSize,
            offset = page * pageSize
        ).map { it.toDomain() }
    }

    /** Escape SQLite LIKE wildcards so user input is treated as literal text. */
    private fun String.escapeForLike(): String {
        // Order matters: escape the escape character first, then % and _
        return this.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }

    companion object {
        /** Exposed for testing. */
        fun escapeForLikeStatic(input: String): String {
            return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        }
    }

    // --- Entity ↔ Domain 类型转换 ---
    // Room 操作的是 Entity，UI 使用的是 Domain 模型
    // 这两个 private fun 做双向转换

    private fun VideoEntity.toDomain() = Video(
        id = id,
        avid = avid,
        cid = cid,
        title = title,
        ownerName = ownerName,
        quality = quality,
        width = width,
        height = height,
        path = path,
        audioPath = audioPath,
        size = size,
        duration = duration,
        categoryId = categoryId,
        coverPath = coverPath,
        coverSourcePath = coverSourcePath,
        pubdate = pubdate,
        addedAt = addedAt,
        exportedPath = exportedPath,
        sourceAvailable = sourceAvailable,
        sourceLastSeenAt = sourceLastSeenAt,
        exportedSize = exportedSize,
        exportedLastModified = exportedLastModified,
        onlineStatus = com.example.sillybilibili.domain.model.OnlineVideoStatus.fromStorage(onlineStatus),
        onlineCheckedAt = onlineCheckedAt
    )

    private fun Video.toEntity() = VideoEntity(
        id = id,
        avid = avid,
        cid = cid,
        title = title,
        ownerName = ownerName,
        quality = quality,
        width = width,
        height = height,
        path = path,
        audioPath = audioPath,
        size = size,
        duration = duration,
        categoryId = categoryId,
        coverPath = coverPath,
        coverSourcePath = coverSourcePath,
        pubdate = pubdate,
        addedAt = addedAt,
        exportedPath = exportedPath,
        sourceAvailable = sourceAvailable,
        sourceLastSeenAt = sourceLastSeenAt,
        exportedSize = exportedSize,
        exportedLastModified = exportedLastModified,
        onlineStatus = onlineStatus.name,
        onlineCheckedAt = onlineCheckedAt
    )
}
