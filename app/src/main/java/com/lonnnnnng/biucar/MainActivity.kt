package com.lonnnnnng.biucar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonnnnnng.biucar.data.local.AudioCacheState
import com.lonnnnnng.biucar.data.local.LikedMediaEntity
import com.lonnnnnng.biucar.data.local.PlaybackHistoryEntity
import com.lonnnnnng.biucar.data.model.Creator
import com.lonnnnnng.biucar.data.model.FavoriteFolder
import com.lonnnnnng.biucar.data.model.FavoriteGroup
import com.lonnnnnng.biucar.data.model.Video
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private val CarBackground = Color(0xFF0B0F0D)
private val CarSurface = Color(0xFF131A17)
private val CarSurfaceRaised = Color(0xFF1A241F)
private val CarGreen = Color(0xFF53E491)
private val CarGreenSoft = Color(0xFF173A29)
private val CarText = Color(0xFFF1F6F3)
private val CarMuted = Color(0xFF98A69E)
private val CarDivider = Color(0xFF26332D)
private val CarDanger = Color(0xFFE66A6A)

class MainActivity : ComponentActivity() {
    private val viewModel: CarViewModel by viewModels()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // long: Android 8.1 车机不会可靠继承 Compose 的系统栏图标颜色，Activity 启动时显式固定为深色背景和浅色图标。
        window.statusBarColor = android.graphics.Color.rgb(11, 15, 13)
        window.navigationBarColor = android.graphics.Color.rgb(11, 15, 13)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CarGreen,
                    onPrimary = Color(0xFF06140C),
                    background = CarBackground,
                    onBackground = CarText,
                    surface = CarSurface,
                    onSurface = CarText,
                ),
            ) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CarApp(state, viewModel)
            }
        }
    }
}

@Composable
private fun CarApp(state: CarUiState, viewModel: CarViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        containerColor = CarBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            CarNavigation(state.rootPage, viewModel::selectRoot)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.rootPage) {
                        RootPage.HOME -> HomeScreen(state, viewModel)
                        RootPage.LIBRARY -> LibraryScreen(state, viewModel)
                        RootPage.PLAYER -> PlayerScreen(state, viewModel)
                    }
                    if (state.resolvingMedia) {
                        Surface(
                            color = CarSurfaceRaised,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("正在加载音频", color = CarText, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (state.rootPage != RootPage.PLAYER) MiniPlayer(state, viewModel)
            }
        }
    }
}

