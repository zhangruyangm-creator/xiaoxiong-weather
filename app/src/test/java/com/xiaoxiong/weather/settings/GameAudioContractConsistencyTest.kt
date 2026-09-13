package com.xiaoxiong.weather.settings

import android.content.Context
import android.content.SharedPreferences
import com.xiaoxiong.weather.ui.settings.GameAudioPrefs
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.Mockito
import java.lang.reflect.InvocationTargetException

/**
 * 跨模块契约一致性：`feature:settings` 的 [GameAudioPrefs] 与 `:game:common` 的
 * `GameAudio` 各自声明同一份 `game_audio` 契约（lite 版设置页不依赖游戏实现模块）。
 * 本测试在 `:app` 同时依赖两者，用 SharedPreferences 环境断言键名、默认值/范围、
 * coerce 行为与旧键迁移结果完全一致；lite flavor classpath 无 `GameAudio` 时跳过。
 *
 * `GameAudio` 只经反射访问（测试源集需在 full/lite 两个 flavor 下都能编译），
 * 其私有旧键常量也按同名字段反射比对。
 */
class GameAudioContractConsistencyTest {
    private val gameAudioClass: Class<*>? =
        runCatching { Class.forName(GAME_AUDIO_CLASS) }.getOrNull()

    private fun assumeGameAudio() {
        assumeTrue(":game:common 不在 classpath（lite flavor），跳过跨模块契约断言", gameAudioClass != null)
    }

    @Test
    fun keysAndVolumeRange_matchGameAudio() {
        assumeGameAudio()
        val clazz = requireNotNull(gameAudioClass)
        assertEquals(staticField(clazz, "PREF_FILE"), GameAudioPrefs.PREF_FILE)
        assertEquals(staticField(clazz, "KEY_ENABLED"), GameAudioPrefs.KEY_ENABLED)
        assertEquals(staticField(clazz, "KEY_VOLUME"), GameAudioPrefs.KEY_VOLUME)
        assertEquals(staticField(clazz, "DEFAULT_VOLUME"), GameAudioPrefs.DEFAULT_VOLUME)
        assertEquals(staticField(clazz, "MIN_VOLUME"), GameAudioPrefs.MIN_VOLUME)
        assertEquals(staticField(clazz, "MAX_VOLUME"), GameAudioPrefs.MAX_VOLUME)
        // 旧键迁移契约：prefs 文件名与两个旧开关键也必须一致，否则迁移行为会分叉
        assertEquals(
            staticField(clazz, "LEGACY_PREF_FILE"),
            staticField(GameAudioPrefs::class.java, "LEGACY_PREF_FILE"),
        )
        assertEquals(
            staticField(clazz, "LEGACY_KEY_HOCKEY_SOUND"),
            staticField(GameAudioPrefs::class.java, "LEGACY_KEY_HOCKEY_SOUND"),
        )
        assertEquals(
            staticField(clazz, "LEGACY_KEY_POOL_SOUND"),
            staticField(GameAudioPrefs::class.java, "LEGACY_KEY_POOL_SOUND"),
        )
    }

    @Test
    fun coerceVolume_matchesGameAudio() {
        assumeGameAudio()
        val clazz = requireNotNull(gameAudioClass)
        val instance = clazz.getField("INSTANCE").get(null)
        val method = clazz.getMethod("coerceVolume", Float::class.javaPrimitiveType)
        listOf(-1f, 0f, 0.3f, 0.42f, 0.6f, 1f, Float.NaN).forEach { volume ->
            assertEquals(
                "coerceVolume($volume)",
                method.invoke(instance, volume) as Float,
                GameAudioPrefs.coerceVolume(volume),
                0f,
            )
        }
    }

    @Test
    fun defaultAndNoLegacyMigration_matchGameAudio() {
        assumeGameAudio()
        // 无任何旧键：两侧都应迁移出 enabled=true 并写回全局键（迁移只做一次）
        assertMigrationParity(expectedEnabled = true)
    }

    @Test
    fun legacyHockeyMigration_matchGameAudio() {
        assumeGameAudio()
        assertMigrationParity(expectedEnabled = false, legacy = mapOf("hockey_sound" to false))
        assertMigrationParity(expectedEnabled = true, legacy = mapOf("hockey_sound" to true))
    }

    @Test
    fun legacyPoolMigration_matchGameAudio() {
        assumeGameAudio()
        assertMigrationParity(expectedEnabled = false, legacy = mapOf("pool_sound" to false))
        assertMigrationParity(expectedEnabled = true, legacy = mapOf("pool_sound" to true))
    }

    @Test
    fun legacyHockeyWins_overPool_matchGameAudio() {
        assumeGameAudio()
        assertMigrationParity(
            expectedEnabled = true,
            legacy = mapOf("hockey_sound" to true, "pool_sound" to false),
        )
        assertMigrationParity(
            expectedEnabled = false,
            legacy = mapOf("hockey_sound" to false, "pool_sound" to true),
        )
    }

    @Test
    fun existingGlobalEnabled_skipsLegacyMigration_matchGameAudio() {
        assumeGameAudio()
        assertMigrationParity(
            expectedEnabled = false,
            global = mapOf("enabled" to false),
            legacy = mapOf("hockey_sound" to true),
        )
    }

