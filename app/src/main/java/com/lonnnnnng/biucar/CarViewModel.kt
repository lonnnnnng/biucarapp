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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lonnnnnng.biucar.data.local.AudioCacheState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RootPage { HOME, LIBRARY, PLAYER }
enum class LibrarySection { CREATED, COLLECTED, HISTORY, SOURCES, ACCOUNT }
enum class HistoryMode { ONLINE, LOCAL }

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
    val draftCreatorMids: Set<Long> = emptySet(),
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
    val controllerReady: Boolean = false,
    val nowTitle: String = "尚未播放",
    val nowArtist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val resolvingMedia: Boolean = false,
    val message: String? = null,
)

class CarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.carContainer
    private val _uiState = MutableStateFlow(CarUiState())
    val uiState: StateFlow<CarUiState> = _uiState.asStateFlow()
    private var loginJob: Job? = null
    private var progressJob: Job? = null
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, CarPlaybackService::class.java)),
    ).buildAsync()

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncPlayerState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncPlayerState()
        override fun onPlaybackStateChanged(playbackState: Int) = syncPlayerState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncPlayerState()
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
                        draftCreatorMids = if (it.availableCreators.isEmpty()) selected.map(Creator::mid).toSet() else it.draftCreatorMids,
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
                .onSuccess { account -> _uiState.update { it.copy(account = account, accountLoading = false) } }
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
                            draftCreatorMids = it.selectedCreators.map(Creator::mid).toSet(),
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

    fun toggleCreator(mid: Long) {
        _uiState.update { state ->
            val updated = state.draftCreatorMids.toMutableSet().apply { if (!add(mid)) remove(mid) }
            state.copy(draftCreatorMids = updated)
        }
    }

    fun saveCreatorSelection() {
        val state = _uiState.value
        val selected = state.availableCreators.filter { it.mid in state.draftCreatorMids }
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
        if (_uiState.value.resolvingMedia) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingMedia = true) }
            runCatching { container.bilibiliRepository.resolveAudioTrack(video, pageIndex) }
                .onSuccess { track ->
                    playMediaItem(track.toMediaItem(), 0L)
                    _uiState.update { it.copy(rootPage = RootPage.PLAYER, resolvingMedia = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(resolvingMedia = false) }
                    showError("音频解析失败", error)
                }
        }
    }

    fun playHistory(item: PlaybackHistoryEntity) {
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

    fun togglePlayback() {
        val active = controller ?: return
        if (active.isPlaying) active.pause() else active.play()
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
        _uiState.update {
            it.copy(
                nowTitle = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: "尚未播放",
                nowArtist = metadata.artist?.toString().orEmpty(),
                isPlaying = active.isPlaying,
                positionMs = active.currentPosition.coerceAtLeast(0L),
                durationMs = active.duration.takeIf { duration -> duration != C.TIME_UNSET && duration > 0L } ?: 0L,
            )
        }
    }

    private fun showError(prefix: String, error: Throwable) {
        _uiState.update { it.copy(message = "$prefix：${error.message ?: "未知错误"}") }
    }

    override fun onCleared() {
        loginJob?.cancel()
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }

    private companion object {
        const val QR_POLL_INTERVAL_MS = 2_000L
    }
}
