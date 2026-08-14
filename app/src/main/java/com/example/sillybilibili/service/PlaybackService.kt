package com.example.sillybilibili.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.sillybilibili.util.ShizukuFileHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Owns playback independently from the Compose screen for reliable background audio. */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var shizukuHelper: ShizukuFileHelper
    @Inject lateinit var readAheadCache: ShizukuReadAheadCache
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopPlaybackRunnable = Runnable {
        player?.pause()
        timerEndTimeMillis = 0L
    }

    override fun onCreate() {
        super.onCreate()
        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 1_500, 2_000)
            .build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(CacheMediaSourceFactory(this, shizukuHelper, readAheadCache))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                repeatMode = Player.REPEAT_MODE_OFF
            }
        mediaSession = MediaSession.Builder(this, requireNotNull(player)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_SLEEP_TIMER -> {
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                if (durationMs > 0L) {
                    mainHandler.removeCallbacks(stopPlaybackRunnable)
                    timerEndTimeMillis = System.currentTimeMillis() + durationMs
                    mainHandler.postDelayed(stopPlaybackRunnable, durationMs)
                }
            }
            ACTION_CANCEL_SLEEP_TIMER -> {
                mainHandler.removeCallbacks(stopPlaybackRunnable)
                timerEndTimeMillis = 0L
            }
            ACTION_STOP_PLAYBACK -> {
                mainHandler.removeCallbacks(stopPlaybackRunnable)
                timerEndTimeMillis = 0L
                player?.stop()
                player?.clearMediaItems()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (player?.isPlaying != true) stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(stopPlaybackRunnable)
        timerEndTimeMillis = 0L
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SET_SLEEP_TIMER = "com.example.sillybilibili.action.SET_SLEEP_TIMER"
        private const val ACTION_CANCEL_SLEEP_TIMER = "com.example.sillybilibili.action.CANCEL_SLEEP_TIMER"
        private const val ACTION_STOP_PLAYBACK = "com.example.sillybilibili.action.STOP_PLAYBACK"
        private const val EXTRA_DURATION_MS = "duration_ms"

        @Volatile
        var timerEndTimeMillis: Long = 0L
            private set

        fun setSleepTimer(context: Context, durationMs: Long) {
            context.startService(Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SET_SLEEP_TIMER)
                .putExtra(EXTRA_DURATION_MS, durationMs))
        }

        fun cancelSleepTimer(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java)
                .setAction(ACTION_CANCEL_SLEEP_TIMER))
        }

        fun stopPlayback(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java)
                .setAction(ACTION_STOP_PLAYBACK))
        }
    }
}
