package com.xiaoxiong.weather.ui.game

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.game.PoolGame.Spin
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/** 精瞄按钮单步：0.1°（长按连续微调，点击单步）。 */
private val FINE_AIM_STEP_RAD = PoolMath.radians(0.1f)

/** 可选模式：关卡（CHALLENGE）只通过专门入口进入，不出现在普通模式选择器里。 */
internal val POOL_MODES = listOf(PoolMode.EIGHT_BALL, PoolMode.NINE_BALL, PoolMode.PRACTICE)

/** 花色面板/提示里「剩 %d」的高亮色。 */
internal val POOL_PLAYER_ACCENT = Color(0xFFFFCC80)
private val POOL_RIVAL_ACCENT = Color(0xFF80DEEA)

/** 比分/球组面板：双方局分、当前该打的花色与剩余球数、回合高亮、提示横幅。 */
@Composable
internal fun BoxScope.PoolScoreboard(game: PoolGame) {
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
internal fun PoolControls(
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
internal fun OverlayScrim(content: @Composable ColumnScope.() -> Unit) {
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
internal fun BoxScope.ReplayOverlay(
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
internal fun BoxScope.PoolLessonBanner(lesson: PoolLesson) {
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

/** 击球历史面板：展示最近若干杆的双方、进球、犯规/白球落袋。 */
@Composable
internal fun BoxScope.ShotHistoryPanel(shots: List<ShotSummary>) {
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
internal fun lessonGoalLabel(goal: Goal): String =
    when (goal) {
        Goal.POT_ANY -> stringResource(R.string.bear_pool_goal_pot_any)
        Goal.POT_ONE -> stringResource(R.string.bear_pool_goal_pot_one)
        Goal.CLEAR -> stringResource(R.string.bear_pool_goal_clear)
        Goal.DRAW_BACK -> stringResource(R.string.bear_pool_goal_draw_back)
        Goal.BANK_SHOT -> stringResource(R.string.bear_pool_goal_bank_shot)
    }

/** 模式选择chip：八球 / 九球 / 练习。 */
@Composable
internal fun ModeSelector(
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
private fun elevationHint(elevation: Float): String =
    when {
        elevation < 0.15f -> stringResource(R.string.bear_pool_elevation_flat)
        elevation < 0.6f -> stringResource(R.string.bear_pool_elevation_slight)
        else -> stringResource(R.string.bear_pool_elevation_high)
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
