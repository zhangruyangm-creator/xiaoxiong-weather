package com.xiaoxiong.weather.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlin.math.abs

@Composable
internal fun MazePlayField(
    game: MazeGame,
    sprites: BearSprites,
    frame: Long,
    weather: FarmWeather,
    rainStrength: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    onExitSelect: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
    onMove: (MazeDir) -> Unit,
    onCharacter: () -> Unit,
) {
    val style = mazeThemeStyle(game.level.theme)
    var swipeAcc by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(skyGradient(style.skyTop, style.skyBottom))),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(game.levelIndex, game.phase) {
                        detectDragGestures(
                            onDragStart = { swipeAcc = Offset.Zero },
                            onDrag = { _, drag -> swipeAcc += drag },
                            onDragEnd = {
                                val dx = swipeAcc.x
                                val dy = swipeAcc.y
                                if (abs(dx) < 24f && abs(dy) < 24f) return@detectDragGestures
                                val dir =
                                    if (abs(dx) > abs(dy)) {
                                        if (dx > 0) MazeDir.RIGHT else MazeDir.LEFT
                                    } else {
                                        if (dy > 0) MazeDir.DOWN else MazeDir.UP
                                    }
                                onMove(dir)
                            },
                        )
                    }.pointerInput(game.levelIndex) {
                        detectTapGestures { /* 吞掉，避免误触 */ }
                    },
        ) {
            // frame 订阅每帧更新，触发逐帧重绘（UI 视角跟随小熊移动、天气动画）
            @Suppress("UNUSED_EXPRESSION")
            frame
            drawMazeWorld(game, sprites, style)
            drawMazeWeather(game, weather, rainStrength, freezingRain, hail)
        }

        // HUD
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExitSelect) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${game.level.id}. ${game.level.titleZh}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.bear_maze_hud,
                            game.honeyLeft,
                            game.honeyTotal,
                            game.keys,
                            game.moves,
                            game.level.moveBudget,
                        ),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, stringResource(R.string.bear_maze_restart), tint = Color.White)
            }
            GameCharacterButton(onClick = onCharacter)
        }

        if (game.needHoneyHintT > 0f) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp)
                        .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE6333333),
            ) {
                Text(
                    text = stringResource(R.string.bear_maze_need_honey),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 方向键
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MazeArrow(Icons.Default.KeyboardArrowUp) { onMove(MazeDir.UP) }
            Row {
                MazeArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { onMove(MazeDir.LEFT) }
                Spacer(modifier = Modifier.width(56.dp))
                MazeArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight) { onMove(MazeDir.RIGHT) }
            }
            MazeArrow(Icons.Default.KeyboardArrowDown) { onMove(MazeDir.DOWN) }
        }

        if (game.phase == MazePhase.CLEAR) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
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
                        .align(Alignment.Center)
                        .padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (game.failed) {
                        Text(
                            stringResource(R.string.bear_maze_failed),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(
                                R.string.bear_maze_failed_detail,
                                game.endlessWave,
                                game.moves,
                                game.level.moveBudget,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            stringResource(R.string.bear_maze_clear),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "★".repeat(game.clearStars) + "☆".repeat(3 - game.clearStars),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFFB300),
                        )
                        Text(
                            stringResource(R.string.bear_maze_clear_moves, game.moves),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onExitSelect) {
                            Text(stringResource(R.string.bear_maze_levels))
                        }
                        Button(onClick = onRestart) {
                            Text(stringResource(R.string.bear_maze_restart))
                        }
                        if (!game.failed && (game.endless || game.levelIndex + 1 < MAZE_CAMPAIGN_SIZE)) {
                            Button(onClick = onNext) {
                                Text(
                                    if (game.endless) {
                                        stringResource(R.string.bear_maze_next_wave)
                                    } else {
                                        stringResource(R.string.bear_maze_next)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MazeArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.28f),
        modifier =
            Modifier
                .padding(4.dp)
                .size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
