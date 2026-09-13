package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.game.PoolGame.Aids
import com.xiaoxiong.weather.ui.game.PoolGame.Phase
import com.xiaoxiong.weather.ui.game.PoolGame.Spin
import com.xiaoxiong.weather.ui.weather.weatherSceneStyle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PREF_POOL_DIFF = "pool_difficulty"
private const val PREF_POOL_AIDS = "pool_aids"
private const val PREF_POOL_RACE = "pool_race_to"
private const val PREF_POOL_MODE = "pool_mode"

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
private const val PREF_POOL_SOUND = "pool_sound"
private const val PREF_POOL_REDUCE_MOTION = "pool_reduce_motion"
private const val PREF_POOL_REPLAY = "pool_last_replay_v2"

// 关卡 / 每日挑战进度 key（前缀 + id / date）
private const val PREF_LEVEL_CLEARED = "pool_level_cleared_"
private const val PREF_LEVEL_BEST = "pool_level_best_"
private const val PREF_DAILY_BEST = "pool_daily_"

// 新手教学 / 台呢主题
private const val PREF_LESSON_DONE = "pool_lesson_done_"
private const val PREF_POOL_CLOTH = "pool_cloth_theme"

/** 精瞄按钮单步：0.1°（长按连续微调，点击单步）。 */
private val FINE_AIM_STEP_RAD = PoolMath.radians(0.1f)

/** 台呢主题文案资源（顺序即 `pool_cloth_theme` 的取值）。 */
private val CLOTH_THEME_RES =
    listOf(
        R.string.bear_pool_cloth_azure,
        R.string.bear_pool_cloth_honey,
        R.string.bear_pool_cloth_midnight,
        R.string.bear_pool_cloth_sakura,
    )

/** 可选模式：关卡（CHALLENGE）只通过专门入口进入，不出现在普通模式选择器里。 */
private val POOL_MODES = listOf(PoolMode.EIGHT_BALL, PoolMode.NINE_BALL, PoolMode.PRACTICE)

private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private fun dailyLeaderboardKey(dateEpochDay: Long): String = "leaderboard_pool_daily_${dateEpochDay}_v1"

private fun dailyBestKey(dateEpochDay: Long): String = PREF_DAILY_BEST + dateEpochDay

private fun clearedLevelKey(id: Int): String = PREF_LEVEL_CLEARED + id

private fun bestLevelKey(id: Int): String = PREF_LEVEL_BEST + id

private fun challengeDisplayName(c: PoolChallenge): String = if (Locale.getDefault().language.startsWith("en")) c.nameEn else c.nameZh

private fun lessonDoneKey(id: Int): String = PREF_LESSON_DONE + id

private fun loadDoneLessons(prefs: SharedPreferences): Set<Int> =
    PoolTutorial
        .lessons()
        .map { it.id }
        .filter { prefs.getBoolean(lessonDoneKey(it), false) }
        .toSet()

private fun lessonName(lesson: PoolLesson): String = if (Locale.getDefault().language.startsWith("en")) lesson.nameEn else lesson.nameZh

private fun lessonTip(lesson: PoolLesson): String = if (Locale.getDefault().language.startsWith("en")) lesson.tipEn else lesson.tipZh

/** 关卡选择锁定策略：第 1 关常开，其余需上一关已通关。 */
private fun isLevelUnlocked(
    id: Int,
    cleared: Set<Int>,
): Boolean = id <= 1 || (id - 1) in cleared

private fun loadClearedLevels(prefs: SharedPreferences): Set<Int> =
    PoolChallenges
        .campaigns()
        .map { it.id }
        .filter { prefs.getBoolean(clearedLevelKey(it), false) }
        .toSet()

private fun loadBestLevels(prefs: SharedPreferences): Map<Int, Int> =
    PoolChallenges
        .campaigns()
        .mapNotNull { c ->
            val best = prefs.getInt(bestLevelKey(c.id), 0)
            if (best > 0) c.id to best else null
        }.toMap()

/** 结算后用于「新纪录」展示的快照。 */
private data class ChallengeResult(
    val name: String,
    val par: Int,
    val shots: Int,
    val score: Int,
    val newRecord: Boolean,
    val daily: Boolean,
)

/** 需要被「清空统计与成就」清除的 prefs key（排行榜 / 设置 / 最高分不受影响）。 */
private val POOL_STAT_KEYS =
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
private data class StoredShot(
    val angle: Float,
    val power: Float,
    val side: Float,
    val vertical: Float,
    val elevation: Float,
    val x: Float = 0f,
    val y: Float = 0f,
)

