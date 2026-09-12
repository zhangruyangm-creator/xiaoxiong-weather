package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoxiong.weather.R

@Composable
fun BearMazeScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    val prefs = rememberGamePrefs()
    val characterState = rememberBearCharacterState()
    var showCharacterPicker by remember { mutableStateOf(false) }
    val mazeWeather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val mazeRain = remember(sceneCode) { rainStrengthOf(sceneCode) }
    val mazeFreezingRain = remember(sceneCode) { isFreezingRainCode(sceneCode) }
    val mazeHail = remember(sceneCode) { isHailCode(sceneCode) }
    val game =
        remember {
            MazeGame().also { it.loadProgress(prefs) }
        }
    // 旋转屏幕等进程重建后恢复局中进度。放 LaunchedEffect 而非 remember 初始化块：
    // restoreSession 会写 phase/bearR 等 snapshot state，组合期写入会触发额外重组；
    // 且若该次组合被丢弃（如立即导航离开），组合期执行恢复的副作用不应生效。
    LaunchedEffect(Unit) {
        game.restoreSession(prefs)
    }
    // 组合销毁（切屏/旋转重建）时把局中对局落盘，供下次恢复；
    // 按 Home 等切入后台（ON_STOP）时也落盘一次，避免进程被系统回收丢档
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) game.saveSession(prefs)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            game.saveSession(prefs)
        }
    }
    val sprites = rememberBearSprites(characterState.selected)
    var frame by remember { mutableLongStateOf(0L) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    // 选关列表只需标题/主题等元数据，轻量同步可得；完整关卡（含迷宫图）在
    // 点击某关开始游玩时才生成，避免进入选关界面就批量生成造成等待。
    val campaignMetas = remember { List(MAZE_CAMPAIGN_SIZE) { mazeCampaignMeta(it) } }

    // 弹窗（放弃/角色选择）与选关界面都停帧：否则弹窗背后 moveT 仍落地、
    // SELECT 阶段 time 空转 60fps 耗电（与 jump/catch 的 paused 语义一致）。
    GameFrameLoop(
        paused = {
            showAbandonDialog || showCharacterPicker || game.phase == MazePhase.SELECT
        },
    ) { dt, now ->
        val before = game.phase
        val wasMoving = game.moving
        game.tick(dt)
        if (wasMoving && !game.moving) {
            // 落地事件里可能清关；结算触感
            if (game.phase == MazePhase.CLEAR && before != MazePhase.CLEAR) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                game.saveProgress(prefs)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        frame = now
    }

    BackHandler {
        if (showAbandonDialog) {
            showAbandonDialog = false
        } else {
            when (game.phase) {
                MazePhase.SELECT -> onExit()
                MazePhase.PLAYING -> {
                    if (game.endless) {
                        // 无尽退出前询问：保留进度续玩，或放弃从第 1 波重来
                        showAbandonDialog = true
                    } else {
                        game.openSelect()
                    }
                }
                MazePhase.CLEAR -> game.openSelect()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (game.phase) {
            MazePhase.SELECT -> {
                MazeLevelSelect(
                    game = game,
                    campaignMetas = campaignMetas,
                    onPick = { game.startLevel(it) },
                    onEndless = { game.startEndless(1) },
                    onExit = onExit,
                    onCharacter = { showCharacterPicker = true },
                )
            }
            MazePhase.PLAYING, MazePhase.CLEAR -> {
                MazePlayField(
                    game = game,
                    sprites = sprites,
                    frame = frame,
                    weather = mazeWeather,
                    rainStrength = mazeRain,
                    freezingRain = mazeFreezingRain,
                    hail = mazeHail,
                    onExitSelect = {
                        if (game.endless && game.phase == MazePhase.PLAYING) {
                            showAbandonDialog = true
                        } else {
                            game.openSelect()
                        }
                    },
                    onRestart = { game.restart() },
                    onNext = {
                        if (game.endless) {
                            game.startEndless(game.endlessWave + 1)
                        } else {
                            val n = game.levelIndex + 1
                            if (n < MAZE_CAMPAIGN_SIZE) {
                                game.startLevel(n)
                            } else {
                                game.openSelect()
                            }
                        }
                    },
                    onMove = { dir ->
                        if (game.tryMove(dir)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    onCharacter = { showCharacterPicker = true },
                )
            }
        }

        if (showCharacterPicker) {
            GameCharacterPickerDialog(
                state = characterState,
                onDismiss = { showCharacterPicker = false },
            )
        }

        if (showAbandonDialog) {
            AbandonEndlessDialog(
                wave = game.endlessWave,
                onContinue = { showAbandonDialog = false },
                onSaveAndExit = {
                    game.saveSession(prefs)
                    showAbandonDialog = false
                    game.openSelect()
                },
                onAbandon = {
                    // 彻底放弃本次无尽对局：删除落盘会话，并清除续玩标记，
                    // 避免 onDispose 的兜底 saveSession 把已放弃的对局重新写回复活
                    game.discardSavedSession(prefs)
                    game.resumeEndless = false
                    showAbandonDialog = false
                    game.openSelect()
                },
            )
        }
    }
}

/** 无尽退出选择：保留进度续玩，或放弃并从第 1 波重来。 */
@Composable
private fun AbandonEndlessDialog(
    wave: Int,
    onContinue: () -> Unit,
    onSaveAndExit: () -> Unit,
    onAbandon: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = {
            Text(stringResource(R.string.bear_maze_abandon_title, wave))
        },
        text = {
            Column {
                Text(stringResource(R.string.bear_maze_abandon_text, wave))
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(R.string.bear_maze_abandon_keep))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAbandon) {
                Text(
                    stringResource(R.string.bear_maze_abandon_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.bear_maze_abandon_cancel))
            }
        },
    )
}
