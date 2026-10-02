package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

/**
 * READY / 结算卡共享文案。
 *
 * 各游戏模块的 R 为非传递（仅含本模块资源），无法直接读取 common 的字符串，
 * 故统一由 common 解析。游戏模块若需自行拼「最高 xx」，用 [gameBestLabel]；
 * 需要默认重开文案时用 [gameRestart]。
 *
 * [gameBestLabel] 返回「最高」标签（与既有 `bear_game_best_label` 同值，
 * common 侧新键避免跨模块重名冲突）。
 */
@Composable
fun gameBestLabel(): String = stringResource(R.string.game_best_label)

/** 「查看排行榜」按钮文案。 */
@Composable
fun gameViewRank(): String = stringResource(R.string.game_view_rank)

/** 「回到天气」退出按钮文案。 */
@Composable
fun gameExit(): String = stringResource(R.string.game_exit)

/** 默认「再来一局」文案（各游戏可用自己的 `restartText` 覆盖）。 */
@Composable
fun gameRestart(): String = stringResource(R.string.game_restart)

/** 卡片上的次级动作（如 READY 页的排行榜入口）：文案 + 点击回调。 */
class GameCardAction(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * 统一的 READY 卡片：Material 卡片 + 居中标题 / 提示 / 可选最高分 + 开始按钮。
 *
 * 视觉对齐摘星 / 冰球现有 READY 卡（Surface 20dp 圆角、居中 Column、主按钮）。
 * 字符串全部由调用方传入（游戏模块用自己的 R 解析），公共的「最高」/「查看排行榜」等
 * 用 [gameBestLabel] / [gameViewRank] 等公共方法。
 *
 * @param title 标题（如「小熊跳跳」）
 * @param hint 操作提示（如「点一下屏幕，开始奔跑」）
 * @param bestLine 可选的最高分展示行；为 null 不显示（跑酷/摘星 READY 不展示最高分）
 * @param startText 开始按钮文案
 * @param onStart 开始回调
 * @param secondary 可选的次级动作（如 READY 卡难度选择之外的入口）
 * @param extra 可选扩展内容（如难度 Chip 行），渲染在主按钮上方
 */
@Composable
fun GameReadyCard(
    title: String,
    hint: String,
    bestLine: String? = null,
    startText: String,
    onStart: () -> Unit,
    secondary: GameCardAction? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (bestLine != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = bestLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (extra != null) {
                    Spacer(Modifier.height(14.dp))
                    extra()
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onStart) {
                    Text(startText)
                }
                if (secondary != null) {
                    TextButton(onClick = secondary.onClick) {
                        Text(secondary.text)
                    }
                }
            }
        }
    }
}

/**
 * 统一的结算卡片：半透明遮罩（吞掉画布手势）+ Surface 卡片 + 分数 / 最高分 / 名次 /
 * 查看排行榜 / 退出 / 重开按钮，视觉对齐跑酷 / 摘星现有结算卡。
 *
 * 「查看排行榜」「回到天气」由 common 解析公共文案；其余全部由调用方传入。
 *
 * @param overTitle 结算标题（如「哎呀，撞到了」）
 * @param scoreLine 本局分数展示（已格式化字符串）
 * @param bestLine 最高分展示行（已格式化字符串）
 * @param rankLine 可选的本局排名展示行（如「排名 3」）；为 null 不显示
 * @param restartText 重开按钮文案
 * @param onRestart 重开回调
 * @param onViewRank 查看排行榜回调
 * @param onExit 退出回调
 * @param extra 可选扩展内容，渲染在按钮区上方（如本局新纪录提示）
 */
@Composable
fun GameOverCard(
    overTitle: String,
    scoreLine: String,
    bestLine: String,
    restartText: String,
    onRestart: () -> Unit,
    onViewRank: () -> Unit,
    onExit: () -> Unit,
    rankLine: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 遮罩与卡片是兄弟节点、遮罩在下：既吞掉点击穿透，又不影响卡片内按钮命中
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        )
        Surface(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = overTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = scoreLine,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = bestLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rankLine != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = rankLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (extra != null) {
                    Spacer(Modifier.height(12.dp))
                    extra()
                }
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onViewRank) {
                    Text(gameViewRank())
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onExit) {
                        Text(gameExit())
                    }
                    Button(onClick = onRestart) {
                        Text(restartText)
                    }
                }
            }
        }
    }
}
