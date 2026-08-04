package com.lonnnnnng.biucar.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSignerTest {
    @Test
    fun `签名不受参数插入顺序影响`() {
        val first = AppSigner.sign(linkedMapOf("ts" to "100", "appkey" to "key", "name" to "车机 音乐"), "secret")
        val second = AppSigner.sign(linkedMapOf("name" to "车机 音乐", "appkey" to "key", "ts" to "100"), "secret")

        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `不同密钥不会生成相同签名`() {
        val parameters = mapOf("appkey" to "key", "ts" to "100")
        assertNotEquals(AppSigner.sign(parameters, "first"), AppSigner.sign(parameters, "second"))
    }
}
