package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import com.xiaoxiong.weather.ui.weather.WeatherScene
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 小熊冰球的 Compose Canvas 渲染层。
 *
 * 设计基调：蜂蜜棕木框 + 冷调冰面的桌面空气曲棍球；球面高光、投影、暗角与
 * 确定性划痕做质感。所有不变内容（木框 / 冰面 / 标记 / 库边 / 球门）按
 * 「画布尺寸 + 场景样式 + isDay + 球桌几何」烘焙成 [ImageBitmap]，每帧一次
 * drawImage；挡板与蜜罐也各烘焙一张精灵图，逐帧零 Path / Brush 分配。
 */
internal object HockeyUi {
    val iceBorder = Color(0x998FD9F2)
    val glass = Color(0xE8172533)
    val glassDeep = Color(0xF20C1622)
    val textSecondary = Color(0xB3C3DCEF)
    val accentWarm = Color(0xFFFFC46B)
    val accentCool = Color(0xFF7FE3F2)
    val danger = Color(0xFFFF8A80)
    val warn = Color(0xFFFFB74D)
}

// ---------------------------------------------------------------------
// 调色板与常量
// ---------------------------------------------------------------------

private val woodLight = Color(0xFFC0894A)
private val woodMid = Color(0xFF9B6533)
private val woodDark = Color(0xFF5E3A1C)
private val woodEdge = Color(0xFF3A2210)

private val iceDayTop = Color(0xFFEFF9FF)
private val iceDayMid = Color(0xFFD8EFFB)
private val iceDayBottom = Color(0xFFC2E3F4)
private val iceNightTop = Color(0xFFC7DFEF)
private val iceNightMid = Color(0xFFA9C9DE)
private val iceNightBottom = Color(0xFF8FB2CB)
private val iceCold = Color(0xFF9CC0DA)
private val iceWarm = Color(0xFFFFF0CE)
private val markBlue = Color(0xFF3D6C96)
private val boardShade = Color(0x66031B2E)

private val playerGoal = Color(0xFFFFB74D)
private val aiGoal = Color(0xFF4DD0E1)

private val honeyGlow = Color(0xFFFFC107)
private val iceSpark = Color(0xFFB3E5FC)
private val confettiColors =
    listOf(
        Color(0xFFFFC46B),
        Color(0xFF7FE3F2),
        Color.White,
        Color(0xFFFF8A65),
    )

private val paddlePeach =
    PaddlePalette(
        base = Color(0xFFF2A65A),
        rim = Color(0xFFB85E14),
        accent = Color(0xFFFFE0B2),
        ear = Color(0xFFE08A3C),
        earInner = Color(0xFFFFD9A0),
        cheeks = Color(0x66F48FB1),
    )
private val paddleSun =
    PaddlePalette(
        base = Color(0xFFE7B24C),
        rim = Color(0xFF8A5320),
        accent = Color(0xFFFFECB3),
        ear = Color(0xFFC98E33),
        earInner = Color(0xFFFFE082),
        cheeks = null,
    )
private val paddleAi =
    PaddlePalette(
        base = Color(0xFF4DD0E1),
        rim = Color(0xFF00697A),
        accent = Color(0xFFB2EBF2),
        ear = Color(0xFF26C6DA),
        earInner = Color(0xFFB2EBF2),
        cheeks = null,
    )

private const val SERVE_READY_SECONDS = 1.5f
private const val SERVE_BEAT_SECONDS = 0.5f

private class PaddlePalette(
    val base: Color,
    val rim: Color,
    val accent: Color,
    val ear: Color,
    val earInner: Color,
    val cheeks: Color?,
)

private fun paletteId(character: BearCharacter): Int = if (character == BearCharacter.PEACH) 0 else 1

// ---------------------------------------------------------------------
// 复用缓存：Path / Paint / Stroke
// ---------------------------------------------------------------------

private object HockeyRenderCache {
    val icePath = Path()
    val recessPath = Path()
    val shape = Path()
    val shard = Path()
    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
}

private class StrokeSlot {
    var width = -1f
    var stroke: Stroke? = null
}

private val strokeSlotA = StrokeSlot()
private val strokeSlotB = StrokeSlot()

/** 按宽度复用 Stroke（A/B 双槽）：绘制热路径上不 new Stroke。 */
private fun strokeOf(width: Float): Stroke {
    val w = round(width * 10f) / 10f
    val a = strokeSlotA
    if (a.stroke != null && a.width == w) return a.stroke!!
    val b = strokeSlotB
    if (b.stroke != null && b.width == w) {
        val swapStroke = a.stroke
        val swapWidth = a.width
        a.stroke = b.stroke
        a.width = b.width
        b.stroke = swapStroke
        b.width = swapWidth
        return a.stroke!!
    }
    val fresh = Stroke(w)
    b.stroke = a.stroke
    b.width = a.width
    a.stroke = fresh
    a.width = w
    return fresh
}

// ---------------------------------------------------------------------
// 静态冰面层烘焙缓存
// ---------------------------------------------------------------------

/**
 * 冰面静态层（木框 / 冰面渐变与划痕 / 冰面标记 / 库边 / 球门洞与网）
 * 每帧不变，烘焙成一张整屏 [ImageBitmap]，每帧一次 drawImage。
 * 仅主线程绘制，无需加锁；最多保留 [HockeyTableLayerCache.MAX_ENTRIES] 张做 LRU。
 */
private class HockeyTableLayer(
    val bitmap: ImageBitmap,
    val width: Int,
    val height: Int,
    val tableLeft: Float,
    val tableTop: Float,
    val tableWidth: Float,
    val tableHeight: Float,
    val goalWidth: Float,
    val style: WeatherSceneStyle,
    val isDay: Boolean,
)

private object HockeyTableLayerCache {
    const val MAX_ENTRIES = 2
    private const val RESIZE_TOLERANCE_PX = 24
    private const val GOAL_TOLERANCE = 2f
    private val entries = ArrayList<HockeyTableLayer>(MAX_ENTRIES)

