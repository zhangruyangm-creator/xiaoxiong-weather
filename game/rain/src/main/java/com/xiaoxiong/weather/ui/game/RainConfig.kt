package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import com.xiaoxiong.weather.game.GameAudioPrefsContract
import kotlin.random.Random

/*
 * 小熊雨幕 · 配置与持久化。
 *
 * 单独成文件的原因：这些是「跨对局的静态契约」（键名、种子、快照格式），
 * 与玩法逻辑（RainFall）和界面（Screen）都无关，混在一起会让两边都变难读。
 */

/** 排行榜表键。 */
const val RAIN_LEADERBOARD_KEY = "rain_lb"

/** 最高分 prefs 键。 */
const val RAIN_BEST_KEY = "rain_best"

/** 对局快照在 prefs 中的标记键。 */
const val RAIN_SNAPSHOT_KEY = "rain_snapshot_v1"

private const val KEY_SEED = "seed"
private const val KEY_SCORE = "score"
private const val KEY_MAX_COMBO = "max_combo"
private const val KEY_ELAPSED = "elapsed"

/**
 * 对局快照的"可续玩阶段"。
 *
 * 快照生命周期件（[GameSnapshotLifecycle]）需要读取一个**可观察**的阶段值。
 * [RainFall.phase] 是普通字段（引擎刻意不依赖 Compose），直接传给它会读到陈旧值，
 * 故用一个显式的小枚举做桥接——这也让"什么阶段能续玩"成为显式契约。
 */
enum class RainPersistPhase {
    READY,
    PLAYING,
    OVER,
}

/** 引擎阶段 → 快照阶段。 */
fun rainPersistPhase(phase: RainPhase): RainPersistPhase =
    when (phase) {
        RainPhase.READY -> RainPersistPhase.READY
        RainPhase.PLAYING -> RainPersistPhase.PLAYING
        RainPhase.OVER -> RainPersistPhase.OVER
    }

/** 帧循环的 key：换一局（种子变化）就重启循环，避免沿用上一局的时间基。 */
fun rainSessionKey(seed: Long): Any = seed

/** 天气码 → 场景（只影响背景氛围）。复用公共天气判定，不重复解析码表。 */
fun rainSceneOf(sceneCode: Int?): RainScene =
    when (farmWeatherFromScene(sceneCode, null)) {
        FarmWeather.SUNNY -> RainScene.SUNNY
        FarmWeather.STORMY -> RainScene.STORM
        else -> RainScene.RAIN
    }

/** 新一局的种子。 */
fun rainNewSeed(): Long = Random.nextLong()

/** 类型 → 音效：雨滴用清脆的星星音，冰雹用闷响，两者必须一听就能分辨。 */
fun rainCueFor(kind: RainKind): GameCue =
    when (kind) {
        RainKind.DROP -> GameCue.STAR
        RainKind.HAIL -> GameCue.FOUL
    }

/** 历史最高分。 */
fun rainBestScore(prefs: SharedPreferences): Int = prefs.getInt(RAIN_BEST_KEY, 0)

/**
 * 记录最高分，返回是否破纪录。
 *
 * 只有真的有操作（分数 > 0）才写入，避免"开局即结束"的 0 分污染记录。
 */
fun rainRecordScore(
    prefs: SharedPreferences,
    score: Int,
): Boolean {
    if (score <= 0) return false
    if (score <= rainBestScore(prefs)) return false
    prefs.edit().putInt(RAIN_BEST_KEY, score).apply()
    return true
}

/**
 * 保存对局快照。
 *
 * 快照很轻：只存种子、当前分数、最高连击与已进行时间。
 * 方块序列由种子**确定性重建**，因此不需要把已生成的方块列表写进存档——
 * 这也是本玩法比"按拍判定"类玩法更省状态的地方。
 *
 * 不存"已点掉哪几块"：本玩法是**一击即结算**（漏接/误点立刻结束），
 * 所以对局中不可能存在"点了一半"的中间态，进度只需分数与时间。
 */
fun rainSaveSnapshot(
    prefs: SharedPreferences,
    seed: Long,
    score: Int,
    maxCombo: Int,
    elapsed: Float,
) {
    prefs
        .edit()
        .putString(RAIN_SNAPSHOT_KEY, "1")
        .putLong(KEY_SEED, seed)
        .putInt(KEY_SCORE, score)
        .putInt(KEY_MAX_COMBO, maxCombo)
        .putFloat(KEY_ELAPSED, elapsed)
        .apply()
}

/**
 * 恢复快照；无档 / 坏档返回 false（引擎保持 READY，不崩）。
 *
 * 恢复后仍处于 READY：**本玩法不支持"接着玩"**——继续一局中途的接雨雪没有意义，
 * 因为漏接即结束、进度只以分数体现。玩家回到 READY 后重开即可。
 */
internal fun rainRestoreSnapshot(
    game: RainFall,
    prefs: SharedPreferences,
): Boolean {
    if (!prefs.contains(RAIN_SNAPSHOT_KEY)) return false
    val seed = prefs.getLong(KEY_SEED, Long.MIN_VALUE)
    if (seed == Long.MIN_VALUE) return false
    val score = prefs.getInt(KEY_SCORE, -1)
    val maxCombo = prefs.getInt(KEY_MAX_COMBO, -1)
    if (score < 0 || maxCombo < 0) return false
    // 只重建谱块序列与既有成绩，阶段仍为 READY（不中途续玩）
    game.prepare(seed)
    return true
}

/** 音效是否可用（供测试与设置页判断，避免在无声环境下做无意义反馈）。 */
fun rainAudioEnabled(context: android.content.Context): Boolean = GameAudioPrefsContract.read(context).enabled
