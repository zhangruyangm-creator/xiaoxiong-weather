package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color

/**
 * 摘星难度：影响平台间距、移动速度、碎台占比、星星密度与得分倍率。
 * [id] 用于 prefs / 排行榜键，必须保持稳定。
 */
enum class StarDifficulty(
    val id: String,
    val gapMul: Float,
    val movingSpeedMul: Float,
    val breakShareMul: Float,
    val starChanceMul: Float,
    val scoreMul: Float,
) {
    CASUAL(id = "casual", gapMul = 0.88f, movingSpeedMul = 0.85f, breakShareMul = 0.55f, starChanceMul = 1.35f, scoreMul = 0.85f),
    STANDARD(id = "standard", gapMul = 1f, movingSpeedMul = 1f, breakShareMul = 1f, starChanceMul = 1f, scoreMul = 1f),
    CHALLENGE(id = "challenge", gapMul = 1.12f, movingSpeedMul = 1.2f, breakShareMul = 1.35f, starChanceMul = 0.85f, scoreMul = 1.35f),
    ;

    companion object {
        fun fromId(id: String?): StarDifficulty = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** 海拔主题带：与天空渐变阈值对齐，供横幅文案使用。 */
enum class StarAltitudeTheme {
    DAY,
    DUSK,
    NIGHT,
    SPACE,
}

/** 局外涂装：按任意难度历史最高分解锁。 */
enum class StarTrailTint(
    val id: String,
    val unlockBest: Int,
) {
    CLASSIC(id = "classic", unlockBest = 0),
    GOLD(id = "gold", unlockBest = 80),
    MIDNIGHT(id = "midnight", unlockBest = 180),
    ROSE(id = "rose", unlockBest = 320),
    AURORA(id = "aurora", unlockBest = 500),
    ;

    companion object {
        fun fromId(id: String?): StarTrailTint = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

internal const val STAR_DIFFICULTY_KEY = "star_difficulty"
internal const val STAR_TRAIL_TINT_KEY = "star_trail_tint"
internal const val STAR_BEST_PREFIX = "star_best_"
internal const val STAR_LB_PREFIX = "leaderboard_star_"

/** 旧版单一最高分键（迁移到 [STAR_BEST_PREFIX]standard）。 */
internal const val STAR_LEGACY_BEST_KEY = "best_jump"
internal const val STAR_LEGACY_LB_KEY = "leaderboard_jump"

internal const val STAR_COMBO_WINDOW_SEC = 2.4f
internal const val STAR_MAX_COMBO_BONUS_STEPS = 10
internal const val STAR_COMBO_BONUS_PER_STEP = 3
internal const val STAR_GRAZE_SCORE = 8
internal const val STAR_GRAZE_PAD_FRAC = 0.55f

fun starBestKey(difficulty: StarDifficulty): String = STAR_BEST_PREFIX + difficulty.id

fun starLeaderboardKey(difficulty: StarDifficulty): String = STAR_LB_PREFIX + difficulty.id + "_v1"

fun starTrailTintUnlocked(
    tint: StarTrailTint,
    best: Int,
): Boolean = best.coerceAtLeast(0) >= tint.unlockBest

fun starUnlockedTrailTints(best: Int): List<StarTrailTint> = StarTrailTint.entries.filter { starTrailTintUnlocked(it, best) }

fun starAltitudeTheme(altKm: Float): StarAltitudeTheme =
    when {
        altKm >= 2.8f -> StarAltitudeTheme.SPACE
        altKm >= 1.8f -> StarAltitudeTheme.NIGHT
        altKm >= 0.9f -> StarAltitudeTheme.DUSK
        else -> StarAltitudeTheme.DAY
    }

/** 尾迹粒子主题色（局外涂装）。 */
fun starTrailParticleColor(tint: StarTrailTint): Color =
    when (tint) {
        StarTrailTint.CLASSIC -> Color.White.copy(alpha = 0.85f)
        StarTrailTint.GOLD -> Color(0xFFFFD54F)
        StarTrailTint.MIDNIGHT -> Color(0xFF90CAF9)
        StarTrailTint.ROSE -> Color(0xFFF48FB1)
        StarTrailTint.AURORA -> Color(0xFF80CBC4)
    }