    fun bitmap(
        game: HockeyGame,
        style: WeatherSceneStyle,
        isDay: Boolean,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
    ): HockeyTableLayer? {
        val t = game.table ?: return null
        if (t.width <= 2f || t.height <= 2f) return null
        val w = ceil(size.width).toInt()
        val h = ceil(size.height).toInt()
        if (w <= 0 || h <= 0) return null

        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.width == w &&
                e.height == h &&
                e.tableLeft == t.left &&
                e.tableTop == t.top &&
                e.tableWidth == t.width &&
                e.tableHeight == t.height &&
                e.goalWidth == t.goalWidth &&
                e.style == style &&
                e.isDay == isDay
            ) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        // 窗口连续拖动时避免逐像素重建整屏位图：尺寸相近且几何一致时复用并拉伸绘制
        i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.style == style &&
                e.isDay == isDay &&
                abs(e.goalWidth - t.goalWidth) <= GOAL_TOLERANCE &&
                abs(e.width - w) <= RESIZE_TOLERANCE_PX &&
                abs(e.height - h) <= RESIZE_TOLERANCE_PX
            ) {
                return e
            }
            i--
        }

        val bitmap = ImageBitmap(w, h)
        // 用原始浮点 size 布局，保证坐标与直接绘制一致；位图取 ceil 仅多出边缘空像素
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
            drawHockeyTableStatic(game, style, isDay)
        }
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(
            HockeyTableLayer(
                bitmap = bitmap,
                width = w,
                height = h,
                tableLeft = t.left,
                tableTop = t.top,
                tableWidth = t.width,
                tableHeight = t.height,
                goalWidth = t.goalWidth,
                style = style,
                isDay = isDay,
            ),
        )
        return entries.last()
    }
}

// ---------------------------------------------------------------------
// 挡板 / 蜜罐精灵图缓存
// ---------------------------------------------------------------------

private class HockeySprite(
    val bitmap: ImageBitmap,
    val half: Float,
    val kind: Int,
    val radius: Float,
    val paletteId: Int,
)

private object HockeySpriteCache {
    private const val MAX_ENTRIES = 3
    private const val KIND_PADDLE = 0
    private const val KIND_PUCK = 1
    private val entries = ArrayList<HockeySprite>(MAX_ENTRIES)

    fun paddle(
        radius: Float,
        paletteId: Int,
        density: Density,
        layoutDirection: LayoutDirection,
    ): HockeySprite? {
        if (radius < 0.5f || radius > 4096f) return null
        val keyR = round(radius * 2f) / 2f
        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.kind == KIND_PADDLE && e.radius == keyR && e.paletteId == paletteId) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        val palette =
            when (paletteId) {
                0 -> paddlePeach
                1 -> paddleSun
                else -> paddleAi
            }
        val half = keyR * 1.42f
        val side = ceil(half * 2f).toInt().coerceAtLeast(2)
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            translate(half, half) { drawPaddleArt(keyR, palette) }
        }
        return add(HockeySprite(bitmap, half, KIND_PADDLE, keyR, paletteId))
    }

    fun puck(
        radius: Float,
        density: Density,
        layoutDirection: LayoutDirection,
    ): HockeySprite? {
        if (radius < 0.5f || radius > 4096f) return null
        val keyR = round(radius * 2f) / 2f
        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.kind == KIND_PUCK && e.radius == keyR) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        val half = keyR * 1.12f
        val side = ceil(half * 2f).toInt().coerceAtLeast(2)
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            translate(half, half) { drawPuckArt(keyR) }
        }
        return add(HockeySprite(bitmap, half, KIND_PUCK, keyR, 0))
    }

    private fun add(sprite: HockeySprite): HockeySprite {
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(sprite)
        return sprite
    }
}

// ---------------------------------------------------------------------
// 入口：每帧绘制
// ---------------------------------------------------------------------

