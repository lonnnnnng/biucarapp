package com.lonnnnnng.biucar.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lonnnnnng.biucar.data.model.LoginCredentials
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): LoginCredentials? {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plainText = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            val parts = plainText.split(SEPARATOR, limit = 5)
            require(parts.size == 5)
            LoginCredentials(
                cookieHeader = parts[0],
                accessToken = parts[1],
                refreshToken = parts[2],
                mid = parts[3].toLong(),
                expiresAtEpochSeconds = parts[4].toLong(),
            )
        }.getOrNull()
    }

    @Synchronized
    fun write(credentials: LoginCredentials) {
        val plainText = listOf(
            credentials.cookieHeader,
            credentials.accessToken,
            credentials.refreshToken,
            credentials.mid.toString(),
            credentials.expiresAtEpochSeconds.toString(),
        ).joinToString(SEPARATOR)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // long: Cookie 与 token 必须和随机 IV 一起原子提交，避免车机异常断电后留下无法解密的半份凭据。
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    fun cookieHeader(): String? = read()?.cookieHeader?.takeIf(String::isNotBlank)

    fun csrfToken(): String? = cookieHeader()
        ?.split(';')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("bili_jct=") }
        ?.substringAfter('=')
        ?.takeIf(String::isNotBlank)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "biucar_credentials"
        const val KEY_PAYLOAD = "payload"
        const val KEY_IV = "iv"
        const val KEY_ALIAS = "biucar_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = "\u001f"
    }
}
