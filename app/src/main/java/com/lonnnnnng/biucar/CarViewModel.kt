package com.lonnnnnng.biucar

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lonnnnnng.biucar.data.local.AudioCacheState
import com.lonnnnnng.biucar.data.local.LikedMediaEntity
import com.lonnnnnng.biucar.data.local.PlaybackHistoryEntity
import com.lonnnnnng.biucar.data.model.Account
import com.lonnnnnng.biucar.data.model.Creator
import com.lonnnnnng.biucar.data.model.EXTRA_BVID
import com.lonnnnnng.biucar.data.model.EXTRA_CID
import com.lonnnnnng.biucar.data.model.EXTRA_PAGE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_RESOURCE_TITLE
import com.lonnnnnng.biucar.data.model.EXTRA_STREAM_URL
import com.lonnnnnng.biucar.data.model.FavoriteFolder
import com.lonnnnnng.biucar.data.model.FavoriteGroup
import com.lonnnnnng.biucar.data.model.HistoryCursor
import com.lonnnnnng.biucar.data.model.QrPollResult
import com.lonnnnnng.biucar.data.model.Video
import com.lonnnnnng.biucar.playback.CarPlaybackService
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RootPage { HOME, LIBRARY, PLAYER }
enum class LibrarySection { CREATED, COLLECTED, HISTORY, LIKED, SOURCES, ACCOUNT }
enum class CreatorSourceTab { FOLLOWING, SEARCH }
enum class HistoryMode { ONLINE, LOCAL }

data class PlaybackQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
)

data class CarUiState(
    val rootPage: RootPage = RootPage.HOME,
    val librarySection: LibrarySection = LibrarySection.CREATED,
    val historyMode: HistoryMode = HistoryMode.LOCAL,
    val account: Account = Account(),
    val accountLoading: Boolean = true,
    val qrUrl: String? = null,
    val qrStatus: String = "",
    val loginBusy: Boolean = false,
    val selectedCreators: List<Creator> = emptyList(),
    val selectedHomeMid: Long? = null,
    val homeVideos: Map<Long, List<Video>> = emptyMap(),
    val homePages: Map<Long, Int> = emptyMap(),
    val homeHasMore: Map<Long, Boolean> = emptyMap(),
    val homeLoading: Boolean = false,
    val availableCreators: List<Creator> = emptyList(),
    val creatorSourceTab: CreatorSourceTab = CreatorSourceTab.FOLLOWING,
    val creatorSearchKeyword: String = "",
    val creatorSearchResults: List<Creator> = emptyList(),
    val creatorSearchLoading: Boolean = false,
    val draftCreators: List<Creator> = emptyList(),
    val sourcesLoading: Boolean = false,
    val favoriteFolders: Map<FavoriteGroup, List<FavoriteFolder>> = emptyMap(),
    val selectedFavoriteFolder: FavoriteFolder? = null,
    val favoriteVideos: List<Video> = emptyList(),
    val favoritePage: Int = 0,
    val favoriteHasMore: Boolean = false,
    val favoriteLoading: Boolean = false,
    val onlineHistory: List<Video> = emptyList(),
    val historyCursor: HistoryCursor? = null,
    val onlineHistoryLoading: Boolean = false,
    val localHistory: List<PlaybackHistoryEntity> = emptyList(),
    val likedItems: List<LikedMediaEntity> = emptyList(),
    val controllerReady: Boolean = false,
    val nowTitle: String = "尚未播放",
    val nowArtist: String = "",
    val nowArtworkUrl: String = "",
    val isMultiPage: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackQueue: List<PlaybackQueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val liked: Boolean = false,
    val resolvingMedia: Boolean = false,
    val message: String? = null,
) {
    val draftCreatorMids: Set<Long>
        get() = draftCreators.mapTo(mutableSetOf(), Creator::mid)
}

class CarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.carContainer
    private val _uiState = MutableStateFlow(CarUiState())
    val uiState: StateFlow<CarUiState> = _uiState.asStateFlow()
    private var loginJob: Job? = null
    private var progressJob: Job? = null
    private var mediaResolveJob: Job? = null
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, CarPlaybackService::class.java)),
    ).buildAsync()

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncPlayerState()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = syncPlayerState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncPlayerState()
        override fun onPlaybackStateChanged(playbackState: Int) = syncPlayerState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncPlayerState()
        override fun onPlayerError(error: PlaybackException) = showError("播放失败", error)
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    _uiState.update { it.copy(controllerReady = true) }
                    syncPlayerState()
                    startProgressTicker()
                }.onFailure { error -> showError("播放器连接失败", error) }
            },
            ContextCompat.getMainExecutor(application),
        )
        viewModelScope.launch {
            container.creatorSelectionRepository.selected.collect { selected ->
                val currentMid = _uiState.value.selectedHomeMid?.takeIf { mid -> selected.any { it.mid == mid } }
                    ?: selected.firstOrNull()?.mid
                _uiState.update {
                    it.copy(
                        selectedCreators = selected,
                        selectedHomeMid = currentMid,
                        draftCreators = if (it.availableCreators.isEmpty() && it.draftCreators.isEmpty()) selected else it.draftCreators,
                    )
                }
                currentMid?.let { mid -> if (_uiState.value.homeVideos[mid] == null) loadHome(mid, reset = true) }
            }
        }
        viewModelScope.launch {
            container.playbackHistoryRepository.recent.collect { items ->
                _uiState.update { it.copy(localHistory = items) }
            }
        }
        viewModelScope.launch {
            container.likedMediaRepository.liked.collect { items ->
                val currentMediaId = controller?.currentMediaItem?.mediaId
                _uiState.update {
                    it.copy(
                        likedItems = items,
                        liked = currentMediaId != null && items.any { item -> item.mediaId == currentMediaId },
                    )
                }
            }
        }
        refreshAccount()
    }

    fun selectRoot(page: RootPage) {
        _uiState.update { it.copy(rootPage = page) }
    }

    fun selectLibrary(section: LibrarySection) {
        _uiState.update { it.copy(librarySection = section) }
        when (section) {
            LibrarySection.CREATED -> loadFavoriteFolders(FavoriteGroup.CREATED)
            LibrarySection.COLLECTED -> loadFavoriteFolders(FavoriteGroup.COLLECTED)
            LibrarySection.HISTORY -> if (_uiState.value.account.isLoggedIn && _uiState.value.onlineHistory.isEmpty()) loadOnlineHistory(true)
            LibrarySection.LIKED -> Unit
            LibrarySection.SOURCES -> if (_uiState.value.availableCreators.isEmpty()) loadAvailableCreators()
            LibrarySection.ACCOUNT -> Unit
        }
    }

    fun selectHistoryMode(mode: HistoryMode) {
        _uiState.update { it.copy(historyMode = mode) }
        if (mode == HistoryMode.ONLINE && _uiState.value.onlineHistory.isEmpty()) loadOnlineHistory(true)
    }

    fun refreshAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(accountLoading = true) }
            runCatching { container.bilibiliRepository.account() }
                .onSuccess { account ->
                    _uiState.update { it.copy(account = account, accountLoading = false) }
                    // long: 媒体库默认停留在“我创建的”，账号态确认后立即预加载收藏夹，避免首屏空白必须切换标签才出现数据。
                    if (account.isLoggedIn && _uiState.value.librarySection == LibrarySection.CREATED) {
                        loadFavoriteFolders(FavoriteGroup.CREATED)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(account = Account(), accountLoading = false) }
                    showError("账号状态读取失败", error)
                }
        }
    }

    fun startQrLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _uiState.update { it.copy(loginBusy = true, qrUrl = null, qrStatus = "正在申请二维码", message = null) }
            val challenge = runCatching { container.loginApi.createChallenge() }.getOrElse { error ->
                _uiState.update { it.copy(loginBusy = false, qrStatus = "二维码申请失败") }
                showError("二维码申请失败", error)
                return@launch
            }
            _uiState.update { it.copy(qrUrl = challenge.url, qrStatus = "请使用哔哩哔哩 App 扫码并确认") }
            while (isActive) {
                delay(QR_POLL_INTERVAL_MS)
                when (val result = runCatching { container.loginApi.poll(challenge.authCode) }
                    .getOrElse { QrPollResult.Error(it.message ?: "轮询失败") }) {
                    QrPollResult.Waiting -> _uiState.update { it.copy(qrStatus = "等待扫码") }
                    QrPollResult.Scanned -> _uiState.update { it.copy(qrStatus = "已扫码，请在手机上确认") }
                    QrPollResult.Expired -> {
                        _uiState.update { it.copy(loginBusy = false, qrStatus = "二维码已过期，请刷新") }
                        return@launch
                    }
                    is QrPollResult.Error -> {
                        _uiState.update { it.copy(loginBusy = false, qrStatus = result.message) }
                        return@launch
                    }
                    is QrPollResult.Success -> {
                        container.credentialStore.write(result.credentials)
                        val account = runCatching { container.bilibiliRepository.account() }.getOrElse { error ->
                            container.credentialStore.clear()
                            showError("登录确认失败", error)
                            _uiState.update { it.copy(loginBusy = false, qrStatus = "登录确认失败") }
                            return@launch
                        }
                        if (!account.isLoggedIn) {
                            container.credentialStore.clear()
                            _uiState.update { it.copy(loginBusy = false, qrStatus = "服务端未确认登录，请重试") }
                            return@launch
                        }
                        _uiState.update {
                            it.copy(account = account, accountLoading = false, loginBusy = false, qrUrl = null, qrStatus = "登录成功")
                        }
                        if (_uiState.value.librarySection == LibrarySection.CREATED) {
                            loadFavoriteFolders(FavoriteGroup.CREATED)
                        }
                        return@launch
                    }
                }
            }
        }
    }

    fun logout() {
        loginJob?.cancel()
        container.credentialStore.clear()
        viewModelScope.launch { container.creatorSelectionRepository.replaceAll(emptyList()) }
        _uiState.update {
            CarUiState(
                rootPage = it.rootPage,
                librarySection = LibrarySection.ACCOUNT,
                controllerReady = it.controllerReady,
                localHistory = it.localHistory,
                likedItems = it.likedItems,
                nowTitle = it.nowTitle,
                nowArtist = it.nowArtist,
                isPlaying = it.isPlaying,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
            )
        }
    }

    fun selectHomeCreator(mid: Long) {
        _uiState.update { it.copy(selectedHomeMid = mid) }
        if (_uiState.value.homeVideos[mid] == null) loadHome(mid, true)
    }

    fun loadHome(mid: Long? = null, reset: Boolean = false) {
        if (_uiState.value.homeLoading) return
        val targetMid = mid ?: _uiState.value.selectedHomeMid ?: return
        val creator = _uiState.value.selectedCreators.firstOrNull { it.mid == targetMid } ?: return
        val page = if (reset) 1 else (_uiState.value.homePages[targetMid] ?: 0) + 1
        viewModelScope.launch {
            _uiState.update { it.copy(homeLoading = true) }
            runCatching { container.bilibiliRepository.creatorVideos(creator, page) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        val previous = if (reset) emptyList() else state.homeVideos[targetMid].orEmpty()
                        state.copy(
                            homeVideos = state.homeVideos + (targetMid to (previous + result.items).distinctBy(Video::bvid)),
                            homePages = state.homePages + (targetMid to result.page),
                            homeHasMore = state.homeHasMore + (targetMid to result.hasMore),
                            homeLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(homeLoading = false) }
                    showError("首页加载失败", error)
                }
        }
    }

    fun loadAvailableCreators() {
        val account = _uiState.value.account
        if (!account.isLoggedIn || account.mid <= 0L || _uiState.value.sourcesLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(sourcesLoading = true) }
            runCatching { container.bilibiliRepository.followingCreators(account.mid) }
                .onSuccess { creators ->
                    _uiState.update {
                        it.copy(
                            availableCreators = creators,
                            sourcesLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(sourcesLoading = false) }
                    showError("关注列表加载失败", error)
                }
        }
    }

    fun updateCreatorSearchKeyword(keyword: String) {
        _uiState.update { it.copy(creatorSearchKeyword = keyword) }
    }

    fun selectCreatorSourceTab(tab: CreatorSourceTab) {
        _uiState.update { it.copy(creatorSourceTab = tab) }
        if (tab == CreatorSourceTab.FOLLOWING && _uiState.value.availableCreators.isEmpty()) loadAvailableCreators()
    }

    fun searchCreators() {
        val keyword = _uiState.value.creatorSearchKeyword.trim()
        if (keyword.isBlank() || _uiState.value.creatorSearchLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(creatorSearchLoading = true, creatorSearchResults = emptyList()) }
            runCatching { container.bilibiliRepository.searchCreators(keyword) }
                .onSuccess { result ->
                    _uiState.update { it.copy(creatorSearchResults = result.items, creatorSearchLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(creatorSearchLoading = false) }
                    showError("UP 主搜索失败", error)
                }
        }
    }

    fun toggleCreator(creator: Creator) {
        _uiState.update { state ->
            val updated = state.draftCreators.toMutableList()
            val index = updated.indexOfFirst { it.mid == creator.mid }
            if (index >= 0) updated.removeAt(index) else updated += creator
            state.copy(draftCreators = updated)
        }
    }

    fun saveCreatorSelection() {
        val state = _uiState.value
        val selected = state.draftCreators
        viewModelScope.launch {
            container.creatorSelectionRepository.replaceAll(selected)
            _uiState.update { it.copy(message = "首页来源已保存") }
        }
    }

    fun loadFavoriteFolders(group: FavoriteGroup) {
        val state = _uiState.value
        if (!state.account.isLoggedIn || state.account.mid <= 0L || state.favoriteLoading) return
        if (state.favoriteFolders[group] != null) {
            selectFavoriteFolder(state.favoriteFolders[group]?.firstOrNull())
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(favoriteLoading = true) }
            runCatching { container.bilibiliRepository.favoriteFolders(state.account.mid, group) }
                .onSuccess { folders ->
                    _uiState.update { it.copy(favoriteFolders = it.favoriteFolders + (group to folders), favoriteLoading = false) }
                    selectFavoriteFolder(folders.firstOrNull())
                }
                .onFailure { error ->
                    _uiState.update { it.copy(favoriteLoading = false) }
                    showError("收藏夹加载失败", error)
                }
        }
    }

    fun selectFavoriteFolder(folder: FavoriteFolder?) {
        _uiState.update {
            it.copy(selectedFavoriteFolder = folder, favoriteVideos = emptyList(), favoritePage = 0, favoriteHasMore = false)
        }
        folder?.let { loadFavoriteVideos(reset = true) }
    }

    fun loadFavoriteVideos(reset: Boolean = false) {
        val folder = _uiState.value.selectedFavoriteFolder ?: return
        if (_uiState.value.favoriteLoading) return
        val page = if (reset) 1 else _uiState.value.favoritePage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(favoriteLoading = true) }
            runCatching { container.bilibiliRepository.favoriteVideos(folder, page) }
                .onSuccess { result ->
                    _uiState.update {
                        val previous = if (reset) emptyList() else it.favoriteVideos
                        it.copy(
                            favoriteVideos = (previous + result.items).distinctBy(Video::bvid),
                            favoritePage = result.page,
                            favoriteHasMore = result.hasMore,
                            favoriteLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(favoriteLoading = false) }
                    showError("收藏内容加载失败", error)
                }
        }
    }

    fun loadOnlineHistory(reset: Boolean = false) {
        if (!_uiState.value.account.isLoggedIn || _uiState.value.onlineHistoryLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(onlineHistoryLoading = true) }
            runCatching { container.bilibiliRepository.onlineHistory(if (reset) null else _uiState.value.historyCursor) }
                .onSuccess { result ->
                    _uiState.update {
                        val previous = if (reset) emptyList() else it.onlineHistory
                        it.copy(
                            onlineHistory = (previous + result.items).distinctBy { video -> "${video.bvid}:${video.cid}" },
                            historyCursor = result.nextCursor,
                            onlineHistoryLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(onlineHistoryLoading = false) }
                    showError("在线历史加载失败", error)
                }
        }
    }

    fun playVideo(video: Video, pageIndex: Int = 0) {
        mediaResolveJob?.cancel()
        mediaResolveJob = viewModelScope.launch {
            _uiState.update { it.copy(resolvingMedia = true) }
            var queueStarted = false
            runCatching {
                container.bilibiliRepository.resolveAudioTracks(video, pageIndex).collect { track ->
                    if (!queueStarted) {
                        // long: 首 P 到达后立即替换旧队列并开播；后续分 P 渐进追加，兼顾老车机首播速度与自动连续播放。
                        playMediaItem(track.toMediaItem(), 0L)
                        queueStarted = true
                        _uiState.update { it.copy(rootPage = RootPage.PLAYER, resolvingMedia = false) }
                    } else {
                        appendMediaItem(track.toMediaItem())
                    }
                }
            }
                .onFailure { error ->
                    _uiState.update { it.copy(resolvingMedia = false) }
                    showError(if (queueStarted) "后续分 P 解析失败" else "音频解析失败", error)
                }
        }
    }

    fun playHistory(item: PlaybackHistoryEntity) {
        // long: 本地历史会建立独立的单项离线队列，先取消旧视频尚未完成的分 P 解析，避免网络结果稍后串入本地队列。
        mediaResolveJob?.cancel()
        _uiState.update { it.copy(resolvingMedia = false) }
        val localFile = item.localFilePath?.let(::File)?.takeIf(File::isFile)
        if (item.cacheState == AudioCacheState.READY.name && localFile != null) {
            val extras = Bundle().apply {
                putString(EXTRA_BVID, item.bvid)
                putLong(EXTRA_CID, item.cid)
                putString(EXTRA_STREAM_URL, item.streamUrl)
                putString(EXTRA_RESOURCE_TITLE, item.title)
                putString(EXTRA_PAGE_TITLE, item.pageTitle)
            }
            val mediaItem = MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(Uri.fromFile(localFile))
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
            playMediaItem(mediaItem, item.lastPositionMs)
            _uiState.update { it.copy(rootPage = RootPage.PLAYER) }
        } else {
            playVideo(Video(item.bvid, cid = item.cid, title = item.title, author = item.artist))
        }
    }

    fun playLiked(item: LikedMediaEntity) {
        viewModelScope.launch {
            val history = container.playbackHistoryRepository.find(item.mediaId)
            if (history != null) {
                playHistory(history)
            } else {
                playVideo(
                    Video(
                        bvid = item.bvid,
                        cid = item.cid,
                        title = item.title,
                        author = item.artist,
                        coverUrl = item.artworkUrl,
                    ),
                )
            }
        }
    }

    fun togglePlayback() {
        val active = controller ?: return
        if (active.isPlaying) active.pause() else active.play()
    }

    fun playPrevious() {
        controller?.let { active ->
            if (active.hasPreviousMediaItem()) active.seekToPreviousMediaItem() else active.seekTo(0L)
            syncPlayerState()
        }
    }

    fun playNext() {
        controller?.let { active ->
            if (active.hasNextMediaItem()) active.seekToNextMediaItem() else active.seekTo(0L)
            syncPlayerState()
        }
    }

    fun selectQueueItem(index: Int) {
        controller?.let { active ->
            if (index !in 0 until active.mediaItemCount) return@let
            // long: 直接定位 Media3 队列项，保持通知栏、锁屏标题和 Room 当前 cid 同步更新。
            active.seekToDefaultPosition(index)
            active.play()
            syncPlayerState()
        }
    }

    fun cycleRepeatMode() {
        val active = controller ?: return
        val next = when (active.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        active.repeatMode = next
        syncPlayerState()
    }

    fun cyclePlaybackOrder() {
        val active = controller ?: return
        when {
            active.repeatMode == Player.REPEAT_MODE_OFF && !active.shuffleModeEnabled -> {
                active.repeatMode = Player.REPEAT_MODE_ONE
            }
            active.repeatMode == Player.REPEAT_MODE_ONE -> {
                active.repeatMode = Player.REPEAT_MODE_ALL
            }
            active.repeatMode == Player.REPEAT_MODE_ALL && !active.shuffleModeEnabled -> {
                active.shuffleModeEnabled = true
            }
            else -> {
                active.repeatMode = Player.REPEAT_MODE_OFF
                active.shuffleModeEnabled = false
            }
        }
        syncPlayerState()
    }

    fun toggleLiked() {
        val current = controller?.currentMediaItem ?: return
        val metadata = current.mediaMetadata
        val extras = metadata.extras
        val bvid = extras?.getString(EXTRA_BVID).orEmpty()
        val cid = extras?.getLong(EXTRA_CID, 0L) ?: 0L
        if (bvid.isBlank() || cid <= 0L) {
            _uiState.update { it.copy(message = "当前内容缺少收藏标识") }
            return
        }
        val item = LikedMediaEntity(
            mediaId = current.mediaId,
            bvid = bvid,
            cid = cid,
            title = extras?.getString(EXTRA_RESOURCE_TITLE).orEmpty()
                .ifBlank { metadata.albumTitle?.toString().orEmpty() }
                .ifBlank { metadata.title?.toString().orEmpty() },
            pageTitle = extras?.getString(EXTRA_PAGE_TITLE),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString().orEmpty(),
            likedAtEpochMs = 0L,
        )
        val isLiked = _uiState.value.likedItems.any { it.mediaId == current.mediaId }
        viewModelScope.launch {
            if (isLiked) {
                container.likedMediaRepository.remove(current.mediaId)
            } else {
                container.likedMediaRepository.add(item)
            }
        }
    }

    fun toggleShuffle() {
        val active = controller ?: return
        active.shuffleModeEnabled = !active.shuffleModeEnabled
        syncPlayerState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        syncPlayerState()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun playMediaItem(mediaItem: MediaItem, positionMs: Long) {
        val active = controller
        if (active == null) {
            _uiState.update { it.copy(message = "播放器正在初始化") }
            return
        }
        active.setMediaItem(mediaItem, positionMs.coerceAtLeast(0L))
        active.prepare()
        active.play()
        syncPlayerState()
    }

    private fun appendMediaItem(mediaItem: MediaItem) {
        val active = controller ?: return
        val currentItemEnded = active.playbackState == Player.STATE_ENDED
        active.addMediaItem(mediaItem)
        if (currentItemEnded && active.hasNextMediaItem()) {
            // long: 极短分 P 可能在下一项网络解析完成前结束；追加后主动进入下一项，保证慢网络下仍满足连续播放语义。
            active.seekToNextMediaItem()
            active.prepare()
            active.play()
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                syncPlayerState()
                delay(1_000L)
            }
        }
    }

    private fun syncPlayerState() {
        val active = controller ?: return
        val metadata = active.mediaMetadata
        val currentMediaId = active.currentMediaItem?.mediaId
        _uiState.update { state ->
            state.copy(
                nowTitle = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: "尚未播放",
                nowArtist = metadata.artist?.toString().orEmpty(),
                nowArtworkUrl = metadata.artworkUri?.toString().orEmpty(),
                isMultiPage = metadata.extras?.getString(EXTRA_PAGE_TITLE).orEmpty().isNotBlank(),
                liked = currentMediaId != null && state.likedItems.any { it.mediaId == currentMediaId },
                isPlaying = active.isPlaying,
                positionMs = active.currentPosition.coerceAtLeast(0L),
                durationMs = active.duration.takeIf { duration -> duration != C.TIME_UNSET && duration > 0L } ?: 0L,
                playbackQueue = (0 until active.mediaItemCount).map { index ->
                    val item = active.getMediaItemAt(index)
                    PlaybackQueueItem(
                        mediaId = item.mediaId,
                        title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "未命名分 P" },
                        artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    )
                },
                currentQueueIndex = active.currentMediaItemIndex,
                repeatMode = active.repeatMode,
                shuffleEnabled = active.shuffleModeEnabled,
            )
        }
    }

    private fun showError(prefix: String, error: Throwable) {
        _uiState.update { it.copy(message = "$prefix：${error.message ?: "未知错误"}") }
    }

    override fun onCleared() {
        loginJob?.cancel()
        progressJob?.cancel()
        mediaResolveJob?.cancel()
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }

    private companion object {
        const val QR_POLL_INTERVAL_MS = 2_000L
    }
}
