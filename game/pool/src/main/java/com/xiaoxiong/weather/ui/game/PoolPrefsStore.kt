package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaoxiong.weather.game.booleanOr
import com.xiaoxiong.weather.game.intOr
import com.xiaoxiong.weather.game.stringOr
import com.xiaoxiong.weather.ui.game.PoolGame.Aids
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

internal const val PREF_POOL_DIFF = "pool_difficulty"
internal const val PREF_POOL_AIDS = "pool_aids"
internal const val PREF_POOL_RACE = "pool_race_to"
internal const val PREF_POOL_MODE = "pool_mode"

// 生涯统计数据 key（前缀 pool_）
private const val PREF_STAT_GAMES = "pool_games"
private const val PREF_STAT_WINS = "pool_wins"
private const val PREF_STAT_FRAMES = "pool_frames"
private const val PREF_STAT_POTS = "pool_pots"
private const val PREF_STAT_BEST_RUN = "pool_best_run"
private const val PREF_STAT_BREAK_RUNS = "pool_break_runs"
private const val PREF_STAT_CLEAN_SHEETS = "pool_clean_sheets"
private const val PREF_STAT_MASTER_WINS = "pool_master_wins"
private const val PREF_STAT_BEST_SHOT = "pool_best_shot_pots"
private const val PREF_STAT_WIN_STREAK = "pool_win_streak"
private const val PREF_STAT_BEST_STREAK = "pool_best_win_streak"
private const val PREF_STAT_NINE_WINS = "pool_nine_wins"
private const val PREF_STAT_PRACTICE_CLEARS = "pool_practice_clears"
private const val PREF_STAT_BEST_PRACTICE = "pool_best_practice"
private const val PREF_STAT_ACH_SEEN = "pool_ach_seen"
internal const val PREF_POOL_REDUCE_MOTION = "pool_reduce_motion"
private const val PREF_POOL_REPLAY = "pool_last_replay_v2"

// 关卡 / 每日挑战进度 key（前缀 + id / date）
private const val PREF_LEVEL_CLEARED = "pool_level_cleared_"
private const val PREF_LEVEL_BEST = "pool_level_best_"
private const val PREF_DAILY_BEST = "pool_daily_"

// 新手教学 / 台呢主题
private const val PREF_LESSON_DONE = "pool_lesson_done_"
internal const val PREF_POOL_CLOTH = "pool_cloth_theme"

/** 台呢主题档位数量：与 [CLOTH_THEME_RES] 一一对应，钳制入口见 [clampClothTheme]。 */
internal const val CLOTH_THEME_COUNT = 4

/** 台呢主题索引统一钳制入口：旧档 / 坏档 / 设置保存都走它。 */
internal fun clampClothTheme(theme: Int): Int = theme.coerceIn(0, CLOTH_THEME_COUNT - 1)

/** 读取持久化的台呢主题：旧键 `pool_cloth_theme` 无值回退 0（经典湖蓝），越界钳制。 */
internal fun loadClothTheme(prefs: SharedPreferences): Int = clampClothTheme(prefs.intOr(PREF_POOL_CLOTH, 0))

/** 保存台呢主题（点保存才写入，与其它设置一致）。 */
internal fun saveClothTheme(
    prefs: SharedPreferences,
    theme: Int,
) {
    prefs.edit { putInt(PREF_POOL_CLOTH, clampClothTheme(theme)) }
}

internal fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

internal fun dailyLeaderboardKey(dateEpochDay: Long): String = "leaderboard_pool_daily_${dateEpochDay}_v1"

internal fun dailyBestKey(dateEpochDay: Long): String = PREF_DAILY_BEST + dateEpochDay

internal fun clearedLevelKey(id: Int): String = PREF_LEVEL_CLEARED + id

internal fun bestLevelKey(id: Int): String = PREF_LEVEL_BEST + id

