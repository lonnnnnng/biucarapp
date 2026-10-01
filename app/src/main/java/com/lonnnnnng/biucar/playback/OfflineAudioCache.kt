package com.lonnnnnng.biucar.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import com.lonnnnnng.biucar.data.local.AudioCacheState
import com.lonnnnnng.biucar.data.local.PlaybackHistoryRepository
import com.lonnnnnng.biucar.data.model.EXTRA_DURATION_MS
import com.lonnnnnng.biucar.data.model.EXTRA_STREAM_URL
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OfflineAudioCache(
    context: Context,
    private val client: OkHttpClient,
    private val historyRepository: PlaybackHistoryRepository,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxItemBytes: Long = minOf(DEFAULT_MAX_ITEM_BYTES, maxBytes),
) {
    private val directory = File(context.filesDir, "offline-audio").apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun cache(mediaItem: MediaItem): Unit = mutex.withLock {
        val mediaId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return
        val existing = historyRepository.find(mediaId)
        val existingFile = existing?.localFilePath?.let(::File)
        if (existing?.cacheState == AudioCacheState.READY.name && existingFile?.isFile == true) return
        val durationMs = mediaItem.mediaMetadata.extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L
        if (durationMs > MAX_CACHE_DURATION_MS) {
            // long: 数小时合集即使能在线播放，也很容易超过离线单项磁盘预算；直接保留“仅在线”状态，避免每次播放都重复下载后失败。
            historyRepository.clearCache(mediaId)
            return
        }
        val streamUrl = mediaItem.mediaMetadata.extras?.getString(EXTRA_STREAM_URL)
            ?.takeIf(String::isNotBlank) ?: return
        if (mediaItem.localConfiguration?.uri?.scheme == "file") return
        val finalFile = File(directory, "${mediaId.replace(':', '_')}.m4a")
        val tempFile = File(directory, "${finalFile.name}.part")
        tempFile.delete()
        historyRepository.markCaching(mediaId)
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(streamUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("音频缓存 HTTP ${response.code}")
                    val body = response.body ?: throw IOException("音频缓存响应为空")
                    if (body.contentLength() > maxItemBytes) throw IOException("单条音频超过离线缓存上限")
                    FileOutputStream(tempFile).use { output ->
                        body.byteStream().use { input -> input.copyToWithLimit(output, maxItemBytes) }
                    }
                }
                if (tempFile.length() <= 0L) throw IOException("音频缓存写入失败")
                if (finalFile.exists() && !finalFile.delete()) throw IOException("旧缓存无法替换")
                if (!tempFile.renameTo(finalFile)) throw IOException("音频缓存写入失败")
            }
            historyRepository.markReady(mediaId, finalFile.absolutePath)
            trimToSize(mediaId)
        } catch (error: CancellationException) {
            tempFile.delete()
            // long: 取消下载时仍要把 CACHING 状态收敛为失败，否则历史页会永久显示转圈且下次无法判断是否应重试。
            withContext(NonCancellable) { historyRepository.markFailed(mediaId) }
            throw error
        } catch (_: Exception) {
            tempFile.delete()
            // long: 缓存失败只更新离线状态，在线 Media3 播放继续使用原始 DASH 地址。
            historyRepository.markFailed(mediaId)
        }
    }

    fun localUri(path: String?): Uri? = path?.let(::File)?.takeIf(File::isFile)?.let(Uri::fromFile)

    private suspend fun trimToSize(currentMediaId: String) {
        directory.listFiles { file -> file.name.endsWith(".part") }?.forEach(File::delete)
        val readyItems = historyRepository.readyCaches()
        val referencedPaths = readyItems.mapNotNull { it.localFilePath }.toSet()
        // long: Room 记录可能因异常退出而落后于文件目录，先清理没有对应历史记录的孤儿缓存，避免容量上限失效。
        directory.listFiles { file ->
            file.isFile && file.extension == "m4a" && file.absolutePath !in referencedPaths &&
                file.name != "${currentMediaId.replace(':', '_')}.m4a"
        }?.forEach(File::delete)
        var total = directory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
        if (total <= maxBytes) return
        readyItems.forEach { item ->
            if (total <= maxBytes) return
            if (item.mediaId == currentMediaId) return@forEach
            val file = item.localFilePath?.let(::File) ?: return@forEach
            val size = file.length()
            if (file.delete()) {
                total -= size
                historyRepository.clearCache(item.mediaId)
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_MAX_ITEM_BYTES = 256L * 1024L * 1024L
        const val MAX_CACHE_DURATION_MS = 4L * 60L * 60L * 1_000L
    }
}

internal fun InputStream.copyToWithLimit(output: OutputStream, maxBytes: Long, bufferSize: Int = 64 * 1024): Long {
    require(maxBytes >= 0L && bufferSize > 0)
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return copied
        if (copied + read > maxBytes) {
            // long: 在写入超限数据前终止，保证未知 Content-Length 的 CDN 响应也不会突破单项磁盘预算。
            throw IOException("单条音频超过离线缓存上限")
        }
        output.write(buffer, 0, read)
        copied += read
    }
}
