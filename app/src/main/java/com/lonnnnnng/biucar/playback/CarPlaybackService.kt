package com.lonnnnnng.biucar.playback

import android.os.Bundle
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lonnnnnng.biucar.carContainer
import com.lonnnnnng.biucar.data.model.EXTRA_BVID
import com.lonnnnnng.biucar.data.model.EXTRA_CID
import com.lonnnnnng.biucar.data.model.EXTRA_DURATION_MS
import com.lonnnnnng.biucar.data.model.EXTRA_PAGE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_RESOURCE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_STREAM_URL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CarPlaybackService : MediaSessionService() {
    // long: Media3 要求 Player 只在创建它的主线程访问；网络缓存和 Room 自己会切换到后台线程，服务协程因此必须以主线程为调度基准。
    private val serviceScope = CoroutineScope(SupervisorJob() + Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private var recordedMediaId: String? = null
    private var suppressNextTransitionRecord = false
    private val historyWriteMutex = Mutex()
    private val pendingProgress = mutableMapOf<String, ProgressSnapshot>()

    private data class ProgressSnapshot(
        val positionMs: Long,
        val durationMs: Long,
    )

    private val sessionCallback = object : MediaSession.Callback {
        @UnstableApi
        override fun onConnect(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // long: 车机和系统媒体入口可能来自第三方包；只接受系统信任或包名可验证的控制器，拒绝身份无法核实的调用方。
            val allowed = controllerInfo.packageName == packageName ||
                controllerInfo.isTrusted ||
                controllerInfo.isPackageNameVerified
            return if (allowed) {
                MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                )
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }
    }

    private val listener = object : Player.Listener {
        @UnstableApi
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val oldMediaId = oldPosition.mediaItem?.mediaId.orEmpty()
            val newMediaId = newPosition.mediaItem?.mediaId.orEmpty()
            if (oldMediaId.isNotBlank() && oldMediaId != newMediaId) {
                // long: 自动切 P 后 currentMediaItem 已指向新 P，必须使用回调携带的旧媒体项保存上一 P 的最终进度，避免写到错误 cid。
                persistProgress(
                    mediaId = oldMediaId,
                    positionMs = oldPosition.positionMs,
                    durationMs = durationFor(oldPosition.mediaItem),
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (suppressNextTransitionRecord) {
                suppressNextTransitionRecord = false
                mediaItem?.let(::rememberDuration)
                return
            }
            mediaItem?.let {
                rememberDuration(it)
                recordStarted(it)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                player?.currentMediaItem?.let(::recordStarted)
                startProgressPersistence()
            } else {
                stopProgressPersistence()
                rememberCurrentDuration()
                persistProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                rememberCurrentDuration()
                persistProgress()
            }
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        // long: DefaultDataSource 会把离线历史的 file:// 交给 FileDataSource，把在线 DASH 交给带 Bilibili 请求头的 OkHttpDataSource。
        val dataSourceFactory = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(carContainer.httpClient))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                // long: 车机播放页只输出音频；禁用合并 MP4 的视频轨，避免 Android 8.1 为无用画面创建解码器并把起播卡在大缓冲阈值。
                setTrackSelectionParameters(
                    trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                        .build(),
                )
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                addListener(listener)
            }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .build()
        restoreLatestHistory()
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
        if (mediaItem.mediaId.isBlank()) return
        val extras = mediaItem.mediaMetadata.extras ?: return
        val bvid = extras.getString(EXTRA_BVID).orEmpty()
        val cid = extras.getLong(EXTRA_CID, 0L)
        val streamUrl = extras.getString(EXTRA_STREAM_URL).orEmpty()
        if (bvid.isBlank() || cid <= 0L || streamUrl.isBlank()) return
        val shouldRecordHistory = mediaItem.mediaId != recordedMediaId
        if (shouldRecordHistory) recordedMediaId = mediaItem.mediaId
        serviceScope.launch {
            historyWriteMutex.withLock {
                if (shouldRecordHistory) {
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
                        durationMs = durationFor(mediaItem),
                    )
                }
                pendingProgress.remove(mediaItem.mediaId)?.let { pending ->
                    // long: 首次播放和暂停回调可能交错，历史行创建后立即补写等待中的进度，避免 UPDATE 找不到记录而丢失。
                    val updated = carContainer.playbackHistoryRepository.updateProgress(
                        mediaId = mediaItem.mediaId,
                        positionMs = pending.positionMs,
                        durationMs = pending.durationMs,
                    )
                    if (updated == 0) pendingProgress[mediaItem.mediaId] = pending
                }
            }
            // long: 播放历史按媒体去重，但缓存失败后下一次播放仍需重试，避免一次网络抖动永久失去离线能力。
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
        // long: 先在主线程读取 Player 快照，再把纯数据交给历史仓库，避免后台协程触发 Media3 线程断言。
        val positionMs = activePlayer.currentPosition
        val duration = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: durationFor(activePlayer.currentMediaItem)
        persistProgress(mediaId, positionMs, duration)
    }

    private fun persistProgress(mediaId: String, positionMs: Long, durationMs: Long) {
        serviceScope.launch {
            historyWriteMutex.withLock {
                val snapshot = ProgressSnapshot(positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L))
                val updated = carContainer.playbackHistoryRepository.updateProgress(
                    mediaId = mediaId,
                    positionMs = snapshot.positionMs,
                    durationMs = snapshot.durationMs,
                )
                if (updated == 0) {
                    // long: 播放刚开始时 Room 历史行可能还在创建，先暂存快照，等 recordStarted 完成后补写。
                    pendingProgress[mediaId] = snapshot
                }
            }
        }
    }

    private fun rememberCurrentDuration() {
        player?.currentMediaItem?.let(::rememberDuration)
    }

    private fun rememberDuration(mediaItem: MediaItem) {
        val durationMs = durationFor(mediaItem)
        if (durationMs > 0L) durationByMediaId[mediaItem.mediaId] = durationMs
    }

    private fun durationFor(mediaItem: MediaItem?): Long {
        val metadataDuration = mediaItem?.mediaMetadata?.extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L
        return metadataDuration.takeIf { it > 0L }
            ?: mediaItem?.mediaId?.let(durationByMediaId::get).orZero()
    }

    private fun restoreLatestHistory() {
        serviceScope.launch {
            val activePlayer = player ?: return@launch
            if (activePlayer.mediaItemCount > 0) return@launch
            val item = carContainer.playbackHistoryRepository.latest() ?: return@launch
            // long: 查询历史期间页面可能已提交新队列，此时应保留用户刚选择的内容，不能再用旧历史覆盖。
            if (activePlayer.mediaItemCount > 0) return@launch
            val localFile = item.localFilePath?.let(::File)?.takeIf(File::isFile)
            val extras = Bundle().apply {
                putString(EXTRA_BVID, item.bvid)
                putLong(EXTRA_CID, item.cid)
                putString(EXTRA_STREAM_URL, item.streamUrl)
                putString(EXTRA_RESOURCE_TITLE, item.title)
                putString(EXTRA_PAGE_TITLE, item.pageTitle)
                putLong(EXTRA_DURATION_MS, item.durationMs.coerceAtLeast(0L))
            }
            val restored = MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(localFile?.let(Uri::fromFile) ?: Uri.parse(item.streamUrl))
                .setMimeType(localFile?.let { null } ?: MimeTypes.AUDIO_MP4)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.pageTitle?.takeIf(String::isNotBlank) ?: item.title)
                        .setAlbumTitle(item.title)
                        .setArtist(item.artist)
                        .setArtworkUri(item.artworkUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
                        .setExtras(extras)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build(),
                )
                .build()
            // long: 服务被系统回收后恢复最近一条历史并停在上次位置，等待车机或页面发出播放指令，不在后台静默抢占音频焦点。
            suppressNextTransitionRecord = true
            activePlayer.setMediaItem(restored, item.lastPositionMs.coerceAtLeast(0L))
            activePlayer.prepare()
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 5_000L
    }

    private val durationByMediaId = mutableMapOf<String, Long>()
}

private fun Long?.orZero(): Long = this ?: 0L
