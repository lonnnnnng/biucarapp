package com.lonnnnnng.biucar.data.auth

import com.lonnnnnng.biucar.data.model.QrPollResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvQrLoginApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TvQrLoginApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = TvQrLoginApi(
            client = OkHttpClient(),
            nowEpochSeconds = { 1_000L },
            authCodeUrl = server.url("/auth").toString(),
            pollUrl = server.url("/poll").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `二维码申请返回地址和密钥`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"url":"https://example.test/qr","auth_code":"abc"}}"""))

        val challenge = api.createChallenge()

        assertEquals("https://example.test/qr", challenge.url)
        assertEquals("abc", challenge.authCode)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("appkey="))
        assertTrue(body.contains("sign="))
    }

    @Test
    fun `轮询状态码映射为可展示状态`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":86039,"message":"not scanned"}"""))
        server.enqueue(MockResponse().setBody("""{"code":86090,"message":"scanned"}"""))
        server.enqueue(MockResponse().setBody("""{"code":86038,"message":"expired"}"""))

        assertTrue(api.poll("abc") is QrPollResult.Waiting)
        assertTrue(api.poll("abc") is QrPollResult.Scanned)
        assertTrue(api.poll("abc") is QrPollResult.Expired)
    }

    @Test
    fun `成功响应解析Web Cookie并使用默认有效期`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"data":{"token_info":{"access_token":"token","refresh_token":"refresh","mid":42},"cookie_info":{"cookies":[{"name":"SESSDATA","value":"session"},{"name":"bili_jct","value":"csrf"}]}}}""",
            ),
        )

        val result = api.poll("abc") as QrPollResult.Success

        assertEquals("SESSDATA=session; bili_jct=csrf", result.credentials.cookieHeader)
        assertEquals(42L, result.credentials.mid)
        assertEquals(2_593_000L, result.credentials.expiresAtEpochSeconds)
    }
}