/** 最近一场的回放快照：模式 + 重摆种子 + 开球方 + 杆序。 */
private data class StoredReplay(
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
private fun saveReplay(
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

private fun loadReplay(prefs: SharedPreferences): StoredReplay? {
    val raw = runCatching { prefs.getString(PREF_POOL_REPLAY, null) }.getOrNull() ?: return null
    return runCatching {
        val parts = raw.split('|')
        if (parts.size < 4) return@runCatching null
        val shots =
            parts[3]
                .split(';')
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val f = entry.split(',')
                    if (f.size < 5) {
                        null
                    } else {
                        StoredShot(
                            angle = f[0].toFloat(),
                            power = f[1].toFloat(),
                            side = f[2].toFloat(),
                            vertical = f[3].toFloat(),
                            elevation = f[4].toFloat(),
                            x = f.getOrNull(5)?.toFloat() ?: 0f,
                            y = f.getOrNull(6)?.toFloat() ?: 0f,
                        )
                    }
                }
        if (shots.isEmpty()) null else StoredReplay(parts[0], parts[1].toLong(), parts[2], shots)
    }.getOrNull()
}

/** 花色面板/提示里「剩 %d」的高亮色。 */
private val POOL_PLAYER_ACCENT = Color(0xFFFFCC80)
private val POOL_RIVAL_ACCENT = Color(0xFF80DEEA)

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

private fun loadPoolProgress(prefs: SharedPreferences): PoolProgress =
    PoolProgress(
        games = prefs.getInt(PREF_STAT_GAMES, 0),
        wins = prefs.getInt(PREF_STAT_WINS, 0),
        frames = prefs.getInt(PREF_STAT_FRAMES, 0),
        pots = prefs.getInt(PREF_STAT_POTS, 0),
        bestRun = prefs.getInt(PREF_STAT_BEST_RUN, 0),
        breakRuns = prefs.getInt(PREF_STAT_BREAK_RUNS, 0),
        cleanSheets = prefs.getInt(PREF_STAT_CLEAN_SHEETS, 0),
        masterWins = prefs.getInt(PREF_STAT_MASTER_WINS, 0),
        bestShotPots = prefs.getInt(PREF_STAT_BEST_SHOT, 0),
        winStreak = prefs.getInt(PREF_STAT_WIN_STREAK, 0),
        bestWinStreak = prefs.getInt(PREF_STAT_BEST_STREAK, 0),
        nineBallWins = prefs.getInt(PREF_STAT_NINE_WINS, 0),
        practiceClears = prefs.getInt(PREF_STAT_PRACTICE_CLEARS, 0),
        bestPractice = prefs.getInt(PREF_STAT_BEST_PRACTICE, 0),
        unlocked =
            prefs
                .getString(PREF_STAT_ACH_SEEN, null)
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty(),
    )

private fun savePoolProgress(
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

private fun leaderboardKey(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> "leaderboard_pool_eight_v1"
        PoolMode.NINE_BALL -> "leaderboard_pool_nine_v1"
        PoolMode.PRACTICE, PoolMode.CHALLENGE -> "leaderboard_pool_practice_v1"
    }

private fun bestModeKey(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> "best_pool_eight"
        PoolMode.NINE_BALL -> "best_pool_nine"
        PoolMode.PRACTICE, PoolMode.CHALLENGE -> "best_pool_practice"
    }

private fun modeKey(mode: PoolMode): String = mode.name.lowercase()

private fun modeOf(key: String?): PoolMode = runCatching { PoolMode.valueOf(key.orEmpty().uppercase()) }.getOrDefault(PoolMode.EIGHT_BALL)

/**
 * 小熊台球：竖屏八球。玩家与 AI 轮番击球，清空本方花色后打进黑八赢下一局，
 * 先赢下设定局数者获得整场胜利。底部左侧白球控件调整高低杆/左右塞，拖拖桌面蓄力瞄准。
 */
@Composable
fun BearPoolScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
    cityName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val pause = rememberGamePauseState()
    val prefs = rememberGamePrefs()
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showShotHistory by remember { mutableStateOf(false) }
    var doneLessons by remember { mutableStateOf(loadDoneLessons(prefs)) }
    var clothTheme by remember { mutableIntStateOf(prefs.getInt(PREF_POOL_CLOTH, 0).coerceIn(0, CLOTH_THEME_RES.size - 1)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean(PREF_POOL_SOUND, true)) }
    var reduceMotionPref by remember { mutableStateOf(prefs.getBoolean(PREF_POOL_REDUCE_MOTION, false)) }
    var storedReplay by remember { mutableStateOf(loadReplay(prefs)) }
    // 系统级「减弱动态效果」与游戏内设置任一开启即生效
    val systemReduceMotion = LocalReduceMotion.current
    val reduceMotion = reduceMotionPref || systemReduceMotion

    // 短促提示音：惰性创建，退出时释放；创建 / 播放失败静默忽略
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { toneGen?.release() }
            // 退出对局时释放静态台面层的两张全屏 Bitmap；二次进入按需重建
            clearPoolTableLayerCache()
        }
    }

    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val rain = remember(sceneCode) { rainStrengthOf(sceneCode) }
    val freezingRain = remember(sceneCode) { isFreezingRainCode(sceneCode) }
    val hail = remember(sceneCode) { isHailCode(sceneCode) }
    val style = remember(sceneCode, isDay) { weatherSceneStyle(sceneCode, isDay, appDark = false) }

    val game =
        remember {
            PoolGame().also {
                it.difficulty = difficultyOf(prefs.getString(PREF_POOL_DIFF, "normal"))
                it.aids = aidsOf(prefs.getString(PREF_POOL_AIDS, "full"))
                it.raceTo = prefs.getInt(PREF_POOL_RACE, 2).coerceIn(1, 3)
                it.mode = modeOf(prefs.getString(PREF_POOL_MODE, null))
                it.best = prefs.getInt(bestModeKey(it.mode), 0)
                it.reduceMotion = reduceMotion
                it.clothTheme = clothTheme
            }
        }
    LaunchedEffect(reduceMotion) {
        game.reduceMotion = reduceMotion
    }
    LaunchedEffect(clothTheme) {
        game.clothTheme = clothTheme
    }
    // 恢复进行中的对局（旋转屏幕 / 进程被回收后重进）。放 LaunchedEffect 而非
    // remember 初始化块：恢复会写 phase/balls 等 snapshot state，组合期写入会触发额外重组；
    // 且若该次组合被丢弃（如立即导航离开），组合期执行恢复的副作用不应生效。
    LaunchedEffect(Unit) {
        game.restoreSnapshot(prefs)
    }
    // 明确退出（关闭/暂停菜单退出）时抑制 onDispose 的兜底落盘，避免退出后又能续玩
    val snapshotSuppressed = remember { mutableStateOf(false) }
    // 组合销毁（切屏/旋转重建）或按 Home 切入后台（ON_STOP）时把进行中的对局落盘；
    // game.saveSnapshot 内部在 AIMING/SHOOTING/FRAME_OVER 落盘，MATCH_OVER 等阶段自动清档。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) game.saveSnapshot(prefs)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!snapshotSuppressed.value) game.saveSnapshot(prefs)
        }
    }
    // 对局结束（MATCH_OVER）无法续玩，清掉快照；FRAME_OVER 保留以便恢复后继续点下一局
    LaunchedEffect(game.phase) {
        if (game.phase == Phase.MATCH_OVER) {
            game.clearSnapshot(prefs)
        }
    }
    var progress by remember { mutableStateOf(loadPoolProgress(prefs)) }
    var pendingUnlocks by remember { mutableStateOf<List<PoolAchievement>>(emptyList()) }

    // 关卡 / 每日挑战状态
    val todayDate = remember { todayEpochDay() }
    val dailyChallenge = remember(todayDate) { PoolChallenges.daily(todayDate) }
    var showChallenges by remember { mutableStateOf(false) }
    var showDaily by remember { mutableStateOf(false) }
    var showDailyLeaderboard by remember { mutableStateOf(false) }
    var dailyEntries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var clearedLevels by remember { mutableStateOf(loadClearedLevels(prefs)) }
    var bestLevels by remember { mutableStateOf(loadBestLevels(prefs)) }
    var dailyBest by remember(todayDate) { mutableIntStateOf(prefs.getInt(dailyBestKey(todayDate), 0)) }
    var lastChallengeResult by remember { mutableStateOf<ChallengeResult?>(null) }

    // 排行榜按模式分开，练习模式保留高分（其它保留最近局，惨败不被挤出）
    val mode = game.mode
    val lb =
        rememberLeaderboardState(
            prefs,
            leaderboardKey(mode),
            bestModeKey(mode),
            retainRecent = mode != PoolMode.PRACTICE,
        )
    LaunchedEffect(mode) {
        game.best = prefs.getInt(bestModeKey(mode), 0)
        lb.resetLastRank()
    }

    fun startDailyChallenge() {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startChallenge(dailyChallenge, todayDate)
        showDaily = false
    }

    fun startLevel(challenge: PoolChallenge) {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startChallenge(challenge)
        showChallenges = false
    }

    fun startLesson(lesson: PoolLesson) {
        lb.resetLastRank()
        lastChallengeResult = null
        game.startLesson(lesson)
        showTutorial = false
    }

    var frame by remember { mutableLongStateOf(0L) }
    val reportScene by rememberUpdatedState(sceneCode)
    val reportCity by rememberUpdatedState(cityName)

    // 结算落盘 + 提交排行榜（幂等）。BackHandler / LaunchedEffect 进入时都调用，
    // 避免在 MATCH_OVER 浮层出现前退出丢掉整场战绩。
    fun submitResultIfNeeded() {
        if (game.resultSubmitted) return
        if (game.phase != Phase.MATCH_OVER && !game.matchOver()) return
        // 教学不与排行榜 / 生涯统计挂钩
        if (game.lesson != null) {
            game.resultSubmitted = true
            return
        }
        game.resultSubmitted = true

        // 关卡 / 每日挑战：只记录清台成绩，不进入常规排行榜与生涯成就统计
        val challenge = game.challenge
        if (challenge != null) {
            val cleared = game.match.playerFrames > 0
            if (!cleared) return
            val shots = game.shots
            val score = game.challengeScore()
            val daily = challenge.id == PoolChallenges.DAILY_ID
            if (daily) {
                val date = if (game.challengeDate > 0L) game.challengeDate else todayDate
                val bestKey = dailyBestKey(date)
                val prev = prefs.getInt(bestKey, 0)
                val newRecord = score > prev
                if (newRecord) prefs.edit { putInt(bestKey, score) }
                dailyBest = max(prev, score)
                runCatching {
                    GameLeaderboardStore(prefs, dailyLeaderboardKey(date)).submit(
                        LeaderboardEntry(
                            score = score,
                            timestamp = System.currentTimeMillis(),
                            city = reportCity,
                            weather = leaderboardWeatherOf(context, reportScene),
                            label = context.getString(R.string.bear_pool_shots_label, shots),
                        ),
                    )
                }
                lastChallengeResult =
                    ChallengeResult(challengeDisplayName(challenge), challenge.par, shots, score, newRecord, daily = true)
            } else {
                val bestKey = bestLevelKey(challenge.id)
                val prev = prefs.getInt(bestKey, Int.MAX_VALUE)
                val newRecord = shots < prev
                if (newRecord) prefs.edit { putInt(bestKey, shots) }
                prefs.edit { putBoolean(clearedLevelKey(challenge.id), true) }
                clearedLevels = clearedLevels + challenge.id
                bestLevels = bestLevels + (challenge.id to min(prev, shots))
                lastChallengeResult =
                    ChallengeResult(challengeDisplayName(challenge), challenge.par, shots, score, newRecord, daily = false)
            }
            return
        }

        val won = game.match.playerFrames > game.match.aiFrames
        val practice = game.match.mode == PoolMode.PRACTICE
        val cleared = practice && game.match.playerFrames > 0
        val score = if (practice) game.practiceScore() else game.match.playerFrames
        val label =
            if (practice) {
                context.getString(R.string.bear_pool_shots_label, game.shots)
            } else {
                "${game.match.playerFrames} : ${game.match.aiFrames}"
            }

        // 生涯统计：任何模式每场都计入场次/局数/进球/单杆
        val advanced =
            progress.advance(
                won = if (practice) cleared else won,
                framesPlayed = game.match.playerFrames + game.match.aiFrames,
                aiFrames = game.match.aiFrames,
                potsThisMatch = game.matchPots,
                bestShotThisMatch = game.bestShot,
                breakAndRun = game.breakRunsThisMatch > 0,
                mode = game.match.mode,
                difficulty = game.difficulty,
                practiceCleared = cleared,
                practiceScore = if (practice) score else 0,
            )
        // 新解锁成就（弹窗一次）
        val newly = PoolAchievement.entries.filter { it.isUnlocked(advanced) && it.id !in progress.unlocked }
        if (newly.isNotEmpty()) pendingUnlocks = pendingUnlocks + newly
        val withUnlocks =
            if (newly.isEmpty()) {
                advanced
            } else {
                advanced.copy(unlocked = advanced.unlocked + newly.map { it.id })
            }
        progress = withUnlocks
        savePoolProgress(prefs, withUnlocks)

        if (score > game.best) {
            game.best = score
            prefs.edit { putInt(bestModeKey(game.match.mode), score) }
        }
        lb.lastRank =
            lb.store.submit(
                LeaderboardEntry(
                    score = score,
                    timestamp = System.currentTimeMillis(),
                    city = reportCity,
                    weather = leaderboardWeatherOf(context, reportScene),
                    label = label,
                ),
            ) ?: 0
        lb.entries = lb.store.load()
        // 持久化最近一场回放（足够冷启动重现最后一局）
        saveReplay(prefs, game)
    }

    // 明确退出：清掉进行中快照并抑制 onDispose 兜底落盘，避免「退出后又能续玩」
    fun exitGame() {
        snapshotSuppressed.value = true
        game.clearSnapshot(prefs)
        onExit()
    }

    // 暂停菜单「重新开始」：优先重开当前教学 / 关卡 / 每日挑战，否则重开普通对局
    fun restartCurrentGame() {
        // 重开前清掉旧快照，避免新对局尚未落盘时旧的续玩档被恢复
        game.clearSnapshot(prefs)
        val lesson = game.lesson
        val challenge = game.challenge
        when {
            lesson != null -> startLesson(lesson)
            challenge != null ->
                if (challenge.id == PoolChallenges.DAILY_ID) {
                    startDailyChallenge()
                } else {
                    startLevel(challenge)
                }
            else -> {
                lb.resetLastRank()
                game.startMatch()
            }
        }
    }

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            game.phase == Phase.AIMING || game.phase == Phase.SHOOTING -> pause.open()
            else -> {
                submitResultIfNeeded()
                exitGame()
            }
        }
    }

    // READY / FRAME_OVER / MATCH_OVER 无对局物理推进，节流到约 12fps；
    // dt 仍按真实帧累计后整笔提交，避免慢动作计时/动画失真（对齐 hockey/jump）。
    var slowTick by remember { mutableStateOf(0) }
    var animDt by remember { mutableFloatStateOf(0f) }
    GameFrameLoop(
        paused = { lb.show || showSettings || showCharacterPicker || showAchievements || pendingUnlocks.isNotEmpty() || pause.paused },
        onTick = { dt, now ->
            val active = game.phase == Phase.AIMING || game.phase == Phase.SHOOTING
            animDt += dt
            if (active || slowTick % 5 == 0) {
                val worldDt = animDt
                animDt = 0f
                game.tick(worldDt)
                if (game.pendingHapticCue) {
                    game.pendingHapticCue = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (soundOn && !reduceMotion) playTone(toneGen, ToneGenerator.TONE_PROP_BEEP, 60)
                }
                if (game.pendingHapticPot) {
                    game.pendingHapticPot = false
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (soundOn && !reduceMotion) playTone(toneGen, ToneGenerator.TONE_PROP_ACK, 80)
                }
                if (game.pendingHapticRail) {
                    game.pendingHapticRail = false
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (soundOn && !reduceMotion) playTone(toneGen, ToneGenerator.TONE_PROP_BEEP2, 40)
                }
                frame = now
            }
            if (!active) slowTick++
        },
    )

    LaunchedEffect(game.phase) {
        if (game.phase == Phase.MATCH_OVER) submitResultIfNeeded()
    }

    // 教学通过：持久化通过状态（按课次）。
    LaunchedEffect(game.lesson?.id, game.lessonPassed) {
        val lesson = game.lesson
        if (lesson != null && game.lessonPassed && lesson.id !in doneLessons) {
            prefs.edit { putBoolean(lessonDoneKey(lesson.id), true) }
            doneLessons = doneLessons + lesson.id
        }
    }

    val poolTableCd = stringResource(R.string.cd_pool_table)
    // 底部控制区高度：把台面布局让出来，避免杆法盘/力度条压住台呢与左下角袋口。
    var controlsBandPx by remember { mutableIntStateOf(0) }
    val controlsBandDp = with(LocalDensity.current) { controlsBandPx.toDp() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(style.gradient)),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = controlsBandDp)
                    .semantics { contentDescription = poolTableCd }
                    .onSizeChanged { sz -> game.resize(sz.width.toFloat(), sz.height.toFloat()) }
                    .pointerInput(Unit) {
                        // 不以 phase 为 key：阶段切换会取消重建手势协程，手指已按下的拖动会「死掉」
                        // 直到抬手重按。READY / 结算阶段本就不读触摸点，常开手势无副作用。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            game.onDown(down.position.x, down.position.y)
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        game.onMove(change.position.x, change.position.y)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                game.onUp()
                            }
                        }
                    },
        ) {
            frame // 每帧触发重绘
            val day = isDay ?: true
            drawPoolWeather(game, weather, rain, freezingRain, hail, style, day, reduceMotion)
            drawPoolTable(game, style, day)
            drawPoolBalls(game, style, day)
            drawPoolAim(game, game.aids)
            drawPoolEffects(game)
        }

        if (game.phase != Phase.READY && !game.replaying) {
            PoolScoreboard(game)
        }

        // 教学进行中：顶部提示 + 目标
        game.lesson?.let { lesson ->
            if (game.phase != Phase.READY && game.phase != Phase.MATCH_OVER && !game.replaying) {
                PoolLessonBanner(lesson)
            }
        }

        // 击球历史面板（可展开）
        if (showShotHistory && game.phase != Phase.READY && !game.replaying) {
            ShotHistoryPanel(game.shotHistory)
        }

        if ((game.phase == Phase.AIMING || game.phase == Phase.SHOOTING) && !game.replaying) {
            PoolControls(
                game = game,
                onBeginPlacing = { game.beginPlacing() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { controlsBandPx = it.height },
            )
        }

        if (game.replaying) {
            ReplayOverlay(
                progress = game.replayProgress,
                total = game.replayTotal,
                onStop = { game.stopReplay() },
            )
        }

        when (game.phase) {
            Phase.READY -> {
                OverlayScrim {
                    Text(
                        text = stringResource(R.string.bear_pool_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4E342E),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_pool_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6D4C41),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    ModeSelector(
                        mode = game.mode,
                        onSelect = {
                            game.mode = it
                            prefs.edit { putString(PREF_POOL_MODE, modeKey(it)) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = configLine(game),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8D6E63),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            lb.resetLastRank()
                            game.startMatch()
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_start))
                    }
                    TextButton(onClick = { showChallenges = true }) {
                        Text(stringResource(R.string.bear_pool_levels))
                    }
                    TextButton(
                        onClick = {
                            doneLessons = loadDoneLessons(prefs)
                            showTutorial = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_tutorial))
                    }
                    TextButton(
                        onClick = {
                            dailyBest = prefs.getInt(dailyBestKey(todayDate), 0)
                            showDaily = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_daily))
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.bear_pool_settings))
                    }
                    TextButton(
                        onClick = {
                            lb.entries = lb.store.load()
                            lb.show = true
                        },
                    ) {
                        Text(stringResource(R.string.bear_pool_leaderboard))
                    }
                    TextButton(onClick = { showAchievements = true }) {
                        Text(stringResource(R.string.bear_pool_achievements))
                    }
                    storedReplay?.let { stored ->
                        TextButton(
                            onClick = {
                                game.loadReplay(
                                    mode = modeOf(stored.mode),
                                    seed = stored.seed,
                                    breaker = runCatching { PoolSide.valueOf(stored.breaker) }.getOrDefault(PoolSide.PLAYER),
                                    shots =
                                        stored.shots.map {
                                            PoolGame.RecordedShot(it.angle, it.power, it.side, it.vertical, it.elevation, it.x, it.y)
                                        },
                                )
                                if (game.startReplay()) {
                                    lb.resetLastRank()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_replay_last))
                        }
                    }
                }
            }

            Phase.FRAME_OVER -> {
                OverlayScrim {
                    Text(
                        text =
                            if (game.frameWinner == PoolSide.PLAYER) {
                                stringResource(R.string.bear_pool_frame_win)
                            } else {
                                stringResource(R.string.bear_pool_frame_lose)
                            },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4E342E),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_pool_frames, game.match.playerFrames, game.match.aiFrames),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6D4C41),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { game.nextFrame() }) {
                        Text(stringResource(R.string.bear_pool_next_frame))
                    }
                    if (game.replayShots().isNotEmpty()) {
                        TextButton(onClick = { game.startReplay() }) {
                            Text(stringResource(R.string.bear_pool_replay))
                        }
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.bear_pool_settings))
                    }
                }
            }

            Phase.MATCH_OVER -> {
                val challenge = game.challenge
                val lesson = game.lesson
                val playerWon = game.match.playerFrames >= game.raceTo
                val practice = game.mode == PoolMode.PRACTICE
                if (lesson != null) {
                    PoolLessonResultOverlay(
                        lesson = lesson,
                        passed = game.lessonPassed,
                        onRetry = { startLesson(lesson) },
                        onNext = { next -> startLesson(next) },
                        onMenu = { game.returnToReady() },
                    )
                } else if (challenge != null) {
                    PoolChallengeResultOverlay(
                        game = game,
                        challenge = challenge,
                        result = lastChallengeResult,
                        dailyBest = dailyBest,
                        onReplay = {
                            if (challenge.id == PoolChallenges.DAILY_ID) {
                                startDailyChallenge()
                            } else {
                                startLevel(challenge)
                            }
                        },
                        onNextLevel = {
                            PoolChallenges.campaigns().firstOrNull { it.id == challenge.id + 1 }?.let { startLevel(it) }
                        },
                        onLeaderboard = {
                            dailyEntries =
                                GameLeaderboardStore(prefs, dailyLeaderboardKey(game.challengeDate.takeIf { it > 0L } ?: todayDate)).load()
                            showDailyLeaderboard = true
                        },
                        onMenu = { game.returnToReady() },
                    )
                } else {
                    OverlayScrim {
                        Text(
                            text =
                                if (practice) {
                                    stringResource(R.string.bear_pool_practice_done)
                                } else if (playerWon) {
                                    stringResource(R.string.bear_pool_win)
                                } else {
                                    stringResource(R.string.bear_pool_lose)
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E342E),
                        )
                        Spacer(Modifier.height(8.dp))
                        if (practice) {
                            Text(
                                text = stringResource(R.string.bear_pool_practice_stats, game.shots, game.practiceScore()),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF6D4C41),
                            )
                            Text(
                                text = stringResource(R.string.bear_pool_pots_value, game.matchPots),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF8D6E63),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.bear_pool_frames, game.match.playerFrames, game.match.aiFrames),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF6D4C41),
                            )
                        }
                        if (lb.lastRank > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.bear_leaderboard_rank_label, lb.lastRank),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                lb.resetLastRank()
                                game.startMatch()
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_rematch))
                        }
                        if (game.replayShots().isNotEmpty()) {
                            TextButton(onClick = { game.startReplay() }) {
                                Text(stringResource(R.string.bear_pool_replay))
                            }
                        }
                        TextButton(
                            onClick = {
                                lb.entries = lb.store.load()
                                lb.show = true
                            },
                        ) {
                            Text(stringResource(R.string.bear_pool_leaderboard))
                        }
                        TextButton(onClick = { showAchievements = true }) {
                            Text(stringResource(R.string.bear_pool_achievements))
                        }
                        TextButton(onClick = { showSettings = true }) {
                            Text(stringResource(R.string.bear_pool_settings))
                        }
                    }
                }
            }

            else -> Unit
        }

        // 顶栏放最后：即使结算浮层铺满全屏，关闭 / 设置 / 排行榜仍可点击
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { exitGame() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_game),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.bear_pool_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            GameCharacterButton(onClick = { showCharacterPicker = true })
            IconButton(onClick = { showShotHistory = !showShotHistory }) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.bear_pool_history),
                    tint = if (showShotHistory) POOL_PLAYER_ACCENT else Color.White,
                )
            }
            if (game.phase == Phase.AIMING || game.phase == Phase.SHOOTING) {
                GamePauseButton(onClick = { pause.open() })
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.bear_pool_settings),
                    tint = Color.White,
                )
            }
            LeaderboardButton(
                onClick = {
                    lb.entries = lb.store.load()
                    lb.show = true
                },
            )
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                scoreText = "${game.match.playerFrames} : ${game.match.aiFrames}",
                onResume = pause::resume,
                onRestart = {
                    pause.resume()
                    restartCurrentGame()
                },
                onSettings = {
                    pause.resume()
                    showSettings = true
                },
                onExit = {
                    submitResultIfNeeded()
                    exitGame()
                },
            )
        }
    }

    if (showSettings) {
        PoolSettingsDialog(
            difficulty = game.difficulty,
            aids = game.aids,
            raceTo = game.raceTo,
            mode = game.mode,
            soundOn = soundOn,
            reduceMotion = reduceMotionPref,
            clothTheme = clothTheme,
            dailyBest = dailyBest,
            onOpenDaily = {
                dailyBest = prefs.getInt(dailyBestKey(todayDate), 0)
                showSettings = false
                showDaily = true
            },
            onDismiss = { showSettings = false },
            onSave = { d, a, r, m, s, rm, cl ->
                game.difficulty = d
                game.aids = a
                // 对局进行中不改变赛制/模式（否则 raceTo 变小会当场判 matchOver，成就口径也被污染）
                val canChangeRule = game.phase == Phase.READY || game.phase == Phase.MATCH_OVER
                val race = r.coerceIn(1, 3)
                if (canChangeRule) {
                    game.raceTo = race
                    game.mode = m
                }
                soundOn = s
                reduceMotionPref = rm
                game.reduceMotion = rm || systemReduceMotion
                clothTheme = cl.coerceIn(0, CLOTH_THEME_RES.size - 1)
                game.clothTheme = clothTheme
                prefs.edit {
                    putString(PREF_POOL_DIFF, difficultyKey(d))
                    putString(PREF_POOL_AIDS, aidsKey(a))
                    putInt(PREF_POOL_RACE, r.coerceIn(1, 3))
                    putString(PREF_POOL_MODE, modeKey(m))
                    putBoolean(PREF_POOL_SOUND, s)
                    putBoolean(PREF_POOL_REDUCE_MOTION, rm)
                    putInt(PREF_POOL_CLOTH, clothTheme)
                }
                showSettings = false
            },
        )
    }

    if (lb.show) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_pool_title),
            entries = lb.entries,
            onClear = {
                lb.store.clear()
                lb.entries = emptyList()
                lb.resetLastRank()
                game.best = 0
            },
            onDismiss = { lb.show = false },
        )
    }

    if (showCharacterPicker) {
        GameCharacterPickerDialog(
            state = characterState,
            onDismiss = { showCharacterPicker = false },
        )
    }

    if (showAchievements) {
        PoolAchievementsDialog(
            progress = progress,
            onDismiss = { showAchievements = false },
            onClear = {
                prefs.edit { POOL_STAT_KEYS.forEach { remove(it) } }
                progress = PoolProgress()
                pendingUnlocks = emptyList()
                storedReplay = null
            },
        )
    }

    if (showChallenges) {
        PoolChallengesDialog(
            cleared = clearedLevels,
            best = bestLevels,
            onStart = { startLevel(it) },
            onDismiss = { showChallenges = false },
        )
    }

    if (showTutorial) {
        PoolTutorialDialog(
            done = doneLessons,
            onStart = { startLesson(it) },
            onDismiss = { showTutorial = false },
        )
    }

    if (showDaily) {
        PoolDailyDialog(
            challenge = dailyChallenge,
            todayBest = dailyBest,
            onStart = { startDailyChallenge() },
            onLeaderboard = {
                dailyEntries = GameLeaderboardStore(prefs, dailyLeaderboardKey(todayDate)).load()
                showDaily = false
                showDailyLeaderboard = true
            },
            onDismiss = { showDaily = false },
        )
    }

    if (showDailyLeaderboard) {
        GameLeaderboardDialog(
            title = stringResource(R.string.bear_pool_daily_leaderboard),
            entries = dailyEntries,
            onClear = {
                GameLeaderboardStore(prefs, dailyLeaderboardKey(game.challengeDate.takeIf { it > 0L } ?: todayDate)).clear()
                dailyEntries = emptyList()
            },
            onDismiss = { showDailyLeaderboard = false },
        )
    }

    if (pendingUnlocks.isNotEmpty()) {
        PoolUnlockDialog(
            achievements = pendingUnlocks,
            onDismiss = { pendingUnlocks = emptyList() },
        )
    }
}

