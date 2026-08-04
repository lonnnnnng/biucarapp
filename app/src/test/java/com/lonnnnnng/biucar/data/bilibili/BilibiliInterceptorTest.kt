package com.lonnnnnng.biucar.data.bilibili

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class BilibiliInterceptorTest {
    @Test
    fun `CDN请求使用桌面浏览器UA`() {
        val userAgent = BilibiliInterceptor.USER_AGENT

        // long: bilivideo CDN 已在目标网络实测拒绝 Mobile UA；该断言防止后续为了匹配旧车机版本而误改回移动端标识。
        assertTrue(userAgent.contains("Chrome/"))
        assertTrue(userAgent.contains("Windows NT"))
        assertFalse(userAgent.contains("Mobile"))
    }

    @Test
    fun `第三方媒体CDN带Referer和UA但不带Cookie`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            OkHttpClient.Builder()
                .addInterceptor(BilibiliInterceptor(null))
                .build()
                .newCall(okhttp3.Request.Builder().url(server.url("/media.mp4")).build())
                .execute()
                .close()

            val request = server.takeRequest()
            assertEquals("https://www.bilibili.com/", request.getHeader("Referer"))
            assertEquals(BilibiliInterceptor.USER_AGENT, request.getHeader("User-Agent"))
            assertNull(request.getHeader("Cookie"))
        }
    }
}
