package com.lonnnnnng.biucar.data.bilibili

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class WbiKeys(val imgKey: String, val subKey: String)

internal object WbiSigner {
    private val mixinKeyEncTable = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )
    private val filteredCharacters = Regex("[!'()*]")

    fun sign(parameters: Map<String, Any?>, keys: WbiKeys, timestampSeconds: Long): String {
        val normalized = buildMap {
            parameters.forEach { (key, value) -> if (value != null) put(key, value.toString()) }
            put("wts", timestampSeconds.toString())
        }
        val encoded = normalized.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value.replace(filteredCharacters, ""))}"
        }
        val source = keys.imgKey + keys.subKey
        require(source.length >= 64) { "WBI key 无效" }
        val mixinKey = mixinKeyEncTable.map(source::get).joinToString("").take(32)
        val signature = MessageDigest.getInstance("MD5")
            .digest((encoded + mixinKey).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "$encoded&w_rid=$signature"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .replace("%7E", "~")
}
