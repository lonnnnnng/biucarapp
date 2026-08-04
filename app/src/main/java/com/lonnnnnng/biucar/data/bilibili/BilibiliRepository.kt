package com.lonnnnnng.biucar.data.bilibili

import com.lonnnnnng.biucar.data.model.Account
import com.lonnnnnng.biucar.data.model.AudioTrack
import com.lonnnnnng.biucar.data.model.Creator
import com.lonnnnnng.biucar.data.model.FavoriteFolder
import com.lonnnnnng.biucar.data.model.FavoriteGroup
import com.lonnnnnng.biucar.data.model.FavoriteType
import com.lonnnnnng.biucar.data.model.HistoryCursor
import com.lonnnnnng.biucar.data.model.HistoryPage
import com.lonnnnnng.biucar.data.model.Page
import com.lonnnnnng.biucar.data.model.Video
import com.lonnnnnng.biucar.data.model.VideoDetail
import com.lonnnnnng.biucar.data.model.VideoPage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class BilibiliRepository(
    private val client: OkHttpClient,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val apiBase: HttpUrl = API_BASE,
) {
    private val wbiMutex = Mutex()
    private var cachedWbiKeys: Pair<WbiKeys, Long>? = null

    suspend fun account(): Account {
        val root = request("/x/web-interface/nav")
        val code = root.optInt("code", Int.MIN_VALUE)
        if (code != 0 && code != -101) throw apiError(root)
        val data = root.optJSONObject("data") ?: JSONObject()
        return Account(
            isLoggedIn = data.optBoolean("isLogin", false),
            mid = data.optLong("mid", 0L),
            name = data.optString("uname"),
            faceUrl = httpsUrl(data.optString("face")),
        )
    }

    suspend fun followingCreators(mid: Long): List<Creator> {
        require(mid > 0L) { "账号 UID 无效" }
        val result = mutableListOf<Creator>()
        var page = 1
        var total = Int.MAX_VALUE
        while (result.size < total && page <= MAX_FOLLOWING_PAGES) {
            val data = request(
                "/x/relation/followings",
                mapOf("vmid" to mid, "pn" to page, "ps" to FOLLOWING_PAGE_SIZE, "order_type" to ""),
            ).successData()
            total = data.optInt("total", 0).coerceAtLeast(0)
            val current = data.optJSONArray("list").objects().mapNotNull { item ->
                val creatorMid = item.optLong("mid", 0L).takeIf { it > 0L } ?: return@mapNotNull null
                Creator(creatorMid, plainText(item.optString("uname")), httpsUrl(item.optString("face")))
            }
            result += current
            if (current.isEmpty()) break
            page += 1
        }
        return result.distinctBy(Creator::mid)
    }

    suspend fun creatorVideos(creator: Creator, page: Int = 1): Page<Video> {
        val normalizedPage = page.coerceAtLeast(1)
        val data = request(
            "/x/space/wbi/arc/search",
            mapOf("mid" to creator.mid, "pn" to normalizedPage, "ps" to VIDEO_PAGE_SIZE, "order" to "pubdate"),
            useWbi = true,
        ).successData()
        val videos = data.optJSONObject("list")?.optJSONArray("vlist").objects().mapNotNull { item ->
            parseVideo(
                item = item,
                bvidField = "bvid",
                titleField = "title",
                coverField = "pic",
                author = item.optString("author").ifBlank { creator.name },
                duration = parseDuration(item.optString("length")),
                publishedAt = item.optLongOrNull("created") ?: item.optLongOrNull("pubdate"),
            )
        }
        val total = data.optJSONObject("page")?.optInt("count", 0)?.coerceAtLeast(0) ?: 0
        return Page(videos, normalizedPage, videos.isNotEmpty() && normalizedPage * VIDEO_PAGE_SIZE < total)
    }

    suspend fun favoriteFolders(mid: Long, group: FavoriteGroup): List<FavoriteFolder> {
        require(mid > 0L) { "账号 UID 无效" }
        val path = if (group == FavoriteGroup.CREATED) {
            "/x/v3/fav/folder/created/list"
        } else {
            "/x/v3/fav/folder/collected/list"
        }
        val result = mutableListOf<FavoriteFolder>()
        var page = 1
        while (page <= MAX_FAVORITE_PAGES) {
            val data = request(
                path,
                buildMap {
                    put("up_mid", mid)
                    put("pn", page)
                    put("ps", FAVORITE_PAGE_SIZE)
                    if (group == FavoriteGroup.COLLECTED) put("platform", "web")
                },
            ).successData()
            val raw = data.optJSONArray("list").objects()
            result += raw.mapNotNull { item ->
                if (item.optInt("state", 0) != 0) return@mapNotNull null
                val id = item.optLong("id", 0L).takeIf { it > 0L } ?: return@mapNotNull null
                FavoriteFolder(
                    id = id,
                    title = plainText(item.optString("title")).ifBlank { "未命名收藏夹" },
                    mediaCount = item.optInt("media_count", 0).coerceAtLeast(0),
                    group = group,
                    type = if (group == FavoriteGroup.CREATED) {
                        FavoriteType.VIDEO_FOLDER
                    } else {
                        FavoriteType.fromApiValue(item.optInt("type", 11))
                    },
                )
            }
            val total = data.optInt("count", 0).coerceAtLeast(0)
            val hasMore = if (data.has("has_more")) data.optBoolean("has_more") else result.size < total
            if (!hasMore || raw.isEmpty()) break
            page += 1
        }
        return result.distinctBy { it.group to it.id }
    }

    suspend fun favoriteVideos(folder: FavoriteFolder, page: Int = 1): Page<Video> {
        val normalizedPage = page.coerceAtLeast(1)
        return when (folder.type) {
            FavoriteType.VIDEO_FOLDER -> regularFavoriteVideos(folder, normalizedPage)
            FavoriteType.VIDEO_COLLECTION -> collectionFavoriteVideos(folder, normalizedPage)
            FavoriteType.UNKNOWN -> throw IOException("无法识别收藏夹类型")
        }
    }

    suspend fun onlineHistory(cursor: HistoryCursor? = null): HistoryPage {
        val data = request(
            "/x/web-interface/history/cursor",
            mapOf(
                "max" to (cursor?.max ?: 0),
                "view_at" to (cursor?.viewAtEpochSeconds ?: 0),
                "business" to cursor?.business,
                "type" to "archive",
                "ps" to HISTORY_PAGE_SIZE,
            ),
        ).successData()
        val raw = data.optJSONArray("list")
        val videos = raw.objects().mapNotNull { item ->
            val history = item.optJSONObject("history") ?: return@mapNotNull null
            val bvid = history.optString("bvid").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Video(
                bvid = bvid,
                aid = history.optLongOrNull("oid"),
                cid = history.optLongOrNull("cid"),
                title = plainText(item.optString("title")),
                author = plainText(item.optString("author_name")),
                coverUrl = httpsUrl(item.optString("cover")),
                durationSeconds = item.optIntOrNull("duration"),
                progressSeconds = item.optIntOrNull("progress"),
            )
        }
        val next = data.optJSONObject("cursor")?.let { value ->
            HistoryCursor(value.optLong("max"), value.optLong("view_at"), value.optString("business"))
        }?.takeIf { videos.isNotEmpty() && it.max > 0L && it.viewAtEpochSeconds > 0L && it.business.isNotBlank() }
        return HistoryPage(videos, next)
    }

    suspend fun videoDetail(bvid: String): VideoDetail {
        val data = request("/x/web-interface/view", mapOf("bvid" to bvid)).successData()
        val pages = data.optJSONArray("pages").objects().mapNotNull { page ->
            val cid = page.optLong("cid", 0L).takeIf { it > 0L } ?: return@mapNotNull null
            VideoPage(cid, page.optInt("page", 1), plainText(page.optString("part")), page.optInt("duration", 0))
        }
        return VideoDetail(
            bvid = data.optString("bvid", bvid),
            aid = data.optLongOrNull("aid"),
            title = plainText(data.optString("title")),
            author = plainText(data.optJSONObject("owner")?.optString("name").orEmpty()),
            coverUrl = httpsUrl(data.optString("pic")),
            pages = pages,
        )
    }

    suspend fun resolveAudioTrack(video: Video, pageIndex: Int = 0): AudioTrack =
        resolveAudioTracks(video, pageIndex).first()

    fun resolveAudioTracks(video: Video, pageIndex: Int = 0): Flow<AudioTrack> = flow {
        val detail = videoDetail(video.bvid)
        if (detail.pages.isEmpty()) throw IOException("视频没有可播放分 P")
        val preferredIndex = video.cid?.let { cid -> detail.pages.indexOfFirst { it.cid == cid } }?.takeIf { it >= 0 }
            ?: pageIndex.coerceIn(detail.pages.indices)

        // long: 从用户选中的分 P 开始逐项解析并立即交给播放器，首 P 无需等待整个合辑解析，后续项会按 API 顺序追加到 Media3 队列。
        detail.pages.drop(preferredIndex).forEach { page ->
            emit(resolveAudioTrack(video, detail, page))
        }
    }

    private suspend fun resolveAudioTrack(video: Video, detail: VideoDetail, page: VideoPage): AudioTrack {
        val dash = request(
            "/x/player/wbi/playurl",
            mapOf("bvid" to detail.bvid, "cid" to page.cid, "fnval" to 16, "fnver" to 0, "fourk" to 0),
            useWbi = true,
        ).successData().optJSONObject("dash") ?: throw IOException("没有 DASH 音频")
        // long: 旧车机只选择标准 AAC 音轨，不请求 FLAC、杜比和视频轨，降低带宽、解码压力与兼容风险。
        val audio = dash.optJSONArray("audio").objects()
            .filter { item ->
                val codec = item.optString("codecs").lowercase()
                val id = item.optInt("id", -1)
                codec.contains("mp4a") || id in STANDARD_AAC_IDS
            }
            .maxByOrNull { it.optLong("bandwidth", 0L) }
            ?: throw IOException("没有标准 AAC 音轨")
        val streamUrl = audio.optString("baseUrl").ifBlank { audio.optString("base_url") }
            .takeIf(String::isNotBlank) ?: throw IOException("音频地址为空")
        val pageTitle = if (detail.pages.size > 1) "P${page.page} · ${page.title.ifBlank { "第 ${page.page} P" }}" else null
        return AudioTrack(
            bvid = detail.bvid,
            cid = page.cid,
            title = detail.title,
            pageTitle = pageTitle,
            artist = detail.author.ifBlank { video.author },
            artworkUrl = detail.coverUrl.ifBlank { video.coverUrl },
            streamUrl = streamUrl,
            qualityLabel = "${audio.optLong("bandwidth", 0L) / 1000L} kbps",
        )
    }

    private suspend fun regularFavoriteVideos(folder: FavoriteFolder, page: Int): Page<Video> {
        val data = request(
            "/x/v3/fav/resource/list",
            mapOf("media_id" to folder.id, "pn" to page, "ps" to VIDEO_PAGE_SIZE, "order" to "mtime", "platform" to "web"),
        ).successData()
        val raw = data.optJSONArray("medias")
        val videos = raw.objects().mapNotNull { item ->
            if (item.optInt("type", 0) != 2 || item.optInt("attr", 0) != 0) return@mapNotNull null
            parseVideo(
                item,
                "bvid",
                "title",
                "cover",
                item.optJSONObject("upper")?.optString("name").orEmpty(),
                item.optIntOrNull("duration"),
                item.optLongOrNull("pubtime"),
            )
        }
        return Page(videos, page, data.optBoolean("has_more", raw?.length() == VIDEO_PAGE_SIZE))
    }

    private suspend fun collectionFavoriteVideos(folder: FavoriteFolder, page: Int): Page<Video> {
        val data = request(
            "/x/space/fav/season/list",
            mapOf("season_id" to folder.id, "pn" to page, "ps" to VIDEO_PAGE_SIZE),
            useWbi = true,
        ).successData()
        val raw = data.optJSONArray("medias")
        val videos = raw.objects().mapNotNull { item ->
            parseVideo(
                item,
                "bvid",
                "title",
                "cover",
                item.optJSONObject("upper")?.optString("name").orEmpty(),
                item.optIntOrNull("duration"),
                item.optLongOrNull("pubtime"),
            )
        }
        val total = data.optJSONObject("info")?.optInt("media_count", 0) ?: 0
        return Page(videos, page, videos.isNotEmpty() && page * VIDEO_PAGE_SIZE < total)
    }

    private fun parseVideo(
        item: JSONObject,
        bvidField: String,
        titleField: String,
        coverField: String,
        author: String,
        duration: Int?,
        publishedAt: Long?,
    ): Video? {
        val bvid = item.optString(bvidField).ifBlank { item.optString("bv_id") }.takeIf(String::isNotBlank) ?: return null
        return Video(
            bvid = bvid,
            aid = item.optLongOrNull("aid") ?: item.optLongOrNull("id"),
            title = plainText(item.optString(titleField)),
            author = plainText(author),
            coverUrl = httpsUrl(item.optString(coverField)),
            durationSeconds = duration,
            publishedAtEpochSeconds = publishedAt,
        )
    }

    private suspend fun request(path: String, parameters: Map<String, Any?> = emptyMap(), useWbi: Boolean = false): JSONObject {
        val query = if (useWbi) WbiSigner.sign(parameters, currentWbiKeys(), nowEpochSeconds()) else null
        val url = apiBase.newBuilder().addPathSegments(path.removePrefix("/")).apply {
            if (query != null) encodedQuery(query) else parameters.forEach { (key, value) ->
                if (value != null) addQueryParameter(key, value.toString())
            }
        }.build()
        return execute(url)
    }

    private suspend fun currentWbiKeys(): WbiKeys = wbiMutex.withLock {
        cachedWbiKeys?.takeIf { nowEpochSeconds() < it.second }?.first ?: run {
            val data = execute(apiBase.newBuilder().addPathSegments("x/web-interface/nav").build())
                .optJSONObject("data")?.optJSONObject("wbi_img") ?: throw IOException("无法获取 WBI key")
            val keys = WbiKeys(fileStem(data.optString("img_url")), fileStem(data.optString("sub_url")))
            require(keys.imgKey.isNotBlank() && keys.subKey.isNotBlank()) { "WBI key 无效" }
            cachedWbiKeys = keys to (nowEpochSeconds() + TimeUnit.HOURS.toSeconds(6))
            keys
        }
    }

    private suspend fun execute(url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bilibili HTTP ${response.code}")
            runCatching { JSONObject(response.body?.string().orEmpty()) }
                .getOrElse { throw IOException("Bilibili 响应不是 JSON", it) }
        }
    }

    private fun JSONObject.successData(): JSONObject {
        if (optInt("code", Int.MIN_VALUE) != 0) throw apiError(this)
        return optJSONObject("data") ?: JSONObject()
    }

    private fun apiError(root: JSONObject): IOException =
        IOException(root.optString("message", "Bilibili API ${root.optInt("code", -1)}"))

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun fileStem(url: String): String = url.substringAfterLast('/').substringBeforeLast('.', "")
    private fun httpsUrl(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https://${value.removePrefix("http://")}"
        else -> value
    }

    private fun plainText(value: String): String = value
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .trim()

    private fun parseDuration(value: String): Int? {
        val parts = value.split(':').mapNotNull(String::toIntOrNull)
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private companion object {
        val API_BASE = "https://api.bilibili.com".toHttpUrl()
        const val FOLLOWING_PAGE_SIZE = 50
        const val VIDEO_PAGE_SIZE = 20
        const val FAVORITE_PAGE_SIZE = 20
        const val HISTORY_PAGE_SIZE = 20
        const val MAX_FOLLOWING_PAGES = 100
        const val MAX_FAVORITE_PAGES = 100
        val STANDARD_AAC_IDS = setOf(30216, 30232, 30280)
    }
}