internal fun challengeDisplayName(c: PoolChallenge): String = if (Locale.getDefault().language.startsWith("en")) c.nameEn else c.nameZh

internal fun lessonDoneKey(id: Int): String = PREF_LESSON_DONE + id

internal fun loadDoneLessons(prefs: SharedPreferences): Set<Int> =
    PoolTutorial
        .lessons()
        .map { it.id }
        .filter { prefs.booleanOr(lessonDoneKey(it), false) }
        .toSet()

internal fun lessonName(lesson: PoolLesson): String = if (Locale.getDefault().language.startsWith("en")) lesson.nameEn else lesson.nameZh

internal fun lessonTip(lesson: PoolLesson): String = if (Locale.getDefault().language.startsWith("en")) lesson.tipEn else lesson.tipZh

/** 关卡选择锁定策略：第 1 关常开，其余需上一关已通关。 */
internal fun isLevelUnlocked(
    id: Int,
    cleared: Set<Int>,
): Boolean = id <= 1 || (id - 1) in cleared

internal fun loadClearedLevels(prefs: SharedPreferences): Set<Int> =
    PoolChallenges
        .campaigns()
        .map { it.id }
        .filter { prefs.booleanOr(clearedLevelKey(it), false) }
        .toSet()

internal fun loadBestLevels(prefs: SharedPreferences): Map<Int, Int> =
    PoolChallenges
        .campaigns()
        .mapNotNull { c ->
            val best = prefs.intOr(bestLevelKey(c.id), 0)
            if (best > 0) c.id to best else null
        }.toMap()

/** 需要被「清空统计与成就」清除的 prefs key（排行榜 / 设置 / 最高分不受影响）。 */
internal val POOL_STAT_KEYS =
    listOf(
        PREF_STAT_GAMES,
        PREF_STAT_WINS,
        PREF_STAT_FRAMES,
        PREF_STAT_POTS,
        PREF_STAT_BEST_RUN,
        PREF_STAT_BREAK_RUNS,
        PREF_STAT_CLEAN_SHEETS,
        PREF_STAT_MASTER_WINS,
        PREF_STAT_BEST_SHOT,
        PREF_STAT_WIN_STREAK,
        PREF_STAT_BEST_STREAK,
        PREF_STAT_NINE_WINS,
        PREF_STAT_PRACTICE_CLEARS,
        PREF_STAT_BEST_PRACTICE,
        PREF_STAT_ACH_SEEN,
        PREF_POOL_REPLAY,
    )

/** 持久化的一杆输入（[PoolGame.RecordedShot] 的可落盘镜像）。 */
internal data class StoredShot(
    val angle: Float,
    val power: Float,
    val side: Float,
    val vertical: Float,
    val elevation: Float,
    val x: Float = 0f,
    val y: Float = 0f,
)

/** 最近一场的回放快照：模式 + 重摆种子 + 开球方 + 杆序。 */
internal data class StoredReplay(
    val mode: String,
    val seed: Long,
    val breaker: String,
    val shots: List<StoredShot>,
)

/*
 * 回放持久化：项目未给本模块引入 kotlinx.serialization 运行时依赖，
 * 这里用极简文本格式（分号/竖线分隔）落盘，读写均静默容错。
 * 格式：mode|seed|breaker|a,p,s,v,e;a,p,s,v,e;...
 */
internal fun saveReplay(
    prefs: SharedPreferences,
    game: PoolGame,
) {
    val shots = game.replayShots()
    if (shots.isEmpty()) {
        prefs.edit { remove(PREF_POOL_REPLAY) }
        return
    }
    val shotsPart =
        shots.joinToString(";") {
            "${it.angle},${it.power},${it.spinSide},${it.spinVertical},${it.elevation},${it.cueX},${it.cueY}"
        }
    val raw = "${game.mode.name}|${game.frameSeed}|${game.breaker.name}|$shotsPart"
    runCatching { prefs.edit { putString(PREF_POOL_REPLAY, raw) } }
}