/** 所有动态内容的绘制入口；静态内容命中缓存时每帧仅一次 drawImage。 */
internal fun DrawScope.drawHockeyWorld(
    game: HockeyGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
    reduceMotion: Boolean,
    character: BearCharacter,
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val t = game.table ?: return
    val time = game.time

    drawHockeyAmbient(weather, rain, freezingRain, hail, isDay, time)

    val layer = HockeyTableLayerCache.bitmap(game, style, isDay, size, this, layoutDirection)
    if (layer != null) {
        val dstW = ceil(size.width).toInt()
        val dstH = ceil(size.height).toInt()
        val same = layer.width == dstW && layer.height == dstH
        drawImage(
            image = layer.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(layer.width, layer.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = if (same) FilterQuality.None else FilterQuality.Low,
        )
    } else {
        drawHockeyTableStatic(game, style, isDay)
    }

    drawGoalLight(game)

    if (game.phase == Phase.MATCH_OVER && game.matchWinner == Scorer.PLAYER && !reduceMotion) {
        drawWinnerConfetti(game, time)
    }

    if (!reduceMotion) drawPuckTrail(game)
    if (!reduceMotion) drawHockeySparks(game)

    val short = t.shortSide
    drawBearPaddle(
        x = game.ai.x,
        y = game.ai.y,
        r = game.ai.r,
        palette = paddleAi,
        paletteId = 2,
        vx = game.ai.vx,
        vy = game.ai.vy,
        shortSide = short,
        hit = game.hitFlash,
        reduceMotion = reduceMotion,
    )
    val palette = if (character == BearCharacter.PEACH) paddlePeach else paddleSun
    drawBearPaddle(
        x = game.player.x,
        y = game.player.y,
        r = game.player.r,
        palette = palette,
        paletteId = paletteId(character),
        vx = game.player.vx,
        vy = game.player.vy,
        shortSide = short,
        hit = game.hitFlash,
        reduceMotion = reduceMotion,
    )
    drawHoneyPuck(game, time, reduceMotion)

    if (game.phase == Phase.SERVE) {
        drawServeHint(game, t, time)
    }

    val flash = game.scoreFlash
    if (flash > 0.01f) {
        val scorer = game.lastScorer
        val tint =
            when (scorer) {
                Scorer.PLAYER -> playerGoal
                Scorer.AI -> aiGoal
                null -> Color.Transparent
            }
        if (tint != Color.Transparent) {
            drawRect(tint.copy(alpha = 0.09f * flash))
            drawScoreFloat(
                text = "+1",
                x = t.midX,
                y =
                    if (scorer == Scorer.PLAYER) {
                        t.top + short * (0.09f + 0.12f * (1f - flash))
                    } else {
                        t.bottom - short * (0.09f + 0.12f * (1f - flash))
                    },
                color = tint,
                sizePx = short * 0.06f,
                alpha = flash,
            )
        }
    }
}

// ---------------------------------------------------------------------
// 静态冰面：木框 / 冰面 / 标记 / 库边 / 球门
// ---------------------------------------------------------------------

private fun DrawScope.drawHockeyTableStatic(
    game: HockeyGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val t = game.table ?: return
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    if (w <= 2f || h <= 2f) return
    val short = t.shortSide
    val margin = min(min(l, size.width - r), min(tp, size.height - b))
    val frame = min(short * 0.075f, margin * 0.86f).coerceAtLeast(2f)
    val fl = l - frame
    val ft = tp - frame
    val fr = r + frame
    val fb = b + frame
    val outerW = fr - fl
    val outerH = fb - ft
    val corner = min(frame * 0.85f, short * 0.045f)
    val iceCorner = corner * 0.82f
    val dark = !isDay
    val midX = t.midX
    val midY = t.midY
    val goalHalf = t.goalWidth * 0.5f

    // 整桌投影
    drawRoundRect(
        Color.Black.copy(alpha = if (dark) 0.42f else 0.30f),
        Offset(fl + frame * 0.14f, ft + frame * 0.22f),
        Size(outerW, outerH),
        CornerRadius(corner),
    )

    // 木框：对角渐变
    drawRoundRect(
        Brush.linearGradient(
            colors = listOf(woodLight, woodMid, woodDark),
            start = Offset(fl, ft),
            end = Offset(fr, fb),
        ),
        Offset(fl, ft),
        Size(outerW, outerH),
        CornerRadius(corner),
    )
    // 木纹：确定性细纹，仅烘焙一次
    for (i in 0 until 14) {
        val gx = fl + outerW * hockeyHash01(i * 5 + 31)
        val sway = sin(i * 1.7f) * frame * 0.05f
        drawLine(
            if (i % 2 == 0) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.05f),
            Offset(gx, ft + frame * 0.10f),
            Offset(gx + sway, fb - frame * 0.10f),
            strokeWidth = 0.8f + hockeyHash01(i * 2 + 7),
        )
    }
    // 木框内侧高光 + 外描边
    drawRoundRect(
        Color.White.copy(alpha = if (dark) 0.05f else 0.13f),
        Offset(fl + frame * 0.14f, ft + frame * 0.14f),
        Size(outerW - frame * 0.28f, outerH - frame * 0.28f),
        CornerRadius(corner * 0.9f),
        style = strokeOf(frame * 0.16f),
    )
    drawRoundRect(
        woodEdge.copy(alpha = if (dark) 0.75f else 0.5f),
        Offset(fl, ft),
        Size(outerW, outerH),
        CornerRadius(corner),
        style = strokeOf(short * 0.006f),
    )
    // 四角收边斜接
    drawFrameMiter(fl, ft, l, tp, frame)
    drawFrameMiter(fr, ft, r, tp, frame)
    drawFrameMiter(fl, fb, l, b, frame)
    drawFrameMiter(fr, fb, r, b, frame)

    // 冰面
    val iceTop = tintIce(if (isDay) iceDayTop else iceNightTop, style, isDay)
    val iceMid = tintIce(if (isDay) iceDayMid else iceNightMid, style, isDay)
    val iceBottom = tintIce(if (isDay) iceDayBottom else iceNightBottom, style, isDay)
    val icePath = HockeyRenderCache.icePath
    icePath.reset()
    icePath.addRoundRect(RoundRect(l, tp, r, b, CornerRadius(iceCorner)))
    clipPath(icePath) {
        drawRect(
            Brush.verticalGradient(listOf(iceTop, iceMid, iceBottom), startY = tp, endY = b),
            Offset(l, tp),
            Size(w, h),
        )
        // 顶侧灯带反光
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(l + w * 0.22f, tp + h * 0.10f),
                radius = max(w, h) * 0.55f,
            ),
            radius = max(w, h) * 0.55f,
            center = Offset(l + w * 0.22f, tp + h * 0.10f),
        )
        // 细化划痕
        for (i in 0 until 120) {
            val fx = hockeyHash01(i * 3 + 1)
            val fy = hockeyHash01(i * 3 + 2)
            val ang = hockeyHash01(i * 5 + 4) * PI.toFloat()
            val len = short * (0.010f + hockeyHash01(i * 7 + 5) * 0.050f)
            val dx = cos(ang) * len * 0.5f
            val dy = sin(ang) * len * 0.5f
            val px = l + fx * w
            val py = tp + fy * h
            drawLine(
                if (i % 2 == 0) Color.White.copy(alpha = 0.05f) else Color(0xFF2E4B63).copy(alpha = 0.045f),
                Offset(px - dx, py - dy),
                Offset(px + dx, py + dy),
                strokeWidth = 0.6f + hockeyHash01(i * 11 + 6) * 1.1f,
            )
        }
        // 对角反光带
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                start = Offset(l, tp + h * 0.72f),
                end = Offset(r, tp + h * 0.28f),
            ),
            Offset(l, tp),
            Size(w, h),
        )
        // 暗角
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = if (dark) 0.30f else 0.20f)),
                center = Offset(midX, midY),
                radius = max(w, h) * 0.72f,
            ),
            Offset(l, tp),
            Size(w, h),
        )
    }

    drawIceMarkings(t, frame, iceCorner)

    // 库边（内阴影 + 库鼻高光）
    val board = short * 0.028f
    drawRect(
        Brush.verticalGradient(listOf(boardShade, Color.Transparent), startY = tp, endY = tp + board),
        Offset(l, tp),
        Size(w, board),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, boardShade), startY = b - board, endY = b),
        Offset(l, b - board),
        Size(w, board),
    )
    drawRect(
        Brush.horizontalGradient(listOf(boardShade, Color.Transparent), startX = l, endX = l + board),
        Offset(l, tp),
        Size(board, h),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, boardShade), startX = r - board, endX = r),
        Offset(r - board, tp),
        Size(board, h),
    )
    val nose = Color.White.copy(alpha = 0.30f)
    val goalLeft = midX - goalHalf
    val goalRight = midX + goalHalf
    drawLine(nose, Offset(l + board, tp + board), Offset(goalLeft, tp + board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(goalRight, tp + board), Offset(r - board, tp + board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(l + board, b - board), Offset(goalLeft, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(goalRight, b - board), Offset(r - board, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(l + board, tp + board), Offset(l + board, b - board), strokeWidth = short * 0.006f)
    drawLine(nose, Offset(r - board, tp + board), Offset(r - board, b - board), strokeWidth = short * 0.006f)
    drawRoundRect(
        woodEdge.copy(alpha = 0.35f),
        Offset(l, tp),
        Size(w, h),
        CornerRadius(iceCorner),
        style = strokeOf(short * 0.008f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.18f),
        Offset(l + 1.5f, tp + 1.5f),
        Size(w - 3f, h - 3f),
        CornerRadius(iceCorner * 0.96f),
        style = strokeOf(short * 0.003f),
    )

    // 球门（洞口 + 网 + 门柱）
    drawGoalRecess(t, frame, isTop = true, team = aiGoal)
    drawGoalRecess(t, frame, isTop = false, team = playerGoal)
    // 门区半圆
    val creaseR = goalHalf * 1.28f
    val creaseW = short * 0.008f
    drawCrease(midX, tp, creaseR, isTop = true, color = aiGoal, lineW = creaseW)
    drawCrease(midX, b, creaseR, isTop = false, color = playerGoal, lineW = creaseW)
}

private fun DrawScope.drawFrameMiter(
    ox: Float,
    oy: Float,
    ix: Float,
    iy: Float,
    frame: Float,
) {
    val dx = ix - ox
    val dy = iy - oy
    drawLine(woodEdge.copy(alpha = 0.42f), Offset(ox, oy), Offset(ix, iy), strokeWidth = frame * 0.10f)
    drawLine(
        Color.White.copy(alpha = 0.09f),
        Offset(ox + dx * 0.12f, oy + dy * 0.12f),
        Offset(ox + dx * 0.42f, oy + dy * 0.42f),
        strokeWidth = frame * 0.05f,
    )
}

private fun DrawScope.drawIceMarkings(
    t: HockeyLogic.Table,
    frame: Float,
    iceCorner: Float,
) {
    val l = t.left
    val tp = t.top
    val r = t.right
    val b = t.bottom
    val w = t.width
    val h = t.height
    val short = t.shortSide
    val midX = t.midX
    val midY = t.midY
    val lineW = short * 0.010f

    // 中线 + 中圈 + 开球点
    drawLine(markBlue.copy(alpha = 0.42f), Offset(l + frame * 0.1f, midY), Offset(r - frame * 0.1f, midY), strokeWidth = lineW)
    drawLine(
        Color.White.copy(alpha = 0.22f),
        Offset(l + frame * 0.1f, midY - lineW * 0.55f),
        Offset(r - frame * 0.1f, midY - lineW * 0.55f),
        strokeWidth = lineW * 0.32f,
    )
    drawCircle(markBlue.copy(alpha = 0.40f), short * 0.155f, Offset(midX, midY), style = strokeOf(lineW * 0.75f))
    drawCircle(Color.White.copy(alpha = 0.20f), short * 0.055f, Offset(midX, midY), style = strokeOf(lineW * 0.40f))
    drawCircle(markBlue.copy(alpha = 0.50f), short * 0.014f, Offset(midX, midY))
    drawPawMark(midX, midY, short * 0.052f, Color.White.copy(alpha = 0.10f))

    // 四个开球点
    val dotX = short * 0.26f
    val dotY = h * 0.20f
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val cx = midX + sx * dotX
            val cy = midY + sy * dotY
            drawCircle(markBlue.copy(alpha = 0.32f), short * 0.042f, Offset(cx, cy), style = strokeOf(lineW * 0.55f))
            drawCircle(markBlue.copy(alpha = 0.42f), short * 0.011f, Offset(cx, cy))
        }
    }

    // 四角弧线
    val cInset = short * 0.065f
    val rad = short * 0.10f
    val arcW = lineW * 0.60f
    drawCornerArc(l + cInset, tp + cInset, rad, 0f, arcW)
    drawCornerArc(r - cInset, tp + cInset, rad, 90f, arcW)
    drawCornerArc(r - cInset, b - cInset, rad, 180f, arcW)
    drawCornerArc(l + cInset, b - cInset, rad, 270f, arcW)

    // 中线位两侧墙短标记
    drawLine(
        Color.White.copy(alpha = 0.28f),
        Offset(l, midY),
        Offset(l + short * 0.035f, midY),
        strokeWidth = lineW * 0.55f,
    )
    drawLine(
        Color.White.copy(alpha = 0.28f),
        Offset(r - short * 0.035f, midY),
        Offset(r, midY),
        strokeWidth = lineW * 0.55f,
    )
    // 冰面圆角内沿细线
    drawRoundRect(
        Color.White.copy(alpha = 0.10f),
        Offset(l + iceCorner * 0.10f, tp + iceCorner * 0.10f),
        Size(w - iceCorner * 0.20f, h - iceCorner * 0.20f),
        CornerRadius(iceCorner * 0.9f),
        style = strokeOf(lineW * 0.3f),
    )
}

private fun DrawScope.drawCornerArc(
    cx: Float,
    cy: Float,
    radius: Float,
    startAngle: Float,
    strokeWidth: Float,
) {
    drawArc(
        color = markBlue.copy(alpha = 0.26f),
        startAngle = startAngle,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = strokeOf(strokeWidth),
    )
    drawArc(
        color = Color.White.copy(alpha = 0.16f),
        startAngle = startAngle + 12f,
        sweepAngle = 66f,
        useCenter = false,
        topLeft = Offset(cx - radius * 0.90f, cy - radius * 0.90f),
        size = Size(radius * 1.80f, radius * 1.80f),
        style = strokeOf(strokeWidth * 0.5f),
    )
}

private fun DrawScope.drawPawMark(
    cx: Float,
    cy: Float,
    s: Float,
    color: Color,
) {
    drawCircle(color, s * 0.42f, Offset(cx, cy + s * 0.10f))
    drawCircle(color, s * 0.155f, Offset(cx - s * 0.36f, cy - s * 0.30f))
    drawCircle(color, s * 0.155f, Offset(cx - s * 0.12f, cy - s * 0.48f))
    drawCircle(color, s * 0.155f, Offset(cx + s * 0.12f, cy - s * 0.48f))
    drawCircle(color, s * 0.155f, Offset(cx + s * 0.36f, cy - s * 0.30f))
}

private fun DrawScope.drawGoalRecess(
    t: HockeyLogic.Table,
    frame: Float,
    isTop: Boolean,
    team: Color,
) {
    val half = t.goalWidth * 0.5f
    val depth = frame * 0.92f
    if (depth <= 1f || half <= 1f) return
    val y0 = if (isTop) t.top - depth else t.bottom
    val x0 = t.midX - half
    val corner = CornerRadius(depth * 0.28f)
    val topLeft = Offset(x0, y0)

    drawRoundRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF07131F), Color(0xFF102738), Color(0xFF07131F)),
            startY = y0,
            endY = y0 + depth,
        ),
        topLeft,
        Size(t.goalWidth, depth),
        corner,
    )
    // 网：细网格 + 纵深阴影（裁到圆角洞口内）
    val recessPath = HockeyRenderCache.recessPath
    recessPath.reset()
    recessPath.addRoundRect(RoundRect(x0, y0, x0 + t.goalWidth, y0 + depth, corner))
    clipPath(recessPath) {
        val cols = (t.goalWidth / t.shortSide * 32f).roundToInt().coerceIn(5, 14)
        for (i in 0 until cols) {
            val gx = x0 + t.goalWidth * (i + 0.5f) / cols
            drawLine(
                Color.White.copy(alpha = 0.15f),
                Offset(gx, y0 + depth * 0.10f),
                Offset(gx, y0 + depth * 0.94f),
                strokeWidth = max(0.7f, t.shortSide * 0.0022f),
            )
        }
        for (r2 in 0 until 3) {
            val gy = y0 + depth * (r2 + 1) / 4f
            drawLine(
                Color.White.copy(alpha = 0.13f),
                Offset(x0 + t.goalWidth * 0.04f, gy),
                Offset(x0 + t.goalWidth * 0.96f, gy),
                strokeWidth = max(0.7f, t.shortSide * 0.0020f),
            )
        }
        val backY = if (isTop) y0 + depth * 0.24f else y0 + depth * 0.76f
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(t.midX, backY),
                radius = depth * 1.1f,
            ),
            radius = depth * 1.1f,
            center = Offset(t.midX, backY),
        )
    }
    // 洞口描边
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        topLeft,
        Size(t.goalWidth, depth),
        corner,
        style = strokeOf(max(1f, t.shortSide * 0.004f)),
    )
    // 门线
    val lineY = if (isTop) t.top else t.bottom
    drawLine(
        team.copy(alpha = 0.80f),
        Offset(x0, lineY),
        Offset(x0 + t.goalWidth, lineY),
        strokeWidth = t.shortSide * 0.009f,
    )
    drawLine(
        Color.White.copy(alpha = 0.40f),
        Offset(x0, lineY),
        Offset(x0 + t.goalWidth, lineY),
        strokeWidth = t.shortSide * 0.003f,
    )
    // 门柱（圆头 + 高光）
    val postR = max(depth * 0.40f, t.shortSide * 0.012f)
    for (sx in intArrayOf(-1, 1)) {
        val px = t.midX + sx * half
        drawCircle(Color.Black.copy(alpha = 0.30f), postR * 1.08f, Offset(px + postR * 0.10f, lineY + postR * 0.16f))
        drawCircle(team, postR, Offset(px, lineY))
        drawCircle(team.copy(alpha = 0.5f), postR, Offset(px, lineY), style = strokeOf(postR * 0.28f))
        drawCircle(Color.White.copy(alpha = 0.55f), postR * 0.30f, Offset(px - postR * 0.32f, lineY - postR * 0.34f))
    }
}