/** 比分/球组面板：双方局分、当前该打的花色与剩余球数、回合高亮、提示横幅。 */
@Composable
private fun BoxScope.PoolScoreboard(game: PoolGame) {
    val remaining = PoolRules.remaining(game.balls)
    val playerTurn = game.match.turn == PoolSide.PLAYER
    Column(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScoreSide(
                    label = stringResource(R.string.bear_pool_you),
                    frames = game.match.playerFrames,
                    groupLine = groupLine(game, PoolSide.PLAYER, remaining),
                    active = playerTurn,
                    accent = POOL_PLAYER_ACCENT,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bear_pool_frames, game.match.playerFrames, game.match.aiFrames),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.bear_pool_race_to_value, game.raceTo),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text =
                            stringResource(
                                if (playerTurn) R.string.bear_pool_turn_you else R.string.bear_pool_turn_rival,
                            ),
                        color = if (playerTurn) POOL_PLAYER_ACCENT else POOL_RIVAL_ACCENT,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                ScoreSide(
                    label = stringResource(R.string.bear_pool_rival),
                    frames = game.match.aiFrames,
                    groupLine = groupLine(game, PoolSide.AI, remaining),
                    active = !playerTurn,
                    accent = POOL_RIVAL_ACCENT,
                )
            }
        }
        val msg = poolMessageText(game.message)
        if (msg.isNotEmpty() && game.messageT > 0f) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.4f),
            ) {
                Text(
                    text = msg,
                    modifier =
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .alpha((game.messageT / 0.6f).coerceIn(0f, 1f)),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ScoreSide(
    label: String,
    frames: Int,
    groupLine: String,
    active: Boolean,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            if (active) {
                Modifier
                    .background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            },
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelMedium)
        Text(
            text = "$frames",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = groupLine,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 底部控件：左侧白球杆法盘、中间力度条、右侧自由球放置入口。 */
@Composable
private fun PoolControls(
    game: PoolGame,
    onBeginPlacing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        SpinControl(
            game = game,
            enabled = game.canPlayerShoot(),
            modifier = Modifier.align(Alignment.BottomStart),
        )
        ElevationControl(
            elevation = game.elevation,
            spin = game.spin,
            enabled = game.canPlayerShoot(),
            onElevation = { game.updateElevation(it) },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 106.dp),
        )
        PowerBar(
            power = game.power,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        FineAimControl(
            angle = game.aimAngle,
            enabled = game.canPlayerShoot(),
            onNudge = { game.nudgeAim(it) },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp),
        )
        if (game.isPlayerTurn() && game.ballInHand) {
            TextButton(
                onClick = onBeginPlacing,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            ) {
                Text(
                    text = stringResource(R.string.bear_pool_place_hint),
                    color = Color.White,
                )
            }
        }
    }
}

/** 圆形「白球」杆法控件：拖动圆内小点设置侧塞/高低杆（-1..1，映射到控件半径）。 */
@Composable
private fun SpinControl(
    game: PoolGame,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val spinCd = stringResource(R.string.cd_pool_spin)
        Canvas(
            modifier =
                Modifier
                    .size(80.dp)
                    .alpha(if (enabled) 1f else 0.45f)
                    .semantics { contentDescription = spinCd }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        val radius = (min(size.width, size.height) / 2f).coerceAtLeast(1f)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            applySpin(game, down.position, radius)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    applySpin(game, change.position, radius)
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        ) {
            drawSpinPad(game.spin)
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.35f),
        ) {
            Text(
                text = stringResource(R.string.bear_pool_spin) + " " + spinHint(game.spin),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 抬杆控件：0..1 映射 [PoolGame.updateElevation]，并提示弧线可能。 */
@Composable
private fun ElevationControl(
    elevation: Float,
    spin: Spin,
    enabled: Boolean,
    onElevation: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(150.dp)
                .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.35f)) {
            Text(
                text = stringResource(R.string.bear_pool_elevation) + " " + elevationHint(elevation),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val elevCd = stringResource(R.string.cd_pool_elevation)
        Slider(
            value = elevation,
            onValueChange = onElevation,
            enabled = enabled,
            modifier =
                Modifier
                    .height(30.dp)
                    .semantics { contentDescription = elevCd },
        )
        if (elevation > 0.3f && abs(spin.side) > 0.2f) {
            Text(
                text = stringResource(R.string.bear_pool_curve_hint),
                color = POOL_PLAYER_ACCENT,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 水平力度条：读 [PoolGame.power]（0..1）。 */
@Composable
private fun PowerBar(
    power: Float,
    modifier: Modifier = Modifier,
) {
    val powerCd = stringResource(R.string.cd_pool_power)
    Column(
        modifier = modifier.semantics { contentDescription = powerCd },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.bear_pool_power),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .width(150.dp)
                    .height(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(power.coerceIn(0f, 1f))
                        .height(12.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFE53935)),
                            ),
                            RoundedCornerShape(6.dp),
                        ),
            )
        }
    }
}

/** 精瞄：左右微调按钮 + 当前角度（度）。点击单步 0.1°，长按连续微调。 */
@Composable
private fun FineAimControl(
    angle: Float,
    enabled: Boolean,
    onNudge: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deg = PoolMath.degrees(PoolMath.normalizeAngle(angle))
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FineAimButton(
            label = "◀",
            cd = stringResource(R.string.bear_pool_fine_aim_left),
            enabled = enabled,
            onNudge = { onNudge(-FINE_AIM_STEP_RAD) },
        )
        Surface(
            modifier = Modifier.padding(horizontal = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.35f),
        ) {
            Text(
                text = stringResource(R.string.bear_pool_aim_angle, deg),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        FineAimButton(
            label = "▶",
            cd = stringResource(R.string.bear_pool_fine_aim_right),
            enabled = enabled,
            onNudge = { onNudge(FINE_AIM_STEP_RAD) },
        )
    }
}

/** 精瞄微调按钮：按下立即单步，按住后约每 70ms 连续微调。 */
@Composable
private fun FineAimButton(
    label: String,
    cd: String,
    enabled: Boolean,
    onNudge: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        onNudge()
        while (true) {
            delay(70)
            onNudge()
        }
    }
    Surface(
        modifier =
            Modifier
                .semantics { contentDescription = cd }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {},
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 结算 / 主菜单浮层：全屏压暗并拦截点击，中央白卡承载内容。 */
@Composable
private fun OverlayScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF2FFF8EF),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/** 回放中的轻量覆盖层：显示进度与停止入口，不遮挡桌面主体。 */
@Composable
private fun BoxScope.ReplayOverlay(
    progress: Int,
    total: Int,
    onStop: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 130.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bear_pool_replay_progress, progress, total),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onStop) {
                Text(
                    text = stringResource(R.string.bear_pool_replay_stop),
                    color = POOL_PLAYER_ACCENT,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 教学进行中顶部横幅：课次 + 名称 + 提示 + 目标。 */
@Composable
private fun BoxScope.PoolLessonBanner(lesson: PoolLesson) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 150.dp, start = 16.dp, end = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.52f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.bear_pool_lesson_banner, lesson.id, lessonName(lesson)),
                color = POOL_PLAYER_ACCENT,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = lessonTip(lesson),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(R.string.bear_pool_lesson_goal, lessonGoalLabel(lesson.goal)),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 教学结算浮层：显示是否通过，提供重试 / 下一课 / 返回。 */
@Composable
private fun PoolLessonResultOverlay(
    lesson: PoolLesson,
    passed: Boolean,
    onRetry: () -> Unit,
    onNext: (PoolLesson) -> Unit,
    onMenu: () -> Unit,
) {
    val next = PoolTutorial.lessons().firstOrNull { it.id == lesson.id + 1 }
    OverlayScrim {
        Text(
            text =
                stringResource(
                    if (passed) R.string.bear_pool_lesson_pass_title else R.string.bear_pool_lesson_fail_title,
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${lesson.id}. ${lessonName(lesson)}",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF6D4C41),
        )
        Text(
            text = stringResource(R.string.bear_pool_lesson_goal, lessonGoalLabel(lesson.goal)),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8D6E63),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.bear_pool_lesson_retry)) }
        if (passed && next != null) {
            TextButton(onClick = { onNext(next) }) { Text(stringResource(R.string.bear_pool_next_level)) }
        }
        TextButton(onClick = onMenu) { Text(stringResource(R.string.bear_pool_back_menu)) }
    }
}

