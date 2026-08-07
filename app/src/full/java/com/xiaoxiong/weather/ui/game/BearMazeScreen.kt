package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 彩蛋口令：搜索框输入「#小熊迷宫」进入迷宫冒险。 */
const val EASTER_EGG_BEAR_MAZE = "#小熊迷宫"

private enum class MazeDir(
    val dr: Int,
    val dc: Int,
) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1),
}

private enum class MazePhase { SELECT, PLAYING, CLEAR }

private class MazeGame {
    var phase by mutableStateOf(MazePhase.SELECT)
    var levelIndex by mutableIntStateOf(0)
    var unlocked by mutableIntStateOf(1)

    /** 战役每关最高星级 0..3。 */
    var stars = IntArray(MAZE_CAMPAIGN_SIZE)

    /** 无尽模式：当前波次 / 历史最高。 */
    var endless by mutableStateOf(false)
    var endlessWave by mutableIntStateOf(1)
    var endlessBest by mutableIntStateOf(0)
    private var endlessSessionSeed = Random.nextLong()

    var rows = 0
    var cols = 0
    lateinit var grid: Array<Array<MazeCell>>
    lateinit var currentLevel: MazeLevel
    var startR = 0
    var startC = 0
    var exitR = 0
    var exitC = 0

    var bearR by mutableIntStateOf(0)
    var bearC by mutableIntStateOf(0)
    var keys by mutableIntStateOf(0)
    var honeyLeft by mutableIntStateOf(0)
    var honeyTotal = 0
    var moves by mutableIntStateOf(0)
    var facing by mutableStateOf(MazeDir.RIGHT)
    var cleared by mutableStateOf(false)
    var clearStars by mutableIntStateOf(0)

    /** 站在出口但蜂蜜未收齐时的提示剩余时间（秒）。 */
    var needHoneyHintT by mutableFloatStateOf(0f)

    /** 最后一罐蜂蜜被收集时的 [time]（秒）；-1 表示本关还没有记录（出口直接视为全开）。 */
    var lastHoneyAt = -1f

    // 平滑移动插值：从 (fromR,fromC) → (bearR,bearC)
    var fromR = 0
    var fromC = 0
    var moveT by mutableFloatStateOf(1f) // 1 = 静止
    var sliding = false
    var time = 0f

    val level: MazeLevel get() = currentLevel
    val moving get() = moveT < 1f

    fun loadProgress(prefs: android.content.SharedPreferences) {
        unlocked = prefs.getInt("maze_unlocked", 1).coerceIn(1, MAZE_CAMPAIGN_SIZE)
        for (i in stars.indices) {
            stars[i] = prefs.getInt("maze_star_$i", 0)
        }
        endlessBest = prefs.getInt("maze_endless_best", 0)
    }

    fun saveProgress(prefs: android.content.SharedPreferences) {
        prefs.edit().apply {
            putInt("maze_unlocked", unlocked)
            stars.forEachIndexed { i, s -> putInt("maze_star_$i", s) }
            putInt("maze_endless_best", endlessBest)
            apply()
        }
    }

    fun openSelect() {
        phase = MazePhase.SELECT
        cleared = false
        endless = false
    }

    fun startLevel(index: Int) {
        endless = false
        levelIndex = index.coerceIn(0, MAZE_CAMPAIGN_SIZE - 1)
        loadLevel(mazeCampaignLevel(levelIndex))
    }

    /** 无尽随机：每波用新种子生成，难度随波次上升。 */
    fun startEndless(wave: Int = 1) {
        endless = true
        endlessWave = wave.coerceAtLeast(1)
        if (wave == 1) endlessSessionSeed = Random.nextLong()
        levelIndex = -1
        val diff = (8 + endlessWave * 2).coerceAtMost(45)
        val seed = endlessSessionSeed xor (endlessWave.toLong() * 0x9E3779B9L)
        loadLevel(
            generateMazeLevel(
                seed = seed,
                id = endlessWave,
                difficulty = diff,
            ).copy(
                titleZh = "无尽 · 第${endlessWave}波",
                titleEn = "Endless #$endlessWave",
            ),
        )
    }