private fun DrawScope.drawCrease(
    cx: Float,
    baseY: Float,
    radius: Float,
    isTop: Boolean,
    color: Color,
    lineW: Float,
) {
    if (radius <= 1f) return
    val start = if (isTop) 0f else 180f
    val topLeft = Offset(cx - radius, baseY - radius)
    val sz = Size(radius * 2f, radius * 2f)
    drawArc(color.copy(alpha = 0.11f), start, 180f, true, topLeft, sz)
    drawArc(color.copy(alpha = 0.55f), start, 180f, false, topLeft, sz, style = strokeOf(lineW))
    drawArc(
        Color.White.copy(alpha = 0.20f),
        start + 14f,
        152f,
        false,
        Offset(cx - radius * 0.92f, baseY - radius * 0.92f),
        Size(radius * 1.84f, radius * 1.84f),
        style = strokeOf(lineW * 0.5f),
    )
    drawCircle(color.copy(alpha = 0.45f), lineW * 0.9f, Offset(cx, baseY))
}

private fun tintIce(
    c: Color,
    style: WeatherSceneStyle,
    isDay: Boolean,
): Color {
    val cold =
        when (style.scene) {
            WeatherScene.SNOW,
            WeatherScene.HEAVY_SNOW,
            WeatherScene.FREEZING_RAIN,
            WeatherScene.HAIL,
            -> 0.16f
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.DRIZZLE,
            WeatherScene.THUNDER,
            WeatherScene.FOG,
            WeatherScene.OVERCAST,
            WeatherScene.UNKNOWN,
            -> 0.10f
            else -> 0f
        }
    return when {
        cold > 0f -> lerp(c, iceCold, cold)
        !isDay -> lerp(c, Color(0xFF5E7E96), 0.10f)
        style.scene == WeatherScene.CLEAR || style.scene == WeatherScene.PARTLY_CLOUDY -> lerp(c, iceWarm, 0.05f)
        else -> c
    }
}

