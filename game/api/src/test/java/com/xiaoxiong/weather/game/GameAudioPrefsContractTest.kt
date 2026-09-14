package com.xiaoxiong.weather.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「游戏音效」偏好契约自身的 JVM 测试：用内存 SharedPreferences 验证
 * [GameAudioPrefsContract.coerceVolume] / 默认值 / 旧键迁移与一次性写回。
 *
 * 契约唯一的实现同时服务设置页与游戏侧，因此这里覆盖的迁移语义即两侧共享的行为；
 * 测试走 prefs 重载，不依赖 Android framework / Robolectric。
 */
class GameAudioPrefsContractTest {
    @Test
    fun `coerceVolume clamps out-of-range values`() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefsContract.coerceVolume(-1f), 0f)
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefsContract.coerceVolume(0.61f), 0f)
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefsContract.coerceVolume(1f), 0f)
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefsContract.coerceVolume(GameAudioPrefsContract.MAX_VOLUME), 0f)
        assertEquals(0.42f, GameAudioPrefsContract.coerceVolume(0.42f), 0f)
    }

    @Test
    fun `coerceVolume maps NaN to min`() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefsContract.coerceVolume(Float.NaN), 0f)
    }

    @Test
    fun `default snapshot is enabled at default volume`() {
        assertTrue(GameAudioPrefsContract.default.enabled)
        assertEquals(GameAudioPrefsContract.DEFAULT_VOLUME, GameAudioPrefsContract.default.volume, 0f)
    }

    @Test
    fun `read without legacy keys migrates enabled true and writes it back`() {
        val env = Environment()
        val settings = env.read()
        assertTrue(settings.enabled)
        assertEquals(GameAudioPrefsContract.DEFAULT_VOLUME, settings.volume, 0f)
        assertEquals(true, env.globalEnabledOrNull())
    }

    @Test
    fun `read treats missing legacy prefs as no legacy keys`() {
        val env = Environment(legacy = null)
        assertTrue(env.read().enabled)
        assertEquals(true, env.globalEnabledOrNull())
    }

    @Test
    fun `legacy hockey sound value is migrated`() {
        val off = Environment(legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to false))
        assertFalse(off.read().enabled)
        assertEquals(false, off.globalEnabledOrNull())

        val on = Environment(legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to true))
        assertTrue(on.read().enabled)
        assertEquals(true, on.globalEnabledOrNull())
    }

    @Test
    fun `legacy pool sound value is migrated`() {
        val off = Environment(legacy = mapOf(LEGACY_KEY_POOL_SOUND to false))
        assertFalse(off.read().enabled)
        assertEquals(false, off.globalEnabledOrNull())

        val on = Environment(legacy = mapOf(LEGACY_KEY_POOL_SOUND to true))
        assertTrue(on.read().enabled)
        assertEquals(true, on.globalEnabledOrNull())
    }

    @Test
    fun `hockey sound wins over pool sound`() {
        val offWins =
            Environment(
                legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to false, LEGACY_KEY_POOL_SOUND to true),
            )
        assertFalse(offWins.read().enabled)

        val onWins =
            Environment(
                legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to true, LEGACY_KEY_POOL_SOUND to false),
            )
        assertTrue(onWins.read().enabled)
    }

    @Test
    fun `existing global enabled skips legacy migration`() {
        val env =
            Environment(
                global = mapOf(GameAudioPrefsContract.KEY_ENABLED to false),
                legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to true),
            )
        assertFalse(env.read().enabled)
        assertEquals(false, env.globalEnabledOrNull())
    }

    @Test
    fun `stored volume is read back coerced`() {
        assertEquals(0.2f, Environment(global = mapOf(GameAudioPrefsContract.KEY_VOLUME to 0.2f)).read().volume, 0f)
        assertEquals(0.42f, Environment(global = mapOf(GameAudioPrefsContract.KEY_VOLUME to 0.42f)).read().volume, 0f)
        assertEquals(
            GameAudioPrefsContract.MAX_VOLUME,
            Environment(global = mapOf(GameAudioPrefsContract.KEY_VOLUME to 0.9f)).read().volume,
            0f,
        )
        assertEquals(
            GameAudioPrefsContract.MIN_VOLUME,
            Environment(global = mapOf(GameAudioPrefsContract.KEY_VOLUME to -1f)).read().volume,
            0f,
        )
        assertEquals(
            GameAudioPrefsContract.MIN_VOLUME,
            Environment(global = mapOf(GameAudioPrefsContract.KEY_VOLUME to Float.NaN)).read().volume,
            0f,
        )
    }

    @Test
    fun `migration only happens while global enabled is absent`() {
        val env = Environment(legacy = mapOf(LEGACY_KEY_HOCKEY_SOUND to false))
        assertFalse(env.read().enabled)
        // 迁移后全局键已写入：此后旧键变化不再影响读取
        env.legacyPrefs?.put(LEGACY_KEY_HOCKEY_SOUND, true)
        assertFalse(env.read().enabled)
    }

    private companion object {
        const val LEGACY_KEY_HOCKEY_SOUND = "hockey_sound"
        const val LEGACY_KEY_POOL_SOUND = "pool_sound"
    }

    /** 内存 SharedPreferences 环境：分别模拟全局 `game_audio` 与旧 `bear_game`。 */
    private class Environment(
        global: Map<String, Any> = emptyMap(),
        legacy: Map<String, Any>? = emptyMap(),
    ) {
        val globalPrefs: FakeSharedPreferences = seeded(global)
        val legacyPrefs: FakeSharedPreferences? = legacy?.let { seeded(it) }

        fun read(): GameAudioSettings = GameAudioPrefsContract.read(globalPrefs, legacyPrefs)

        fun globalEnabledOrNull(): Boolean? =
            globalPrefs
                .takeIf { it.contains(GameAudioPrefsContract.KEY_ENABLED) }
                ?.getBoolean(GameAudioPrefsContract.KEY_ENABLED, true)

        private companion object {
            fun seeded(values: Map<String, Any>): FakeSharedPreferences =
                FakeSharedPreferences().apply {
                    values.forEach { (key, value) -> put(key, value) }
                }
        }
    }
}

/**
 * 测试用最小 SharedPreferences 实现：常量 seeded 值 + `edit().apply()` 写回，
 * 其余监听/字符串集合接口按空实现即可（生产代码只用 Boolean/Float）。
 */
private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    fun put(
        key: String,
        value: Any?,
    ) {
        values[key] = value
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = stage(key, value)

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = stage(key, values)

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = stage(key, value)

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = stage(key, value)

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = stage(key, value)

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = stage(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            pending[key] = REMOVED
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value === REMOVED) values.remove(key) else values[key] = value
            }
        }

        private fun stage(
            key: String,
            value: Any?,
        ): SharedPreferences.Editor {
            pending[key] = value
            return this
        }
    }

    private companion object {
        val REMOVED = Any()
    }
}
