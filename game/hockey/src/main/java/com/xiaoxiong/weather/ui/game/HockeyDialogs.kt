package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty

@Composable
internal fun HockeySettingsDialog(
    difficulty: Difficulty,
    customLevel: Int,
    winScore: Int,
    paddleScale: Float,
    paddleScaleBoth: Boolean,
    puckScale: Float,
    goalScale: Float,
    rinkTheme: RinkTheme,
    rulesLocked: Boolean,
    onOpenCareer: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Difficulty, Int, Int, Float, Boolean, Float, Float, RinkTheme, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var d by remember(difficulty) { mutableStateOf(difficulty) }
    var levelText by remember(customLevel) { mutableStateOf(customLevel.toString()) }
    var winText by remember(winScore) { mutableStateOf(winScore.toString()) }
    var scale by remember(paddleScale) { mutableStateOf(paddleScale) }
    var scaleBoth by remember(paddleScaleBoth) { mutableStateOf(paddleScaleBoth) }
    var puck by remember(puckScale) { mutableStateOf(puckScale) }
    var goal by remember(goalScale) { mutableStateOf(goalScale) }
    var theme by remember(rinkTheme) { mutableStateOf(rinkTheme) }
    // 音效开关：只改本地状态，点「保存」才随 onSave 写入全局 GameAudio（旧 hockey_sound 仅由 GameAudio 首次迁移读取）
    var sound by remember { mutableStateOf(GameAudio.settings(context).enabled) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_hockey_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (rulesLocked) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_hockey_rules_locked),
                        style = MaterialTheme.typography.labelSmall,
                        color = HockeyUi.warn,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_diff), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.BEGINNER, Difficulty.EASY, Difficulty.NORMAL).forEach { opt ->
                        HockeyFilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Difficulty.ADVANCED, Difficulty.HARD, Difficulty.MASTER).forEach { opt ->
                        HockeyFilterChip(
                            selected = d == opt,
                            onClick = { d = opt },
                            label = { Text(difficultyLabel(opt)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HockeyFilterChip(
                    selected = d == Difficulty.CUSTOM,
                    onClick = { d = Difficulty.CUSTOM },
                    label = { Text(stringResource(R.string.bear_hockey_diff_custom)) },
                )
                if (d == Difficulty.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    HockeyOutlinedField(
                        value = levelText,
                        onValueChange = { raw ->
                            levelText = raw.filter { it.isDigit() }.take(2)
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.bear_hockey_diff_level_hint,
                                    HockeyLogic.CUSTOM_LEVEL_MIN,
                                    HockeyLogic.CUSTOM_LEVEL_MAX,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_win_score), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7).forEach { n ->
                        HockeyFilterChip(
                            selected = winText.toIntOrNull() == n,
                            onClick = { winText = n.toString() },
                            label = { Text("$n") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HockeyOutlinedField(
                    value = winText,
                    onValueChange = { raw ->
                        winText = raw.filter { it.isDigit() }.take(2)
                    },
                    label = {
                        Text(
                            stringResource(
                                R.string.bear_hockey_win_score_hint,
                                HockeyLogic.WIN_SCORE_MIN,
                                HockeyLogic.WIN_SCORE_MAX,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_paddle_size),
                    value = scale,
                    range = HockeyLogic.PADDLE_SCALE_MIN..HockeyLogic.PADDLE_SCALE_MAX,
                    onChange = { scale = it },
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.bear_hockey_paddle_scope), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HockeyFilterChip(
                        selected = scaleBoth,
                        onClick = { scaleBoth = true },
                        label = { Text(stringResource(R.string.bear_hockey_paddle_scope_both)) },
                    )
                    HockeyFilterChip(
                        selected = !scaleBoth,
                        onClick = { scaleBoth = false },
                        label = { Text(stringResource(R.string.bear_hockey_paddle_scope_mine)) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_puck_size),
                    value = puck,
                    range = HockeyLogic.PUCK_SCALE_MIN..HockeyLogic.PUCK_SCALE_MAX,
                    onChange = { puck = it },
                )
                Spacer(Modifier.height(12.dp))
                SizeSliderRow(
                    label = stringResource(R.string.bear_hockey_goal_size),
                    value = goal,
                    range = HockeyLogic.GOAL_SCALE_MIN..HockeyLogic.GOAL_SCALE_MAX,
                    onChange = { goal = it },
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.bear_hockey_rink_theme), color = HockeyUi.textSecondary)
                Spacer(Modifier.height(8.dp))
                RinkTheme.entries.chunked(2).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowThemes.forEach { opt ->
                            RinkThemeChip(
                                theme = opt,
                                selected = theme == opt,
                                onClick = { theme = opt },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bear_hockey_sound),
                        color = HockeyUi.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = sound,
                        onCheckedChange = { sound = it },
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenCareer,
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.bear_hockey_career))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.textSecondary),
                    ) {
                        Text(stringResource(R.string.bear_hockey_cancel))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val level =
                                levelText
                                    .toIntOrNull()
                                    ?.coerceIn(HockeyLogic.CUSTOM_LEVEL_MIN, HockeyLogic.CUSTOM_LEVEL_MAX)
                                    ?: customLevel.coerceIn(
                                        HockeyLogic.CUSTOM_LEVEL_MIN,
                                        HockeyLogic.CUSTOM_LEVEL_MAX,
                                    )
                            val win =
                                winText
                                    .toIntOrNull()
                                    ?.coerceIn(HockeyLogic.WIN_SCORE_MIN, HockeyLogic.WIN_SCORE_MAX)
                                    ?: winScore.coerceIn(
                                        HockeyLogic.WIN_SCORE_MIN,
                                        HockeyLogic.WIN_SCORE_MAX,
                                    )
                            onSave(
                                d,
                                level,
                                win,
                                scale.coerceIn(HockeyLogic.PADDLE_SCALE_MIN, HockeyLogic.PADDLE_SCALE_MAX),
                                scaleBoth,
                                puck.coerceIn(HockeyLogic.PUCK_SCALE_MIN, HockeyLogic.PUCK_SCALE_MAX),
                                goal.coerceIn(HockeyLogic.GOAL_SCALE_MIN, HockeyLogic.GOAL_SCALE_MAX),
                                theme,
                                sound,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.bear_hockey_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun HockeyAchievementTitle(a: HockeyAchievement): String =
    when (a) {
        HockeyAchievement.FIRST_WIN -> stringResource(R.string.bear_hockey_ach_first_win)
        HockeyAchievement.SHUTOUT -> stringResource(R.string.bear_hockey_ach_shutout)
        HockeyAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_hockey_ach_win_streak)
        HockeyAchievement.HARD_WIN -> stringResource(R.string.bear_hockey_ach_hard_win)
        HockeyAchievement.MASTER_WIN -> stringResource(R.string.bear_hockey_ach_master_win)
        HockeyAchievement.FIVE_GOALS -> stringResource(R.string.bear_hockey_ach_five_goals)
        HockeyAchievement.PERFECT_FIVE -> stringResource(R.string.bear_hockey_ach_perfect_five)
        HockeyAchievement.VETERAN -> stringResource(R.string.bear_hockey_ach_veteran)
    }

@Composable
private fun HockeyAchievementDesc(a: HockeyAchievement): String =
    when (a) {
        HockeyAchievement.FIRST_WIN -> stringResource(R.string.bear_hockey_ach_first_win_desc)
        HockeyAchievement.SHUTOUT -> stringResource(R.string.bear_hockey_ach_shutout_desc)
        HockeyAchievement.WIN_STREAK_3 -> stringResource(R.string.bear_hockey_ach_win_streak_desc)
        HockeyAchievement.HARD_WIN -> stringResource(R.string.bear_hockey_ach_hard_win_desc)
        HockeyAchievement.MASTER_WIN -> stringResource(R.string.bear_hockey_ach_master_win_desc)
        HockeyAchievement.FIVE_GOALS -> stringResource(R.string.bear_hockey_ach_five_goals_desc)
        HockeyAchievement.PERFECT_FIVE -> stringResource(R.string.bear_hockey_ach_perfect_five_desc)
        HockeyAchievement.VETERAN -> stringResource(R.string.bear_hockey_ach_veteran_desc)
    }

/** 生涯成就面板：列出全部成就与解锁状态及统计。 */
@Composable
internal fun HockeyCareerDialog(
    progress: HockeyProgress,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.bear_hockey_career),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line,
                        progress.games,
                        progress.wins,
                        progress.goalsFor,
                        progress.goalsAgainst,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = HockeyUi.textSecondary,
                )
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line2,
                        progress.shutouts,
                        progress.bestStreak,
                        progress.bestGoals,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = HockeyUi.textSecondary,
                )
                Text(
                    stringResource(
                        R.string.bear_hockey_stats_line3,
                        progress.winsOn(Difficulty.BEGINNER),
                        progress.winsOn(Difficulty.EASY),
                        progress.winsOn(Difficulty.NORMAL),
                        progress.winsOn(Difficulty.ADVANCED),
                        progress.winsOn(Difficulty.HARD),
                        progress.winsOn(Difficulty.MASTER),
                        progress.winsOn(Difficulty.CUSTOM),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HockeyUi.textSecondary.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(12.dp))
                HockeyAchievement.entries.forEach { ach ->
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
                            color = if (unlocked) HockeyUi.accentWarm else Color(0x66FFFFFF),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = HockeyAchievementTitle(ach),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) Color.White else Color(0x80FFFFFF),
                            )
                            Text(
                                text = HockeyAchievementDesc(ach),
                                style = MaterialTheme.typography.labelSmall,
                                color = HockeyUi.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.danger),
                    ) {
                        Text(stringResource(R.string.bear_hockey_clear_stats))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.bear_hockey_save)) }
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = HockeyUi.glassDeep,
            titleContentColor = Color.White,
            textContentColor = HockeyUi.textSecondary,
            title = { Text(stringResource(R.string.bear_hockey_clear_stats)) },
            text = { Text(stringResource(R.string.bear_hockey_clear_stats_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.danger),
                ) {
                    Text(stringResource(R.string.bear_hockey_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = HockeyUi.accentCool),
                ) {
                    Text(stringResource(R.string.bear_hockey_cancel))
                }
            },
        )
    }
}

/** 新成就解锁弹窗。 */
@Composable
internal fun HockeyUnlockDialog(
    achievements: List<HockeyAchievement>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = HockeyUi.glassDeep,
            border = BorderStroke(1.dp, HockeyUi.iceBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bear_hockey_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HockeyUi.accentWarm,
                )
                Spacer(Modifier.height(10.dp))
                achievements.forEach { ach ->
                    Text(
                        text = "★ " + HockeyAchievementTitle(ach),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = HockeyAchievementDesc(ach),
                        style = MaterialTheme.typography.labelSmall,
                        color = HockeyUi.textSecondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.bear_hockey_save)) }
            }
        }
    }
}
