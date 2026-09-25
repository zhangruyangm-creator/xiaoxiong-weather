package com.xiaoxiong.weather.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 契约层进程内缓存的行为测试（不依赖 Android framework：loader 与 update 均为注入式）。
 *
 * 背景：音效播放是高频路径，每次 cue 都经 [GameAudioPrefsContract.read]；
 * 缓存放在契约层，保证设置页（feature/settings）与游戏侧（GameAudio）
 * 读到的是同一份值，不会出现「设置页改了、音效仍按旧值播」。
 */
class GameAudioPrefsCacheTest {
    @Before
    fun setUp() {
        GameAudioPrefsContract.resetCache()
    }

    private fun settings(
        enabled: Boolean = true,
        volume: Float = GameAudioPrefsContract.DEFAULT_VOLUME,
    ) = GameAudioSettings(enabled = enabled, volume = volume)

    @Test
    fun `first read loads through and caches`() {
        var loads = 0
        val first =
            GameAudioPrefsContract.cachedOrLoad {
                loads++
                settings(volume = 0.42f)
            }
        assertEquals(0.42f, first.volume, 0f)
        assertEquals(1, loads)
    }

    @Test
    fun `second read hits cache without loading again`() {
        var loads = 0
        repeat(3) {
            GameAudioPrefsContract.cachedOrLoad {
                loads++
                settings(volume = 0.42f)
            }
        }
        assertEquals("高频播放路径只应加载一次", 1, loads)
    }

    @Test
    fun `write-through makes change visible to next read`() {
        GameAudioPrefsContract.cachedOrLoad { settings(volume = 0.42f) }
        GameAudioPrefsContract.updateCached { it.copy(volume = 0.1f) }
        var loads = 0
        val after =
            GameAudioPrefsContract.cachedOrLoad {
                loads++
                settings(volume = 0.42f)
            }
        assertEquals("设置页改音量后应立刻生效", 0.1f, after.volume, 0f)
        assertEquals(0, loads)
    }

    @Test
    fun `write-through preserves the other field`() {
        GameAudioPrefsContract.cachedOrLoad { settings(enabled = false, volume = 0.42f) }
        GameAudioPrefsContract.updateCached { it.copy(volume = 0.1f) }
        val after = GameAudioPrefsContract.cachedOrLoad { settings() }
        assertTrue("改音量不该把 enabled 拉回默认", !after.enabled)
        assertEquals(0.1f, after.volume, 0f)
    }

    @Test
    fun `write-through on empty cache stays empty`() {
        GameAudioPrefsContract.updateCached { it.copy(volume = 0.1f) }
        var loads = 0
        val loaded =
            GameAudioPrefsContract.cachedOrLoad {
                loads++
                settings(enabled = false, volume = 0.42f)
            }
        assertEquals("缓存为空时不能伪造默认组合", 0.42f, loaded.volume, 0f)
        assertTrue("enabled 仍应来自真实 prefs", !loaded.enabled)
        assertEquals(1, loads)
    }

    @Test
    fun `resetCache forces reload`() {
        var loads = 0
        GameAudioPrefsContract.cachedOrLoad {
            loads++
            settings(volume = 0.42f)
        }
        GameAudioPrefsContract.resetCache()
        val reloaded =
            GameAudioPrefsContract.cachedOrLoad {
                loads++
                settings(volume = 0.05f)
            }
        assertEquals(2, loads)
        assertEquals(0.05f, reloaded.volume, 0f)
    }
}
