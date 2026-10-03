package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.ui.unit.dp
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

/** 旧版曾把 seed/score 写进公共 `bear_game` 顶层键，清档时一并删掉。 */
private const val LEGACY_KEY_SEED = "seed"
private const val LEGACY_KEY_SCORE = "score"
private const val LEGACY_KEY_MAX_COMBO = "max_combo"
private const val LEGACY_KEY_ELAPSED = "elapsed"

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
fun rainSceneOf(
    sceneCode: Int?,
    isDay: Boolean? = null,
): RainScene =
    when (farmWeatherFromScene(sceneCode, isDay)) {
        FarmWeather.SUNNY -> RainScene.SUNNY
        FarmWeather.STORMY -> RainScene.STORM
        else -> RainScene.RAIN
    }

/**
 * 底部小熊条的高度。
 *
 * 必须是显式常量：`Canvas` 无内在尺寸，只写 `fillMaxWidth()` 会量成 0 高度，
 * 导致小熊完全不显示（曾发生）。
 */
val RAIN_BEAR_STRIP_HEIGHT = 76.dp

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
 * 本玩法漏接即结束，已点块不进档，中途无法忠实续玩。
 * 生命周期件在任何阶段都应清档，避免往公共 prefs 写半成品。
 */
fun rainSnapshotShouldClear(
    @Suppress("UNUSED_PARAMETER") phase: RainPersistPhase,
): Boolean = true

/** 清掉本游戏快照，以及旧版误写到 `bear_game` 顶层的 seed/score 键。 */
fun rainClearSnapshot(prefs: SharedPreferences) {
    prefs
        .edit()
        .remove(RAIN_SNAPSHOT_KEY)
        .remove(LEGACY_KEY_SEED)
        .remove(LEGACY_KEY_SCORE)
        .remove(LEGACY_KEY_MAX_COMBO)
        .remove(LEGACY_KEY_ELAPSED)
        .apply()
}

/**
 * 保存入口保留给生命周期件调用；本玩法不续玩，因此只清档、不写进度。
 */
fun rainSaveSnapshot(
    prefs: SharedPreferences,
    @Suppress("UNUSED_PARAMETER") seed: Long,
    @Suppress("UNUSED_PARAMETER") score: Int,
    @Suppress("UNUSED_PARAMETER") maxCombo: Int,
    @Suppress("UNUSED_PARAMETER") elapsed: Float,
) {
    rainClearSnapshot(prefs)
}

/**
 * 恢复快照。始终返回 false：不中途续玩，并清掉旧版脏键。
 */
internal fun rainRestoreSnapshot(
    @Suppress("UNUSED_PARAMETER") game: RainFall,
    prefs: SharedPreferences,
): Boolean {
    rainClearSnapshot(prefs)
    return false
}

/** 音效是否可用（供测试与设置页判断，避免在无声环境下做无意义反馈）。 */
fun rainAudioEnabled(context: android.content.Context): Boolean = GameAudioPrefsContract.read(context).enabled
