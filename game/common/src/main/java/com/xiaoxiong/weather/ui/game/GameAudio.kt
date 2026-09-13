package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.xiaoxiong.weather.R

/**
 * 全局游戏音效 cue。每个 cue 对应一个程序合成的 WAV（`res/raw/sfx_*.wav`）。
 *
 * @property rawRes 音频资源
 * @property gain 单音效相对音量（0.5~1.0）；高频 / 频繁触发的音效取值低一些
 */
enum class GameCue(
    @get:RawRes val rawRes: Int,
    val gain: Float,
) {
    UI_CLICK(R.raw.sfx_ui_click, 0.5f),
    JUMP(R.raw.sfx_jump, 0.6f),
    COIN(R.raw.sfx_coin, 0.7f),
    STAR(R.raw.sfx_star, 0.7f),
    CATCH(R.raw.sfx_catch, 0.7f),
    BLOCK(R.raw.sfx_block, 0.7f),
    HIT(R.raw.sfx_hit, 0.6f),
    BUMP(R.raw.sfx_bump, 0.55f),
    POT(R.raw.sfx_pot, 0.8f),
    CUE(R.raw.sfx_cue, 0.5f),
    RAIL(R.raw.sfx_rail, 0.55f),
    KEY(R.raw.sfx_key, 0.75f),
    PORTAL(R.raw.sfx_portal, 0.7f),
    GOAL(R.raw.sfx_goal, 0.9f),
    FOUL(R.raw.sfx_foul, 0.8f),
    WIN(R.raw.sfx_win, 0.85f),
    LOSE(R.raw.sfx_lose, 0.8f),
    WHISTLE(R.raw.sfx_whistle, 0.7f),

    // 小熊战机（竖屏弹幕射击）
    SHOT(R.raw.sfx_shot, 0.40f),
    LASER(R.raw.sfx_laser, 0.45f),
    EXPLOSION(R.raw.sfx_explosion, 0.70f),
    POWERUP(R.raw.sfx_powerup, 0.65f),
    BOMB(R.raw.sfx_bomb, 0.80f),
    WARNING(R.raw.sfx_warning, 0.60f),
}

/** 全局游戏音效设置快照。 */
data class GameAudioSettings(
    val enabled: Boolean,
    val volume: Float,
)

/**
 * 全局游戏音效管理器：统一开关 + 主音量，全部程序合成、不依赖音乐文件。
 *
 * 与动画设置无关（不读取 reduceMotion）；所有公开方法都不抛异常，
 * 关闭、样本未就绪或加载失败时静默返回。
 *
 * 游戏入口建议调用一次 [preload]（降低首次播放延迟），随后事件处直接 [play]：
 * ```
 * GameAudio.preload(context)
 * GameAudio.play(GameCue.JUMP)
 * ```
 * 未预加载时可改用带 context 的重载 [play]，其会记录 applicationContext。
 */
object GameAudio {
    /** 全局设置 SharedPreferences 文件名。 */
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

    private const val MAX_STREAMS = 6

    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var soundPool: SoundPool? = null

    private val sampleIds = HashMap<GameCue, Int>()

    /** 把任意音量收敛到 [MIN_VOLUME]..[MAX_VOLUME]，NaN 视为 0。 */
    fun coerceVolume(volume: Float): Float = if (volume.isNaN()) MIN_VOLUME else volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /**
     * 读取全局设置；仅首次读取时触发一次旧键迁移（见 [migrateLegacyEnabledIfNeeded]），
     * 之后 [settings] 只做内存键值读取，不再触碰 `bear_game`、不写盘。
     */
    fun settings(context: Context): GameAudioSettings {
        val prefs = prefs(context)
        migrateLegacyEnabledIfNeeded(context, prefs)
        return GameAudioSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            volume = coerceVolume(prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)),
        )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        runCatching { prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply() }
    }

    fun setVolume(
        context: Context,
        volume: Float,
    ) {
        runCatching { prefs(context).edit().putFloat(KEY_VOLUME, coerceVolume(volume)).apply() }
    }

    /** 预加载全部样本（游戏入口调用可消除首次播放的加载延迟）；幂等、线程安全。 */
    fun preload(context: Context) {
        runCatching {
            val app = context.applicationContext
            synchronized(lock) {
                appContext = app
                val pool = ensurePoolLocked(app) ?: return
                GameCue.entries.forEach { cue ->
                    if (sampleIds[cue] == null) loadCueLocked(app, pool, cue)
                }
            }
        }
    }

    /** 无 context 的播放入口：需先 [preload]（或调用过带 context 的重载）。 */
    fun play(cue: GameCue) {
        runCatching {
            val app = appContext ?: return
            playWithContext(app, cue)
        }
    }

    /** 带 context 的播放入口：内部记录 applicationContext，适合未显式预加载的场景。 */
    fun play(
        context: Context,
        cue: GameCue,
    ) {
        runCatching {
            val app = context.applicationContext
            appContext = app
            playWithContext(app, cue)
        }
    }

    private fun playWithContext(
        app: Context,
        cue: GameCue,
    ) {
        val current = settings(app)
        if (!current.enabled) return
        val pool = synchronized(lock) { ensurePoolLocked(app) } ?: return
        // 首次触发时懒加载：本次静默，样本就绪后自动生效
        val sampleId =
            synchronized(lock) {
                sampleIds[cue] ?: loadCueLocked(app, pool, cue)
            } ?: return
        val volume = (current.volume * cue.gain).coerceIn(0f, 1f)
        pool.play(sampleId, volume, volume, 1, 0, 1f)
    }

    private fun ensurePoolLocked(app: Context): SoundPool? {
        soundPool?.let { return it }
        val pool =
            runCatching {
                SoundPool
                    .Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).build()
            }.getOrNull() ?: return null
        soundPool = pool
        return pool
    }

    private fun loadCueLocked(
        app: Context,
        pool: SoundPool,
        cue: GameCue,
    ): Int? =
        runCatching { pool.load(app, cue.rawRes, 1) }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?.also { sampleIds[cue] = it }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /**
     * 迁移：全局 enabled 尚未写入时，若旧游戏开关存在，则用其值初始化全局键，否则写入默认 true。
     * 无论是否有旧键都落一次 KEY_ENABLED，保证后续 [settings] 不再读取 `bear_game`（含农场存档等）。
     * 同时存在时以 hockey_sound 为准；旧键清理由各游戏接线时处理。
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
                        // 无旧键写入默认值，迁移同样只做一次
                        else -> true
                    }
                }.getOrDefault(true)
            runCatching { prefs.edit().putBoolean(KEY_ENABLED, migrated).apply() }
        }
    }
}
