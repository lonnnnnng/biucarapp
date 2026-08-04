package com.lonnnnnng.biucar.data.bilibili

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliInterceptorTest {
    @Test
    fun `CDN请求使用桌面浏览器UA`() {
        val userAgent = BilibiliInterceptor.USER_AGENT

        // long: bilivideo CDN 已在目标网络实测拒绝 Mobile UA；该断言防止后续为了匹配旧车机版本而误改回移动端标识。
        assertTrue(userAgent.contains("Chrome/"))
        assertTrue(userAgent.contains("Windows NT"))
        assertFalse(userAgent.contains("Mobile"))
    }
}
