package com.example.sillybilibili.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.sillybilibili.util.ShizukuFileHelper
import java.io.IOException
import kotlin.math.min

/** A seekable Media3 data source backed by bounded Shizuku Binder reads. */
@UnstableApi
class ShizukuDataSource(
    private val shizukuHelper: ShizukuFileHelper
) : BaseDataSource(false) {
    private var openedUri: Uri? = null
    private var sourcePath = ""
    private var readPosition = 0L
    private var bytesRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path?.takeIf { it.isNotBlank() }
            ?: throw IOException("Invalid Shizuku media URI")
        val fileLength = shizukuHelper.fileLength(path, useShizuku = true)
        if (fileLength <= dataSpec.position) throw IOException("Media file is unavailable")
        sourcePath = path
        openedUri = dataSpec.uri
        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            fileLength - readPosition
        } else {
            min(dataSpec.length, fileLength - readPosition)
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val requestSize = min(min(length.toLong(), bytesRemaining), MAX_RANGE_BYTES.toLong()).toInt()
        val chunk = shizukuHelper.readFileRange(sourcePath, readPosition, requestSize, useShizuku = true)
            ?: throw IOException("Unable to read isolated media file")
        if (chunk.isEmpty()) return C.RESULT_END_OF_INPUT
        val copied = min(chunk.size, requestSize)
        chunk.copyInto(buffer, destinationOffset = offset, endIndex = copied)
        readPosition += copied
        bytesRemaining -= copied
        bytesTransferred(copied)
        return copied
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        if (openedUri != null) transferEnded()
        openedUri = null
        sourcePath = ""
        readPosition = 0L
        bytesRemaining = 0L
    }

    class Factory(private val shizukuHelper: ShizukuFileHelper) : DataSource.Factory {
        override fun createDataSource(): DataSource = ShizukuDataSource(shizukuHelper)
    }

    private companion object {
        const val MAX_RANGE_BYTES = 128 * 1024
    }
}
