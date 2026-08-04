package com.lonnnnnng.biucar.data.bilibili

import com.lonnnnnng.biucar.data.auth.CredentialStore
import okhttp3.Interceptor
import okhttp3.Response

class BilibiliInterceptor(
    private val credentialStore: CredentialStore?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        // long: playurl 可能返回第三方 CDN 域名；它们同样要求 Bilibili 的 Referer 和桌面 UA，但绝不能收到账号 Cookie。
        val builder = request.newBuilder()
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", USER_AGENT)
        if (isBilibiliHost(host)) {
            builder.header("Origin", "https://www.bilibili.com")
            // long: 账号 Cookie 只能发给 bilibili.com；第三方媒体 CDN 仅保留 Referer 和 UA，避免凭据外泄。
            credentialStore?.cookieHeader()?.let { builder.header("Cookie", it) }
        }
        return chain.proceed(builder.build())
    }

    private fun isBilibiliHost(host: String): Boolean = OWNED_SUFFIXES.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }

    internal companion object {
        val OWNED_SUFFIXES = setOf("bilibili.com", "bilivideo.com", "bilivideo.cn", "hdslb.com")
        // long: Bilibili 部分 bilivideo CDN 会拒绝旧版或 Mobile UA；网络身份与系统 WebView 解耦，确保 Android 8.1 车机仍能读取 DASH 音频。
        internal const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
    }
}