internal fun loadReplay(prefs: SharedPreferences): StoredReplay? =
    runCatching { prefs.stringOr(PREF_POOL_REPLAY) }.getOrNull()?.let { parseReplay(it) }

/**
 * 逐杆容错解析回放文本。
 *
 * 原先每杆用 `toFloat()`，任一脏数据抛 NumberFormatException 后被外层 runCatching
 * 吞成 null——**整段回放作废**，与已修的城市列表「单条坏数据抹掉整批」同型。
 * 这里改为逐杆 `toFloatOrNull()` + 有限性校验：坏杆只丢自己，其余照常可回放。
 * 头部（模式/种子/球方）坏了仍整段作废，因为那决定回放如何重建球局。
 */
internal fun parseReplay(raw: String): StoredReplay? {
    val parts = raw.split('|')
    if (parts.size < 4) return null
    val seed = parts[1].toLongOrNull() ?: return null
    val shots =
        parts[3]
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { parseStoredShot(it) }
    return if (shots.isEmpty()) null else StoredReplay(parts[0], seed, parts[2], shots)
}

/** 单杆容错解析：字段不足、非数字或非有限值都返回 null（只丢这一杆）。 */
private fun parseStoredShot(entry: String): StoredShot? {
    val f = entry.split(',')
    if (f.size < 5) return null
    val angle = f[0].toFloatOrNull() ?: return null
    val power = f[1].toFloatOrNull() ?: return null
    val side = f[2].toFloatOrNull() ?: return null
    val vertical = f[3].toFloatOrNull() ?: return null
    val elevation = f[4].toFloatOrNull() ?: return null
    if (!listOf(angle, power, side, vertical, elevation).all { it.isFinite() }) return null
    // 区分「旧格式没有 x/y 字段」（合法，缺省 0）和「字段存在但非法」（坏数据，丢这一杆）：
    // 后者若也当 0 处理，白球会被摆到球桌左上角，回放从错误局面开始。
    val x = if (f.size > 5) f[5].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null else 0f
    val y = if (f.size > 6) f[6].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null else 0f
    return StoredShot(
        angle = angle,
        power = power,
        side = side,
        vertical = vertical,
        elevation = elevation,
        x = x,
        y = y,
    )
}

/** 台球生涯统计（只存本地 SharedPreferences）。 */
internal data class PoolProgress(
    val games: Int = 0,
    val wins: Int = 0,
    val frames: Int = 0,
    val pots: Int = 0,
    val bestRun: Int = 0,
    val breakRuns: Int = 0,
    val cleanSheets: Int = 0,
    val masterWins: Int = 0,
    val bestShotPots: Int = 0,
    val winStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val nineBallWins: Int = 0,
    val practiceClears: Int = 0,
    val bestPractice: Int = 0,
    val unlocked: Set<String> = emptySet(),
)

/** 成就：解锁状态由生涯统计派生，[unlocked] 仅记录是否已弹窗提示过。 */
internal enum class PoolAchievement(
    val id: String,
) {
    FIRST_WIN("first_win"),
    SHUTOUT("shutout"),
    TRIPLE_POT("triple_pot"),
    CLEAN_TABLE("clean_table"),
    WIN_STREAK_3("win_streak_3"),
    MASTER_WIN("master_win"),
    NINE_BALL_WIN("nine_ball_win"),
    PRACTICE_CLEAR("practice_clear"),
    ;

    fun isUnlocked(p: PoolProgress): Boolean =
        when (this) {
            FIRST_WIN -> p.wins >= 1
            SHUTOUT -> p.cleanSheets >= 1
            TRIPLE_POT -> p.bestShotPots >= 3
            CLEAN_TABLE -> p.breakRuns >= 1 || p.practiceClears >= 1
            WIN_STREAK_3 -> p.bestWinStreak >= 3
            MASTER_WIN -> p.masterWins >= 1
            NINE_BALL_WIN -> p.nineBallWins >= 1
            PRACTICE_CLEAR -> p.practiceClears >= 1
        }
}

