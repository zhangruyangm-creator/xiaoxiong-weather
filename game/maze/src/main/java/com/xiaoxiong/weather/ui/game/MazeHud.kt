package com.xiaoxiong.weather.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.delay

/** 主题徽标 emoji（与选关页保持同一套视觉语言）。 */
internal fun mazeThemeGlyph(theme: MazeTheme): String =
    when (theme) {
        MazeTheme.GARDEN -> "🌿"
        MazeTheme.RAIN -> "🌧️"
        MazeTheme.SNOW -> "❄️"
        MazeTheme.NIGHT -> "🌙"
        MazeTheme.FESTIVAL -> "🏮"
        MazeTheme.CLOUD -> "☁️"
    }

/**
 * 顶栏：主题胶囊（蜜/钥匙/步数）+ 玻璃圆钮（撤销/提示/重来/暂停/角色）。
 * 语义：每个可点区域都保留 contentDescription；装饰不参与语义。
 */
@Composable
internal fun MazeHudTopBar(
    levelTitle: String,
    theme: MazeTheme,
    style: MazeThemeStyle,
    game: MazeGame,
    moving: Boolean,
    canUndo: Boolean,
    hintBusy: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onRestart: () -> Unit,
    onPause: (() -> Unit)?,
    onCharacter: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MazeGlassButton(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close_game),
                style = style,
                onClick = onClose,
            )
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = mazeThemeGlyph(theme), fontSize = 15.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = levelTitle,
                    color = style.hudInk,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MazeGlassButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.cd_maze_undo),
                style = style,
                enabled = canUndo,
                onClick = onUndo,
            )
            MazeGlassButton(
                icon = Icons.Default.Lightbulb,
                contentDescription = stringResource(R.string.cd_maze_hint),
                style = style,
                enabled = game.phase == MazePhase.PLAYING && !moving && !hintBusy,
                onClick = onHint,
            )
            MazeGlassButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.bear_maze_restart),
                style = style,
                onClick = onRestart,
            )
            if (onPause != null) {
                MazeGlassButton(
                    icon = Icons.Default.Pause,
                    contentDescription = stringResource(R.string.cd_maze_pause),
                    style = style,
                    onClick = onPause,
                )
            }
            GameCharacterButton(onClick = onCharacter, tint = style.hudInk)
        }
        // 胶囊芯片单独一行：窄屏也能完整展示蜜/钥匙/步数进度
        Row(
            modifier = Modifier.padding(start = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MazeChip(
                glyph = "🍯",
                label =
                    stringResource(
                        R.string.bear_maze_chip_honey,
                        game.honeyLeft,
                        game.honeyTotal,
                    ),
                fraction = if (game.honeyTotal > 0) 1f - game.honeyLeft.toFloat() / game.honeyTotal else 1f,
                style = style,
                active = game.honeyLeft == 0,
            )
            MazeChip(
                glyph = "🔑",
                label = stringResource(R.string.bear_maze_chip_keys, game.keys),
                fraction = null,
                style = style,
                active = game.keys > 0,
            )
            MazeChip(
                glyph = "👣",
                label =
                    stringResource(
                        R.string.bear_maze_chip_moves,
                        game.moves,
                        game.level.moveBudget,
                    ),
                fraction = if (game.level.moveBudget > 0) game.moves.toFloat() / game.level.moveBudget else 0f,
                style = style,
                warn = game.moves > game.level.moveBudget,
            )
        }
    }
}

@Composable
private fun MazeGlassButton(
    icon: ImageVector,
    contentDescription: String,
    style: MazeThemeStyle,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(2.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            style.hudPanel.copy(alpha = if (enabled) 0.66f else 0.34f),
                            style.hudPanel.copy(alpha = if (enabled) 0.48f else 0.24f),
                        ),
                    ),
                ).border(
                    1.dp,
                    Color.White.copy(alpha = if (enabled) 0.32f else 0.14f),
                    CircleShape,
                ).clickable(
                    enabled = enabled,
                    onClick = {
                        GameAudio.play(GameCue.UI_CLICK)
                        onClick()
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = style.hudInk.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MazeChip(
    glyph: String,
    label: String,
    fraction: Float?,
    style: MazeThemeStyle,
    active: Boolean = false,
    warn: Boolean = false,
) {
    val border =
        when {
            warn -> Color(0xFFFF8A80)
            active -> style.accent
            else -> Color.White.copy(alpha = 0.22f)
        }
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(style.hudPanel.copy(alpha = 0.70f))
                .border(1.dp, border, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = glyph, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                color = if (warn) Color(0xFFFFCDD2) else style.hudInk,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (fraction != null) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .width(52.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                color = if (warn) Color(0xFFFF8A80) else style.accent,
                trackColor = Color.White.copy(alpha = 0.20f),
            )
        }
    }
}

/**
 * 方向键：木质爪印风 D-pad。按下缩放、提示方向高亮、移动中禁用。
 * 滑动操作仍由画布手势处理，这里只提供按键路径。
 */
@Composable
internal fun MazeDPad(
    style: MazeThemeStyle,
    enabled: Boolean,
    highlightDir: MazeDir?,
    onMove: (MazeDir) -> Unit,
) {
    val padShape = RoundedCornerShape(20.dp)
    val panelBrush =
        remember(style) {
            Brush.verticalGradient(
                listOf(
                    mazeLighten(style.boardRim, 0.22f),
                    mazeDarken(style.boardRim, 0.28f),
                ),
            )
        }
    Column(
        modifier =
            Modifier
                .clip(padShape)
                .background(panelBrush)
                .border(1.5.dp, mazeLighten(style.boardRim, 0.40f).copy(alpha = 0.8f), padShape)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MazePadKey(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.cd_maze_up),
            style = style,
            enabled = enabled,
            highlight = highlightDir == MazeDir.UP,
            onClick = { onMove(MazeDir.UP) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MazePadKey(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_maze_left),
                style = style,
                enabled = enabled,
                highlight = highlightDir == MazeDir.LEFT,
                onClick = { onMove(MazeDir.LEFT) },
            )
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🐾", fontSize = 22.sp)
            }
            MazePadKey(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_maze_right),
                style = style,
                enabled = enabled,
                highlight = highlightDir == MazeDir.RIGHT,
                onClick = { onMove(MazeDir.RIGHT) },
            )
        }
        MazePadKey(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_maze_down),
            style = style,
            enabled = enabled,
            highlight = highlightDir == MazeDir.DOWN,
            onClick = { onMove(MazeDir.DOWN) },
        )
    }
}

