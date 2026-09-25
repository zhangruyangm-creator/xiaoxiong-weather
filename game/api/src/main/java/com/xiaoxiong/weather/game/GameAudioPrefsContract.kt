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

    /**
     * 进程内已读到的设置。
     *
     * 音效播放是高频路径（每次 cue 都读一次），而 [read] 每次都要走
     * `getSharedPreferences` + `contains`，在主线程上是 SharedPreferencesImpl 的
     * 锁竞争。缓存放在契约层而不是 `GameAudio`，是因为设置页
     * （`feature/settings` 的 `GameAudioPrefs`）也直接经契约写同一个文件；
     * 放这里则两侧天然一致，不会出现「设置页改了、音效仍按旧值播」的失同步。
     */
    @Volatile
    private var cached: GameAudioSettings? = null

    /** 未读取到偏好时的快照（enabled=true、默认音量），供设置页预览 / 初始状态使用。 */
    val default: GameAudioSettings = GameAudioSettings(enabled = true, volume = DEFAULT_VOLUME)

    /** 把任意音量收敛到 [MIN_VOLUME]..[MAX_VOLUME]，NaN 视为 0。 */
    fun coerceVolume(volume: Float): Float = if (volume.isNaN()) MIN_VOLUME else volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /**
     * 读取全局设置。
     *
     * 首次读取会触发一次旧键迁移（见 [migrateLegacyEnabledIfNeeded]）与 prefs 文件加载，
     * 仍应放在 IO 线程；之后命中进程内缓存，纯内存读，任意线程都安全。
     */
    fun read(context: Context): GameAudioSettings = cachedOrLoad { readFromPrefs(context) }

    /** 缓存优先读取；[loader] 只在未命中时执行一次。抽成注入式以便无 Robolectric 单测。 */
    internal fun cachedOrLoad(loader: () -> GameAudioSettings): GameAudioSettings {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val loaded = loader()
            cached = loaded
            return loaded
        }
    }

    /**
     * 写入成功后同步缓存。
     *
     * 缓存为空时保持为空：此时我们并不知道另一项（如 `enabled`）的真实值，
     * 用默认值补一个组合反而会覆盖掉用户此前的选择。
     */
    internal fun updateCached(update: (GameAudioSettings) -> GameAudioSettings) {
        synchronized(lock) { cached = cached?.let(update) }
    }

    /** 丢弃进程内缓存；仅供单测在注入不同 prefs 后复位。 */
    internal fun resetCache() {
        synchronized(lock) { cached = null }
    }

    private fun readFromPrefs(context: Context): GameAudioSettings {
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
        // apply() 同步改了内存副本，缓存跟着走
        updateCached { it.copy(enabled = enabled) }
    }

    /** 仅由用户操作触发写入。 */
    fun setVolume(
        context: Context,
        volume: Float,
    ) {
        val coerced = coerceVolume(volume)
        runCatching { prefs(context).edit().putFloat(KEY_VOLUME, coerced).apply() }
        updateCached { it.copy(volume = coerced) }
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
