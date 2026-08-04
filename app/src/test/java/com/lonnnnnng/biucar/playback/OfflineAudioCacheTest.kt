package com.lonnnnnng.biucar.playback

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflineAudioCacheTest {
    @Test
    fun `copies response within item limit`() {
        val source = ByteArray(32) { it.toByte() }
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(source).copyToWithLimit(output, maxBytes = source.size.toLong(), bufferSize = 7)

        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun `stops before writing bytes beyond item limit`() {
        val output = ByteArrayOutputStream()

        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(10)).copyToWithLimit(output, maxBytes = 6L, bufferSize = 4)
        }
        // long: 超过预算的第二个分块不会写入，临时缓存文件始终保持在上限以内。
        assertEquals(4, output.size())
    }
}
