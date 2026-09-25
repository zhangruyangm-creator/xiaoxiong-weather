package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.game.PoolGame.Aids

private val CLOTH_THEME_RES =
    listOf(
        R.string.bear_pool_cloth_azure,
        R.string.bear_pool_cloth_honey,
        R.string.bear_pool_cloth_midnight,
        R.string.bear_pool_cloth_sakura,
    ).also { require(it.size == CLOTH_THEME_COUNT) { "台呢主题档位数量与资源不一致" } }

/** 结算后用于「新纪录」展示的快照。 */
internal data class ChallengeResult(
    val name: String,
    val par: Int,
    val shots: Int,
    val score: Int,
    val newRecord: Boolean,
    val daily: Boolean,
)

/** 教学结算浮层：显示是否通过，提供重试 / 下一课 / 返回。 */
@Composable
internal fun PoolLessonResultOverlay(
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
internal fun PoolTutorialDialog(
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

/** 设置弹窗：模式 3 档 / 难度 5 档 / 瞄准辅助 3 档 / 赛制 1/2/3 快捷档 + 自定义 1–15 / 音效 / 减弱动画。 */
@Composable
internal fun PoolSettingsDialog(
    difficulty: PoolAi.Difficulty,
    aids: Aids,
    raceTo: Int,
    mode: PoolMode,
    reduceMotion: Boolean,
    clothTheme: Int,
    dailyBest: Int,
    onOpenDaily: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PoolAi.Difficulty, Aids, Int, PoolMode, Boolean, Boolean, Int) -> Unit,
) {
    val context = LocalContext.current
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var a by remember(aids) { mutableStateOf(aids) }
    var r by remember(raceTo) { mutableStateOf(raceTo.coerceIn(POOL_RACE_TO_MIN, POOL_RACE_TO_MAX)) }
    var m by remember(mode) { mutableStateOf(mode) }
    // 音效开关：弹窗内只改本地状态，点保存才写全局 GameAudio（旧 pool_sound 仅由 GameAudio 首次迁移读取）
    var s by remember { mutableStateOf(GameAudio.settings(context).enabled) }
    var rm by remember(reduceMotion) { mutableStateOf(reduceMotion) }
    var cl by remember(clothTheme) { mutableStateOf(clampClothTheme(clothTheme)) }
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
                    FilterChip(
                        selected = r !in 1..3,
                        onClick = { if (r in 1..3) r = 5 },
                        label = { Text(stringResource(R.string.bear_pool_race_custom)) },
                    )
                }
                if (r !in 1..3) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = { r = (r - 1).coerceAtLeast(POOL_RACE_TO_MIN) },
                            enabled = r > POOL_RACE_TO_MIN,
                        ) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = stringResource(R.string.cd_pool_race_minus),
                            )
                        }
                        Text(
                            text = stringResource(R.string.bear_pool_race_to_value, r),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E342E),
                        )
                        IconButton(
                            onClick = { r = (r + 1).coerceAtMost(POOL_RACE_TO_MAX) },
                            enabled = r < POOL_RACE_TO_MAX,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cd_pool_race_plus),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_pool_cloth), color = Color(0xFF6D4C41))
                Spacer(Modifier.height(8.dp))
                CLOTH_THEME_RES.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIndex, res ->
                            val index = rowIndex * 2 + colIndex
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
                    Switch(
                        checked = s,
                        onCheckedChange = { s = it },
                    )
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
                    Button(onClick = { onSave(d, a, clampRaceTo(r), m, s, rm, cl) }) {
                        Text(stringResource(R.string.bear_pool_save))
                    }
                }
            }
        }
    }
}

/** 生涯成就面板：列出全部成就与解锁状态及统计。 */
@Composable
internal fun PoolAchievementsDialog(
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
internal fun PoolUnlockDialog(
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
internal fun PoolChallengeResultOverlay(
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
internal fun PoolChallengesDialog(
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
    // semantics lambda 不是组合上下文，文案必须先在组合期取好
    val clearedLabel = stringResource(R.string.bear_pool_level_cleared)
    Surface(
        modifier =
            Modifier
                .width(96.dp)
                // 语义：挑战格是可点按钮，未解锁要明确 disabled（此前只有 clickable(enabled)，
                // 读屏用户听不出"尚未解锁"）；已通关用 stateDescription 传达。
                .semantics {
                    role = Role.Button
                    if (unlocked) {
                        if (cleared) stateDescription = clearedLabel
                    } else {
                        disabled()
                    }
                }.clickable(enabled = unlocked, onClick = onClick),
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
internal fun PoolDailyDialog(
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
internal fun configLine(game: PoolGame): String =
    modeLabel(game.displayMode()) +
        " · " +
        difficultyLabel(game.difficulty) +
        " · " +
        aidsLabel(game.aids) +
        " · " +
        stringResource(R.string.bear_pool_race_to_value, game.displayRaceTo())

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
internal fun modeLabel(mode: PoolMode): String =
    when (mode) {
        PoolMode.EIGHT_BALL -> stringResource(R.string.bear_pool_mode_eight)
        PoolMode.NINE_BALL -> stringResource(R.string.bear_pool_mode_nine)
        PoolMode.PRACTICE -> stringResource(R.string.bear_pool_mode_practice)
        PoolMode.CHALLENGE -> stringResource(R.string.bear_pool_mode_challenge)
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
