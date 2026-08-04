package com.lonnnnnng.biucar.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOrderStoreTest {
    @Test
    fun `cycles through every playback order and returns to sequential`() {
        var mode = PlaybackOrderMode.SEQUENTIAL

        mode = mode.next()
        assertEquals(PlaybackOrderMode.REPEAT_ONE, mode)
        mode = mode.next()
        assertEquals(PlaybackOrderMode.REPEAT_ALL, mode)
        mode = mode.next()
        assertEquals(PlaybackOrderMode.SHUFFLE, mode)
        mode = mode.next()
        assertEquals(PlaybackOrderMode.SEQUENTIAL, mode)
    }

    @Test
    fun `maps player flags and rejects unknown stored value`() {
        assertEquals(
            PlaybackOrderMode.REPEAT_ONE,
            PlaybackOrderMode.fromPlayerState(Player.REPEAT_MODE_ONE, shuffleEnabled = false),
        )
        assertEquals(
            PlaybackOrderMode.SHUFFLE,
            PlaybackOrderMode.fromPlayerState(Player.REPEAT_MODE_OFF, shuffleEnabled = true),
        )
        // long: 升级或异常写入产生未知值时回退顺序播放，避免车机启动后进入不可理解的控制状态。
        assertEquals(PlaybackOrderMode.SEQUENTIAL, PlaybackOrderMode.fromStoredValue("UNKNOWN"))
    }
}