@Composable
private fun CarNavigation(selected: RootPage, onSelect: (RootPage) -> Unit) {
    Column(
        modifier = Modifier
            .width(126.dp)
            .fillMaxHeight()
            .background(CarSurface)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = CarGreenSoft,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CarGreen.copy(alpha = 0.42f)),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("BIU CAR", color = CarGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        NavigationItem("首页", Icons.Rounded.Home, selected == RootPage.HOME) { onSelect(RootPage.HOME) }
        Spacer(Modifier.height(8.dp))
        NavigationItem("媒体库", Icons.Rounded.LibraryMusic, selected == RootPage.LIBRARY) { onSelect(RootPage.LIBRARY) }
        Spacer(Modifier.height(8.dp))
        NavigationItem("播放", Icons.Rounded.Equalizer, selected == RootPage.PLAYER) { onSelect(RootPage.PLAYER) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NavigationItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) CarGreen else CarMuted
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CarGreenSoft else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        if (selected) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(CarGreen).align(Alignment.CenterStart))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = color, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

@Composable
private fun HomeScreen(state: CarUiState, viewModel: CarViewModel) {
    val homeLoading = state.homeLoadingMid == state.selectedHomeMid
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("首页", color = CarText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.loadHome(reset = true) },
                enabled = state.selectedHomeMid != null && !homeLoading,
                modifier = Modifier.size(48.dp),
            ) {
                if (homeLoading) {
                    CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新首页", tint = CarMuted, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (state.selectedCreators.isEmpty()) {
            EmptyState(
                title = "首页暂未配置内容",
                detail = "前往媒体库选择关注的 UP 主，每位 UP 会生成一个 Tab。",
                action = "配置首页来源",
            ) {
                viewModel.selectRoot(RootPage.LIBRARY)
                viewModel.selectLibrary(LibrarySection.SOURCES)
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selectedCreators.forEach { creator ->
                    CompactTab(creator.name, state.selectedHomeMid == creator.mid) { viewModel.selectHomeCreator(creator.mid) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("${state.selectedCreators.size} 位 UP", color = CarMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        val videos = state.homeVideos[state.selectedHomeMid].orEmpty()
        VideoList(
            listIdentity = "home:${state.selectedHomeMid}",
            videos = videos,
            loading = homeLoading,
            hasMore = state.homeHasMore[state.selectedHomeMid] == true,
            onPlay = viewModel::playVideo,
            onLoadMore = { viewModel.loadHome() },
            emptyText = "该 UP 暂无可展示投稿",
        )
    }
}

@Composable
private fun LibraryScreen(state: CarUiState, viewModel: CarViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("媒体库", color = CarText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryTab("我创建的", LibrarySection.CREATED, state.librarySection, viewModel)
                LibraryTab("我收藏的", LibrarySection.COLLECTED, state.librarySection, viewModel)
                LibraryTab("播放历史", LibrarySection.HISTORY, state.librarySection, viewModel)
                LibraryTab("我喜欢的", LibrarySection.LIKED, state.librarySection, viewModel)
                LibraryTab("首页 UP", LibrarySection.SOURCES, state.librarySection, viewModel)
                LibraryTab("账号", LibrarySection.ACCOUNT, state.librarySection, viewModel)
            }
            Spacer(Modifier.weight(1f))
            val refreshGroup = when (state.librarySection) {
                LibrarySection.CREATED -> FavoriteGroup.CREATED
                LibrarySection.COLLECTED -> FavoriteGroup.COLLECTED
                else -> null
            }
            if (refreshGroup != null) {
                IconButton(
                    onClick = { viewModel.refreshFavoriteFolders(refreshGroup) },
                    enabled = !state.favoriteLoading,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.favoriteLoading) {
                        CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新收藏夹", tint = CarMuted, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CarDivider)
        Spacer(Modifier.height(8.dp))
        when (state.librarySection) {
            LibrarySection.CREATED -> FavoriteScreen(state, FavoriteGroup.CREATED, viewModel)
            LibrarySection.COLLECTED -> FavoriteScreen(state, FavoriteGroup.COLLECTED, viewModel)
            LibrarySection.HISTORY -> HistoryScreen(state, viewModel)
            LibrarySection.LIKED -> LikedScreen(state, viewModel)
            LibrarySection.SOURCES -> SourcesScreen(state, viewModel)
            LibrarySection.ACCOUNT -> AccountScreen(state, viewModel)
        }
    }
}

@Composable
private fun LibraryTab(label: String, section: LibrarySection, selected: LibrarySection, viewModel: CarViewModel) {
    CompactTab(label, section == selected) { viewModel.selectLibrary(section) }
}

@Composable
private fun FavoriteScreen(state: CarUiState, group: FavoriteGroup, viewModel: CarViewModel) {
    if (!state.account.isLoggedIn) {
        LoginRequired { viewModel.selectLibrary(LibrarySection.ACCOUNT) }
        return
    }
    val folders = state.favoriteFolders[group].orEmpty()
    val selectedFolder = state.selectedFavoriteFolder?.takeIf { it.group == group }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = CarSurface,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, CarDivider),
            modifier = Modifier.width(250.dp).fillMaxHeight(),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("收藏夹", color = CarText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("${folders.size} 个", color = CarMuted, fontSize = 11.sp)
                }
                HorizontalDivider(color = CarDivider)
                when {
                    state.favoriteLoading && folders.isEmpty() -> LoadingStatePanel("正在加载收藏夹", Modifier.weight(1f))
                    state.favoriteFolderErrors[group] != null && folders.isEmpty() -> ListStatePanel(
                        icon = Icons.Rounded.Refresh,
                        title = "收藏夹加载失败",
                        detail = state.favoriteFolderErrors[group],
                        action = "重试",
                        modifier = Modifier.weight(1f),
                        onAction = { viewModel.loadFavoriteFolders(group, forceRefresh = true) },
                    )
                    folders.isEmpty() -> ListStatePanel(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "暂无收藏夹",
                        detail = "可在 Bilibili 创建或收藏内容后刷新",
                        modifier = Modifier.weight(1f),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(folders, key = { "${it.group}:${it.id}" }) { folder ->
                            FolderRow(folder, selectedFolder?.id == folder.id) { viewModel.selectFavoriteFolder(folder) }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedFolder?.title ?: "收藏内容",
                    color = CarText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                selectedFolder?.let { Text("${it.mediaCount} 项", color = CarMuted, fontSize = 11.sp) }
            }
            HorizontalDivider(color = CarDivider)
            VideoList(
                modifier = Modifier.weight(1f),
                listIdentity = "favorite:${group}:${selectedFolder?.id}",
                videos = state.favoriteVideos,
                loading = state.favoriteLoading,
                hasMore = state.favoriteHasMore,
                onPlay = viewModel::playVideo,
                onLoadMore = viewModel::loadFavoriteVideos,
                emptyText = if (selectedFolder == null) "请选择左侧收藏夹" else "此收藏夹暂无内容",
                errorText = state.favoriteVideoError,
                onRetry = { viewModel.loadFavoriteVideos(reset = true) },
            )
        }
    }
}

@Composable
private fun FolderRow(folder: FavoriteFolder, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) CarSurfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        if (selected) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(CarGreen).align(Alignment.CenterStart))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = if (selected) CarGreen else CarMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.title, color = if (selected) CarGreen else CarText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folder.mediaCount} 项", color = CarMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: CarUiState, viewModel: CarViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTab("本地历史", state.historyMode == HistoryMode.LOCAL) { viewModel.selectHistoryMode(HistoryMode.LOCAL) }
            CompactTab("B站历史", state.historyMode == HistoryMode.ONLINE) { viewModel.selectHistoryMode(HistoryMode.ONLINE) }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.historyMode == HistoryMode.LOCAL) {
                    val cachedCount = state.localHistory.count { it.cacheState == AudioCacheState.READY.name }
                    "${state.localHistory.size} 条 · $cachedCount 条离线可用"
                } else {
                    "已加载 ${state.onlineHistory.size} 条"
                },
                color = CarMuted,
                fontSize = 11.sp,
            )
            if (state.historyMode == HistoryMode.ONLINE) {
                CompactIconAction(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "刷新在线历史",
                    loading = state.onlineHistoryLoading,
                    onClick = { viewModel.loadOnlineHistory(true) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.historyMode == HistoryMode.LOCAL) {
            if (state.localHistory.isEmpty()) {
                ListStatePanel(
                    icon = Icons.Rounded.History,
                    title = "暂无本地播放历史",
                    detail = "播放过的音频会保留在这里，缓存完成后可离线播放",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.localHistory, key = PlaybackHistoryEntity::mediaId) { item ->
                        HistoryRow(item) { viewModel.playHistory(item) }
                        HorizontalDivider(color = CarDivider)
                    }
                }
            }
        } else if (!state.account.isLoggedIn) {
            LoginRequired { viewModel.selectLibrary(LibrarySection.ACCOUNT) }
        } else {
            VideoList(
                listIdentity = "online-history",
                videos = state.onlineHistory,
                loading = state.onlineHistoryLoading,
                hasMore = state.historyCursor != null,
                onPlay = viewModel::playVideo,
                onLoadMore = { viewModel.loadOnlineHistory(false) },
                emptyText = "暂无在线历史",
                errorText = state.onlineHistoryError,
                onRetry = { viewModel.loadOnlineHistory(true) },
            )
        }
    }
}

