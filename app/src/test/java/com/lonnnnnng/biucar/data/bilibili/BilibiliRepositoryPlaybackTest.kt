package com.lonnnnnng.biucar.data.bilibili

import com.lonnnnnng.biucar.data.model.Video
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BilibiliRepositoryPlaybackTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BilibiliRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repository = BilibiliRepository(
            client = OkHttpClient(),
            nowEpochSeconds = { 1_000L },
            apiBase = server.url("/"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `多P资源按分P顺序生成完整播放队列`() = runBlocking {
        enqueueVideoDetail(pageCount = 2)
        enqueueWbiKeys()
        enqueueAudio(cid = 101L)
        enqueueAudio(cid = 102L)

        val tracks = repository.resolveAudioTracks(
            Video(bvid = "BV1TEST", title = "测试合辑", author = "测试UP"),
        ).toList()

        // long: Media3 只有收到完整的分 P 队列才会在当前 P 结束后自动前进，不能把多 P 再压回首个音轨。
        assertEquals(listOf(101L, 102L), tracks.map { it.cid })
        assertEquals(listOf("P1 · 第一首", "P2 · 第二首"), tracks.map { it.pageTitle })
    }

    @Test
    fun `单P资源只生成一个播放项且不显示分P标题`() = runBlocking {
        enqueueVideoDetail(pageCount = 1)
        enqueueWbiKeys()
        enqueueAudio(cid = 101L)

        val tracks = repository.resolveAudioTracks(
            Video(bvid = "BV1TEST", title = "单曲", author = "测试UP"),
        ).toList()

        assertEquals(listOf(101L), tracks.map { it.cid })
        assertEquals(listOf(null), tracks.map { it.pageTitle })
    }

    @Test
    fun `历史资源从记录的分P继续并衔接后续分P`() = runBlocking {
        enqueueVideoDetail(pageCount = 2)
        enqueueWbiKeys()
        enqueueAudio(cid = 102L)

        val tracks = repository.resolveAudioTracks(
            Video(bvid = "BV1TEST", cid = 102L, title = "测试合辑", author = "测试UP"),
        ).toList()

        // long: 历史中的 cid 表示用户上次实际播放的分 P，恢复时从该项开始，不能退回 P1 重播。
        assertEquals(listOf(102L), tracks.map { it.cid })
        assertEquals(listOf("P2 · 第二首"), tracks.map { it.pageTitle })
    }

    @Test
    fun `关键词搜索UP主解析名称UID和头像`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"numResults":1,"result":[{"mid":12345,"uname":"<em class=\"keyword\">测试</em>UP","upic":"//i.test/avatar.png"}]}}""",
            ),
        )

        val result = repository.searchCreators("测试UP")

        assertEquals(1, result.items.size)
        assertEquals(12345L, result.items.single().mid)
        assertEquals("测试UP", result.items.single().name)
        assertEquals("https://i.test/avatar.png", result.items.single().faceUrl)
        assertEquals(false, result.hasMore)
        val request = server.takeRequest()
        assertEquals("bili_user", request.requestUrl?.queryParameter("search_type"))
        assertEquals("测试UP", request.requestUrl?.queryParameter("keyword"))
    }

    private fun enqueueVideoDetail(pageCount: Int) {
        val pages = listOf(
            """{"cid":101,"page":1,"part":"第一首","duration":180}""",
            """{"cid":102,"page":2,"part":"第二首","duration":200}""",
        ).take(pageCount).joinToString(",")
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"bvid":"BV1TEST","title":"测试合辑","owner":{"name":"测试UP"},"pages":[$pages]}}""",
            ),
        )
    }

    private fun enqueueWbiKeys() {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"wbi_img":{"img_url":"https://i.test/0123456789abcdef0123456789abcdef.png","sub_url":"https://i.test/fedcba9876543210fedcba9876543210.png"}}}""",
            ),
        )
    }

    private fun enqueueAudio(cid: Long) {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"dash":{"audio":[{"id":30280,"codecs":"mp4a.40.2","bandwidth":192000,"baseUrl":"https://cdn.test/$cid.m4a"}]}}}""",
            ),
        )
    }
}
