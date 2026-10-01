package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color

/**
 * 局外轻量成长：围巾色随历史最高分解锁，选择写入 prefs，不进对局快照。
 *
 * [unlockScore] 为解锁所需「当前难度」历史最高分。
 */
enum class RunScarfTint(
    val id: String,
    val unlockScore: Int,
) {
    CLASSIC(id = "classic", unlockScore = 0),
    GOLD(id = "gold", unlockScore = 500),
    MIDNIGHT(id = "midnight", unlockScore = 1500),
    ROSE(id = "rose", unlockScore = 3000),
    AURORA(id = "aurora", unlockScore = 5000),
    ;

    companion object {
        fun fromId(id: String?): RunScarfTint = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** 开局旁路：围巾色选择。 */
internal const val RUN_SCARF_TINT_KEY = "run_scarf_tint"

/** [bestScore] 是否已解锁该围巾色。 */
fun runScarfTintUnlocked(
    tint: RunScarfTint,
    bestScore: Int,
): Boolean = bestScore.coerceAtLeast(0) >= tint.unlockScore

/** 已解锁围巾色列表（含 CLASSIC，按解锁分升序）。 */
fun runUnlockedScarfTints(bestScore: Int): List<RunScarfTint> = RunScarfTint.entries.filter { runScarfTintUnlocked(it, bestScore) }

/** 渲染用点缀色：围巾/光晕。 */
fun runScarfAccent(tint: RunScarfTint): Color =
    when (tint) {
        RunScarfTint.CLASSIC -> Color(0xFFE53935)
        RunScarfTint.GOLD -> Color(0xFFFFC107)
        RunScarfTint.MIDNIGHT -> Color(0xFF5C6BC0)
        RunScarfTint.ROSE -> Color(0xFFEC407A)
        RunScarfTint.AURORA -> Color(0xFF26A69A)
    }
