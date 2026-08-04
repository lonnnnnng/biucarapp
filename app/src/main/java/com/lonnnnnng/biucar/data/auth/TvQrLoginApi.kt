package com.lonnnnnng.biucar.data.auth

import com.lonnnnnng.biucar.data.model.LoginCredentials
import com.lonnnnnng.biucar.data.model.QrChallenge
import com.lonnnnnng.biucar.data.model.QrPollResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TvQrLoginApi(
    private val client: OkHttpClient,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val authCodeUrl: String = AUTH_CODE_URL,
    private val pollUrl: String = POLL_URL,
) {
    suspend fun createChallenge(): QrChallenge = withContext(Dispatchers.IO) {
        val parameters = signedParameters(
            mapOf(
                "local_id" to "0",
                "mobi_app" to "android_tv_yst",
                "ts" to nowEpochSeconds().toString(),
            ),
        )
        val root = post(authCodeUrl, parameters)
        requireSuccess(root)
        val data = root.optJSONObject("data") ?: throw IOException("二维码响应为空")
        QrChallenge(
            url = data.optString("url").takeIf(String::isNotBlank) ?: throw IOException("二维码地址为空"),
            authCode = data.optString("auth_code").takeIf(String::isNotBlank) ?: throw IOException("二维码密钥为空"),
        )
    }

    suspend fun poll(authCode: String): QrPollResult = withContext(Dispatchers.IO) {
        val parameters = signedParameters(
            mapOf(
                "auth_code" to authCode,
                "local_id" to "0",
                "ts" to nowEpochSeconds().toString(),
            ),
        )
        val root = post(pollUrl, parameters)
        when (val code = root.optInt("code", Int.MIN_VALUE)) {
            0 -> parseCredentials(root)
            86039 -> QrPollResult.Waiting
            86090 -> QrPollResult.Scanned
            86038 -> QrPollResult.Expired
            else -> QrPollResult.Error(root.optString("message", "扫码状态异常：$code"))
        }
    }

    private fun parseCredentials(root: JSONObject): QrPollResult {
        val data = root.optJSONObject("data") ?: return QrPollResult.Error("登录凭据为空")
        val tokenInfo = data.optJSONObject("token_info") ?: data
        val cookies = data.optJSONObject("cookie_info")?.optJSONArray("cookies")
        val cookieHeader = buildList {
            if (cookies != null) {
                for (index in 0 until cookies.length()) {
                    val cookie = cookies.optJSONObject(index) ?: continue
                    val name = cookie.optString("name").takeIf(String::isNotBlank) ?: continue
                    val value = cookie.optString("value").takeIf(String::isNotBlank) ?: continue
                    add("$name=$value")
                }
            }
        }.joinToString("; ")
        if (cookieHeader.isBlank()) return QrPollResult.Error("登录成功但未返回 Web Cookie")
        // long: 部分 TV 客户端响应不返回 expires_in；使用保守的 30 天仅作为本地展示值，真实账号态始终由 nav 接口复核。
        val expiresIn = tokenInfo.optLong("expires_in", DEFAULT_EXPIRES_IN_SECONDS)
            .takeIf { it > 0L }
            ?: DEFAULT_EXPIRES_IN_SECONDS
        return QrPollResult.Success(
            LoginCredentials(
                cookieHeader = cookieHeader,
                accessToken = tokenInfo.optString("access_token"),
                refreshToken = tokenInfo.optString("refresh_token"),
                mid = tokenInfo.optLong("mid", data.optLong("mid", 0L)),
                expiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
            ),
        )
    }

    private fun signedParameters(parameters: Map<String, String>): Map<String, String> {
        val withAppKey = parameters + ("appkey" to APP_KEY)
        return withAppKey + ("sign" to AppSigner.sign(withAppKey, APP_SECRET))
    }

    private fun post(url: String, parameters: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { parameters.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url).post(body).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bilibili HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun requireSuccess(root: JSONObject) {
        val code = root.optInt("code", Int.MIN_VALUE)
        if (code != 0) throw IOException(root.optString("message", "Bilibili API $code"))
    }

    private companion object {
        const val AUTH_CODE_URL = "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code"
        const val POLL_URL = "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll"
        // long: TV 扫码接口只接受对应客户端的公开 APPKey 签名；该值来自客户端逆向资料，不是用户凭据，也不能替代服务端秘密。
        const val APP_KEY = "4409e2ce8ffd12b8"
        const val APP_SECRET = "59b43e04ad6965f34319062b478f83dd"
        const val DEFAULT_EXPIRES_IN_SECONDS = 30L * 24L * 60L * 60L
    }
}
