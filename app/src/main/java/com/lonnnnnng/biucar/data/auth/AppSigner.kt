package com.lonnnnnng.biucar.data.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object AppSigner {
    fun sign(parameters: Map<String, String>, appSecret: String): String {
        val query = parameters.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return MessageDigest.getInstance("MD5")
            .digest((query + appSecret).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
