package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.game.stringOr

/** prefs：当前难度。 */
internal const val PREF_RUN_DIFFICULTY = "run_difficulty"

/** prefs：按难度最高分前缀 → `run_best_{id}`。 */
internal const val PREF_RUN_BEST_PREFIX = "run_best_"

/** 旧版统一最高分键：迁移到 [RunDifficulty.STANDARD]。 */
internal const val PREF_RUN_BEST_LEGACY = "best"

/** prefs：按难度排行榜前缀 → `leaderboard_run_{id}_v1`。 */
internal const val PREF_RUN_LEADERBOARD_PREFIX = "leaderboard_run_"

/** 旧版统一排行榜键。 */
internal const val PREF_RUN_LEADERBOARD_LEGACY = "leaderboard_run"

internal fun runBestKey(difficulty: RunDifficulty): String = PREF_RUN_BEST_PREFIX + difficulty.id

internal fun runLeaderboardKey(difficulty: RunDifficulty): String = PREF_RUN_LEADERBOARD_PREFIX + difficulty.id + "_v1"

/** 读取难度；缺省 / 非法回退 STANDARD。 */
internal fun loadRunDifficulty(prefs: SharedPreferences): RunDifficulty = RunDifficulty.fromId(prefs.stringOr(PREF_RUN_DIFFICULTY))

/** 写入难度。 */
internal fun persistRunDifficulty(
    prefs: SharedPreferences,
    difficulty: RunDifficulty,
) {
    prefs.edit { putString(PREF_RUN_DIFFICULTY, difficulty.id) }
}

/**
 * 读取某难度最高分。
 * STANDARD 若新键缺失，回退旧键 [PREF_RUN_BEST_LEGACY]（一次性兼容）。
 */
internal fun loadRunBest(
    prefs: SharedPreferences,
    difficulty: RunDifficulty,
): Int {
    val key = runBestKey(difficulty)
    if (prefs.contains(key)) return prefs.intOr(key, 0)
    if (difficulty == RunDifficulty.STANDARD) {
        val legacy = prefs.intOr(PREF_RUN_BEST_LEGACY, 0)
        if (legacy > 0) {
            prefs.edit { putInt(key, legacy) }
        }
        return legacy
    }
    return 0
}

/** 写入最高分（仅当更高）；同步 STANDARD 的旧键以便旧入口可读。 */
internal fun persistRunBestIfHigher(
    prefs: SharedPreferences,
    difficulty: RunDifficulty,
    score: Int,
): Int {
    val key = runBestKey(difficulty)
    val prev = loadRunBest(prefs, difficulty)
    if (score > prev) {
        prefs.edit {
            putInt(key, score)
            if (difficulty == RunDifficulty.STANDARD) putInt(PREF_RUN_BEST_LEGACY, score)
        }
        return score
    }
    return prev
}

/**
 * 排行榜键：优先新键；STANDARD 若新键无内容且旧键有数据，把旧 JSON 拷到新键。
 */
internal fun ensureRunLeaderboardMigrated(
    prefs: SharedPreferences,
    difficulty: RunDifficulty,
): String {
    val key = runLeaderboardKey(difficulty)
    if (difficulty == RunDifficulty.STANDARD && !prefs.contains(key)) {
        val legacy = prefs.stringOr(PREF_RUN_LEADERBOARD_LEGACY)
        if (!legacy.isNullOrBlank()) {
            prefs.edit { putString(key, legacy) }
        }
    }
    return key
}