// ---------------------------------------------------------------------
// 挡板 / 蜜罐精灵图
// ---------------------------------------------------------------------

private fun DrawScope.drawPaddleArt(
    r: Float,
    p: PaddlePalette,
) {
    // 柔和投影（朝右下，稍大稍淡）
    drawOval(
        Color.Black.copy(alpha = 0.10f),
        topLeft = Offset(-r * 1.00f + r * 0.16f, -r * 0.66f + r * 0.30f),
        size = Size(r * 2.32f, r * 1.84f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(-r * 0.82f + r * 0.16f, -r * 0.52f + r * 0.30f),
        size = Size(r * 1.96f, r * 1.56f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(-r * 0.62f + r * 0.16f, -r * 0.38f + r * 0.30f),
        size = Size(r * 1.56f, r * 1.24f),
    )
    // 小熊耳朵
    drawCircle(p.ear, r * 0.36f, Offset(-r * 0.60f, -r * 0.56f))
    drawCircle(p.ear, r * 0.36f, Offset(r * 0.60f, -r * 0.56f))
    drawCircle(p.earInner, r * 0.19f, Offset(-r * 0.60f, -r * 0.56f))
    drawCircle(p.earInner, r * 0.19f, Offset(r * 0.60f, -r * 0.56f))
    // 圆盘主体
    drawCircle(
        Brush.radialGradient(
            colors = listOf(lighten(p.base, 0.50f), p.base, darken(p.base, 0.45f)),
            center = Offset(-r * 0.30f, -r * 0.34f),
            radius = r * 1.55f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 边缘圈与高光弧
    drawCircle(p.rim.copy(alpha = 0.92f), r, Offset.Zero, style = strokeOf(r * 0.14f))
    drawCircle(Color.White.copy(alpha = 0.20f), r * 0.93f, Offset.Zero, style = strokeOf(r * 0.035f))
    drawArc(
        Color.White.copy(alpha = 0.32f),
        198f,
        124f,
        false,
        Offset(-r * 0.90f, -r * 0.90f),
        Size(r * 1.80f, r * 1.80f),
        style = strokeOf(r * 0.07f),
    )
    // 环带
    drawCircle(p.accent.copy(alpha = 0.32f), r * 0.70f, Offset.Zero, style = strokeOf(r * 0.045f))
    // 爪印环
    for (k in 0 until 4) {
        val a = (45f + 90f * k) * PI.toFloat() / 180f
        drawPawMark(
            cos(a) * r * 0.70f,
            sin(a) * r * 0.70f,
            r * 0.115f,
            darken(p.rim, 0.05f).copy(alpha = 0.62f),
        )
    }
    // 脸
    drawOval(
        Color(0xFFFFF6E8).copy(alpha = 0.95f),
        topLeft = Offset(-r * 0.40f, -r * 0.10f),
        size = Size(r * 0.80f, r * 0.62f),
    )
    drawCircle(Color(0xFF3B2414), r * 0.10f, Offset(0f, r * 0.02f))
    drawCircle(Color(0xFF3B2414), r * 0.080f, Offset(-r * 0.20f, -r * 0.10f))
    drawCircle(Color(0xFF3B2414), r * 0.080f, Offset(r * 0.20f, -r * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.028f, Offset(-r * 0.22f, -r * 0.12f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.028f, Offset(r * 0.18f, -r * 0.12f))
    drawArc(
        Color(0xFF3B2414).copy(alpha = 0.80f),
        20f,
        50f,
        false,
        Offset(-r * 0.13f, r * 0.10f),
        Size(r * 0.13f, r * 0.12f),
        style = strokeOf(r * 0.024f),
    )
    drawArc(
        Color(0xFF3B2414).copy(alpha = 0.80f),
        110f,
        50f,
        false,
        Offset(0f, r * 0.10f),
        Size(r * 0.13f, r * 0.12f),
        style = strokeOf(r * 0.024f),
    )
    p.cheeks?.let {
        drawCircle(it, r * 0.09f, Offset(-r * 0.33f, r * 0.13f))
        drawCircle(it, r * 0.09f, Offset(r * 0.33f, r * 0.13f))
    }
}

private fun DrawScope.drawPuckArt(r: Float) {
    // 蜂蜜球体
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color(0xFFFFF6C8), Color(0xFFFFC42E), Color(0xFFE56700)),
            center = Offset(-r * 0.32f, -r * 0.36f),
            radius = r * 1.60f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 边缘暗化 + 菲涅尔反光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.40f)),
            center = Offset(-r * 0.30f, -r * 0.32f),
            radius = r * 1.45f,
        ),
        radius = r,
        center = Offset.Zero,
    )
    drawCircle(
        Brush.radialGradient(
            0.60f to Color.Transparent,
            0.85f to Color(0x33FFF3D0),
            1.00f to Color(0x59FFF3D0),
            center = Offset.Zero,
            radius = r,
        ),
        radius = r,
        center = Offset.Zero,
    )
    // 蜜罐盖环
    drawCircle(Color(0x668A4A10), r * 0.62f, Offset.Zero, style = strokeOf(r * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.16f), r * 0.53f, Offset.Zero, style = strokeOf(r * 0.05f))
    // 高光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(-r * 0.32f, -r * 0.35f),
            radius = r * 0.50f,
        ),
        radius = r * 0.50f,
        center = Offset(-r * 0.32f, -r * 0.35f),
    )
    drawCircle(Color.White.copy(alpha = 0.90f), r * 0.155f, Offset(-r * 0.32f, -r * 0.35f))
    drawCircle(Color.White.copy(alpha = 0.50f), r * 0.070f, Offset(-r * 0.10f, -r * 0.18f))
    drawCircle(Color(0x33FFE082), r * 0.40f, Offset(r * 0.25f, r * 0.40f))
}