    private fun loadLevel(lv: MazeLevel) {
        currentLevel = lv
        grid = lv.parse()
        rows = lv.rows
        cols = lv.cols
        honeyTotal = 0
        honeyLeft = 0
        keys = 0
        moves = 0
        cleared = false
        clearStars = 0
        needHoneyHintT = 0f
        lastHoneyAt = -1f
        sliding = false
        moveT = 1f
        facing = MazeDir.RIGHT
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (grid[r][c].tile) {
                    MazeTile.START -> {
                        startR = r
                        startC = c
                        bearR = r
                        bearC = c
                        fromR = r
                        fromC = c
                    }
                    MazeTile.EXIT -> {
                        exitR = r
                        exitC = c
                    }
                    MazeTile.HONEY -> {
                        honeyTotal++
                        honeyLeft++
                    }
                    else -> Unit
                }
            }
        }
        phase = MazePhase.PLAYING
    }

    fun restart() {
        if (endless) {
            startEndless(endlessWave)
        } else {
            startLevel(levelIndex)
        }
    }

    private fun inBounds(
        r: Int,
        c: Int,
    ) = r in 0 until rows && c in 0 until cols

    private fun walkable(
        r: Int,
        c: Int,
    ): Boolean {
        if (!inBounds(r, c)) return false
        return when (grid[r][c].tile) {
            MazeTile.WALL -> false
            MazeTile.LOCK -> keys > 0
            else -> true
        }
    }

    /** 尝试朝方向迈一步；成功返回 true。冰面会进入滑动。 */
    fun tryMove(dir: MazeDir): Boolean {
        if (phase != MazePhase.PLAYING || moving || cleared) return false
        val nr = bearR + dir.dr
        val nc = bearC + dir.dc
        if (!walkable(nr, nc)) return false
        facing = dir
        beginStep(nr, nc)
        return true
    }

    private fun beginStep(
        nr: Int,
        nc: Int,
    ) {
        fromR = bearR
        fromC = bearC
        bearR = nr
        bearC = nc
        moveT = 0f
        moves++
        sliding = grid[nr][nc].tile == MazeTile.ICE
    }

    /** 步进动画结束时结算格子事件；若在冰上则继续滑。 */
    fun onStepLanded(): Boolean {
        val cell = grid[bearR][bearC]
        var haptic = false
        when (cell.tile) {
            MazeTile.HONEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                honeyLeft = (honeyLeft - 1).coerceAtLeast(0)
                // 收齐最后一罐：记录此刻时间，出口门从这一秒开始播开启动画
                if (honeyLeft == 0) lastHoneyAt = time
                haptic = true
            }
            MazeTile.KEY -> {
                grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                keys++
                haptic = true
            }
            MazeTile.LOCK -> {
                if (keys > 0) {
                    keys--
                    grid[bearR][bearC] = MazeCell(MazeTile.FLOOR)
                    haptic = true
                }
            }
            MazeTile.PORTAL -> {
                val id = cell.portalId
                // 找另一端
                outer@ for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if ((r != bearR || c != bearC) &&
                            grid[r][c].tile == MazeTile.PORTAL &&
                            grid[r][c].portalId == id
                        ) {
                            bearR = r
                            bearC = c
                            fromR = r
                            fromC = c
                            moveT = 1f
                            sliding = false
                            haptic = true
                            break@outer
                        }
                    }
                }
            }
            MazeTile.EXIT -> {
                if (honeyLeft == 0) {
                    finishLevel()
                    return true
                } else {
                    needHoneyHintT = 2.2f
                    haptic = true
                }
            }
            else -> Unit
        }
        // 冰面继续滑
        if (sliding && phase == MazePhase.PLAYING) {
            val nr = bearR + facing.dr
            val nc = bearC + facing.dc
            if (walkable(nr, nc)) {
                fromR = bearR
                fromC = bearC
                bearR = nr
                bearC = nc
                moveT = 0f
                // 滑行不额外计步
                sliding = grid[nr][nc].tile == MazeTile.ICE
            } else {
                sliding = false
            }
        }
        return haptic
    }

    private fun finishLevel() {
        cleared = true
        phase = MazePhase.CLEAR
        val budget = level.moveBudget
        // 仅在蜂蜜收齐后踩出口才会到这里，直接按步数定星级
        clearStars =
            when {
                moves <= budget -> 3
                moves <= (budget * 1.35f).toInt() -> 2
                else -> 1
            }
        if (endless) {
            if (endlessWave > endlessBest) endlessBest = endlessWave
        } else if (levelIndex in stars.indices) {
            if (clearStars > stars[levelIndex]) stars[levelIndex] = clearStars
            val next = levelIndex + 2 // 1-based unlock count
            if (next > unlocked) unlocked = next.coerceAtMost(MAZE_CAMPAIGN_SIZE)
        }
    }

    fun bearDrawPos(): Pair<Float, Float> {
        if (moveT >= 1f) return bearR.toFloat() to bearC.toFloat()
        // easeOutQuad
        val t = moveT
        val e = 1f - (1f - t) * (1f - t)
        val r = fromR + (bearR - fromR) * e
        val c = fromC + (bearC - fromC) * e
        return r to c
    }

    fun tick(dt: Float) {
        time += dt
        if (needHoneyHintT > 0f) {
            needHoneyHintT = (needHoneyHintT - dt).coerceAtLeast(0f)
        }
        if (moving) {
            val speed = if (sliding) 7.5f else 5.5f
            moveT = min(1f, moveT + dt * speed)
            if (moveT >= 1f) {
                moveT = 1f
                onStepLanded()
            }
        }
    }
}