@Composable
private fun SourcesScreen(state: CarUiState, viewModel: CarViewModel) {
    if (!state.account.isLoggedIn) {
        LoginRequired { viewModel.selectLibrary(LibrarySection.ACCOUNT) }
        return
    }
    val focusManager = LocalFocusManager.current
    val followingMids = state.availableCreators.mapTo(mutableSetOf(), Creator::mid)
    val candidates = if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
        state.availableCreators
    } else {
        (state.draftCreators.filterNot { it.mid in followingMids } + state.creatorSearchResults).distinctBy(Creator::mid)
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("首页内容来源", color = CarText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (state.hasCreatorSelectionChanges) {
                        "已选择 ${state.draftCreatorMids.size} 位 UP 主 · 待保存"
                    } else {
                        "已保存 ${state.selectedCreators.size} 位 UP 主"
                    },
                    color = if (state.hasCreatorSelectionChanges) CarGreen else CarMuted,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = viewModel::saveCreatorSelection,
                enabled = state.hasCreatorSelectionChanges && !state.sourceSaveInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CarGreen,
                    disabledContainerColor = CarSurfaceRaised,
                    disabledContentColor = CarMuted,
                ),
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (state.sourceSaveInProgress) {
                    CircularProgressIndicator(color = CarMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (state.sourceSaveInProgress) "保存中" else "保存")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactTab("我的关注", state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                viewModel.selectCreatorSourceTab(CreatorSourceTab.FOLLOWING)
            }
            CompactTab("搜索添加", state.creatorSourceTab == CreatorSourceTab.SEARCH) {
                viewModel.selectCreatorSourceTab(CreatorSourceTab.SEARCH)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                    "${state.availableCreators.size} 位关注 · ${state.draftCreatorMids.size} 位已选"
                } else if (state.creatorSearchAttempted) {
                    "搜索到 ${state.creatorSearchResults.size} 位 · ${state.draftCreatorMids.size} 位已选"
                } else {
                    "支持名称或 UID · ${state.draftCreatorMids.size} 位已选"
                },
                color = CarMuted,
                fontSize = 11.sp,
            )
            if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                CompactIconAction(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "刷新关注列表",
                    loading = state.sourcesLoading,
                    onClick = viewModel::loadAvailableCreators,
                )
            }
        }
        if (state.creatorSourceTab == CreatorSourceTab.SEARCH) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("搜索 UP 主", color = CarText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text("输入名称或 UID 后确认搜索", color = CarMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.creatorSearchKeyword,
                    onValueChange = viewModel::updateCreatorSearchKeyword,
                    modifier = Modifier.weight(1f).height(50.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (state.creatorSearchKeyword.isNotBlank() && !state.creatorSearchLoading) {
                                viewModel.searchCreators()
                                focusManager.clearFocus()
                            }
                        },
                    ),
                    placeholder = { Text("搜索 UP 主名称或 UID", fontSize = 13.sp, color = CarMuted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = CarMuted, modifier = Modifier.size(18.dp)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CarSurfaceRaised,
                        unfocusedContainerColor = CarSurfaceRaised,
                        focusedTextColor = CarText,
                        unfocusedTextColor = CarText,
                        focusedIndicatorColor = CarGreen,
                        unfocusedIndicatorColor = CarDivider,
                        cursorColor = CarGreen,
                    ),
                )
                Button(
                    onClick = {
                        viewModel.searchCreators()
                        focusManager.clearFocus()
                    },
                    enabled = state.creatorSearchKeyword.isNotBlank() && !state.creatorSearchLoading,
                    modifier = Modifier.width(96.dp).height(50.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CarGreen,
                        contentColor = Color(0xFF06140C),
                        disabledContainerColor = CarSurfaceRaised,
                        disabledContentColor = CarMuted,
                    ),
                ) {
                    if (state.creatorSearchLoading) {
                        CircularProgressIndicator(color = CarMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.creatorSearchLoading) "搜索中" else "搜索", fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = CarDivider)
        if (
            state.creatorSourceTab == CreatorSourceTab.SEARCH &&
            state.creatorSearchAttempted &&
            state.creatorSearchResults.isEmpty() &&
            state.creatorSearchError == null &&
            !state.creatorSearchLoading &&
            candidates.isNotEmpty()
        ) {
            Surface(
                color = CarSurfaceRaised,
                shape = RoundedCornerShape(5.dp),
                border = BorderStroke(1.dp, CarDivider),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = CarMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("未找到新结果，下方保留已添加的 UP 主", color = CarMuted, fontSize = 11.sp)
                }
            }
        }
        when {
            state.sourcesLoading && candidates.isEmpty() -> LoadingStatePanel("正在加载关注列表", Modifier.weight(1f))
            state.creatorSearchLoading && candidates.isEmpty() -> LoadingStatePanel("正在搜索 UP 主", Modifier.weight(1f))
            state.creatorSourceTab == CreatorSourceTab.FOLLOWING && state.sourcesError != null && candidates.isEmpty() -> ListStatePanel(
                icon = Icons.Rounded.Refresh,
                title = "关注列表加载失败",
                detail = state.sourcesError,
                action = "重试",
                modifier = Modifier.weight(1f),
                onAction = viewModel::loadAvailableCreators,
            )
            state.creatorSourceTab == CreatorSourceTab.SEARCH && state.creatorSearchError != null && candidates.isEmpty() -> ListStatePanel(
                icon = Icons.Rounded.Search,
                title = "搜索失败",
                detail = state.creatorSearchError,
                action = "重试",
                modifier = Modifier.weight(1f),
                onAction = viewModel::searchCreators,
            )
            candidates.isEmpty() -> ListStatePanel(
                icon = if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) Icons.Rounded.AccountCircle else Icons.Rounded.Search,
                title = if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                    "暂无关注的 UP 主"
                } else if (state.creatorSearchAttempted) {
                    "没有找到匹配的 UP 主"
                } else {
                    "搜索并添加 UP 主"
                },
                detail = if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                    "关注 UP 主后刷新列表，或切换到搜索添加"
                } else if (state.creatorSearchAttempted) {
                    "可尝试更短的名称关键词，或直接输入 UID"
                } else {
                    "支持按名称关键词或 UID 搜索"
                },
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                items(candidates, key = Creator::mid) { creator ->
                    val selected = creator.mid in state.draftCreatorMids
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) CarGreenSoft.copy(alpha = 0.62f) else Color.Transparent)
                            .clickable { viewModel.toggleCreator(creator) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                            contentDescription = if (selected) "取消选择" else "选择",
                            tint = if (selected) CarGreen else CarMuted,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            creator.name,
                            color = if (selected) CarGreen else CarText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("UID ${creator.mid}", color = CarMuted, fontSize = 11.sp)
                    }
                    HorizontalDivider(color = CarDivider)
                }
                if (state.sourcesLoading || state.creatorSearchLoading) item { LoadingRow("正在加载") }
            }
        }
    }
}