/** 教学列表：5 节，显示是否通过，点击进入。 */
@Composable
private fun PoolTutorialDialog(
    done: Set<Int>,
    onStart: (PoolLesson) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(18.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFF8EF),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_pool_tutorial),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Spacer(Modifier.height(12.dp))
                PoolTutorial.lessons().forEach { lesson ->
                    val cleared = lesson.id in done
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onStart(lesson) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (cleared) Color(0xFFC8E6C9) else Color(0xFFFFECB3),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${lesson.id}. ${lessonName(lesson)}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4E342E),
                                )
                                if (cleared) {
                                    Text(
                                        text = stringResource(R.string.bear_pool_lesson_pass),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.bear_pool_lesson_goal, lessonGoalLabel(lesson.goal)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8D6E63),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.bear_pool_close)) }
            }
        }
    }
}

/** 击球历史面板：展示最近若干杆的双方、进球、犯规/白球落袋。 */
@Composable
private fun BoxScope.ShotHistoryPanel(shots: List<ShotSummary>) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 64.dp)
                .width(210.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.bear_pool_history_title),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (shots.isEmpty()) {
                Text(
                    text = stringResource(R.string.bear_pool_history_empty),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                shots
                    .takeLast(8)
                    .reversed()
                    .forEach { shot ->
                        Text(
                            text = shotSummaryText(shot),
                            color = if (shot.foul || shot.scratch) Color(0xFFFFAB91) else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
            }
        }
    }
}

