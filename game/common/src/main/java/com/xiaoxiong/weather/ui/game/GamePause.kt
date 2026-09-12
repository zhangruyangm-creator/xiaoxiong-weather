package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

/**
 * 各小游戏共用的暂停状态机。
 *
 * [paused] 表示「暂停中」（含暂停菜单显示），供各游戏帧循环 `paused` 条件读取以停帧；
 * [showMenu] 单独控制暂停菜单遮罩是否渲染，便于后续扩展（如暂停后临时展示设置弹窗）。
 */
class GamePauseState {
    var paused by mutableStateOf(false)

    var showMenu by mutableStateOf(false)

    /** 打开暂停菜单：停帧并显示菜单。 */
    fun open() {
        paused = true
        showMenu = true
    }

    /** 继续游戏：恢复帧循环并收起菜单。 */
    fun resume() {
        paused = false
        showMenu = false
    }

    /** 在暂停 / 继续之间切换。 */
    fun toggle() {
        if (paused) resume() else open()
    }
}

/** 记住一个 [GamePauseState]，随组合生命周期存活。 */
@Composable
fun rememberGamePauseState(): GamePauseState = remember { GamePauseState() }

/**
 * 暂停菜单标题文案（取自 common 资源）。
 *
 * 各游戏模块的 R 为非传递（仅含本模块资源），无法直接读取 common 的字符串，
 * 故统一由该组件在 common 内解析，游戏侧调用 `GamePauseOverlay(title = gamePauseTitle())`。
 */
@Composable
fun gamePauseTitle(): String = stringResource(R.string.bear_pause_title)

/**
 * 顶栏暂停按钮（[Icons.Filled.Pause]）。各游戏 HUD 顶栏统一使用，语义与配色跟随游戏 HUD。
 */
@Composable
fun GamePauseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.Pause,
            contentDescription = stringResource(R.string.cd_bear_pause),
            tint = tint,
        )
    }
}

/**
 * 全屏暂停遮罩 + 居中卡片：继续 / 重新开始 / 设置（可选）/ 退出游戏。
 *
 * 遮罩会吞掉所有指针事件，避免点击穿透到下层游戏画布。
 * [scoreText] 为可选的当前分数/战绩展示。
 */
@Composable
fun GamePauseOverlay(
    title: String,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onSettings: (() -> Unit)? = null,
    scoreText: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 遮罩作为卡片的兄弟节点（而非父节点）并置于下层：既吞掉点击穿透，
        // 又不影响卡片内按钮的点击分发。
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF21B222B),
            contentColor = Color.White,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (scoreText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCA28),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bear_pause_resume))
                }
                if (onSettings != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.bear_pause_settings),
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.bear_pause_restart),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.bear_pause_exit),
                        color = Color(0xFFFF8A80),
                    )
                }
            }
        }
    }
}