@Composable
private fun MazePadKey(
    icon: ImageVector,
    contentDescription: String,
    style: MazeThemeStyle,
    enabled: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(90),
        label = "mazePadPress",
    )
    val shape = RoundedCornerShape(16.dp)
    val bg =
        when {
            highlight -> style.accent
            pressed -> mazeLighten(style.boardRim, 0.35f)
            else -> mazeLighten(style.boardRim, 0.10f)
        }
    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.42f
                }.padding(3.dp)
                .size(58.dp)
                .clip(shape)
                .background(bg)
                .border(
                    1.5.dp,
                    if (highlight) Color.White.copy(alpha = 0.7f) else mazeDarken(style.boardRim, 0.35f).copy(alpha = 0.8f),
                    shape,
                ).clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) style.wallDeep else style.hudInk,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** 提示/无解/不可用：主题化底部横幅。 */
@Composable
internal fun MazeHintBanner(
    text: String,
    accent: Color,
    style: MazeThemeStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(style.hudPanel.copy(alpha = 0.92f))
                .border(1.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = style.hudInk,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 结算卡片：通关星级逐个点亮、失败垂头文案；主题底色 + 木质描边。
 * 遮罩吞掉所有指针事件，避免点击穿透到棋盘。
 */
@Composable
internal fun MazeResultOverlay(
    game: MazeGame,
    theme: MazeTheme,
    style: MazeThemeStyle,
    reduceMotion: Boolean,
    onExitSelect: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(28.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    mazeLighten(style.hudPanel, 0.14f),
                                    style.hudPanel,
                                ),
                            ),
                        ).border(2.dp, style.accent.copy(alpha = 0.75f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = mazeThemeGlyph(theme), fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            stringResource(
                                if (game.failed) R.string.bear_maze_failed else R.string.bear_maze_clear,
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (game.failed) Color(0xFFFF8A80) else style.hudInk,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (game.failed) {
                    Text(
                        text =
                            stringResource(
                                R.string.bear_maze_failed_detail,
                                game.endlessWave,
                                game.moves,
                                game.level.moveBudget,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = style.hudInk.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    MazeStarRow(
                        earned = game.clearStars,
                        style = style,
                        reduceMotion = reduceMotion,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bear_maze_clear_moves, game.moves),
                        style = MaterialTheme.typography.bodyMedium,
                        color = style.hudInk.copy(alpha = 0.92f),
                    )
                    if (game.hintsUsed > 0) {
                        Text(
                            text = stringResource(R.string.bear_maze_clear_hints, game.hintsUsed),
                            style = MaterialTheme.typography.bodySmall,
                            color = style.hudInk.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            GameAudio.play(GameCue.UI_CLICK)
                            onExitSelect()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.bear_maze_levels),
                            color = style.hudInk.copy(alpha = 0.85f),
                        )
                    }
                    Button(
                        onClick = {
                            GameAudio.play(GameCue.UI_CLICK)
                            onRestart()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = style.accent,
                                contentColor = style.wallDeep,
                            ),
                    ) {
                        Text(stringResource(R.string.bear_maze_restart), fontWeight = FontWeight.Bold)
                    }
                    if (!game.failed &&
                        !game.daily &&
                        (game.endless || game.levelIndex + 1 < MAZE_CAMPAIGN_SIZE)
                    ) {
                        Button(
                            onClick = {
                                GameAudio.play(GameCue.UI_CLICK)
                                onNext()
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = mazeLighten(style.accent, 0.25f),
                                    contentColor = style.wallDeep,
                                ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (game.endless) R.string.bear_maze_next_wave else R.string.bear_maze_next,
                                    ),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MazeStarRow(
    earned: Int,
    style: MazeThemeStyle,
    reduceMotion: Boolean,
) {
    var lit by remember(earned, reduceMotion) { mutableIntStateOf(if (reduceMotion) earned else 0) }
    LaunchedEffect(earned, reduceMotion) {
        if (reduceMotion) {
            lit = earned
            return@LaunchedEffect
        }
        lit = 0
        repeat(earned.coerceIn(0, MAZE_STAR_MAX)) { i ->
            delay(200)
            lit = i + 1
        }
    }
    // 逐颗星暴露给读屏会变成三次重复的「黑星」，且点亮动画期间反复播报；
    // 这里合并成一个节点，只报一次「获得 N 颗星，满分 3 颗」。
    val starsDescription = stringResource(R.string.maze_a11y_stars_earned, earned, MAZE_STAR_MAX)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = starsDescription },
    ) {
        for (i in 0 until MAZE_STAR_MAX) {
            val active = i < lit
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0.82f,
                animationSpec = tween(220),
                label = "mazeStar",
            )
            Text(
                text = "★",
                fontSize = 38.sp,
                color = if (active) Color(0xFFFFC107) else style.hudInk.copy(alpha = 0.22f),
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (active) 1f else 0.55f
                        rotationZ = if (active) 0f else -8f
                    },
            )
        }
    }
}