@Composable
private fun shotSummaryText(shot: ShotSummary): String {
    val who = stringResource(if (shot.turn == PoolSide.PLAYER) R.string.bear_pool_you else R.string.bear_pool_rival)
    val potted =
        if (shot.pottedNumbers.isEmpty()) {
            stringResource(R.string.bear_pool_history_none)
        } else {
            stringResource(R.string.bear_pool_history_potted, shot.pottedNumbers.joinToString(","))
        }
    val flags =
        buildList {
            if (shot.foul) add(stringResource(R.string.bear_pool_history_foul))
            if (shot.scratch) add(stringResource(R.string.bear_pool_history_scratch))
        }.joinToString(" ")
    val base = stringResource(R.string.bear_pool_history_shot, shot.index) + " · " + who + " · " + potted
    return if (flags.isEmpty()) base else "$base · $flags"
}

@Composable
private fun lessonGoalLabel(goal: Goal): String =
    when (goal) {
        Goal.POT_ANY -> stringResource(R.string.bear_pool_goal_pot_any)
        Goal.POT_ONE -> stringResource(R.string.bear_pool_goal_pot_one)
        Goal.CLEAR -> stringResource(R.string.bear_pool_goal_clear)
        Goal.DRAW_BACK -> stringResource(R.string.bear_pool_goal_draw_back)
        Goal.BANK_SHOT -> stringResource(R.string.bear_pool_goal_bank_shot)
    }

