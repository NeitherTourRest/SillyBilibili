package com.example.sillybilibili.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small process-local cache for the first bytes of nearby isolated cache files.
 *
 * Bilibili's cache is usually local, but reads through Shizuku cross a Binder boundary. Keeping
 * a few adjacent m4s ranges here avoids paying that first Binder round-trip at the moment a user
 * completes a full-screen swipe. This is deliberately not a full media cache: it never writes a
 * file, has a strict memory cap, and evicts least-recently-used ranges.
 */
@Singleton
class ShizukuReadAheadCache @Inject constructor() {
    private data class BlockKey(val path: String, val offset: Long)

    private val lock = Any()
    private val blocks = LinkedHashMap<BlockKey, ByteArray>(16, 0.75f, true)
    private val fileLengths = LinkedHashMap<String, Long>(16, 0.75f, true)
    private var cachedBytes = 0

    fun fileLength(path: String): Long? = synchronized(lock) { fileLengths[path] }

    fun storeFileLength(path: String, length: Long) {
        if (length <= 0L) return
        synchronized(lock) {
            fileLengths[path] = length
            while (fileLengths.size > MAX_CACHED_FILE_LENGTHS) {
                fileLengths.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
        }
    }

    /** Reads a range, using a preloaded block when it completely covers the request. */
    fun read(
        path: String,
        position: Long,
        length: Int,
        loader: (offset: Long, length: Int) -> ByteArray?
    ): ByteArray? {
        if (position < 0L || length <= 0) return null
        readCached(path, position, length)?.let { return it }

        val blockOffset = position / BLOCK_SIZE_BYTES * BLOCK_SIZE_BYTES
        val requiredBytes = (position - blockOffset + length).coerceAtMost(MAX_BLOCK_LOAD_BYTES.toLong()).toInt()
        val loaded = loader(blockOffset, maxOf(BLOCK_SIZE_BYTES, requiredBytes)) ?: return null
        storeBlock(path, blockOffset, loaded)
        return readCached(path, position, length)
    }

    /** Stores the leading media bytes in fixed-size blocks so normal player reads can reuse them. */
    fun preload(
        path: String,
        byteCount: Int,
        loader: (offset: Long, length: Int) -> ByteArray?
    ) {
        var offset = 0L
        val target = byteCount.coerceAtLeast(0).toLong()
        while (offset < target) {
            if (!contains(path, offset)) {
                val requested = minOf(BLOCK_SIZE_BYTES.toLong(), target - offset).toInt()
                val loaded = loader(offset, requested) ?: return
                if (loaded.isEmpty()) return
                storeBlock(path, offset, loaded)
                if (loaded.size < requested) return
            }
            offset += BLOCK_SIZE_BYTES
        }
    }

    private fun readCached(path: String, position: Long, length: Int): ByteArray? = synchronized(lock) {
        val blockOffset = position / BLOCK_SIZE_BYTES * BLOCK_SIZE_BYTES
        val block = blocks[BlockKey(path, blockOffset)] ?: return@synchronized null
        val start = (position - blockOffset).toInt()
        val end = start + length
        if (start < 0 || end > block.size) null else block.copyOfRange(start, end)
    }

    private fun contains(path: String, offset: Long): Boolean = synchronized(lock) {
        blocks.containsKey(BlockKey(path, offset))
    }

    private fun storeBlock(path: String, offset: Long, data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            val key = BlockKey(path, offset)
            blocks.put(key, data)?.let { cachedBytes -= it.size }
            cachedBytes += data.size
            while (cachedBytes > MAX_CACHED_BYTES && blocks.isNotEmpty()) {
                blocks.entries.iterator().run {
                    val eldest = next()
                    cachedBytes -= eldest.value.size
                    remove()
                }
            }
        }
    }

    private companion object {
        const val BLOCK_SIZE_BYTES = 256 * 1024
        const val MAX_BLOCK_LOAD_BYTES = 256 * 1024
        const val MAX_CACHED_BYTES = 6 * 1024 * 1024
        const val MAX_CACHED_FILE_LENGTHS = 32
    }
}
