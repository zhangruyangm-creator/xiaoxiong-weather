package com.xiaoxiong.weather.ui.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置页对「游戏音效」偏好的直接读写。
 *
 * 键名 / 取值范围与 `game/common` 的 `GameAudio` 保持一致（当前仅设置开关与主音量，
 * 不提供试听，因此 `feature:settings` 不再依赖 `:game:common`；lite 版不打包音效资源）。
 * 两侧各自声明同一份契约，修改任一侧时必须同步 GameAudio.kt 中的常量、coerceVolume
 * 与旧键迁移逻辑，跨模块一致性由 `:app` 的 `GameAudioContractConsistencyTest` 钉死。
 */
object GameAudioPrefs {
    const val PREF_FILE = "game_audio"
    const val KEY_ENABLED = "enabled"
    const val KEY_VOLUME = "volume"

    const val DEFAULT_VOLUME = 0.3f
    const val MIN_VOLUME = 0f
    const val MAX_VOLUME = 0.6f

    /** 旧版单游戏音效开关所在的 prefs（迁移用，读取后不改写、不删除）。 */
    private const val LEGACY_PREF_FILE = "bear_game"
    private const val LEGACY_KEY_HOCKEY_SOUND = "hockey_sound"
    private const val LEGACY_KEY_POOL_SOUND = "pool_sound"

    private val lock = Any()

    /** 未读取到偏好时的快照（enabled=true、默认音量）。 */
    val default: GameAudioState = GameAudioState(enabled = true, volume = DEFAULT_VOLUME)

    /** 把任意音量收敛到 [MIN_VOLUME]..[MAX_VOLUME]，NaN 视为 0。 */
    fun coerceVolume(volume: Float): Float = if (volume.isNaN()) MIN_VOLUME else volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /** 读取偏好；请在 IO 线程调用（首次会触发 prefs 文件加载与一次旧键迁移写盘）。 */
    fun read(context: Context): GameAudioState {
        val prefs = prefs(context)
        migrateLegacyEnabledIfNeeded(context, prefs)
        return GameAudioState(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            volume = coerceVolume(prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)),
        )
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
     * 与 `game/common` 的 `GameAudio.migrateLegacyEnabledIfNeeded` 同款迁移：
     * 全局 enabled 尚未写入时，若旧游戏开关存在则用其值初始化（hockey_sound 优先），
     * 否则写入默认 true。无论是否有旧键都落一次 KEY_ENABLED，避免设置页先于游戏读值时
     * 显示默认「开」、随后用户拨动开关把迁移永久写错（旧版升级用户的开关状态丢失）。
     */
    private fun migrateLegacyEnabledIfNeeded(
        context: Context,
        prefs: SharedPreferences,
    ) {
        if (prefs.contains(KEY_ENABLED)) return
        synchronized(lock) {
            if (prefs.contains(KEY_ENABLED)) return
            val migrated =
                runCatching {
                    val legacy =
                        context.applicationContext
                            .getSharedPreferences(LEGACY_PREF_FILE, Context.MODE_PRIVATE)
                    when {
                        legacy.contains(LEGACY_KEY_HOCKEY_SOUND) ->
                            legacy.getBoolean(LEGACY_KEY_HOCKEY_SOUND, true)
                        legacy.contains(LEGACY_KEY_POOL_SOUND) ->
                            legacy.getBoolean(LEGACY_KEY_POOL_SOUND, true)
                        else -> true
                    }
                }.getOrDefault(true)
            runCatching { prefs.edit().putBoolean(KEY_ENABLED, migrated).apply() }
        }
    }
}

/** 游戏音效设置快照（与 game/common 的 `GameAudioSettings` 同构）。 */
data class GameAudioState(
    val enabled: Boolean,
    val volume: Float,
)