@Composable
fun BearMazeScreen(
    @Suppress("UNUSED_PARAMETER") sceneCode: Int?,
    @Suppress("UNUSED_PARAMETER") isDay: Boolean?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs =
        remember {
            context.getSharedPreferences("bear_game", Context.MODE_PRIVATE)
        }
    val game = remember { MazeGame().also { it.loadProgress(prefs) } }
    val sprites =
        BearSprites(
            runA = ImageBitmap.imageResource(R.drawable.bear_run_a),
            runPushA = ImageBitmap.imageResource(R.drawable.bear_run_push_a),
            runPass = ImageBitmap.imageResource(R.drawable.bear_run_pass),
            runB = ImageBitmap.imageResource(R.drawable.bear_run_b),
            runPushB = ImageBitmap.imageResource(R.drawable.bear_run_push_b),
            jump = ImageBitmap.imageResource(R.drawable.bear_jump),
            fall = ImageBitmap.imageResource(R.drawable.bear_fall),
            land = ImageBitmap.imageResource(R.drawable.bear_land),
        )
    var frame by remember { mutableStateOf(0L) }

    // 选关列表只需标题/主题等元数据，轻量同步可得；完整关卡（含迷宫图）在
    // 点击某关开始游玩时才生成，避免进入选关界面就批量生成造成等待。
    val campaignMetas = remember { List(MAZE_CAMPAIGN_SIZE) { mazeCampaignMeta(it) } }

    LaunchedEffect(Unit) {
        var last = Long.MIN_VALUE
        while (isActive) {
            withFrameNanos { now ->
                val dt =
                    if (last == Long.MIN_VALUE) {
                        1f / 60f
                    } else {
                        min((now - last) / 1e9f, 0.033f)
                    }
                last = now
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
                // 选关界面没有动画，只在游玩时推进帧，避免每帧状态更新打断滚动手势
                if (game.phase != MazePhase.SELECT) {
                    frame = now
                }
            }
        }
    }

    BackHandler {
        when (game.phase) {
            MazePhase.SELECT -> onExit()
            MazePhase.PLAYING, MazePhase.CLEAR -> game.openSelect()
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
                )
            }
            MazePhase.PLAYING, MazePhase.CLEAR -> {
                MazePlayField(
                    game = game,
                    sprites = sprites,
                    frame = frame,
                    onExitSelect = { game.openSelect() },
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
                )
            }
        }
    }
}

