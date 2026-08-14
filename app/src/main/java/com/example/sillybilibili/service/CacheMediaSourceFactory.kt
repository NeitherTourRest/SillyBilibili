package com.example.sillybilibili.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.sillybilibili.util.ShizukuFileHelper

/** Keys placed in MediaMetadata extras so a MediaSession can rebuild a two-track cache source. */
object CachePlaybackMetadata {
    const val AUDIO_URI = "com.example.sillybilibili.audio_uri"

    fun extras(audioUri: Uri?): Bundle? = audioUri?.let {
        Bundle().apply { putString(AUDIO_URI, it.toString()) }
    }
}

/** Builds a MergingMediaSource for cached video.m4s + audio.m4s pairs. */
@UnstableApi
class CacheMediaSourceFactory(
    context: Context,
    private val shizukuHelper: ShizukuFileHelper,
    private val readAheadCache: ShizukuReadAheadCache
) : MediaSource.Factory {
    private val defaultFactory = DefaultMediaSourceFactory(context)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val videoSource = createSingleSource(mediaItem)
        val audioUri = mediaItem.mediaMetadata.extras?.getString(CachePlaybackMetadata.AUDIO_URI)
            ?.let(Uri::parse)
            ?: return videoSource
        val audioItem = MediaItem.Builder().setUri(audioUri).build()
        return MergingMediaSource(videoSource, createSingleSource(audioItem))
    }

    private fun createSingleSource(mediaItem: MediaItem): MediaSource {
        return if (mediaItem.localConfiguration?.uri?.scheme == "shizuku") {
            ProgressiveMediaSource.Factory(ShizukuDataSource.Factory(shizukuHelper, readAheadCache))
                .createMediaSource(mediaItem)
        } else {
            defaultFactory.createMediaSource(mediaItem)
        }
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes
}