/** 设置弹窗：模式 3 档 / 难度 5 档 / 瞄准辅助 3 档 / 赛制先到 1、2、3 局 / 音效 / 减弱动画。 */
@Composable
private fun PoolSettingsDialog(
    difficulty: PoolAi.Difficulty,
    aids: Aids,
    raceTo: Int,
    mode: PoolMode,
    soundOn: Boolean,
    reduceMotion: Boolean,
    clothTheme: Int,
    dailyBest: Int,
    onOpenDaily: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PoolAi.Difficulty, Aids, Int, PoolMode, Boolean, Boolean, Int) -> Unit,
) {
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var a by remember(aids) { mutableStateOf(aids) }
    var r by remember(raceTo) { mutableStateOf(raceTo) }
    var m by remember(mode) { mutableStateOf(mode) }
    var s by remember(soundOn) { mutableStateOf(soundOn) }
    var rm by remember(reduceMotion) { mutableStateOf(reduceMotion) }
    var cl by remember(clothTheme) { mutableStateOf(clothTheme.coerceIn(0, CLOTH_THEME_RES.size - 1)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF8EF)) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_pool_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_mode), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    POOL_MODES.forEach { opt ->
                        FilterChip(
                            selected = m == opt,
                            onClick = { m = opt },
                            label = { Text(modeLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_difficulty), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PoolAi.Difficulty.NOVICE, PoolAi.Difficulty.EASY, PoolAi.Difficulty.NORMAL).forEach { opt ->
                        FilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PoolAi.Difficulty.HARD, PoolAi.Difficulty.MASTER).forEach { opt ->
                        FilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_aids), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Aids.entries.forEach { opt ->
                        FilterChip(
                            selected = a == opt,
                            onClick = { a = opt },
                            label = { Text(aidsLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_race_to), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { n ->
                        FilterChip(
                            selected = r == n,
                            onClick = { r = n },
                            label = { Text(raceLabel(n)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_cloth), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                CLOTH_THEME_RES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { res ->
                            val index = CLOTH_THEME_RES.indexOf(res)
                            FilterChip(
                                selected = cl == index,
                                onClick = { cl = index },
                                label = { Text(stringResource(res)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bear_pool_daily),
                            color = Color(0xFF6D4C41),
                        )
                        Text(
                            text = stringResource(R.string.bear_pool_daily_best, dailyBest),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8D6E63),
                        )
                    }
                    TextButton(onClick = onOpenDaily) {
                        Text(stringResource(R.string.bear_pool_daily_enter))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bear_pool_sound),
                        color = Color(0xFF6D4C41),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = s, onCheckedChange = { s = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bear_pool_reduce_motion),
                        color = Color(0xFF6D4C41),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = rm, onCheckedChange = { rm = it })
                }
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.bear_pool_cancel))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(d, a, r.coerceIn(1, 3), m, s, rm, cl) }) {
                        Text(stringResource(R.string.bear_pool_save))
                    }
                }
            }
        }
    }
}

/** 模式选择chip：八球 / 九球 / 练习。 */
@Composable
private fun ModeSelector(
    mode: PoolMode,
    onSelect: (PoolMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        POOL_MODES.forEach { opt ->
            FilterChip(
                selected = mode == opt,
                onClick = { onSelect(opt) },
                label = { Text(modeLabel(opt)) },
            )
        }
    }
}

