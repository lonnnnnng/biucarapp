package com.lonnnnnng.biucar.playback

import android.content.Context
import androidx.media3.common.Player

enum class PlaybackOrderMode(
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
) {
    SEQUENTIAL(Player.REPEAT_MODE_OFF, false),
    REPEAT_ONE(Player.REPEAT_MODE_ONE, false),
    REPEAT_ALL(Player.REPEAT_MODE_ALL, false),
    SHUFFLE(Player.REPEAT_MODE_ALL, true),
    ;

    fun next(): PlaybackOrderMode = when (this) {
        SEQUENTIAL -> REPEAT_ONE
        REPEAT_ONE -> REPEAT_ALL
        REPEAT_ALL -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }

    companion object {
        fun fromPlayerState(repeatMode: Int, shuffleEnabled: Boolean): PlaybackOrderMode = when {
            shuffleEnabled -> SHUFFLE
            repeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
            repeatMode == Player.REPEAT_MODE_ALL -> REPEAT_ALL
            else -> SEQUENTIAL
        }

        fun fromStoredValue(value: String?): PlaybackOrderMode = values()
            .firstOrNull { it.name == value }
            ?: SEQUENTIAL
    }
}

class PlaybackOrderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): PlaybackOrderMode = PlaybackOrderMode.fromStoredValue(preferences.getString(KEY_MODE, null))

    fun write(mode: PlaybackOrderMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "biucar_playback"
        const val KEY_MODE = "playback_order"
    }
}
