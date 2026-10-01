package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty

/**
 * 局外轻量成长：雨具涂装随历史最高分解锁，选择写入 prefs，不进对局快照。
 *
 * [unlockBest] 为解锁所需「任意难度」历史最高分（屏幕侧取跨难度最大值）。
 */
enum class CatchGearTint(
    val id: String,
    val unlockBest: Int,
) {
    CLASSIC(id = "classic", unlockBest = 0),
    GOLD(id = "gold", unlockBest = 200),
    MIDNIGHT(id = "midnight", unlockBest = 500),
    ROSE(id = "rose", unlockBest = 1000),
    AURORA(id = "aurora", unlockBest = 2000),
    ;

    companion object {
        fun fromId(id: String?): CatchGearTint = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** 雨伞/提篮主色与高光：渲染层按涂装染色。 */
data class CatchGearPalette(
    val canopy: Color,
    val trim: Color,
    val basket: Color,
)

/** 涂装 → 调色板。 */
fun catchGearPalette(tint: CatchGearTint): CatchGearPalette =
    when (tint) {
        CatchGearTint.CLASSIC ->
            CatchGearPalette(
                canopy = Color(0xFF5C6BC0),
                trim = Color(0xFF3949AB),
                basket = Color(0xFF8D6E63),
            )
        CatchGearTint.GOLD ->
            CatchGearPalette(
                canopy = Color(0xFFFFB300),
                trim = Color(0xFFFF8F00),
                basket = Color(0xFFC9A227),
            )
        CatchGearTint.MIDNIGHT ->
            CatchGearPalette(
                canopy = Color(0xFF263238),
                trim = Color(0xFF455A64),
                basket = Color(0xFF546E7A),
            )
        CatchGearTint.ROSE ->
            CatchGearPalette(
                canopy = Color(0xFFEC407A),
                trim = Color(0xFFAD1457),
                basket = Color(0xFFD81B60),
            )
        CatchGearTint.AURORA ->
            CatchGearPalette(
                canopy = Color(0xFF26A69A),
                trim = Color(0xFF7E57C2),
                basket = Color(0xFF42A5F5),
            )
    }

/** 雨具涂装选择 prefs 键。 */
internal const val CATCH_GEAR_TINT_KEY = "catch_gear_tint"

/** 分难度最高分前缀：`catch_best_{id}`。 */
internal const val CATCH_BEST_PREFIX = "catch_best_"

/** 分难度排行榜前缀：`leaderboard_catch_{id}_v1`。 */
internal const val CATCH_LB_PREFIX = "leaderboard_catch_"

/** 旧版单一最高分键（迁移到 [CATCH_BEST_PREFIX]standard）。 */
internal const val CATCH_LEGACY_BEST_KEY = "catch_best_score"

/** 更早的最高分键（一并迁移）。 */
internal const val CATCH_LEGACY_BEST_KEY_V0 = "best_catch"

/** 旧版单一排行榜键（迁移到 standard 分表）。 */
internal const val CATCH_LEGACY_LB_KEY = "leaderboard_catch"

fun catchBestKey(difficulty: CatchDifficulty): String = CATCH_BEST_PREFIX + difficulty.id

fun catchLeaderboardKey(difficulty: CatchDifficulty): String = CATCH_LB_PREFIX + difficulty.id + "_v1"

/** [best] 是否已解锁该涂装。 */
fun catchGearTintUnlocked(
    tint: CatchGearTint,
    best: Int,
): Boolean = best.coerceAtLeast(0) >= tint.unlockBest

/** 已解锁涂装列表（含 CLASSIC，按解锁分数升序）。 */
fun catchUnlockedGearTints(best: Int): List<CatchGearTint> = CatchGearTint.entries.filter { catchGearTintUnlocked(it, best) }

/** 跨难度历史最高分（涂装解锁用）。 */
fun catchBestAcrossDifficulties(prefs: SharedPreferences): Int = CatchDifficulty.entries.maxOf { prefs.intOr(catchBestKey(it), 0) }

/**
 * 一次性迁移：旧单一最高分 / 旧榜 → STANDARD 分键。
 * 幂等：STANDARD 已有分时只补更高值，不覆盖更优纪录。
 */
fun migrateCatchBestPrefs(prefs: SharedPreferences) {
    val standardKey = catchBestKey(CatchDifficulty.STANDARD)
    val current = prefs.intOr(standardKey, -1)
    val legacy =
        maxOf(
            prefs.intOr(CATCH_LEGACY_BEST_KEY, 0),
            prefs.intOr(CATCH_LEGACY_BEST_KEY_V0, 0),
        )
    if (legacy > 0 && legacy > current) {
        prefs.edit { putInt(standardKey, legacy) }
    }
    // 旧榜有数据且新 STANDARD 榜空：整表搬迁
    val newLb = catchLeaderboardKey(CatchDifficulty.STANDARD)
    if (!prefs.contains(newLb) && prefs.contains(CATCH_LEGACY_LB_KEY)) {
        val raw = prefs.getString(CATCH_LEGACY_LB_KEY, null)
        if (!raw.isNullOrBlank()) {
            prefs.edit { putString(newLb, raw) }
        }
    }
}

/** 读取指定难度最高分（先迁移旧键）。 */
fun loadCatchBestScore(
    prefs: SharedPreferences,
    difficulty: CatchDifficulty,
): Int {
    migrateCatchBestPrefs(prefs)
    return prefs.intOr(catchBestKey(difficulty), 0)
}

/** 刷新并持久化指定难度最高分；未刷新返回 false。 */
fun recordCatchBestScore(
    prefs: SharedPreferences,
    difficulty: CatchDifficulty,
    score: Int,
    currentBest: Int,
): Boolean {
    if (score <= currentBest) return false
    prefs.edit { putInt(catchBestKey(difficulty), score) }
    return true
}

/** 清榜时一并清掉旧最高分键，避免下次读取又被迁移回来。 */
fun clearCatchLegacyBestKeys(prefs: SharedPreferences) {
    prefs.edit {
        remove(CATCH_LEGACY_BEST_KEY)
        remove(CATCH_LEGACY_BEST_KEY_V0)
        remove(CATCH_LEGACY_LB_KEY)
    }
}