/** 生涯成就面板：列出全部成就与解锁状态及统计。 */
@Composable
private fun PoolAchievementsDialog(
    progress: PoolProgress,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFF8EF),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_pool_achievements),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        R.string.bear_pool_stats_line,
                        progress.games,
                        progress.wins,
                        progress.frames,
                        progress.pots,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6D4C41),
                )
                Text(
                    stringResource(
                        R.string.bear_pool_stats_line2,
                        progress.bestRun,
                        progress.breakRuns,
                        progress.bestWinStreak,
                        progress.bestPractice,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6D4C41),
                )
                Spacer(Modifier.height(12.dp))
                PoolAchievement.entries.forEach { ach ->
                    val unlocked = ach.isUnlocked(progress)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (unlocked) "★" else "☆",
                            color = if (unlocked) Color(0xFFFFA000) else Color(0xFFBDBDBD),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = achievementTitle(ach),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) Color(0xFF4E342E) else Color(0xFF9E9E9E),
                            )
                            Text(
                                text = achievementDesc(ach),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8D6E63),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(
                            text = stringResource(R.string.bear_pool_clear_stats),
                            color = Color(0xFFC62828),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.bear_pool_save)) }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.bear_pool_clear_stats)) },
            text = { Text(stringResource(R.string.bear_pool_clear_stats_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.bear_pool_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.bear_pool_cancel))
                }
            },
        )
    }
}

/** 新成就解锁弹窗。 */
@Composable
private fun PoolUnlockDialog(
    achievements: List<PoolAchievement>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF8EF)) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_pool_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                )
                Spacer(Modifier.height(10.dp))
                achievements.forEach { ach ->
                    Text(
                        text = "★ " + achievementTitle(ach),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4E342E),
                    )
                    Text(
                        text = achievementDesc(ach),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8D6E63),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.bear_pool_unlock_ok)) }
            }
        }
    }
}

/** 关卡 / 每日挑战清台结算浮层：杆数 / par / 得分 / 新纪录，以及重玩 / 下一关 / 返回。 */
@Composable
private fun PoolChallengeResultOverlay(
    game: PoolGame,
    challenge: PoolChallenge,
    result: ChallengeResult?,
    dailyBest: Int,
    onReplay: () -> Unit,
    onNextLevel: () -> Unit,
    onLeaderboard: () -> Unit,
    onMenu: () -> Unit,
) {
    val daily = challenge.id == PoolChallenges.DAILY_ID
    val hasNext = !daily && PoolChallenges.campaigns().any { it.id == challenge.id + 1 }
    OverlayScrim {
        Text(
            text = stringResource(if (daily) R.string.bear_pool_daily_done else R.string.bear_pool_challenge_done),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4E342E),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text =
                challengeDisplayName(challenge) +
                    " · " +
                    stringResource(R.string.bear_pool_level_par, challenge.par),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF6D4C41),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bear_pool_challenge_stats, game.shots, challenge.par, game.challengeScore()),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF6D4C41),
        )
        if (result?.newRecord == true) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.bear_pool_new_record),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100),
            )
        }
        if (daily) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.bear_pool_daily_best, dailyBest),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8D6E63),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onReplay) { Text(stringResource(R.string.bear_pool_challenge_replay)) }
        if (hasNext) {
            TextButton(onClick = onNextLevel) { Text(stringResource(R.string.bear_pool_next_level)) }
        }
        if (daily) {
            TextButton(onClick = onLeaderboard) { Text(stringResource(R.string.bear_pool_daily_leaderboard)) }
        }
        TextButton(onClick = onMenu) { Text(stringResource(R.string.bear_pool_back_menu)) }
    }
}

/** 关卡网格：3 列 × 4 行，显示是否通关 / 最佳杆数 / par；未解锁关卡灰显不可点。 */
@Composable
private fun PoolChallengesDialog(
    cleared: Set<Int>,
    best: Map<Int, Int>,
    onStart: (PoolChallenge) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(18.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFF8EF),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_pool_level_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Spacer(Modifier.height(12.dp))
                PoolChallenges.campaigns().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            LevelCell(
                                challenge = c,
                                unlocked = isLevelUnlocked(c.id, cleared),
                                cleared = c.id in cleared,
                                bestShots = best[c.id],
                                onClick = { onStart(c) },
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.width(96.dp)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.bear_pool_close)) }
            }
        }
    }
}

@Composable
private fun LevelCell(
    challenge: PoolChallenge,
    unlocked: Boolean,
    cleared: Boolean,
    bestShots: Int?,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .width(96.dp)
                .clickable(enabled = unlocked, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (unlocked) Color(0xFFFFECB3) else Color(0xFFE0E0E0),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${challenge.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) Color(0xFF4E342E) else Color(0xFF9E9E9E),
            )
            Text(
                text = challengeDisplayName(challenge),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6D4C41),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.bear_pool_level_par, challenge.par),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8D6E63),
            )
            Text(
                text =
                    when {
                        !unlocked -> stringResource(R.string.bear_pool_level_locked)
                        bestShots != null -> stringResource(R.string.bear_pool_level_best, bestShots)
                        cleared -> stringResource(R.string.bear_pool_level_cleared)
                        else -> ""
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (cleared) Color(0xFF2E7D32) else Color(0xFF8D6E63),
                maxLines = 1,
            )
        }
    }
}

/** 每日挑战卡片：显示今日布局规模 / par 与今日最佳，提供开始与今日排行榜入口。 */
@Composable
private fun PoolDailyDialog(
    challenge: PoolChallenge,
    todayBest: Int,
    onStart: () -> Unit,
    onLeaderboard: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(18.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFF8EF),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_pool_daily),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4E342E),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.bear_pool_daily_desc, challenge.targetCount, challenge.par),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6D4C41),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bear_pool_daily_best, todayBest),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE65100),
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onStart) { Text(stringResource(R.string.bear_pool_daily_start)) }
                TextButton(onClick = onLeaderboard) { Text(stringResource(R.string.bear_pool_daily_leaderboard)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.bear_pool_close)) }
            }
        }
    }
}

@Composable
private fun configLine(game: PoolGame): String =
    modeLabel(game.mode) +
        " · " +
        difficultyLabel(game.difficulty) +
        " · " +
        aidsLabel(game.aids) +
        " · " +
        stringResource(R.string.bear_pool_race_to_value, game.raceTo)

@Composable
private fun groupLine(
    game: PoolGame,
    side: PoolSide,
    remaining: PoolRules.RemainingBalls,
): String =
    when (game.match.mode) {
        PoolMode.NINE_BALL -> {
            val lowest = PoolRules.lowestBall(game.balls)?.number
            if (lowest != null) {
                stringResource(R.string.bear_pool_target_lowest, lowest)
            } else {
                stringResource(R.string.bear_pool_target_open)
            }
        }
        PoolMode.PRACTICE, PoolMode.CHALLENGE ->
            stringResource(R.string.bear_pool_remaining, remaining.total)
        PoolMode.EIGHT_BALL ->
            when (game.match.groupOf(side)) {
                PoolGroup.SOLIDS ->
                    if (remaining.solids == 0) {
                        stringResource(R.string.bear_pool_target_eight)
                    } else {
                        stringResource(R.string.bear_pool_target_solids) +
                            " · " +
                            stringResource(R.string.bear_pool_remaining, remaining.solids)
                    }
                PoolGroup.STRIPES ->
                    if (remaining.stripes == 0) {
                        stringResource(R.string.bear_pool_target_eight)
                    } else {
                        stringResource(R.string.bear_pool_target_stripes) +
                            " · " +
                            stringResource(R.string.bear_pool_remaining, remaining.stripes)
                    }
                null -> stringResource(R.string.bear_pool_target_open)
            }
    }

@Composable
private fun difficultyLabel(d: PoolAi.Difficulty): String =
    when (d) {
        PoolAi.Difficulty.NOVICE -> stringResource(R.string.bear_pool_diff_novice)
        PoolAi.Difficulty.EASY -> stringResource(R.string.bear_pool_diff_easy)
        PoolAi.Difficulty.NORMAL -> stringResource(R.string.bear_pool_diff_normal)
        PoolAi.Difficulty.HARD -> stringResource(R.string.bear_pool_diff_hard)
        PoolAi.Difficulty.MASTER -> stringResource(R.string.bear_pool_diff_master)
    }