@Composable
private fun LikedScreen(state: CarUiState, viewModel: CarViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.likedItems, key = LikedMediaEntity::mediaId) { item ->
            LikedRow(item) { viewModel.playLiked(item) }
            HorizontalDivider(color = CarDivider)
        }
        if (state.likedItems.isEmpty()) item { EmptyInline("暂无喜欢的内容，可从播放页添加") }
    }
}

@Composable
private fun AccountScreen(state: CarUiState, viewModel: CarViewModel) {
    var confirmLogout by remember { mutableStateOf(false) }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = CarSurfaceRaised,
            title = { Text("退出登录？", color = CarText) },
            text = {
                Text(
                    "将清除 Bilibili 登录状态和首页 UP 配置，本地播放历史与“我喜欢的”仍会保留。",
                    color = CarMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CarDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("取消", color = CarText)
                }
            },
        )
    }
    if (state.accountLoading && !state.account.isLoggedIn) {
        LoadingRow()
        return
    }
    if (state.account.isLoggedIn) {
        val refreshingAccountContent = state.accountLoading || state.favoriteLoading || state.onlineHistoryLoading || state.sourcesLoading
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Surface(
                color = CarSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CarDivider),
                modifier = Modifier.fillMaxWidth().height(126.dp),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CarGreenSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(82.dp)) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = CarGreen, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.account.name, color = CarText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Surface(color = CarGreenSoft, shape = RoundedCornerShape(50)) {
                                Text("已登录", color = CarGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("UID ${state.account.mid}", color = CarMuted, fontSize = 13.sp)
                    }
                    Button(
                        onClick = viewModel::refreshAllAccountContent,
                        enabled = !refreshingAccountContent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CarGreenSoft,
                            contentColor = CarGreen,
                            disabledContainerColor = CarSurfaceRaised,
                            disabledContentColor = CarMuted,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        if (refreshingAccountContent) {
                            CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(if (refreshingAccountContent) "刷新中" else "刷新全部")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { confirmLogout = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceRaised, contentColor = CarText),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("退出")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("内容概览", color = CarText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountMetricCard("我创建的", state.favoriteFolders[FavoriteGroup.CREATED]?.size?.toString() ?: "--", Icons.Rounded.LibraryMusic, Modifier.weight(1f))
                AccountMetricCard("我收藏的", state.favoriteFolders[FavoriteGroup.COLLECTED]?.size?.toString() ?: "--", Icons.Rounded.FavoriteBorder, Modifier.weight(1f))
                AccountMetricCard("本地历史", state.localHistory.size.toString(), Icons.Rounded.History, Modifier.weight(1f))
                AccountMetricCard("我喜欢的", state.likedItems.size.toString(), Icons.Rounded.Favorite, Modifier.weight(1f))
                AccountMetricCard("首页 UP", state.selectedCreators.size.toString(), Icons.Rounded.Home, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = CarGreenSoft.copy(alpha = 0.72f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = CarGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("刷新全部会同步账号、两组收藏夹、B站历史和关注 UP 列表", color = CarMuted, fontSize = 12.sp)
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val qrUrl = state.qrUrl
            if (qrUrl != null) {
                Surface(color = Color.White, shape = RoundedCornerShape(4.dp)) {
                    Image(
                        bitmap = remember(qrUrl) { createQrCodeBitmap(qrUrl) }.asImageBitmap(),
                        contentDescription = "Bilibili 登录二维码",
                        modifier = Modifier.size(236.dp).padding(8.dp),
                    )
                }
                Spacer(Modifier.width(34.dp))
            }
            Column(Modifier.width(360.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, tint = CarGreen, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(14.dp))
                Text("扫码登录 Bilibili", color = CarText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("使用哔哩哔哩 App 扫码。车机不打开网页，也不在本地保存手机号或验证码。", color = CarMuted, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(14.dp))
                Text(state.qrStatus, color = if (state.qrStatus.contains("失败") || state.qrStatus.contains("过期")) Color(0xFFFF9B9B) else CarGreen, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = viewModel::startQrLogin,
                    enabled = !state.loginBusy || state.qrUrl != null,
                    colors = ButtonDefaults.buttonColors(containerColor = CarGreen),
                    shape = RoundedCornerShape(5.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (qrUrl == null) "生成二维码" else "刷新二维码")
                }
            }
        }
    }
}

@Composable
private fun AccountMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CarSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CarDivider),
        modifier = modifier.height(78.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = CarGreenSoft, shape = RoundedCornerShape(7.dp), modifier = Modifier.size(38.dp)) {
                Icon(icon, contentDescription = null, tint = CarGreen, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = CarText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(label, color = CarMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlayerScreen(state: CarUiState, viewModel: CarViewModel) {
    val currentMediaId = state.playbackQueue.getOrNull(state.currentQueueIndex)?.mediaId
    val queueListState = rememberLazyListState()
    // long: 拖动期间只更新本地预览，松手后再执行一次 Seek，避免旧车机连续重建网络缓冲造成卡顿和播放失败。
    var pendingSeekMs by remember(currentMediaId) { mutableLongStateOf(-1L) }
    val displayedPositionMs = pendingSeekMs.takeIf { it >= 0L } ?: state.positionMs
    LaunchedEffect(state.currentQueueIndex, state.isMultiPage) {
        if (state.isMultiPage && state.currentQueueIndex in state.playbackQueue.indices) {
            // long: 自动切到下一 P 时同步移动左侧列表，驾驶过程中无需再手动寻找当前曲目。
            queueListState.scrollToItem(state.currentQueueIndex)
        }
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isMultiPage) {
            Surface(
                color = CarSurface,
                shape = RoundedCornerShape(6.dp),
                // long: 车机播放页左右两侧都承担主要信息展示，按相同权重分配空间，避免播放列表偏窄而右侧留出大片空白。
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = CarGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("播放列表", color = CarText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("${state.playbackQueue.size} 项", color = CarMuted, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    HorizontalDivider(color = CarDivider)
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 5.dp),
                    ) {
                        itemsIndexed(state.playbackQueue, key = { _, item -> item.mediaId }) { index, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (index == state.currentQueueIndex) CarSurfaceRaised else Color.Transparent)
                                    .clickable { viewModel.selectQueueItem(index) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    queueTitleForDisplay(item.title),
                                    color = if (index == state.currentQueueIndex) CarText else CarMuted,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            ArtworkPlaceholder(
                url = state.nowArtworkUrl,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(24.dp))
        // long: 单 P 以封面 1、播放器 2 的比例突出播放信息；多 P 仍保持列表与播放器等宽，保证分 P 标题可读。
        Column(Modifier.weight(if (state.isMultiPage) 1f else 2f)) {
            Text(
                queueTitleForDisplay(state.nowTitle),
                color = CarText,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(state.nowArtist.ifBlank { "Biu Car" }, color = CarMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(28.dp))
            Slider(
                value = displayedPositionMs.coerceAtMost(state.durationMs).toFloat(),
                onValueChange = { pendingSeekMs = it.toLong() },
                onValueChangeFinished = {
                    pendingSeekMs.takeIf { it >= 0L }?.let(viewModel::seekTo)
                    pendingSeekMs = -1L
                },
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                enabled = state.controllerReady && state.durationMs > 0L,
                colors = SliderDefaults.colors(thumbColor = CarGreen, activeTrackColor = CarGreen, inactiveTrackColor = CarDivider),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(displayedPositionMs), color = CarMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), color = CarMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = viewModel::cyclePlaybackOrder, enabled = state.controllerReady) {
                    Icon(
                        if (state.shuffleEnabled) Icons.Rounded.Shuffle else if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = playbackOrderLabel(state.repeatMode, state.shuffleEnabled),
                        tint = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF && !state.shuffleEnabled) CarMuted else CarGreen,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = viewModel::playPrevious, enabled = state.controllerReady) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一曲", tint = CarText, modifier = Modifier.size(32.dp))
                }
                Surface(
                    color = CarGreen,
                    contentColor = Color(0xFF06140C),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(62.dp).clickable(enabled = state.controllerReady, onClick = viewModel::togglePlayback),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.padding(14.dp),
                    )
                }
                IconButton(onClick = viewModel::playNext, enabled = state.controllerReady) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一曲", tint = CarText, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = viewModel::toggleLiked, enabled = state.controllerReady) {
                    Icon(
                        if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (state.liked) "取消喜欢" else "喜欢",
                        tint = if (state.liked) CarGreen else CarMuted,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

private fun playbackOrderLabel(mode: Int, shuffleEnabled: Boolean): String = when {
    shuffleEnabled -> "随机播放"
    mode == androidx.media3.common.Player.REPEAT_MODE_ONE -> "单曲循环"
    mode == androidx.media3.common.Player.REPEAT_MODE_ALL -> "列表循环"
    else -> "顺序播放"
}

private fun queueTitleForDisplay(title: String): String = title.substringAfter(" · ", title)

@Composable
private fun ArtworkPlaceholder(url: String, modifier: Modifier = Modifier) {
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = if (url.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
        }
    }
    val artworkAspectRatio = bitmap?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
        ?: (16f / 9f)
    Surface(
        color = CarSurfaceRaised,
        shape = RoundedCornerShape(6.dp),
        // long: 单 P 封面沿用素材自身宽高比完整展示；加载完成前以常见 16:9 占位，避免从正方形突然跳变。
        modifier = modifier.aspectRatio(artworkAspectRatio),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "当前音频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun MiniPlayer(state: CarUiState, viewModel: CarViewModel) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(CarSurface)
            .clickable { viewModel.selectRoot(RootPage.PLAYER) },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(CarDivider)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(CarGreen))
        }
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = CarGreenSoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = CarGreen, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(queueTitleForDisplay(state.nowTitle), color = CarText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.nowArtist.ifBlank { "Biu Car" }, color = CarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}", color = CarMuted, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = viewModel::playPrevious, enabled = state.controllerReady, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一曲", tint = CarText, modifier = Modifier.size(28.dp))
            }
            Surface(
                color = CarGreen,
                contentColor = Color(0xFF06140C),
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(46.dp).clickable(enabled = state.controllerReady, onClick = viewModel::togglePlayback),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.padding(10.dp),
                )
            }
            IconButton(onClick = viewModel::playNext, enabled = state.controllerReady, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一曲", tint = CarText, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun VideoList(
    listIdentity: String,
    videos: List<Video>,
    loading: Boolean,
    hasMore: Boolean,
    onPlay: (Video) -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    // long: 每个 UP、收藏夹和历史列表维护独立滚动位置，切换数据源时不会从旧列表的中间位置开始加载。
    val listState = androidx.compose.runtime.key(listIdentity) { rememberLazyListState() }
    LaunchedEffect(listState, videos.size, loading, hasMore) {
        if (videos.isEmpty() || loading || !hasMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                // long: 内容接近列表末尾时自动请求下一页，减少驾驶过程中对小型“加载更多”按钮的精确点击。
                if (lastVisibleIndex >= videos.lastIndex - 2) onLoadMore()
            }
    }
    if (videos.isEmpty()) {
        when {
            loading -> LoadingStatePanel("正在加载内容", modifier.fillMaxSize())
            errorText != null -> ListStatePanel(
                icon = Icons.Rounded.Refresh,
                title = "内容加载失败",
                detail = errorText,
                action = if (onRetry != null) "重试" else null,
                modifier = modifier.fillMaxSize(),
                onAction = onRetry,
            )
            else -> ListStatePanel(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = emptyText,
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), state = listState) {
        items(videos, key = { video -> "${video.bvid}:${video.cid ?: 0L}" }) { video ->
            VideoRow(video) { onPlay(video) }
            HorizontalDivider(color = CarDivider)
        }
        if (loading) item { LoadingRow("正在加载更多") }
    }
}

@Composable
private fun VideoRow(video: Video, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onPlay).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(video.title, color = CarText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row {
                Text(video.author, color = CarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                video.publishedAtEpochSeconds?.let { Text(formatDate(it), color = CarMuted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.width(12.dp))
        video.durationSeconds?.let { Text(formatDuration(it * 1_000L), color = CarMuted, fontSize = 11.sp) }
        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = CarGreen, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun HistoryRow(item: PlaybackHistoryEntity, onPlay: () -> Unit) {
    val cacheState = runCatching { AudioCacheState.valueOf(item.cacheState) }.getOrDefault(AudioCacheState.NONE)
    Row(
        Modifier.fillMaxWidth().height(62.dp).clickable(onClick = onPlay).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cacheState == AudioCacheState.CACHING) {
            CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(
                if (cacheState == AudioCacheState.READY) Icons.Rounded.CloudDone else Icons.Rounded.History,
                contentDescription = cacheStatusLabel(cacheState),
                tint = when (cacheState) {
                    AudioCacheState.READY -> CarGreen
                    AudioCacheState.FAILED -> Color(0xFFE0A05A)
                    else -> CarMuted
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(queueTitleForDisplay(item.pageTitle?.takeIf(String::isNotBlank) ?: item.title), color = CarText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(item.artist, color = CarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatDuration(item.lastPositionMs)} / ${formatDuration(item.durationMs)}", color = CarMuted, fontSize = 11.sp)
            Text(
                cacheStatusLabel(cacheState),
                color = if (cacheState == AudioCacheState.READY) CarGreen else CarMuted,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放历史", tint = CarGreen, modifier = Modifier.size(28.dp))
    }
}

private fun cacheStatusLabel(state: AudioCacheState): String = when (state) {
    AudioCacheState.READY -> "离线可用"
    AudioCacheState.CACHING -> "缓存中"
    AudioCacheState.FAILED -> "缓存失败"
    AudioCacheState.NONE -> "仅在线"
}

@Composable
private fun LikedRow(item: LikedMediaEntity, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).clickable(onClick = onPlay).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.pageTitle?.takeIf(String::isNotBlank) ?: item.title, color = CarText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(item.artist, color = CarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDate(item.likedAtEpochMs / 1_000L), color = CarMuted, fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.PlayArrow, contentDescription = "播放喜欢内容", tint = CarGreen, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun CompactTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) CarGreenSoft else CarSurfaceRaised,
        contentColor = if (selected) CarGreen else CarMuted,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, if (selected) CarGreen.copy(alpha = 0.72f) else CarDivider),
        modifier = Modifier.height(44.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
        }
    }
}

@Composable
private fun CompactIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = CarSurfaceRaised,
        contentColor = CarMuted,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, CarDivider),
        modifier = Modifier.size(44.dp).clickable(enabled = !loading, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = contentDescription, tint = CarMuted, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun LoadingStatePanel(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = CarMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ListStatePanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = CarGreenSoft, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(42.dp)) {
            Icon(icon, contentDescription = null, tint = CarGreen, modifier = Modifier.padding(10.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(title, color = CarText, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = CarMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = CarGreen, contentColor = Color(0xFF06140C)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(action, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = CarGreen, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = CarText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = CarMuted, fontSize = 12.sp)
        Spacer(Modifier.height(15.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = CarGreen), shape = RoundedCornerShape(5.dp)) {
            Text(action)
        }
    }
}

@Composable
private fun LoginRequired(onClick: () -> Unit) {
    EmptyState("需要登录", "登录后才能读取 Bilibili 在线收藏和账号内容。", "前往扫码登录", onClick)
}

@Composable
private fun LoadingRow(text: String = "正在加载") {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = CarMuted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyInline(text: String) {
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Text(text, color = CarMuted, fontSize = 13.sp)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatDate(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochSeconds * 1_000L))