private fun DrawScope.drawBearPaddle(
    x: Float,
    y: Float,
    r: Float,
    palette: PaddlePalette,
    paletteId: Int,
    vx: Float,
    vy: Float,
    shortSide: Float,
    hit: Float,
    reduceMotion: Boolean,
) {
    if (r < 0.5f) return
    // 运动拖尾（减弱动态效果时关闭）
    val sp = hypot(vx, vy)
    if (!reduceMotion && sp > shortSide * 0.22f) {
        val k = (sp / (shortSide * 1.4f)).coerceIn(0f, 0.55f)
        val ux = vx / sp
        val uy = vy / sp
        drawCircle(palette.base.copy(alpha = 0.10f * k), r * 0.98f, Offset(x - ux * r * 0.60f, y - uy * r * 0.60f))
        drawCircle(palette.base.copy(alpha = 0.05f * k), r * 0.90f, Offset(x - ux * r * 1.25f, y - uy * r * 1.25f))
        drawCircle(palette.base.copy(alpha = 0.03f * k), r * 0.80f, Offset(x - ux * r * 1.90f, y - uy * r * 1.90f))
    }
    val sprite = HockeySpriteCache.paddle(r, paletteId, this, layoutDirection)
    if (sprite != null) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset((x - sprite.half).roundToInt(), (y - sprite.half).roundToInt()),
            dstSize = IntSize(sprite.bitmap.width, sprite.bitmap.height),
            filterQuality = FilterQuality.None,
        )
    }
    if (hit > 0.05f) {
        drawCircle(
            Color.White.copy(alpha = 0.30f * hit),
            r * (1.06f + 0.16f * (1f - hit)),
            Offset(x, y),
            style = strokeOf(r * 0.085f),
        )
        drawCircle(
            palette.accent.copy(alpha = 0.20f * hit),
            r * 0.80f,
            Offset(x, y),
            style = strokeOf(r * 0.10f),
        )
    }
}

