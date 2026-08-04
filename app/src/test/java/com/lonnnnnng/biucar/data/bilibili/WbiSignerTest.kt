package com.lonnnnnng.biucar.data.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WbiSignerTest {
    @Test
    fun `WBI参数排序稳定且过滤特殊字符`() {
        val keys = WbiKeys(
            imgKey = "0123456789abcdef0123456789abcdef",
            subKey = "fedcba9876543210fedcba9876543210",
        )
        val first = WbiSigner.sign(linkedMapOf("mid" to 42, "keyword" to "a!b(c)"), keys, 100L)
        val second = WbiSigner.sign(linkedMapOf("keyword" to "a!b(c)", "mid" to 42), keys, 100L)

        assertEquals(first, second)
        assertTrue(first.contains("keyword=abc"))
        assertTrue(first.substringAfter("w_rid=").matches(Regex("[0-9a-f]{32}")))
    }
}