    @Test
    fun volumeClamp_matchesGameAudio() {
        assumeGameAudio()
        assertVolumeParity(0.2f, expected = 0.2f)
        assertVolumeParity(0.42f, expected = 0.42f)
        assertVolumeParity(0.9f, expected = GameAudioPrefs.MAX_VOLUME)
        assertVolumeParity(-1f, expected = GameAudioPrefs.MIN_VOLUME)
        assertVolumeParity(Float.NaN, expected = GameAudioPrefs.MIN_VOLUME)
    }

    private fun assertVolumeParity(
        stored: Float,
        expected: Float,
    ) {
        val (prefs, audio) = readBoth(global = mapOf("volume" to stored))
        assertEquals(expected, prefs.volume, 0f)
        assertEquals(expected, audio.volume, 0f)
    }

    /**
     * 同一份 prefs 播种分别喂给 [GameAudioPrefs.read] 与 `GameAudio.settings`：
     * 断言 enabled / 迁移写回 / 默认音量三处一致，且符合显式期望。
     */
    private fun assertMigrationParity(
        expectedEnabled: Boolean,
        global: Map<String, Any> = emptyMap(),
        legacy: Map<String, Any> = emptyMap(),
    ) {
        val prefs = readViaSettingsPrefs(global, legacy)
        val audio = readViaGameAudio(global, legacy)
        assertEquals("enabled (GameAudioPrefs)", expectedEnabled, prefs.enabled)
        assertEquals("enabled (GameAudio)", expectedEnabled, audio.enabled)
        assertEquals("default volume (GameAudioPrefs)", GameAudioPrefs.DEFAULT_VOLUME, prefs.volume, 0f)
        assertEquals("default volume (GameAudio)", GameAudioPrefs.DEFAULT_VOLUME, audio.volume, 0f)
        // 迁移写回：无论是否命中旧键都要落全局 enabled，避免设置页拨动开关把迁移写死
        assertEquals("migrated write-back (GameAudioPrefs)", expectedEnabled, prefs.globalEnabledWritten)
        assertEquals("migrated write-back (GameAudio)", expectedEnabled, audio.globalEnabledWritten)
    }

    private fun readBoth(
        global: Map<String, Any> = emptyMap(),
        legacy: Map<String, Any> = emptyMap(),
    ): Pair<Snapshot, Snapshot> = readViaSettingsPrefs(global, legacy) to readViaGameAudio(global, legacy)

    private fun readViaSettingsPrefs(
        global: Map<String, Any>,
        legacy: Map<String, Any>,
    ): Snapshot {
        val env = Environment(global, legacy)
        val state = GameAudioPrefs.read(env.context)
        return Snapshot(
            enabled = state.enabled,
            volume = state.volume,
            globalEnabledWritten = env.globalEnabledOrNull(),
        )
    }

    private fun readViaGameAudio(
        global: Map<String, Any>,
        legacy: Map<String, Any>,
    ): Snapshot {
        val env = Environment(global, legacy)
        val clazz = requireNotNull(gameAudioClass)
        val instance = clazz.getField("INSTANCE").get(null)
        val settings =
            try {
                clazz.getMethod("settings", Context::class.java).invoke(instance, env.context)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        return Snapshot(
            enabled = settings.javaClass.getMethod("getEnabled").invoke(settings) as Boolean,
            volume = settings.javaClass.getMethod("getVolume").invoke(settings) as Float,
            globalEnabledWritten = env.globalEnabledOrNull(),
        )
    }

    private fun staticField(
        clazz: Class<*>,
        name: String,
    ): Any? = clazz.getDeclaredField(name).apply { isAccessible = true }.get(null)

    private data class Snapshot(
        val enabled: Boolean,
        val volume: Float,
        val globalEnabledWritten: Boolean?,
    )

    private companion object {
        const val GAME_AUDIO_CLASS = "com.xiaoxiong.weather.ui.game.GameAudio"
    }

    /** 内存版 SharedPreferences 环境：读写不依赖 Android framework/Robolectric。 */
    private class Environment(
        global: Map<String, Any>,
        legacy: Map<String, Any>,
    ) {
        private val files = mutableMapOf<String, FakeSharedPreferences>()

        val context: Context

        init {
            files[GameAudioPrefs.PREF_FILE] = seeded(global)
            files[LEGACY_PREF_FILE] = seeded(legacy)
            val ctx = Mockito.mock(Context::class.java)
            Mockito.`when`(ctx.applicationContext).thenReturn(ctx)
            Mockito
                .`when`(ctx.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer { invocation ->
                    val name: String = invocation.getArgument(0)
                    files.getOrPut(name) { FakeSharedPreferences() }
                }
            context = ctx
        }

        fun globalEnabledOrNull(): Boolean? =
            files[GameAudioPrefs.PREF_FILE]
                ?.takeIf { it.contains(GameAudioPrefs.KEY_ENABLED) }
                ?.getBoolean(GameAudioPrefs.KEY_ENABLED, true)

        private fun seeded(values: Map<String, Any>): FakeSharedPreferences =
            FakeSharedPreferences().apply {
                values.forEach { (key, value) -> put(key, value) }
            }

        private companion object {
            const val LEGACY_PREF_FILE = "bear_game"
        }
    }
}

/**
 * 测试用最小 SharedPreferences 实现：常量 seeded 值 + `edit().apply()` 写回，
 * 其余监听/字符串集合接口按空实现即可（生产代码只用 Boolean/Float）。
 */
internal class FakeSharedPreferences : SharedPreferences {
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
