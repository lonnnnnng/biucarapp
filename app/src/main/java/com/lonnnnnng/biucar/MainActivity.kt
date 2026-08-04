package com.lonnnnnng.biucar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
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
private val CarText = Color(0xFFF1F6F3)
private val CarMuted = Color(0xFF98A69E)
private val CarDivider = Color(0xFF26332D)

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
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BIU CAR", color = CarGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) CarSurfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 13.sp)
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
                modifier = Modifier.size(38.dp),
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.selectedCreators.forEach { creator ->
                CompactTab(creator.name, state.selectedHomeMid == creator.mid) { viewModel.selectHomeCreator(creator.mid) }
            }
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
                    modifier = Modifier.size(42.dp),
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
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(242.dp).fillMaxHeight().background(CarSurface),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(folders, key = { "${it.group}:${it.id}" }) { folder ->
                FolderRow(folder, state.selectedFavoriteFolder?.id == folder.id) { viewModel.selectFavoriteFolder(folder) }
            }
            if (folders.isEmpty() && !state.favoriteLoading) item { EmptyInline("暂无收藏夹") }
        }
        Spacer(Modifier.width(12.dp))
        VideoList(
            modifier = Modifier.weight(1f),
            listIdentity = "favorite:${group}:${state.selectedFavoriteFolder?.id}",
            videos = state.favoriteVideos,
            loading = state.favoriteLoading,
            hasMore = state.favoriteHasMore,
            onPlay = viewModel::playVideo,
            onLoadMore = viewModel::loadFavoriteVideos,
            emptyText = "请选择左侧收藏夹",
        )
    }
}

@Composable
private fun FolderRow(folder: FavoriteFolder, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (selected) CarSurfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = if (selected) CarGreen else CarMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.title, color = CarText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${folder.mediaCount} 项", color = CarMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HistoryScreen(state: CarUiState, viewModel: CarViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactTab("本地历史", state.historyMode == HistoryMode.LOCAL) { viewModel.selectHistoryMode(HistoryMode.LOCAL) }
            CompactTab("B站历史", state.historyMode == HistoryMode.ONLINE) { viewModel.selectHistoryMode(HistoryMode.ONLINE) }
            if (state.historyMode == HistoryMode.ONLINE) {
                IconButton(onClick = { viewModel.loadOnlineHistory(true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新在线历史", tint = CarMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.historyMode == HistoryMode.LOCAL) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.localHistory, key = PlaybackHistoryEntity::mediaId) { item ->
                    HistoryRow(item) { viewModel.playHistory(item) }
                    HorizontalDivider(color = CarDivider)
                }
                if (state.localHistory.isEmpty()) item { EmptyInline("暂无本地播放历史") }
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
            if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                IconButton(onClick = viewModel::loadAvailableCreators) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新关注列表", tint = CarMuted)
                }
            }
        }
        if (state.creatorSourceTab == CreatorSourceTab.SEARCH) {
            Spacer(Modifier.height(6.dp))
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
                IconButton(
                    onClick = viewModel::searchCreators,
                    enabled = state.creatorSearchKeyword.isNotBlank() && !state.creatorSearchLoading,
                ) {
                    if (state.creatorSearchLoading) {
                        CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索 UP 主", tint = CarGreen)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = CarDivider)
        LazyColumn(Modifier.fillMaxSize()) {
            val followingMids = state.availableCreators.mapTo(mutableSetOf(), Creator::mid)
            val candidates = if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) {
                state.availableCreators
            } else {
                (state.draftCreators.filterNot { it.mid in followingMids } + state.creatorSearchResults).distinctBy(Creator::mid)
            }
            items(candidates, key = Creator::mid) { creator ->
                val selected = creator.mid in state.draftCreatorMids
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { viewModel.toggleCreator(creator) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                        contentDescription = if (selected) "取消选择" else "选择",
                        tint = if (selected) CarGreen else CarMuted,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(creator.name, color = CarText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Text("UID ${creator.mid}", color = CarMuted, fontSize = 11.sp)
                }
                HorizontalDivider(color = CarDivider)
            }
            if (state.sourcesLoading) item { LoadingRow() }
            if (candidates.isEmpty() && !state.sourcesLoading && !state.creatorSearchLoading) {
                item { EmptyInline(if (state.creatorSourceTab == CreatorSourceTab.FOLLOWING) "暂无关注的 UP 主" else "输入名称或 UID 搜索 UP 主") }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD95555), contentColor = Color.White),
                    shape = RoundedCornerShape(5.dp),
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
    if (state.accountLoading) {
        LoadingRow()
        return
    }
    if (state.account.isLoggedIn) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = CarSurfaceRaised, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(108.dp)) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = CarGreen, modifier = Modifier.padding(18.dp))
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(state.account.name, color = CarText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("UID ${state.account.mid}", color = CarMuted, fontSize = 13.sp)
            }
            IconButton(onClick = viewModel::refreshAccount, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新账号", tint = CarMuted)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { confirmLogout = true },
                colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceRaised, contentColor = CarText),
                shape = RoundedCornerShape(5.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("退出登录")
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
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(CarSurface)
            .clickable { viewModel.selectRoot(RootPage.PLAYER) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.nowTitle, color = CarText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.nowArtist, color = CarMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}", color = CarMuted, fontSize = 11.sp)
        Spacer(Modifier.width(14.dp))
        IconButton(onClick = viewModel::togglePlayback, enabled = state.controllerReady) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (state.isPlaying) "暂停" else "播放", tint = CarGreen, modifier = Modifier.size(30.dp))
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
    LazyColumn(modifier.fillMaxSize(), state = listState) {
        items(videos, key = { video -> "${video.bvid}:${video.cid ?: 0L}" }) { video ->
            VideoRow(video) { onPlay(video) }
            HorizontalDivider(color = CarDivider)
        }
        if (loading) item { LoadingRow() }
        if (!loading && videos.isEmpty()) item { EmptyInline(emptyText) }
    }
}

@Composable
private fun VideoRow(video: Video, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).clickable(onClick = onPlay).padding(horizontal = 8.dp),
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
        color = if (selected) CarGreen else CarSurfaceRaised,
        contentColor = if (selected) Color(0xFF06140C) else CarMuted,
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, maxLines = 1)
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
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CarGreen, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochSeconds * 1_000L))
