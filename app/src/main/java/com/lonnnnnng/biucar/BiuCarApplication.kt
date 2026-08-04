package com.lonnnnnng.biucar

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.lonnnnnng.biucar.data.auth.CredentialStore
import com.lonnnnnng.biucar.data.auth.TvQrLoginApi
import com.lonnnnnng.biucar.data.bilibili.BilibiliInterceptor
import com.lonnnnnng.biucar.data.bilibili.BilibiliRepository
import com.lonnnnnng.biucar.data.local.CarDatabase
import com.lonnnnnng.biucar.data.local.CreatorSelectionRepository
import com.lonnnnnng.biucar.data.local.PlaybackHistoryRepository
import com.lonnnnnng.biucar.playback.OfflineAudioCache
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class BiuCarApplication : Application() {
    lateinit var container: CarContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = CarContainer(this)
    }
}

class CarContainer(context: Context) {
    val credentialStore = CredentialStore(context)
    private val dispatcher = Dispatcher().apply {
        // long: 旧车机只允许少量并发请求，避免图片、API 与音频缓存同时占满线程和网络连接。
        maxRequests = 4
        maxRequestsPerHost = 2
    }
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(BilibiliInterceptor(credentialStore))
        .build()
    val loginApi = TvQrLoginApi(httpClient)
    val bilibiliRepository = BilibiliRepository(httpClient)
    private val database = Room.databaseBuilder(context, CarDatabase::class.java, "biucar.db").build()
    val creatorSelectionRepository = CreatorSelectionRepository(database.selectedCreatorDao())
    val playbackHistoryRepository = PlaybackHistoryRepository(database.playbackHistoryDao())
    val offlineAudioCache = OfflineAudioCache(
        context = context,
        client = httpClient,
        historyRepository = playbackHistoryRepository,
    )
}

val Context.carContainer: CarContainer
    get() = (applicationContext as BiuCarApplication).container
