package com.lonnnnnng.biucar.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import com.lonnnnnng.biucar.data.local.AudioCacheState
import com.lonnnnnng.biucar.data.local.PlaybackHistoryRepository
import com.lonnnnnng.biucar.data.model.EXTRA_STREAM_URL
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
            historyRepository.markFailed(mediaId)
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
        var total = directory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
        if (total <= maxBytes) return
        historyRepository.readyCaches().forEach { item ->
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