private fun DrawScope.drawHoneyPuck(
    game: HockeyGame,
    time: Float,
    reduceMotion: Boolean,
) {
    val p = game.puck
    val r = p.r
    if (r < 0.5f) return
    val short = game.table?.shortSide ?: return
    val sp = hypot(p.vx, p.vy)

    // 投影
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(p.x - r * 1.10f + r * 0.14f, p.y - r * 0.86f + r * 0.24f),
        size = Size(r * 2.20f, r * 1.72f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.20f),
        topLeft = Offset(p.x - r * 0.88f + r * 0.14f, p.y - r * 0.66f + r * 0.24f),
        size = Size(r * 1.76f, r * 1.32f),
    )

    // 高速拖影
    if (!reduceMotion && sp > short * 0.16f) {
        val k = (sp / (short * 1.2f)).coerceIn(0f, 1f)
        val ux = p.vx / sp
        val uy = p.vy / sp
        for (i in 1..3) {
            drawCircle(
                honeyGlow.copy(alpha = 0.16f * k * (1f - i / 4f)),
                r * (1f - 0.14f * i),
                Offset(p.x - ux * r * (0.9f + 1.0f * i), p.y - uy * r * (0.9f + 1.0f * i)),
            )
        }
    }

    val sprite = HockeySpriteCache.puck(r, this, layoutDirection)
    if (sprite != null) {
        // 蜜罐盖反光随球滚动（烘焙精灵整体旋转）
        val deg = (time * 110f) % 360f
        withTransform({ rotate(degrees = deg, pivot = Offset(p.x, p.y)) }) {
            drawImage(
                image = sprite.bitmap,
                dstOffset = IntOffset((p.x - sprite.half).roundToInt(), (p.y - sprite.half).roundToInt()),
                dstSize = IntSize(sprite.bitmap.width, sprite.bitmap.height),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

private fun DrawScope.drawPuckTrail(game: HockeyGame) {
    val trail = game.trail
    if (trail.isEmpty()) return
    val r = game.puck.r
    if (r < 0.5f) return
    for (i in trail.indices) {
        val (x, y) = trail[i]
        val a = (1f - i / 12f) * 0.24f
        if (a <= 0.01f) continue
        drawCircle(honeyGlow.copy(alpha = a), r * (0.78f - i * 0.02f), Offset(x, y))
    }
}

// ---------------------------------------------------------------------
// 特效：火花 / 冰屑 / 进球光效 / 结算庆祝
// ---------------------------------------------------------------------

private fun DrawScope.drawHockeySparks(game: HockeyGame) {
    val sparks = game.sparks
    if (sparks.isEmpty()) return
    for (i in sparks.indices) {
        val s = sparks[i]
        val a = (s.life / 0.5f).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        if (s.color == iceSpark) {
            val sp = hypot(s.vx, s.vy).coerceAtLeast(1f)
            val ux = s.vx / sp
            val uy = s.vy / sp
            val len = 2.2f + 3.6f * a
            drawLine(
                Color.White.copy(alpha = 0.30f * a),
                Offset(s.x - ux * len * 2.4f, s.y - uy * len * 2.4f),
                Offset(s.x, s.y),
                strokeWidth = 1.2f,
                cap = StrokeCap.Round,
            )
            drawIceShard(s.x, s.y, ux, uy, len, a)
        } else {
            drawCircle(s.color.copy(alpha = 0.16f * a), 6.5f * (0.6f + a * 0.6f), Offset(s.x, s.y))
            drawCircle(s.color.copy(alpha = 0.90f * a), 2.4f * (0.5f + a), Offset(s.x, s.y))
        }
    }
}

private fun DrawScope.drawIceShard(
    x: Float,
    y: Float,
    ux: Float,
    uy: Float,
    len: Float,
    alpha: Float,
) {
    val px = -uy
    val py = ux
    val path = HockeyRenderCache.shard
    path.reset()
    path.moveTo(x + ux * len, y + uy * len)
    path.lineTo(x + px * len * 0.62f, y + py * len * 0.62f)
    path.lineTo(x - ux * len, y - uy * len)
    path.lineTo(x - px * len * 0.62f, y - py * len * 0.62f)
    path.close()
    drawPath(path, Color.White.copy(alpha = 0.72f * alpha))
}

private fun DrawScope.drawGoalLight(game: HockeyGame) {
    val flash = game.scoreFlash
    if (flash <= 0.01f) return
    val t = game.table ?: return
    val scorer = game.lastScorer ?: return
    val team = if (scorer == Scorer.PLAYER) playerGoal else aiGoal
    val y = if (scorer == Scorer.PLAYER) t.top else t.bottom
    val goalHalf = t.goalWidth * 0.5f
    drawCircle(team.copy(alpha = 0.34f * flash), goalHalf * 1.45f, Offset(t.midX, y))
    drawCircle(Color.White.copy(alpha = 0.20f * flash), goalHalf * 0.85f, Offset(t.midX, y))
    val k = 1f - flash
    drawCircle(
        team.copy(alpha = 0.45f * flash),
        goalHalf * (1.05f + 0.95f * k),
        Offset(t.midX, y),
        style = strokeOf(goalHalf * 0.06f),
    )
    drawCircle(
        Color.White.copy(alpha = 0.30f * flash),
        goalHalf * (0.70f + 0.55f * k),
        Offset(t.midX, y),
        style = strokeOf(goalHalf * 0.03f),
    )
}

private fun DrawScope.drawWinnerConfetti(
    game: HockeyGame,
    time: Float,
) {
    val t = game.table ?: return
    val short = t.shortSide
    val span = t.height + short * 0.16f
    for (i in 0 until 30) {
        val h1 = hockeyHash01(i * 7 + 3)
        val h2 = hockeyHash01(i * 11 + 5)
        val speed = short * (0.10f + h1 * 0.20f)
        val y = t.bottom - ((time * speed + h2 * span) % span)
        val sway = sin(time * (0.8f + h2 * 0.8f) + i) * short * 0.012f
        val x = t.left + h1 * t.width + sway
        val alpha = 0.32f + 0.50f * h2
        val radius = short * (0.005f + 0.007f * h2)
        drawCircle(confettiColors[i % confettiColors.size].copy(alpha = alpha), radius, Offset(x, y))
    }
}

// ---------------------------------------------------------------------
// 发球提示
// ---------------------------------------------------------------------

private fun DrawScope.drawServeHint(
    game: HockeyGame,
    t: HockeyLogic.Table,
    time: Float,
) {
    val p = game.puck
    val r = p.r
    if (r < 0.5f) return
    val pulse = 0.5f + 0.5f * sin(time * 6f)
    drawCircle(Color.White.copy(alpha = 0.08f + 0.09f * pulse), r * 2.5f, Offset(p.x, p.y))
    if (game.serveTimer > SERVE_READY_SECONDS) {
        drawCircle(
            HockeyUi.accentCool.copy(alpha = 0.35f),
            r * 1.55f,
            Offset(p.x, p.y),
            style = strokeOf(r * 0.16f),
        )
    } else {
        val frac = ((game.serveTimer / SERVE_BEAT_SECONDS) % 1f).coerceIn(0f, 1f)
        drawArc(
            color = HockeyUi.accentCool.copy(alpha = 0.85f),
            startAngle = -90f,
            sweepAngle = 360f * frac,
            useCenter = false,
            topLeft = Offset(p.x - r * 1.55f, p.y - r * 1.55f),
            size = Size(r * 3.10f, r * 3.10f),
            style = strokeOf(r * 0.16f),
        )
        drawCircle(
            HockeyUi.accentCool.copy(alpha = 0.18f),
            r * 1.55f,
            Offset(p.x, p.y),
            style = strokeOf(r * 0.05f),
        )
    }
    game.serveTowardPlayer?.let { drawServeArrow(t, it, time) }
}

private fun DrawScope.drawServeArrow(
    t: HockeyLogic.Table,
    towardPlayer: Boolean,
    time: Float,
) {
    val x = t.midX
    val fromY = if (towardPlayer) t.midY + t.height * 0.10f else t.midY - t.height * 0.10f
    val toY = if (towardPlayer) t.midY + t.height * 0.26f else t.midY - t.height * 0.26f
    val color = if (towardPlayer) playerGoal else aiGoal
    val w = t.shortSide * 0.05f
    val tip = if (towardPlayer) t.shortSide * 0.06f else -t.shortSide * 0.06f
    val pulse = 0.72f + 0.28f * sin(time * 4f)
    drawLine(
        color.copy(alpha = 0.55f * pulse),
        Offset(x, fromY),
        Offset(x, toY),
        strokeWidth = t.shortSide * 0.018f,
        cap = StrokeCap.Round,
    )
    val path = HockeyRenderCache.shape
    path.reset()
    path.moveTo(x, toY)
    path.lineTo(x - w, toY - tip)
    path.lineTo(x + w, toY - tip)
    path.close()
    drawPath(path, color.copy(alpha = 0.85f * pulse))
}

// ---------------------------------------------------------------------
// 天气氛围（冰场外）
// ---------------------------------------------------------------------

private fun DrawScope.drawHockeyAmbient(
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    isDay: Boolean,
    time: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    if (freezingRain) {
        drawFreezingRain(time, 1f, w, h, splash = false)
        return
    }
    if (hail) {
        drawRainLayered(
            fgCount = 16,
            bgCount = 8,
            time = time,
            width = w,
            height = h,
            scale = 1f,
            lenMul = 1.05f,
            alpha = 0.42f,
            splash = false,
        )
        drawHailPellets(time, 1f, w, h, count = 40)
        drawStormLightning(time, 1f, w, h)
        return
    }
    when (weather) {
        FarmWeather.RAINY ->
            when (rain) {
                RainStrength.LIGHT ->
                    drawRainLayered(
                        fgCount = 14,
                        bgCount = 7,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.38f,
                        splash = false,
                    )
                RainStrength.MODERATE ->
                    drawRainLayered(
                        fgCount = 26,
                        bgCount = 13,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.48f,
                        splash = false,
                    )
                RainStrength.HEAVY ->
                    drawRainLayered(
                        fgCount = 40,
                        bgCount = 20,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.58f,
                        splash = false,
                    )
                RainStrength.NONE ->
                    drawRainLayered(
                        fgCount = 10,
                        bgCount = 5,
                        time = time,
                        width = w,
                        height = h,
                        scale = 1f,
                        alpha = 0.32f,
                        splash = false,
                    )
            }
        FarmWeather.STORMY -> {
            drawRainLayered(fgCount = 40, bgCount = 20, time = time, width = w, height = h, scale = 1f, alpha = 0.58f, splash = false)
            drawStormLightning(time, 1f, w, h)
        }
        FarmWeather.SNOWY -> drawHockeySnow(time, w, h)
        FarmWeather.FOGGY -> drawHockeyFog(time, w, h)
        FarmWeather.SUNNY -> drawHockeySunGlow(isDay, w, h)
        FarmWeather.CLOUDY -> drawHockeyClouds(time, w, h, isDay)
    }
}

private fun DrawScope.drawHockeySnow(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 44) {
        val phase = ((i * 67) % 29) / 29f
        val speed = 22f + phase * 30f
        val x = ((i * 83 + time * speed * 0.6f) % (w + 40f)) - 20f
        val y = ((i * 61 + time * speed) % (h + 40f)) - 20f
        val sway = sin(time * 1.2f + i) * 6f
        val r = 1.5f + (i % 4) * 0.7f
        drawCircle(Color.White.copy(alpha = 0.50f), r, Offset(x + sway, y))
    }
}

private fun DrawScope.drawHockeyFog(
    time: Float,
    w: Float,
    h: Float,
) {
    for (i in 0 until 5) {
        val y = h * (0.14f + i * 0.17f) + sin(time * 0.4f + i * 1.3f) * 8f
        val x = ((i * 211f + time * 9f) % (w + 260f)) - 130f
        drawOval(
            Color.White.copy(alpha = 0.07f),
            topLeft = Offset(x - w * 0.42f, y - h * 0.05f),
            size = Size(w * 0.84f, h * 0.10f),
        )
    }
}

private fun DrawScope.drawHockeyClouds(
    time: Float,
    w: Float,
    h: Float,
    isDay: Boolean,
) {
    val cloud = if (isDay) Color.White else Color(0xFF93A9CE)
    for (i in 0 until 4) {
        val x = ((i * 210f + time * 10f) % (w + 180f)) - 90f
        val y = h * (0.04f + i * 0.03f)
        val a = 0.10f + i * 0.015f
        drawCircle(cloud.copy(alpha = a), w * 0.10f, Offset(x, y))
        drawCircle(cloud.copy(alpha = a), w * 0.08f, Offset(x + w * 0.07f, y + h * 0.008f))
        drawCircle(cloud.copy(alpha = a), w * 0.07f, Offset(x - w * 0.07f, y + h * 0.006f))
    }
}

private fun DrawScope.drawHockeySunGlow(
    isDay: Boolean,
    w: Float,
    h: Float,
) {
    if (!isDay) return
    val cx = w * 0.88f
    val cy = h * 0.05f
    // 多层圆叠出柔和辉光（避免径向 Brush 的逐帧分配）
    for (i in 0 until 5) {
        val r = w * (0.34f - i * 0.06f)
        drawCircle(Color(0xFFFFE082).copy(alpha = if (i == 4) 0.12f else 0.05f), r, Offset(cx, cy))
    }
}

// ---------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------

/** 深色冰场背景：天气渐变统一压暗，保持 HUD 白字对比度。 */
internal fun hockeyBackdrop(style: WeatherSceneStyle): List<Color> = style.gradient.map { lerp(it, Color(0xFF07131F), 0.52f) }

private fun DrawScope.drawScoreFloat(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizePx: Float,
    alpha: Float,
) {
    if (alpha <= 0.02f || sizePx <= 1f) return
    val paint = HockeyRenderCache.textPaint
    paint.textSize = sizePx
    paint.color = color.toArgb()
    paint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun lighten(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red + (1f - c.red) * amount).coerceIn(0f, 1f),
        green = (c.green + (1f - c.green) * amount).coerceIn(0f, 1f),
        blue = (c.blue + (1f - c.blue) * amount).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

private fun darken(
    c: Color,
    amount: Float,
): Color =
    Color(
        red = (c.red * (1f - amount)).coerceIn(0f, 1f),
        green = (c.green * (1f - amount)).coerceIn(0f, 1f),
        blue = (c.blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = c.alpha,
    )

/** 确定性伪随机 [0,1]：同一 index 每帧结果一致，冰面纹理不闪烁。 */
private fun hockeyHash01(n: Int): Float {
    var h = n * 374761393 + 668265263
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}
