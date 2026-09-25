package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.game.stringOr
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import kotlin.math.max

// 生涯统计数据 key（前缀 hockey_）：与排行榜 / 最高分 / 对局设置互不影响。
private const val PREF_STAT_GAMES = "hockey_games"
private const val PREF_STAT_WINS = "hockey_wins"
private const val PREF_STAT_LOSSES = "hockey_losses"
private const val PREF_STAT_GOALS_FOR = "hockey_goals_for"
private const val PREF_STAT_GOALS_AGAINST = "hockey_goals_against"
private const val PREF_STAT_SHUTOUTS = "hockey_shutouts"
private const val PREF_STAT_WIN_STREAK = "hockey_win_streak"
private const val PREF_STAT_BEST_STREAK = "hockey_best_win_streak"
private const val PREF_STAT_BEST_GOALS = "hockey_best_goals"
private const val PREF_STAT_BEST_SHUTOUT_GOALS = "hockey_best_shutout_goals"
private const val PREF_STAT_WINS_BY_DIFF = "hockey_wins_by_difficulty"
private const val PREF_STAT_ACH_SEEN = "hockey_ach_seen"

/** 需要被「清空统计与成就」清除的 prefs key。 */
val HOCKEY_STAT_KEYS =
    listOf(
        PREF_STAT_GAMES,
        PREF_STAT_WINS,
        PREF_STAT_LOSSES,
        PREF_STAT_GOALS_FOR,
        PREF_STAT_GOALS_AGAINST,
        PREF_STAT_SHUTOUTS,
        PREF_STAT_WIN_STREAK,
        PREF_STAT_BEST_STREAK,
        PREF_STAT_BEST_GOALS,
        PREF_STAT_BEST_SHUTOUT_GOALS,
        PREF_STAT_WINS_BY_DIFF,
        PREF_STAT_ACH_SEEN,
    )

/** 冰球生涯统计（只存本地 SharedPreferences）。 */
data class HockeyProgress(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val shutouts: Int = 0,
    val winStreak: Int = 0,
    val bestStreak: Int = 0,
    val bestGoals: Int = 0,
    /** 零封场次里单场最多进球（用于「零封进5球」成就）。 */
    val bestShutoutGoals: Int = 0,
    /** 各难度胜场。 */
    val winsByDifficulty: Map<Difficulty, Int> = emptyMap(),
    /** 已弹窗提示过的成就 id；解锁状态由统计派生。 */
    val unlocked: Set<String> = emptySet(),
) {
    fun winsOn(d: Difficulty): Int = winsByDifficulty[d] ?: 0
}

/** 成就：解锁状态由生涯统计派生，[HockeyProgress.unlocked] 仅记录是否已弹窗提示过。 */
enum class HockeyAchievement(
    val id: String,
) {
    FIRST_WIN("first_win"),
    SHUTOUT("shutout"),
    WIN_STREAK_3("win_streak_3"),
    HARD_WIN("hard_win"),
    MASTER_WIN("master_win"),
    FIVE_GOALS("five_goals"),
    PERFECT_FIVE("perfect_five"),
    VETERAN("veteran"),
    ;

    fun isUnlocked(p: HockeyProgress): Boolean =
        when (this) {
            FIRST_WIN -> p.wins >= 1
            SHUTOUT -> p.shutouts >= 1
            WIN_STREAK_3 -> p.bestStreak >= 3
            HARD_WIN -> p.winsOn(Difficulty.HARD) >= 1
            MASTER_WIN -> p.winsOn(Difficulty.MASTER) >= 1
            FIVE_GOALS -> p.bestGoals >= 5
            PERFECT_FIVE -> p.bestShutoutGoals >= 5
            VETERAN -> p.games >= 10
        }
}

fun loadHockeyProgress(prefs: SharedPreferences): HockeyProgress =
    HockeyProgress(
        games = prefs.intOr(PREF_STAT_GAMES, 0),
        wins = prefs.intOr(PREF_STAT_WINS, 0),
        losses = prefs.intOr(PREF_STAT_LOSSES, 0),
        goalsFor = prefs.intOr(PREF_STAT_GOALS_FOR, 0),
        goalsAgainst = prefs.intOr(PREF_STAT_GOALS_AGAINST, 0),
        shutouts = prefs.intOr(PREF_STAT_SHUTOUTS, 0),
        winStreak = prefs.intOr(PREF_STAT_WIN_STREAK, 0),
        bestStreak = prefs.intOr(PREF_STAT_BEST_STREAK, 0),
        bestGoals = prefs.intOr(PREF_STAT_BEST_GOALS, 0),
        bestShutoutGoals = prefs.intOr(PREF_STAT_BEST_SHUTOUT_GOALS, 0),
        winsByDifficulty = decodeWinsByDifficulty(prefs.stringOr(PREF_STAT_WINS_BY_DIFF)),
        unlocked =
            prefs
                .getString(PREF_STAT_ACH_SEEN, null)
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty(),
    )

fun saveHockeyProgress(
    prefs: SharedPreferences,
    p: HockeyProgress,
) {
    prefs.edit {
        putInt(PREF_STAT_GAMES, p.games)
        putInt(PREF_STAT_WINS, p.wins)
        putInt(PREF_STAT_LOSSES, p.losses)
        putInt(PREF_STAT_GOALS_FOR, p.goalsFor)
        putInt(PREF_STAT_GOALS_AGAINST, p.goalsAgainst)
        putInt(PREF_STAT_SHUTOUTS, p.shutouts)
        putInt(PREF_STAT_WIN_STREAK, p.winStreak)
        putInt(PREF_STAT_BEST_STREAK, p.bestStreak)
        putInt(PREF_STAT_BEST_GOALS, p.bestGoals)
        putInt(PREF_STAT_BEST_SHUTOUT_GOALS, p.bestShutoutGoals)
        putString(PREF_STAT_WINS_BY_DIFF, encodeWinsByDifficulty(p.winsByDifficulty))
        putString(PREF_STAT_ACH_SEEN, p.unlocked.joinToString(","))
    }
}

/** 一局结束后推进生涯统计（胜场 / 连胜 / 进球 / 零封 / 各难度胜场）。 */
fun HockeyProgress.advance(
    won: Boolean,
    goalsFor: Int,
    goalsAgainst: Int,
    difficulty: Difficulty,
): HockeyProgress {
    val gf = goalsFor.coerceAtLeast(0)
    val ga = goalsAgainst.coerceAtLeast(0)
    val streak = if (won) winStreak + 1 else 0
    val shutout = won && ga == 0
    val winsByDiff =
        if (won) {
            winsByDifficulty + (difficulty to winsOn(difficulty) + 1)
        } else {
            winsByDifficulty
        }
    return copy(
        games = games + 1,
        wins = wins + if (won) 1 else 0,
        losses = losses + if (won) 0 else 1,
        goalsFor = this.goalsFor + gf,
        goalsAgainst = this.goalsAgainst + ga,
        shutouts = shutouts + if (shutout) 1 else 0,
        winStreak = streak,
        bestStreak = max(bestStreak, streak),
        bestGoals = max(bestGoals, gf),
        bestShutoutGoals = max(bestShutoutGoals, if (shutout) gf else 0),
        winsByDifficulty = winsByDiff,
    )
}

private fun encodeWinsByDifficulty(map: Map<Difficulty, Int>): String =
    map.entries
        .filter { it.value > 0 }
        .joinToString(",") { "${it.key.name}:${it.value}" }

private fun decodeWinsByDifficulty(raw: String?): Map<Difficulty, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw
        .split(',')
        .mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val d = Difficulty.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val n = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            d to n
        }.toMap()
}
