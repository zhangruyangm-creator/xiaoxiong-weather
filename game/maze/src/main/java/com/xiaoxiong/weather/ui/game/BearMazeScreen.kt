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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaoxiong.weather.R
import java.time.LocalDate

@Composable
fun BearMazeScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    val pause = rememberGamePauseState()
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
    // 本地日（用于每日迷宫与完成记录）；跨零点回到前台（ON_RESUME）时刷新
    var today by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    // 每日最佳用 today 做 key：跨天后自动重新读取新一天的记录
    var dailyBest by remember(today) { mutableIntStateOf(prefs.getInt("maze_daily_$today", -1)) }
    // 事件特效宿主（粒子/庆祝）。提到 Screen 层是为了让帧循环能在特效结束后暂停；
    // observe() 会按 grid 身份感知换关并清空上一局粒子。
    val fx = remember { MazeFxHost() }
    // 可续会话摘要：进入先回选关，只有点「继续」才 restoreSession
    var savedSession by remember { mutableStateOf<SavedSessionInfo?>(null) }
    // 进入 / 回到选关时探测存档；选关阶段的 saveSession 不再清档，只有主动开新局才放弃
    LaunchedEffect(game.phase) {
        if (game.phase == MazePhase.SELECT) {
            savedSession = game.peekSavedSession(prefs)
        }
    }
    // 组合销毁（切屏/旋转重建）时把局中对局落盘，供下次恢复；
    // 按 Home 等切入后台（ON_STOP）时也落盘一次，避免进程被系统回收丢档；
    // 回到前台（ON_RESUME）时刷新「今天」，跨零点后选关页展示新每日图与新纪录
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> game.saveSession(prefs)
                    Lifecycle.Event.ON_RESUME -> {
                        val now = LocalDate.now().toEpochDay()
                        if (now != today) today = now
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            game.saveSession(prefs)
        }
    }
    val sprites = rememberBearSprites(characterState.selected)
    // 帧时间以 State 形式传入绘制层：只有 Canvas 的 draw lambda 读它，
    // 避免每帧更新 Long 触发整个 BearMazeScreen 重组。
    val frame = remember { mutableLongStateOf(0L) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    // 存在可续会话（战役/无尽/每日）时，开始新局前的放弃确认
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 有存档时先确认再开新局；确认后丢弃旧会话
    fun guardedStart(action: () -> Unit) {
        if (savedSession != null) {
            pendingStart = action
            showOverwriteDialog = true
        } else {
            action()
        }
    }

    // 选关列表只需标题/主题等元数据，轻量同步可得；完整关卡（含迷宫图）在
    // 点击某关开始游玩时才生成，避免进入选关界面就批量生成造成等待。
    val campaignMetas = remember { List(MAZE_CAMPAIGN_SIZE) { mazeCampaignMeta(it) } }

    // 弹窗（放弃/角色选择）与选关界面都停帧：否则弹窗背后 moveT 仍落地、
    // SELECT 阶段 time 空转 60fps 耗电（与 jump/catch 的 paused 语义一致）。
    GameFrameLoop(
        paused = {
            showAbandonDialog ||
                showCharacterPicker ||
                game.phase == MazePhase.SELECT ||
                pause.paused ||
                // CLEAR 且庆祝/粒子都已结束：画面静止，停帧省电（isIdle 会等待首帧观测庆祝）
                (game.phase == MazePhase.CLEAR && fx.isIdle(game))
        },
    ) { dt, now ->
        val before = game.phase
        val wasMoving = game.moving
        val event = game.tick(dt)
        if (wasMoving && !game.moving) {
            if (game.phase == MazePhase.CLEAR && before != MazePhase.CLEAR) {
                // 结算触感 + 落盘进度；结算即清掉落盘会话，避免选关页出现已结算的旧档
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                game.saveProgress(prefs)
                game.saveSession(prefs)
                if (game.daily) {
                    val key = "maze_daily_${game.dailyDate}"
                    val prev = prefs.getInt(key, -1)
                    if (prev < 0 || game.moves < prev) {
                        prefs.edit { putInt(key, game.moves) }
                        // 只更新今天对应的展示状态；旧日期的会话不污染今日最佳
                        if (game.dailyDate == today) dailyBest = game.moves
                    }
                }
            } else {
                // 按落地事件类型区分触感（落地只响一次，按键本身不再响）
                val haptic =
                    when (event) {
                        MazeStepEvent.HONEY, MazeStepEvent.KEY, MazeStepEvent.LOCK ->
                            HapticFeedbackConstants.VIRTUAL_KEY
                        MazeStepEvent.PORTAL -> HapticFeedbackConstants.KEYBOARD_TAP
                        MazeStepEvent.EXIT_LOCKED -> HapticFeedbackConstants.LONG_PRESS
                        else -> HapticFeedbackConstants.CLOCK_TICK
                    }
                view.performHapticFeedback(haptic)
            }
        }
        frame.longValue = now
    }

    BackHandler {
        when {
            pause.showMenu -> pause.resume()
            showOverwriteDialog -> {
                showOverwriteDialog = false
                pendingStart = null
            }
            showAbandonDialog -> showAbandonDialog = false
            game.phase == MazePhase.PLAYING -> pause.open()
            game.phase == MazePhase.SELECT -> onExit()
            else -> game.openSelect()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (game.phase) {
            MazePhase.SELECT -> {
                MazeLevelSelect(
                    game = game,
                    campaignMetas = campaignMetas,
                    savedSession = savedSession,
                    dailyBest = dailyBest,
                    dailyDone = dailyBest >= 0,
                    onPick = { guardedStart { game.startLevel(it) } },
                    onEndless = { guardedStart { game.startEndless(1) } },
                    onResume = {
                        game.restoreSession(prefs)
                        savedSession = null
                    },
                    onDaily = { guardedStart { game.startDaily(today) } },
                    onExit = onExit,
                    onCharacter = { showCharacterPicker = true },
                )
            }
            MazePhase.PLAYING, MazePhase.CLEAR -> {
                MazePlayField(
                    game = game,
                    sprites = sprites,
                    frame = frame,
                    fx = fx,
                    weather = mazeWeather,
                    rainStrength = mazeRain,
                    freezingRain = mazeFreezingRain,
                    hail = mazeHail,
                    onExitSelect = {
                        when {
                            game.endless && game.phase == MazePhase.PLAYING -> showAbandonDialog = true
                            game.phase == MazePhase.PLAYING -> {
                                // 战役/每日回选关：先落盘当前进度，选关页可「继续」
                                game.saveSession(prefs)
                                game.openSelect()
                            }
                            else -> game.openSelect()
                        }
                    },
                    onRestart = { game.restart() },
                    onPause = pause::open,
                    onNext = {
                        when {
                            game.daily -> game.openSelect()
                            game.endless -> game.startEndless(game.endlessWave + 1)
                            else -> {
                                val n = game.levelIndex + 1
                                if (n < MAZE_CAMPAIGN_SIZE) {
                                    game.startLevel(n)
                                } else {
                                    game.openSelect()
                                }
                            }
                        }
                    },
                    onMove = { dir ->
                        // 按键本身不给触感；成功移动的反馈在落地事件里统一给，
                        // 被墙/锁挡住时给一次 REJECT，避免与落地音重复
                        if (!game.tryMove(dir) &&
                            game.phase == MazePhase.PLAYING &&
                            !game.moving &&
                            !game.cleared
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
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
                    // 彻底放弃本次对局：删除落盘会话并清除续玩标记，
                    // 避免 onDispose 的兜底 saveSession 把已放弃的对局重新写回复活
                    game.abandonSession(prefs)
                    showAbandonDialog = false
                    game.openSelect()
                },
            )
        }

        val overwriteInfo = savedSession
        if (showOverwriteDialog && overwriteInfo != null) {
            OverwriteSessionDialog(
                info = overwriteInfo,
                onCancel = {
                    showOverwriteDialog = false
                    pendingStart = null
                },
                onConfirm = {
                    game.abandonSession(prefs)
                    savedSession = null
                    showOverwriteDialog = false
                    val action = pendingStart
                    pendingStart = null
                    action?.invoke()
                },
            )
        }

        if (pause.showMenu) {
            GamePauseOverlay(
                title = gamePauseTitle(),
                onResume = pause::resume,
                // 暂停里的「重新开始」= 重开当前局（战役重开本关 / 无尽重开本波 / 每日同一张图），
                // 与 HUD 刷新按钮语义一致；回选关请用 HUD 的关闭按钮。
                onRestart = {
                    pause.resume()
                    game.restart()
                },
                onExit = onExit,
            )
        }
    }
}

/** 开始新局前提示旧进度（战役/每日/无尽）将被放弃。 */
@Composable
private fun OverwriteSessionDialog(
    info: SavedSessionInfo,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message =
        when (info) {
            is SavedSessionInfo.Campaign ->
                stringResource(R.string.bear_maze_overwrite_text_campaign, info.levelIndex + 1)
            is SavedSessionInfo.Endless -> stringResource(R.string.bear_maze_overwrite_text, info.wave)
            is SavedSessionInfo.Daily -> stringResource(R.string.bear_maze_overwrite_text_daily)
        }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.bear_maze_overwrite_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.bear_maze_overwrite_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.bear_maze_overwrite_cancel))
            }
        },
    )
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