internal fun loadPoolProgress(prefs: SharedPreferences): PoolProgress =
    PoolProgress(
        games = prefs.intOr(PREF_STAT_GAMES, 0),
        wins = prefs.intOr(PREF_STAT_WINS, 0),
        frames = prefs.intOr(PREF_STAT_FRAMES, 0),
        pots = prefs.intOr(PREF_STAT_POTS, 0),
        bestRun = prefs.intOr(PREF_STAT_BEST_RUN, 0),
        breakRuns = prefs.intOr(PREF_STAT_BREAK_RUNS, 0),
        cleanSheets = prefs.intOr(PREF_STAT_CLEAN_SHEETS, 0),
        masterWins = prefs.intOr(PREF_STAT_MASTER_WINS, 0),
        bestShotPots = prefs.intOr(PREF_STAT_BEST_SHOT, 0),
        winStreak = prefs.intOr(PREF_STAT_WIN_STREAK, 0),
        bestWinStreak = prefs.intOr(PREF_STAT_BEST_STREAK, 0),
        nineBallWins = prefs.intOr(PREF_STAT_NINE_WINS, 0),
        practiceClears = prefs.intOr(PREF_STAT_PRACTICE_CLEARS, 0),
        bestPractice = prefs.intOr(PREF_STAT_BEST_PRACTICE, 0),
        unlocked =
            prefs
                .getString(PREF_STAT_ACH_SEEN, null)
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty(),
    )

internal fun savePoolProgress(
    prefs: SharedPreferences,
    p: PoolProgress,
) {
    prefs.edit {
        putInt(PREF_STAT_GAMES, p.games)
        putInt(PREF_STAT_WINS, p.wins)
        putInt(PREF_STAT_FRAMES, p.frames)
        putInt(PREF_STAT_POTS, p.pots)
        putInt(PREF_STAT_BEST_RUN, p.bestRun)
        putInt(PREF_STAT_BREAK_RUNS, p.breakRuns)
        putInt(PREF_STAT_CLEAN_SHEETS, p.cleanSheets)
        putInt(PREF_STAT_MASTER_WINS, p.masterWins)
        putInt(PREF_STAT_BEST_SHOT, p.bestShotPots)
        putInt(PREF_STAT_WIN_STREAK, p.winStreak)
        putInt(PREF_STAT_BEST_STREAK, p.bestWinStreak)
        putInt(PREF_STAT_NINE_WINS, p.nineBallWins)
        putInt(PREF_STAT_PRACTICE_CLEARS, p.practiceClears)
        putInt(PREF_STAT_BEST_PRACTICE, p.bestPractice)
        putString(PREF_STAT_ACH_SEEN, p.unlocked.joinToString(","))
    }
}

/** 一局（场）结束后推进生涯统计。对局类成就只由八球/九球产生，练习等模式不计。 */
internal fun PoolProgress.advance(
    won: Boolean,
    framesPlayed: Int,
    aiFrames: Int,
    potsThisMatch: Int,
    bestShotThisMatch: Int,
    breakAndRun: Boolean,
    mode: PoolMode,
    difficulty: PoolAi.Difficulty,
    practiceCleared: Boolean,
    practiceScore: Int,
): PoolProgress {
    val competitive = mode == PoolMode.EIGHT_BALL || mode == PoolMode.NINE_BALL
    val matchWon = competitive && won
    val streak =
        if (matchWon) {
            winStreak + 1
        } else if (competitive) {
            0
        } else {
            winStreak
        }
    return copy(
        games = games + 1,
        wins = wins + if (matchWon) 1 else 0,
        frames = frames + framesPlayed,
        pots = pots + potsThisMatch,
        bestRun = max(bestRun, bestShotThisMatch),
        breakRuns = breakRuns + if (competitive && breakAndRun) 1 else 0,
        cleanSheets = cleanSheets + if (matchWon && aiFrames == 0) 1 else 0,
        masterWins = masterWins + if (matchWon && difficulty == PoolAi.Difficulty.MASTER) 1 else 0,
        bestShotPots = max(bestShotPots, bestShotThisMatch),
        winStreak = streak,
        bestWinStreak = max(bestWinStreak, streak),
        nineBallWins = nineBallWins + if (matchWon && mode == PoolMode.NINE_BALL) 1 else 0,
        practiceClears = practiceClears + if (practiceCleared) 1 else 0,
        bestPractice = max(bestPractice, practiceScore),
    )
}

