package com.xiaoxiong.weather.ui.settings

import com.xiaoxiong.weather.game.GameAudioPrefsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioPrefsTest {
    @Test
    fun coerceVolume_clampsToRange() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefs.coerceVolume(-1f), 0f)
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefs.coerceVolume(1f), 0f)
        assertEquals(0.42f, GameAudioPrefs.coerceVolume(0.42f), 0f)
    }

    @Test
    fun coerceVolume_nanBecomesMin() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefs.coerceVolume(Float.NaN), 0f)
    }

    @Test
    fun default_isEnabledAtDefaultVolume() {
        assertTrue(GameAudioPrefs.default.enabled)
        assertEquals(GameAudioPrefsContract.DEFAULT_VOLUME, GameAudioPrefs.default.volume, 0f)
    }
}
