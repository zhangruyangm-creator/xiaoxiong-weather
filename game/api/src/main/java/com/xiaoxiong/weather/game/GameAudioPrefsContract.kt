package com.xiaoxiong.weather.game

import android.content.Context
import android.content.SharedPreferences

/** 全局游戏音效设置快照（enabled + 主音量）。 */
data class GameAudioSettings(
    val enabled: Boolean,
    val volume: Float,
)

/**
 * 「游戏音效」偏好契约：文件名 / 键名 / 音量范围 / 默认值 / 旧键迁移的唯一声明处。
 *
 * 设置页（`feature:settings` 的 `GameAudioPrefs`）与游戏实现侧
 * （`game:common` 的 `GameAudio`）都复用本契约读写同一份 prefs，
 * 避免两侧各自维护一份实现后漂移；lite 版只保留本契约层字节码，不含音效资源。
 */
object GameAudioPrefsContract {
    /** 全局游戏音效 SharedPreferences 文件名。 */
    const val PREF_FILE = "game_audio"

    const val KEY_ENABLED = "enabled"
    const val KEY_VOLUME = "volume"

    /** 默认主音量：用户要求「声音不要太大」。 */
    const val DEFAULT_VOLUME = 0.3f
    const val MIN_VOLUME = 0f
    const val MAX_VOLUME = 0.6f

    /** 旧版单游戏音效开关所在的 prefs（迁移用，读取后不改写、不删除）。 */
    private const val LEGACY_PREF_FILE = "bear_game"
    private const val LEGACY_KEY_HOCKEY_SOUND = "hockey_sound"
    private const val LEGACY_KEY_POOL_SOUND = "pool_sound"

    private val lock = Any()

    /** 未读取到偏好时的快照（enabled=true、默认音量），供设置页预览 / 初始状态使用。 */
    val default: GameAudioSettings = GameAudioSettings(enabled = true, volume = DEFAULT_VOLUME)

    /** 把任意音量收敛到 [MIN_VOLUME]..[MAX_VOLUME]，NaN 视为 0。 */
    fun coerceVolume(volume: Float): Float = if (volume.isNaN()) MIN_VOLUME else volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /**
     * 读取全局设置；仅首次读取时触发一次旧键迁移（见 [migrateLegacyEnabledIfNeeded]）。
     * 请在 IO 线程调用（首次会触发 prefs 文件加载与一次迁移写盘）。
     */
    fun read(context: Context): GameAudioSettings {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        // 全局键已写入后不再触碰旧 prefs（bear_game 含农场存档等）
        val legacyPrefs =
            if (prefs.contains(KEY_ENABLED)) {
                null
            } else {
                runCatching { app.getSharedPreferences(LEGACY_PREF_FILE, Context.MODE_PRIVATE) }.getOrNull()
            }
        return read(prefs, legacyPrefs)
    }

    /** 仅由用户操作触发写入。 */
    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        runCatching { prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    }

    /** 仅由用户操作触发写入。 */
    fun setVolume(
        context: Context,
        volume: Float,
    ) {
        runCatching { prefs(context).edit().putFloat(KEY_VOLUME, coerceVolume(volume)).apply() }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /**
     * 契约核心：不依赖 Android framework 的 prefs 读取，供单测直接注入内存实现。
     * `legacyPrefs` 为 null 表示旧 prefs 不可用（按无旧键处理）。
     */
    internal fun read(
        prefs: SharedPreferences,
        legacyPrefs: SharedPreferences?,
    ): GameAudioSettings {
        migrateLegacyEnabledIfNeeded(prefs, legacyPrefs)
        return GameAudioSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            volume = coerceVolume(prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)),
        )
    }

    /**
     * 迁移：全局 enabled 尚未写入时，若旧游戏开关存在则用其值初始化（hockey_sound 优先），
     * 否则写入默认 true。无论是否有旧键都落一次 KEY_ENABLED，保证后续读取不再触碰
     * `bear_game`（含农场存档等），也避免设置页先于游戏读值时把迁移永久写错。
     */
    private fun migrateLegacyEnabledIfNeeded(
        prefs: SharedPreferences,
        legacyPrefs: SharedPreferences?,
    ) {
        if (prefs.contains(KEY_ENABLED)) return
        synchronized(lock) {
            if (prefs.contains(KEY_ENABLED)) return
            val migrated =
                runCatching {
                    when {
                        legacyPrefs?.contains(LEGACY_KEY_HOCKEY_SOUND) == true ->
                            legacyPrefs.getBoolean(LEGACY_KEY_HOCKEY_SOUND, true)
                        legacyPrefs?.contains(LEGACY_KEY_POOL_SOUND) == true ->
                            legacyPrefs.getBoolean(LEGACY_KEY_POOL_SOUND, true)
                        else -> true
                    }
                }.getOrDefault(true)
            runCatching { prefs.edit().putBoolean(KEY_ENABLED, migrated).apply() }
        }
    }
}