@Composable
private fun modeLabel(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> stringResource(R.string.bear_pool_mode_eight)
        PoolMode.NINE_BALL -> stringResource(R.string.bear_pool_mode_nine)
        PoolMode.PRACTICE -> stringResource(R.string.bear_pool_mode_practice)
        PoolMode.CHALLENGE -> stringResource(R.string.bear_pool_mode_challenge)
    }

@Composable
private fun elevationHint(elevation: Float): String =
    when {
        elevation < 0.15f -> stringResource(R.string.bear_pool_elevation_flat)
        elevation < 0.6f -> stringResource(R.string.bear_pool_elevation_slight)
        else -> stringResource(R.string.bear_pool_elevation_high)
    }

@Composable
private fun achievementTitle(a: PoolAchievement): String =
    when (a) {
        PoolAchievement.FIRST_WIN -> stringResource(R.string.bear_pool_ach_first_win)
        PoolAchievement.SHUTOUT -> stringResource(R.string.bear_pool_ach_shutout)
        PoolAchievement.TRIPLE_POT -> stringResource(R.string.bear_pool_ach_triple_pot)
        PoolAchievement.CLEAN_TABLE -> stringResource(R.string.bear_pool_ach_clean_table)
        PoolAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_pool_ach_win_streak)
        PoolAchievement.MASTER_WIN -> stringResource(R.string.bear_pool_ach_master_win)
        PoolAchievement.NINE_BALL_WIN -> stringResource(R.string.bear_pool_ach_nine_win)
        PoolAchievement.PRACTICE_CLEAR -> stringResource(R.string.bear_pool_ach_practice_clear)
    }

@Composable
private fun achievementDesc(a: PoolAchievement): String =
    when (a) {
        PoolAchievement.FIRST_WIN -> stringResource(R.string.bear_pool_ach_first_win_desc)
        PoolAchievement.SHUTOUT -> stringResource(R.string.bear_pool_ach_shutout_desc)
        PoolAchievement.TRIPLE_POT -> stringResource(R.string.bear_pool_ach_triple_pot_desc)
        PoolAchievement.CLEAN_TABLE -> stringResource(R.string.bear_pool_ach_clean_table_desc)
        PoolAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_pool_ach_win_streak_desc)
        PoolAchievement.MASTER_WIN -> stringResource(R.string.bear_pool_ach_master_win_desc)
        PoolAchievement.NINE_BALL_WIN -> stringResource(R.string.bear_pool_ach_nine_win_desc)
        PoolAchievement.PRACTICE_CLEAR -> stringResource(R.string.bear_pool_ach_practice_clear_desc)
    }

@Composable
private fun aidsLabel(a: Aids): String =
    when (a) {
        Aids.FULL -> stringResource(R.string.bear_pool_aids_full)
        Aids.GHOST -> stringResource(R.string.bear_pool_aids_ghost)
        Aids.OFF -> stringResource(R.string.bear_pool_aids_off)
    }

@Composable
private fun raceLabel(n: Int): String =
    when (n) {
        1 -> stringResource(R.string.bear_pool_race_1)
        2 -> stringResource(R.string.bear_pool_race_2)
        else -> stringResource(R.string.bear_pool_race_3)
    }

@Composable
private fun spinHint(spin: Spin): String {
    val vertical =
        when {
            spin.vertical > 0.15f -> stringResource(R.string.bear_pool_spin_follow)
            spin.vertical < -0.15f -> stringResource(R.string.bear_pool_spin_draw)
            else -> stringResource(R.string.bear_pool_spin_center)
        }
    val side =
        when {
            spin.side < -0.15f -> stringResource(R.string.bear_pool_spin_side_l)
            spin.side > 0.15f -> stringResource(R.string.bear_pool_spin_side_r)
            else -> ""
        }
    return if (side.isEmpty()) vertical else "$vertical · $side"
}

/** [PoolMessage] → 本地化文案。 */
@Composable
private fun poolMessageText(message: PoolMessage): String =
    when (message) {
        PoolMessage.NONE -> ""
        PoolMessage.OPEN_TABLE -> stringResource(R.string.bear_pool_msg_open_table)
        PoolMessage.GROUP_ASSIGNED -> stringResource(R.string.bear_pool_msg_group_assigned)
        PoolMessage.CONTINUE -> stringResource(R.string.bear_pool_msg_continue)
        PoolMessage.TURN_PASS -> stringResource(R.string.bear_pool_msg_turn_pass)
        PoolMessage.FOUL_CUE -> stringResource(R.string.bear_pool_msg_foul_cue)
        PoolMessage.FOUL_WRONG_BALL -> stringResource(R.string.bear_pool_msg_foul_wrong_ball)
        PoolMessage.FOUL_NO_RAIL -> stringResource(R.string.bear_pool_msg_foul_no_rail)
        PoolMessage.FOUL_MISS -> stringResource(R.string.bear_pool_msg_foul_miss)
        PoolMessage.EIGHT_WIN -> stringResource(R.string.bear_pool_msg_eight_win)
        PoolMessage.EIGHT_LOSE -> stringResource(R.string.bear_pool_msg_eight_lose)
        PoolMessage.BREAK_RERACK -> stringResource(R.string.bear_pool_msg_break_rerack)
        PoolMessage.NINE_WIN -> stringResource(R.string.bear_pool_msg_nine_win)
        PoolMessage.NINE_LOSE -> stringResource(R.string.bear_pool_msg_nine_lose)
        PoolMessage.PRACTICE_CLEAR -> stringResource(R.string.bear_pool_msg_practice_clear)
    }

/** 白球控件拖拽 → 杆法（垂直向上为跟杆、向下为缩杆）。 */
private fun applySpin(
    game: PoolGame,
    position: Offset,
    radius: Float,
) {
    val nx = ((position.x - radius) / (radius * 0.8f)).coerceIn(-1f, 1f)
    val ny = ((position.y - radius) / (radius * 0.8f)).coerceIn(-1f, 1f)
    game.updateSpin(Spin(side = nx, vertical = -ny))
}

private fun DrawScope.drawSpinPad(spin: Spin) {
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color(0x55000000), r * 1.02f, center + Offset(0f, 2f))
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFECEFF1), Color(0xFFCFD8DC)),
                center = center - Offset(r * 0.3f, r * 0.3f),
                radius = r * 1.3f,
            ),
        radius = r * 0.94f,
        center = center,
    )
    val line = Color(0x33000000)
    drawLine(line, Offset(center.x - r * 0.8f, center.y), Offset(center.x + r * 0.8f, center.y), 2f)
    drawLine(line, Offset(center.x, center.y - r * 0.8f), Offset(center.x, center.y + r * 0.8f), 2f)
    val dotX = center.x + spin.side * r * 0.72f
    val dotY = center.y - spin.vertical * r * 0.72f
    drawCircle(Color(0xFFE53935), r * 0.14f, Offset(dotX, dotY))
    drawCircle(Color(0xFFB71C1C), r * 0.14f, Offset(dotX, dotY), style = Stroke(width = 2f))
}

/** 播放短提示音；生成器为空或播放失败时静默忽略，不影响游戏。 */
private fun playTone(
    gen: ToneGenerator?,
    tone: Int,
    durationMs: Int,
) {
    if (gen == null) return
    runCatching { gen.startTone(tone, durationMs) }
}

private fun difficultyKey(d: PoolAi.Difficulty): String = d.name.lowercase()

private fun difficultyOf(key: String?): PoolAi.Difficulty =
    runCatching { PoolAi.Difficulty.valueOf(key.orEmpty().uppercase()) }
        .getOrDefault(PoolAi.Difficulty.NORMAL)

private fun aidsKey(a: Aids): String = a.name.lowercase()

private fun aidsOf(key: String?): Aids = runCatching { Aids.valueOf(key.orEmpty().uppercase()) }.getOrDefault(Aids.FULL)