/** 对局类排行榜分数：净胜 = 胜局 - 负局（负净值也能入榜，展示仍用比分 label）。 */
internal fun poolRaceScore(
    playerFrames: Int,
    aiFrames: Int,
): Int = playerFrames - aiFrames

/**
 * 构造台球排行榜条目（纯函数，便于对净胜 / tieBreak 口径做单测）：
 * 对局类 score = 净胜、tieBreak = 失局；练习按清台得分（无 tieBreak）。[label] 仅展示。
 */
internal fun poolLeaderboardEntry(
    practice: Boolean,
    playerFrames: Int,
    aiFrames: Int,
    practiceScore: Int,
    label: String,
    timestamp: Long,
    city: String?,
    weather: String?,
): LeaderboardEntry =
    LeaderboardEntry(
        score = if (practice) practiceScore else poolRaceScore(playerFrames, aiFrames),
        timestamp = timestamp,
        city = city,
        weather = weather,
        label = label,
        tieBreak = if (practice) 0 else aiFrames,
    )

internal fun leaderboardKey(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> "leaderboard_pool_eight_v2"
        PoolMode.NINE_BALL -> "leaderboard_pool_nine_v2"
        PoolMode.PRACTICE, PoolMode.CHALLENGE -> "leaderboard_pool_practice_v2"
    }

internal fun bestModeKey(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> "best_pool_eight_v2"
        PoolMode.NINE_BALL -> "best_pool_nine_v2"
        PoolMode.PRACTICE, PoolMode.CHALLENGE -> "best_pool_practice_v2"
    }

/**
 * 被 v2 取代的旧榜单 / 旧最高分 key：v1 记的是「赢下的局数」而 v2 是净胜，
 * 语义不同无法迁移；旧最高分也是帧数口径，一并清理。
 * 清理时机是用户首次向 v2 榜成功提交或主动清榜，避免一进游戏旧记录就永久消失。
 *
 * 注意：不包含每日挑战榜（`leaderboard_pool_daily_*`）与每日最佳（`pool_daily_*`），
 * 两者是杆数计分口径，不受净胜改版影响。
 */
internal val POOL_LEGACY_LEADERBOARD_KEYS =
    listOf(
        "leaderboard_pool_eight_v1",
        "leaderboard_pool_nine_v1",
        "leaderboard_pool_practice_v1",
        "best_pool_eight",
        "best_pool_nine",
        "best_pool_practice",
    )

internal fun modeKey(mode: PoolMode): String = mode.name.lowercase()

internal fun modeOf(key: String?): PoolMode = runCatching { PoolMode.valueOf(key.orEmpty().uppercase()) }.getOrDefault(PoolMode.EIGHT_BALL)

internal fun difficultyKey(d: PoolAi.Difficulty): String = d.name.lowercase()

internal fun difficultyOf(key: String?): PoolAi.Difficulty =
    runCatching { PoolAi.Difficulty.valueOf(key.orEmpty().uppercase()) }
        .getOrDefault(PoolAi.Difficulty.NORMAL)

internal fun aidsKey(a: Aids): String = a.name.lowercase()

internal fun aidsOf(key: String?): Aids = runCatching { Aids.valueOf(key.orEmpty().uppercase()) }.getOrDefault(Aids.FULL)