@Composable
private fun MazeLevelSelect(
    game: MazeGame,
    campaignMetas: List<MazeLevelMeta>,
    onPick: (Int) -> Unit,
    onEndless: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF5BA8E8), Color(0xFFC8E8F8))))
                .statusBarsPadding()
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close_game))
            }
            Text(
                text = stringResource(R.string.bear_maze_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.bear_maze_select_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF2A4A6A),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        // 无尽随机入口
        Button(
            onClick = onEndless,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.bear_maze_endless),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.bear_maze_endless_best, game.endlessBest),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.bear_maze_campaign_label, MAZE_CAMPAIGN_SIZE),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF2A4A6A),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            items(campaignMetas.size) { i ->
                val meta = campaignMetas[i]
                val open = i < game.unlocked
                val style = mazeThemeStyle(meta.theme)
                val handcrafted = i < MAZE_HANDCRAFTED_COUNT
                Card(
                    onClick = { if (open) onPick(i) },
                    enabled = open,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = if (open) style.floor else Color(0xFFB0BEC5),
                        ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "${meta.id}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.wall,
                        )
                        Text(
                            text = meta.titleZh,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = style.wall.copy(alpha = 0.85f),
                        )
                        Text(
                            text =
                                when {
                                    !open -> "🔒"
                                    else -> "★".repeat(game.stars[i]) + "☆".repeat(3 - game.stars[i])
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = style.accent,
                        )
                        Text(
                            text =
                                if (handcrafted) {
                                    stringResource(R.string.bear_maze_tag_hand)
                                } else {
                                    stringResource(R.string.bear_maze_tag_gen)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = style.wall.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MazePlayField(
    game: MazeGame,
    sprites: BearSprites,
    frame: Long,
    onExitSelect: () -> Unit,
    onRestart: () -> Unit,
    onNext: () -> Unit,
    onMove: (MazeDir) -> Unit,
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
            @Suppress("UNUSED_EXPRESSION")
            frame
            drawMazeWorld(game, sprites, style)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onExitSelect) {
                            Text(stringResource(R.string.bear_maze_levels))
                        }
                        Button(onClick = onRestart) {
                            Text(stringResource(R.string.bear_maze_restart))
                        }
                        if (game.endless || game.levelIndex + 1 < MAZE_CAMPAIGN_SIZE) {
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
private fun MazeArrow(
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

// ---------------- 绘制 ----------------

private fun DrawScope.drawMazeWorld(
    game: MazeGame,
    sprites: BearSprites,
    style: MazeThemeStyle,
) {
    val pad = 16f
    val hudTop = 110f
    val hudBottom = 200f
    val availW = size.width - pad * 2
    val availH = size.height - hudTop - hudBottom
    val cell = min(availW / game.cols, availH / game.rows)
    val boardW = cell * game.cols
    val boardH = cell * game.rows
    val ox = (size.width - boardW) / 2f
    val oy = hudTop + (availH - boardH) / 2f

    // 天空地平线光晕：位于棋盘上方天空带，让背景更立体
    val horizonY = oy - cell * 0.5f
    if (horizonY > 0f) {
        drawHorizonGlow(horizonY, midColor(style.skyBottom, Color.White), size.height * 0.16f)
    }

    // 主题氛围装饰
    drawThemeFx(game, style)

    // 棋盘阴影
    drawRoundRect(
        Color.Black.copy(alpha = 0.18f),
        Offset(ox + 4f, oy + 6f),
        Size(boardW, boardH),
        CornerRadius(12f),
    )

    for (r in 0 until game.rows) {
        for (c in 0 until game.cols) {
            val x = ox + c * cell
            val y = oy + r * cell
            drawCell(game, style, r, c, x, y, cell)
        }
    }

    // 小熊
    val (br, bc) = game.bearDrawPos()
    val bx = ox + (bc + 0.5f) * cell
    val by = oy + (br + 0.5f) * cell
    drawMazeBear(game, sprites, bx, by, cell)
}

private fun DrawScope.drawThemeFx(
    game: MazeGame,
    style: MazeThemeStyle,
) {
    when (game.level.theme) {
        MazeTheme.RAIN -> {
            for (i in 0 until 40) {
                val x = ((i * 97 + game.time * 80) % (size.width + 40)) - 20
                val y = ((i * 53 + game.time * 220) % (size.height + 60)) - 30
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x, y),
                    Offset(x - 4f, y + 14f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
        MazeTheme.SNOW -> {
            for (i in 0 until 35) {
                val x = ((i * 83 + game.time * 30) % (size.width + 20))
                val y = ((i * 61 + game.time * 50) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.7f), 2.5f + (i % 3), Offset(x, y))
            }
        }
        MazeTheme.NIGHT -> {
            for (i in 0 until 50) {
                val x = (i * 137) % size.width
                val y = (i * 89) % (size.height * 0.55f)
                val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(game.time * 2f + i))
                drawCircle(Color.White, 1.2f + (i % 3) * 0.4f, Offset(x, y), alpha = tw)
            }
        }
        MazeTheme.FESTIVAL -> {
            for (i in 0 until 8) {
                val x = size.width * (0.08f + i * 0.12f)
                val y = 80f + sin(game.time + i) * 6f
                drawCircle(Color(0xFFFF5252), 10f, Offset(x, y))
                drawLine(Color(0xFFFFD54F), Offset(x, y + 10f), Offset(x, y + 28f), strokeWidth = 2f)
            }
        }
        MazeTheme.CLOUD -> {
            for (i in 0 until 5) {
                val x = ((i * 180f + game.time * 18f) % (size.width + 160f)) - 80f
                val y = 70f + i * 28f
                drawCircle(Color.White.copy(alpha = 0.55f), 28f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.55f), 22f, Offset(x + 24f, y + 4f))
                drawCircle(Color.White.copy(alpha = 0.55f), 20f, Offset(x - 22f, y + 6f))
            }
        }
        MazeTheme.GARDEN -> {
            // 太阳
            val sun = Offset(size.width * 0.85f, 90f)
            drawCircle(Color(0xFFFFE082), 28f, sun)
            for (i in 0 until 10) {
                val a = Math.PI.toFloat() * 2f * i / 10f + game.time * 0.4f
                drawLine(
                    Color(0xFFFFD54F),
                    sun + Offset(cos(a) * 34f, sin(a) * 34f),
                    sun + Offset(cos(a) * 44f, sin(a) * 44f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawCell(
    game: MazeGame,
    style: MazeThemeStyle,
    r: Int,
    c: Int,
    x: Float,
    y: Float,
    cell: Float,
) {
    val tile = game.grid[r][c]
    val pad = cell * 0.06f
    when (tile.tile) {
        MazeTile.WALL -> {
            // 墙体底部阴影（右下）
            drawRoundRect(
                Color.Black.copy(alpha = 0.22f),
                Offset(x + pad * 0.55f, y + pad * 0.55f),
                Size(cell - pad * 0.5f, cell - pad * 0.5f),
                CornerRadius(cell * 0.12f),
            )
            // 墙身
            drawRoundRect(
                style.wall,
                Offset(x + pad * 0.3f, y + pad * 0.3f),
                Size(cell - pad * 0.6f, cell - pad * 0.6f),
                CornerRadius(cell * 0.12f),
            )
            // 顶部受光面
            drawRoundRect(
                style.wallTop,
                Offset(x + pad, y + pad),
                Size(cell - pad * 2, cell * 0.34f),
                CornerRadius(cell * 0.08f),
            )
            // 砖缝：两条细横线压暗，做出立体分层
            drawLine(
                Color.Black.copy(alpha = 0.10f),
                Offset(x + pad * 1.6f, y + pad + cell * 0.52f),
                Offset(x + cell - pad * 1.6f, y + pad + cell * 0.52f),
                strokeWidth = 1.5f,
            )
            drawLine(
                Color.Black.copy(alpha = 0.07f),
                Offset(x + pad * 1.6f, y + pad + cell * 0.78f),
                Offset(x + cell - pad * 1.6f, y + pad + cell * 0.78f),
                strokeWidth = 1.5f,
            )
        }
        else -> {
            val floorCol = if ((r + c) % 2 == 0) style.floor else style.floorAlt
            drawRoundRect(
                floorCol,
                Offset(x + pad * 0.5f, y + pad * 0.5f),
                Size(cell - pad, cell - pad),
                CornerRadius(cell * 0.1f),
            )
            // 地板四周内阴影，增强格子边界立体感
            drawRoundRect(
                Color.Black.copy(alpha = 0.06f),
                Offset(x + pad * 0.5f, y + pad * 0.5f),
                Size(cell - pad, cell - pad),
                CornerRadius(cell * 0.1f),
                style = Stroke(width = 1.5f),
            )
            // 轻量纹理：左上高光小角
            drawCircle(
                Color.White.copy(alpha = 0.10f),
                cell * 0.08f,
                Offset(x + pad + cell * 0.16f, y + pad + cell * 0.18f),
            )
        }
    }
    val cx = x + cell / 2
    val cy = y + cell / 2
    when (tile.tile) {
        MazeTile.ICE -> {
            drawRoundRect(
                style.ice.copy(alpha = 0.85f),
                Offset(x + pad, y + pad),
                Size(cell - pad * 2, cell - pad * 2),
                CornerRadius(cell * 0.12f),
            )
            drawLine(
                Color.White.copy(alpha = 0.6f),
                Offset(cx - cell * 0.2f, cy - cell * 0.15f),
                Offset(cx - cell * 0.05f, cy - cell * 0.25f),
                strokeWidth = 2f,
            )
        }
        MazeTile.HONEY -> {
            // 蜜罐
            drawCircle(Color(0xFFFFB300), cell * 0.22f, Offset(cx, cy + cell * 0.02f))
            drawCircle(Color(0xFFFFD54F), cell * 0.14f, Offset(cx, cy - cell * 0.02f))
            drawRoundRect(
                Color(0xFF8D6E63),
                Offset(cx - cell * 0.12f, cy - cell * 0.22f),
                Size(cell * 0.24f, cell * 0.1f),
                CornerRadius(3f),
            )
            // 闪光
            val pulse = 0.7f + 0.3f * sin(game.time * 4f + r + c)
            drawCircle(Color.White.copy(alpha = 0.35f * pulse), cell * 0.08f, Offset(cx - cell * 0.06f, cy - cell * 0.06f))
        }
        MazeTile.KEY -> {
            drawCircle(Color(0xFFFFD54F), cell * 0.14f, Offset(cx, cy - cell * 0.08f))
            drawRoundRect(
                Color(0xFFFFD54F),
                Offset(cx - cell * 0.05f, cy - cell * 0.02f),
                Size(cell * 0.1f, cell * 0.28f),
                CornerRadius(2f),
            )
            drawCircle(style.floor, cell * 0.06f, Offset(cx, cy - cell * 0.08f))
        }
        MazeTile.LOCK -> {
            drawRoundRect(
                Color(0xFF6D4C41),
                Offset(cx - cell * 0.22f, cy - cell * 0.1f),
                Size(cell * 0.44f, cell * 0.32f),
                CornerRadius(4f),
            )
            drawArc(
                Color(0xFF6D4C41),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - cell * 0.16f, cy - cell * 0.32f),
                size = Size(cell * 0.32f, cell * 0.28f),
                style = Stroke(width = cell * 0.08f),
            )
            drawCircle(Color(0xFFFFD54F), cell * 0.06f, Offset(cx, cy + cell * 0.02f))
        }
        MazeTile.PORTAL -> {
            val spin = game.time * 120f
            run {
                drawCircle(Color(0xFF7E57C2).copy(alpha = 0.35f), cell * 0.32f, Offset(cx, cy))
                drawCircle(
                    Color(0xFFB39DDB),
                    cell * 0.22f,
                    Offset(cx, cy),
                    style = Stroke(width = cell * 0.06f),
                )
                drawCircle(Color(0xFFE1BEE7), cell * 0.10f, Offset(cx, cy))
                // 旋转星芒
                for (i in 0 until 4) {
                    val a = Math.PI.toFloat() / 2f * i + spin * 0.017f
                    drawLine(
                        Color.White.copy(alpha = 0.8f),
                        Offset(cx + cos(a) * cell * 0.12f, cy + sin(a) * cell * 0.12f),
                        Offset(cx + cos(a) * cell * 0.28f, cy + sin(a) * cell * 0.28f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // 门号
            // skip text — use color bands by id
            val band =
                when (tile.portalId % 3) {
                    1 -> Color(0xFF80CBC4)
                    2 -> Color(0xFFFFAB91)
                    else -> Color(0xFFCE93D8)
                }
            drawCircle(band, cell * 0.06f, Offset(cx, cy - cell * 0.28f))
        }
        MazeTile.EXIT -> {
            val open = game.honeyLeft == 0
            // 门框：深色立柱 + 门楣
            val frameC = Color(0xFF6A7A6E)
            drawRoundRect(
                frameC,
                Offset(cx - cell * 0.32f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                frameC,
                Offset(cx + cell * 0.24f, cy - cell * 0.34f),
                Size(cell * 0.08f, cell * 0.66f),
                CornerRadius(cell * 0.03f),
            )
            drawRoundRect(
                frameC.copy(alpha = 0.85f),
                Offset(cx - cell * 0.32f, cy - cell * 0.42f),
                Size(cell * 0.64f, cell * 0.10f),
                CornerRadius(cell * 0.04f),
            )
            // 门扇：开启时向上滑动淡出，关闭时实心
            val openT =
                if (open) {
                    doorOpenProgress(game.time, game.lastHoneyAt)
                } else {
                    0f
                }
            val doorCol = if (open) Color(0xFF66BB6A) else Color(0xFF90A4AE)
            if (openT < 1f) {
                val dy = -openT * cell * 0.42f
                drawRoundRect(
                    doorCol.copy(alpha = 1f - openT * 0.5f),
                    Offset(cx - cell * 0.28f, cy - cell * 0.32f + dy),
                    Size(cell * 0.56f, cell * 0.6f),
                    CornerRadius(cell * 0.08f),
                )
                drawCircle(Color(0xFFFFF176), cell * 0.07f, Offset(cx + cell * 0.14f, cy + dy))
            }
            if (open) {
                // 门洞发光：脉动光环
                val pulse = 0.5f + 0.5f * sin(game.time * 3f)
                drawCircle(Color(0xFFA5D6A7).copy(alpha = 0.40f + 0.2f * pulse), cell * 0.42f, Offset(cx, cy))
                // 门洞内侧亮光
                drawRoundRect(
                    Color(0xFFC8E6C9).copy(alpha = 0.5f),
                    Offset(cx - cell * 0.26f, cy - cell * 0.30f),
                    Size(cell * 0.52f, cell * 0.56f),
                    CornerRadius(cell * 0.06f),
                )
            }
        }
        MazeTile.START -> {
            drawCircle(style.accent.copy(alpha = 0.25f), cell * 0.28f, Offset(cx, cy))
        }
        else -> Unit
    }
}

/** 出口开门动画进度 0..1：从最后一罐蜂蜜被收集的 [lastHoneyAt] 起，1 秒内平滑开门。 */
internal fun doorOpenProgress(
    time: Float,
    lastHoneyAt: Float,
): Float {
    if (lastHoneyAt < 0f) return 1f // 本关无蜂蜜，出口直接视为全开
    val t = (time - lastHoneyAt).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t) // smoothstep：开头慢、中间快、结尾缓
}

private fun DrawScope.drawMazeBear(
    game: MazeGame,
    sprites: BearSprites,
    cx: Float,
    cy: Float,
    cell: Float,
) {
    val bs = cell * 0.92f
    val frame =
        when {
            game.moving && game.sliding -> BearFrame.JUMP
            game.moving -> runCycleFrame(game.time * 10f)
            else -> BearFrame.LAND
        }
    val flip = game.facing == MazeDir.LEFT
    val tilt =
        when (game.facing) {
            MazeDir.UP -> -6f
            MazeDir.DOWN -> 6f
            else -> 0f
        }
    // 脚下影子
    drawOval(
        Color.Black.copy(alpha = 0.18f),
        Offset(cx - bs * 0.28f, cy + bs * 0.28f),
        Size(bs * 0.56f, bs * 0.16f),
    )
    drawGameBear(
        sprites = sprites,
        centerX = cx,
        centerY = cy - bs * 0.05f,
        bs = bs,
        pose =
            BearPose(
                time = game.time,
                frame = frame,
                bodyTilt = tilt,
                flipX = flip,
                alpha = 1f,
            ),
    )
}
