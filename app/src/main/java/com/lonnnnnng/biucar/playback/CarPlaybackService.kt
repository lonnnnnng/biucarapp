package com.lonnnnnng.biucar.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lonnnnnng.biucar.carContainer
import com.lonnnnnng.biucar.data.model.EXTRA_BVID
import com.lonnnnnng.biucar.data.model.EXTRA_CID
import com.lonnnnnng.biucar.data.model.EXTRA_PAGE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_RESOURCE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_STREAM_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CarPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private var recordedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let(::recordStarted)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                player?.currentMediaItem?.let(::recordStarted)
                startProgressPersistence()
            } else {
                stopProgressPersistence()
                persistProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) persistProgress()
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val mediaSourceFactory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(carContainer.httpClient))
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                addListener(listener)
            }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        stopProgressPersistence()
        player?.removeListener(listener)
        mediaSession?.release()
        player?.release()
        serviceScope.cancel()
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun recordStarted(mediaItem: MediaItem) {
        if (mediaItem.mediaId.isBlank() || mediaItem.mediaId == recordedMediaId) return
        val extras = mediaItem.mediaMetadata.extras ?: return
        val bvid = extras.getString(EXTRA_BVID).orEmpty()
        val cid = extras.getLong(EXTRA_CID, 0L)
        val streamUrl = extras.getString(EXTRA_STREAM_URL).orEmpty()
        if (bvid.isBlank() || cid <= 0L || streamUrl.isBlank()) return
        recordedMediaId = mediaItem.mediaId
        serviceScope.launch {
            carContainer.playbackHistoryRepository.recordStarted(
                mediaId = mediaItem.mediaId,
                bvid = bvid,
                cid = cid,
                title = extras.getString(EXTRA_RESOURCE_TITLE).orEmpty().ifBlank {
                    mediaItem.mediaMetadata.albumTitle?.toString().orEmpty()
                },
                pageTitle = extras.getString(EXTRA_PAGE_TITLE),
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                artworkUrl = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty(),
                streamUrl = streamUrl,
            )
            // long: 在线播放与完整缓存并行进行；缓存失败不打断当前播放，历史仍可在恢复网络后重新解析。
            carContainer.offlineAudioCache.cache(mediaItem)
        }
    }

    private fun startProgressPersistence() {
        if (progressJob?.isActive == true) return
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                persistProgress()
            }
        }
    }

    private fun stopProgressPersistence() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun persistProgress() {
        val activePlayer = player ?: return
        val mediaId = activePlayer.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        val duration = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: activePlayer.currentPosition
        serviceScope.launch {
            carContainer.playbackHistoryRepository.updateProgress(mediaId, activePlayer.currentPosition, duration)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 5_000L
    }
}
