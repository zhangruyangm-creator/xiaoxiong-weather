package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.xiaoxiong.weather.ui.weather.WeatherScene
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 小熊台球的 Compose Canvas 渲染层。
 *
 * 设计基调：小熊蜂蜜棕木框 + 湖蓝绿台呢的绘本风，靠渐变/高光/暗角做出质感。
 * 台桌空间（米）经 [PoolGame.toCanvasX]/[PoolGame.toCanvasY] 映射到画布。
 *
 * 性能约定：路径与文字 Paint 放在 [PoolRenderCache] 里复用；织纹用整数哈希做
 * 确定性伪随机，不依赖帧时间，因此不会逐帧抖动；每帧只有少量小对象分配。
 */

private val feltDayBase = Color(0xFF2C9E8C)
private val feltNightBase = Color(0xFF1D6C66)
private val feltColdTint = Color(0xFF3D6E9C)

/** 可选台呢主题：0 经典湖蓝 / 1 蜂蜜棕 / 2 午夜墨绿 / 3 樱花粉。 */
private val clothDayThemes =
    intArrayOf(0xFF2C9E8C.toInt(), 0xFF8A5A2B.toInt(), 0xFF1F5A46.toInt(), 0xFFC97A96.toInt())
private val clothNightThemes =
    intArrayOf(0xFF1D6C66.toInt(), 0xFF5A3A1B.toInt(), 0xFF123A2C.toInt(), 0xFF8A4C63.toInt())

private val honeyWoodLight = Color(0xFFC0894A)
private val honeyWoodMid = Color(0xFF9B6533)
private val honeyWoodDark = Color(0xFF5E3A1C)
private val woodEdge = Color(0xFF3A2210)
private val diamondIvory = Color(0xFFF2E2C2)
private val cushionShade = Color(0xFF0C4640)
private val pocketInk = Color(0xFF020202)
private val pocketDeep = Color(0xFF14100B)
private val pocketRim = Color(0xFF8A5A2C)
private val pocketNetLine = Color(0xFF0B0906).copy(alpha = 0.70f)
private val woodGrainDark = Color.Black.copy(alpha = 0.07f)
private val woodGrainLight = Color.White.copy(alpha = 0.05f)
private val cushionSeam = Color.Black.copy(alpha = 0.14f)
private val curveHintColor = Color(0xFF80DEEA)

private val cueWhite = Color(0xFFF7F4EC)
private val stripeWhite = Color(0xFFF3EFE6)
private val eightBlack = Color(0xFF17161A)
private val cueTipBlue = Color(0xFF2B4C7E)
private val cueWoodLight = Color(0xFFE2C08D)
private val cueWoodMid = Color(0xFFC1934F)
private val cueWoodDark = Color(0xFF6E4321)
private val cueFerrule = Color(0xFFF2EFE6)

private val aimLineColor = Color.White.copy(alpha = 0.50f)
private val targetLineColor = Color(0xFFFFF176).copy(alpha = 0.85f)
private val cueBreakColor = Color(0xFF90CAF9).copy(alpha = 0.62f)
private val reflectLineColor = Color.White.copy(alpha = 0.26f)
private val ghostFill = Color.White.copy(alpha = 0.24f)
private val ghostRing = Color.White.copy(alpha = 0.78f)
private val ghostInner = Color(0xFFFFF59D).copy(alpha = 0.55f)
private val placeRing = Color(0xFFFFE082)

private val weatherRainTint = Color(0xFF29364A).copy(alpha = 0.10f)
private val weatherStormTint = Color(0xFF151B26).copy(alpha = 0.22f)
private val weatherSnowTint = Color(0xFFB9C7D6).copy(alpha = 0.09f)
private val weatherHailTint = Color(0xFF141B28).copy(alpha = 0.24f)
private val weatherFreezingTint = Color(0xFF5E7390).copy(alpha = 0.16f)
private val weatherFogTint = Color(0xFFD7DEE3).copy(alpha = 0.10f)

/** 1..7 的全色色相；花色球用 (number - 1) % 8 取色。 */
private val ballHues =
    longArrayOf(
        0xFFFFC93C, // 1 / 9 黄
        0xFF1E88E5, // 2 / 10 蓝
        0xFFE53935, // 3 / 11 红
        0xFF8E24AA, // 4 / 12 紫
        0xFFFB8C00, // 5 / 13 橙
        0xFF43A047, // 6 / 14 绿
        0xFF8E3B2F, // 7 / 15 栗
    )

// ---------------------------------------------------------------------
// 复用缓存：Path / Paint
// ---------------------------------------------------------------------

private object PoolRenderCache {
    /** 球体裁切圆（花色带贴花时旋转用）。 */
    val ballClip = Path()

    /** 虚线折线。 */
    val dashed = Path()

    /** 通用临时形状（球杆锥形 / 箭头）。 */
    val shape = Path()

    /** 弧线瞄准暗示曲线。 */
    val curveHint = Path()

    /** 号码文字画笔，逐球只改 alpha/textSize。 */
    val numberPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = android.graphics.Color.rgb(0x20, 0x1A, 0x14)
        }
}

// ---------------------------------------------------------------------
// 静态台面层烘焙缓存
// ---------------------------------------------------------------------

/**
 * 台面静态层（木框 / 台呢暗角与织纹 / 库边明暗 / 开球线与置球点 / 菱形 / 袋口）
 * 每帧不变，按「画布尺寸 + style + isDay + viewScale/offset」烘焙成 [ImageBitmap]，
 * 之后每帧只 drawImage。仅主线程绘制，无需加锁；最多保留 [MAX_ENTRIES] 张做 LRU，
 * 尺寸或主题切换时淘汰最旧的一张以限制内存。
 */
private class PoolTableLayer(
    val bitmap: ImageBitmap,
    val width: Int,
    val height: Int,
    val viewScale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val style: WeatherSceneStyle,
    val isDay: Boolean,
    val clothTheme: Int,
)

private object PoolTableLayerCache {
    private const val MAX_ENTRIES = 2
    private val entries = ArrayList<PoolTableLayer>(MAX_ENTRIES)

    fun bitmap(
        game: PoolGame,
        style: WeatherSceneStyle,
        isDay: Boolean,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PoolTableLayer? {
        val w = ceil(size.width).toInt()
        val h = ceil(size.height).toInt()
        if (w <= 0 || h <= 0) return null
        val scale = game.viewScale
        if (scale <= 0f) return null
        val ox = game.viewOffsetX
        val oy = game.viewOffsetY

        val hit =
            entries.firstOrNull {
                it.width == w &&
                    it.height == h &&
                    it.viewScale == scale &&
                    it.offsetX == ox &&
                    it.offsetY == oy &&
                    it.style == style &&
                    it.isDay == isDay &&
                    it.clothTheme == game.clothTheme
            }
        if (hit != null) {
            entries.remove(hit)
            entries.add(hit)
            return hit
        }
        // 连续拖动 resize 时避免每像素重建整屏位图：尺寸相近且主题一致时复用并按需拉伸绘制
        val near =
            entries.lastOrNull {
                it.style == style &&
                    it.isDay == isDay &&
                    it.clothTheme == game.clothTheme &&
                    abs(it.width - w) <= RESIZE_TOLERANCE_PX &&
                    abs(it.height - h) <= RESIZE_TOLERANCE_PX
            }
        if (near != null) return near

        val bitmap = ImageBitmap(w, h)
        // 用原始浮点 size 布局，保证坐标与直接绘制一致；位图取 ceil 仅多出边缘空像素。
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
            drawPoolTableStatic(game, style, isDay)
        }
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(PoolTableLayer(bitmap, w, h, scale, ox, oy, style, isDay, game.clothTheme))
        return entries.last()
    }

    /** 拖动窗口时允许复用的尺寸误差（px）。 */
    private const val RESIZE_TOLERANCE_PX = 24
}

// ---------------------------------------------------------------------
// 天气氛围（台面外侧）
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolWeather(
    game: PoolGame,
    weather: FarmWeather,
    rain: RainStrength,
    freezingRain: Boolean,
    hail: Boolean,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val w = size.width
    val h = size.height
    val time = game.time

    // 场景底色：整屏铺但极淡，给台外一点氛围，不抢台面
    val grad = style.gradient
    if (grad.isNotEmpty()) {
        val a = if (isDay) 0.10f else 0.16f
        val colors =
            grad.map { it.copy(alpha = a) } +
                listOf(Color.Transparent)
        drawRect(
            Brush.verticalGradient(colors),
            topLeft = Offset.Zero,
            size = Size(w, h),
        )
    }

    // 冻雨 / 冰雹优先识别
    if (freezingRain) {
        drawRect(weatherFreezingTint)
        drawFreezingRain(time, 1f, w, h, splash = false)
        return
    }
    if (hail) {
        drawRect(weatherHailTint)
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
        drawHailPellets(time, 1f, w, h, count = 52)
        drawStormLightning(time, 1f, w, h)
        return
    }

    when (weather) {
        FarmWeather.RAINY -> {
            drawRect(weatherRainTint)
            drawPoolRain(time, w, h, rain)
        }
        FarmWeather.STORMY -> {
            drawRect(weatherStormTint)
            drawPoolRain(time, w, h, RainStrength.HEAVY)
            drawStormLightning(time, 1f, w, h)
        }
        FarmWeather.SNOWY -> {
            drawRect(weatherSnowTint)
            drawPoolSnow(time, w, h)
        }
        FarmWeather.FOGGY -> {
            drawRect(weatherFogTint)
            drawPoolFog(time, w, h)
        }
        FarmWeather.SUNNY -> drawPoolSunGlow(w, h, isDay)
        FarmWeather.CLOUDY -> drawPoolClouds(time, w, h, isDay)
    }
}

private fun DrawScope.drawPoolRain(
    time: Float,
    w: Float,
    h: Float,
    rain: RainStrength,
) {
    val (fg, bg, alpha) =
        when (rain) {
            RainStrength.LIGHT -> Triple(14, 7, 0.38f)
            RainStrength.MODERATE -> Triple(26, 13, 0.48f)
            RainStrength.HEAVY -> Triple(40, 20, 0.58f)
            RainStrength.NONE -> Triple(10, 5, 0.32f)
        }
    drawRainLayered(
        fgCount = fg,
        bgCount = bg,
        time = time,
        width = w,
        height = h,
        scale = 1f,
        lenMul = 1.05f,
        alpha = alpha,
        splash = false,
    )
}

private fun DrawScope.drawPoolSnow(
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

private fun DrawScope.drawPoolFog(
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

private fun DrawScope.drawPoolSunGlow(
    w: Float,
    h: Float,
    isDay: Boolean,
) {
    if (!isDay) return
    val cx = w * 0.88f
    val cy = h * 0.05f
    val radius = w * 0.55f
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color(0xFFFFE082).copy(alpha = 0.20f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
    )
}

private fun DrawScope.drawPoolClouds(
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

// ---------------------------------------------------------------------
// 球桌：木框 / 台呢 / 袋口 / 菱形标记
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolTable(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val scale = game.viewScale
    if (scale <= 0f) return
    val table = game.table
    val l = game.toCanvasX(table.left)
    val t = game.toCanvasY(table.top)
    val r = game.toCanvasX(table.right)
    val b = game.toCanvasY(table.bottom)
    if (r - l <= 1f || b - t <= 1f) return
    val ballR = table.ballRadius * scale
    if (ballR < 0.5f) return

    // 静态层：命中缓存则一次 drawImage（尺寸一致时 1:1 且不插值，避免糊边；
    // 连续 resize 复用旧位图时按当前画布尺寸拉伸，用 Low 插值避免锯齿）
    val base = PoolTableLayerCache.bitmap(game, style, isDay, size, this, layoutDirection)
    if (base != null) {
        val dstW = ceil(size.width).toInt()
        val dstH = ceil(size.height).toInt()
        val same = base.width == dstW && base.height == dstH
        drawImage(
            image = base.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(base.width, base.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = if (same) FilterQuality.None else FilterQuality.Low,
        )
    } else {
        drawPoolTableStatic(game, style, isDay)
    }

    // 结算阶段给台面一点压暗（Screen 已有浮层，这里只轻补，避免重复过暗）
    if (game.phase == PoolGame.Phase.FRAME_OVER || game.phase == PoolGame.Phase.MATCH_OVER) {
        clipRect(l, t, r, b) {
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(l, t), Size(r - l, b - t))
        }
    }
}

/** 烘焙进静态层的台面绘制：木框 / 台呢 / 库边 / 开球线与置球点 / 菱形 / 袋口。 */
private fun DrawScope.drawPoolTableStatic(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    val scale = game.viewScale
    if (scale <= 0f) return
    val table = game.table
    val l = game.toCanvasX(table.left)
    val t = game.toCanvasY(table.top)
    val r = game.toCanvasX(table.right)
    val b = game.toCanvasY(table.bottom)
    if (r - l <= 1f || b - t <= 1f) return

    val ballR = table.ballRadius * scale
    if (ballR < 0.5f) return
    val frame = ballR * 3.4f
    val fl = l - frame
    val ft = t - frame
    val fr = r + frame
    val fb = b + frame
    val corner = frame * 0.55f
    val dark = !isDay
    val felt = feltBase(style, isDay, game.clothTheme)

    // 整桌投影
    drawRoundRect(
        Color.Black.copy(alpha = if (dark) 0.44f else 0.30f),
        Offset(fl + frame * 0.30f, ft + frame * 0.46f),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
    )

    // 木框：对角线性渐变做木质明暗
    drawRoundRect(
        Brush.linearGradient(
            colors = listOf(honeyWoodLight, honeyWoodMid, honeyWoodDark),
            start = Offset(fl, ft),
            end = Offset(fr, fb),
        ),
        Offset(fl, ft),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
    )
    // 木框内侧高光
    drawRoundRect(
        Color.White.copy(alpha = if (dark) 0.05f else 0.13f),
        Offset(fl + frame * 0.14f, ft + frame * 0.14f),
        Size(fr - fl - frame * 0.28f, fb - ft - frame * 0.28f),
        CornerRadius(corner * 0.9f),
        style = Stroke(width = frame * 0.16f),
    )
    // 外描边
    drawRoundRect(
        woodEdge.copy(alpha = if (dark) 0.75f else 0.5f),
        Offset(fl, ft),
        Size(fr - fl, fb - ft),
        CornerRadius(corner),
        style = Stroke(width = ballR * 0.22f),
    )
    // 极淡木纹：沿长轴的确定性细纹，仅烘焙一次
    for (i in 0 until 12) {
        val gx = fl + (fr - fl) * hash01(i * 5 + 31)
        val sway = sin(i * 1.7f) * frame * 0.06f
        drawLine(
            if (i % 2 == 0) woodGrainDark else woodGrainLight,
            Offset(gx, ft + frame * 0.08f),
            Offset(gx + sway, fb - frame * 0.08f),
            strokeWidth = 0.8f + hash01(i * 2 + 7),
        )
    }

    // 四角斜接缝
    val seamDark = Color.Black.copy(alpha = 0.22f)
    val seamHi = Color.White.copy(alpha = 0.10f)
    drawMiter(fl, ft, l, t, seamDark, seamHi, ballR)
    drawMiter(fr, ft, r, t, seamDark, seamHi, ballR)
    drawMiter(fl, fb, l, b, seamDark, seamHi, ballR)
    drawMiter(fr, fb, r, b, seamDark, seamHi, ballR)

    // 台呢
    drawRect(felt, Offset(l, t), Size(r - l, b - t))

    val cx = (l + r) * 0.5f
    val cy = (t + b) * 0.5f
    val cushion = ballR * 0.85f

    clipRect(l, t, r, b) {
        // 径向暗角
        drawRect(
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = if (dark) 0.34f else 0.24f),
                    ),
                center = Offset(cx, cy),
                radius = max(r - l, b - t) * 0.72f,
            ),
            Offset(l, t),
            Size(r - l, b - t),
        )

        // 织纹：确定性伪随机，短线横竖交错
        val texCount = 240
        val hi = Color.White.copy(alpha = 0.045f)
        val lo = Color.Black.copy(alpha = 0.05f)
        for (i in 0 until texCount) {
            val fx = hash01(i * 2 + 1)
            val fy = hash01(i * 2 + 2)
            val px = l + fx * (r - l)
            val py = t + fy * (b - t)
            val len = (2f + hash01(i * 5 + 3) * 5f) * scale
            if (i % 2 == 0) {
                drawLine(hi, Offset(px - len * 0.5f, py), Offset(px + len * 0.5f, py), strokeWidth = 0.9f * scale)
            } else {
                drawLine(lo, Offset(px, py - len * 0.5f), Offset(px, py + len * 0.5f), strokeWidth = 0.9f * scale)
            }
        }
    }

    // 库边内阴影：四边向内心渐隐
    drawRect(
        Brush.verticalGradient(listOf(cushionShade.copy(alpha = 0.55f), Color.Transparent), startY = t, endY = t + cushion),
        Offset(l, t),
        Size(r - l, cushion),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, cushionShade.copy(alpha = 0.55f)), startY = b - cushion, endY = b),
        Offset(l, b - cushion),
        Size(r - l, cushion),
    )
    drawRect(
        Brush.horizontalGradient(listOf(cushionShade.copy(alpha = 0.55f), Color.Transparent), startX = l, endX = l + cushion),
        Offset(l, t),
        Size(cushion, b - t),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, cushionShade.copy(alpha = 0.55f)), startX = r - cushion, endX = r),
        Offset(r - cushion, t),
        Size(cushion, b - t),
    )
    // 库鼻高光
    val nose = Color.White.copy(alpha = 0.08f)
    drawLine(nose, Offset(l + cushion, t + cushion), Offset(r - cushion, t + cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(l + cushion, b - cushion), Offset(r - cushion, b - cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(l + cushion, t + cushion), Offset(l + cushion, b - cushion), strokeWidth = ballR * 0.05f)
    drawLine(nose, Offset(r - cushion, t + cushion), Offset(r - cushion, b - cushion), strokeWidth = ballR * 0.05f)
    // 台呢边界细描边
    drawRect(
        woodEdge.copy(alpha = 0.35f),
        Offset(l, t),
        Size(r - l, b - t),
        style = Stroke(width = ballR * 0.10f),
    )
    // 库边内沿接缝：极淡一圈，强调台呢与库边的分界
    drawRect(
        cushionSeam,
        Offset(l + cushion * 0.42f, t + cushion * 0.42f),
        Size(r - l - cushion * 0.84f, b - t - cushion * 0.84f),
        style = Stroke(width = ballR * 0.05f),
    )

    // 开球线与置球点
    val headY = game.toCanvasY(table.headStringY)
    drawDashedLine(
        Offset(l + ballR * 0.6f, headY),
        Offset(r - ballR * 0.6f, headY),
        Color.White.copy(alpha = 0.20f),
        ballR * 0.09f,
        ballR * 0.55f,
        ballR * 0.45f,
    )
    val footY = game.toCanvasY(table.footSpotY)
    drawCircle(Color.White.copy(alpha = 0.32f), ballR * 0.20f, Offset(cx, footY))
    drawCircle(Color.White.copy(alpha = 0.18f), ballR * 0.10f, Offset(cx, cy))

    // 袋口（盖在木框/台呢交界上）
    for (pocket in table.pockets) {
        drawPocket(
            game.toCanvasX(pocket.x),
            game.toCanvasY(pocket.y),
            max(pocket.radius * scale * 1.05f, ballR * 1.9f),
            ballR,
        )
    }

    // 库边菱形标记
    val dm = ballR * 0.40f
    for (k in 1..7) {
        val fx = l + (r - l) * k / 8f
        drawDiamond(fx, t - frame * 0.5f, dm)
        drawDiamond(fx, b + frame * 0.5f, dm)
    }
    for (k in 1..3) {
        val fy = t + (b - t) * k / 4f
        drawDiamond(l - frame * 0.5f, fy, dm)
        drawDiamond(r + frame * 0.5f, fy, dm)
    }
}

private fun feltBase(
    style: WeatherSceneStyle,
    isDay: Boolean,
    theme: Int,
): Color {
    val base =
        if (theme in clothDayThemes.indices) {
            Color(if (isDay) clothDayThemes[theme] else clothNightThemes[theme])
        } else if (isDay) {
            feltDayBase
        } else {
            feltNightBase
        }
    val cold =
        when (style.scene) {
            WeatherScene.RAIN,
            WeatherScene.HEAVY_RAIN,
            WeatherScene.DRIZZLE,
            WeatherScene.THUNDER,
            WeatherScene.HAIL,
            WeatherScene.FREEZING_RAIN,
            WeatherScene.SNOW,
            WeatherScene.HEAVY_SNOW,
            WeatherScene.FOG,
            WeatherScene.OVERCAST,
            WeatherScene.UNKNOWN,
            -> 0.16f
            else -> 0f
        }
    return if (cold > 0f) lerp(base, feltColdTint, cold) else base
}

private fun DrawScope.drawMiter(
    ox: Float,
    oy: Float,
    ix: Float,
    iy: Float,
    dark: Color,
    hi: Color,
    ballR: Float,
) {
    drawLine(dark, Offset(ox, oy), Offset(ix, iy), strokeWidth = ballR * 0.12f)
    // 靠近外角的一小段高光，模拟斜接面受光
    val dx = ix - ox
    val dy = iy - oy
    drawLine(
        hi,
        Offset(ox + dx * 0.12f, oy + dy * 0.12f),
        Offset(ox + dx * 0.42f, oy + dy * 0.42f),
        strokeWidth = ballR * 0.06f,
    )
}

private fun DrawScope.drawPocket(
    px: Float,
    py: Float,
    holeR: Float,
    ballR: Float,
) {
    // 木框收边
    drawCircle(honeyWoodDark.copy(alpha = 0.95f), holeR * 1.26f, Offset(px, py))
    drawCircle(pocketRim.copy(alpha = 0.85f), holeR * 1.26f, Offset(px, py), style = Stroke(width = ballR * 0.14f))
    drawCircle(woodEdge.copy(alpha = 0.6f), holeR * 1.36f, Offset(px, py), style = Stroke(width = ballR * 0.10f))
    // 洞口外阴影：让袋口整体下沉
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(px, py),
            radius = holeR * 1.30f,
        ),
        radius = holeR * 1.30f,
        center = Offset(px, py),
    )
    // 洞（更深：中心偏左上，边到纯黑）
    drawCircle(
        Brush.radialGradient(
            colors = listOf(pocketInk, pocketDeep, Color.Black),
            center = Offset(px - holeR * 0.12f, py - holeR * 0.12f),
            radius = holeR * 1.20f,
        ),
        radius = holeR,
        center = Offset(px, py),
    )
    // 袋网：从内壁向心的细网线 + 网底阴影
    for (i in 0 until 10) {
        val a = i * (2f * PI.toFloat() / 10f)
        val rx = cos(a)
        val ry = sin(a)
        drawLine(
            pocketNetLine,
            Offset(px + rx * holeR * 0.92f, py + ry * holeR * 0.92f),
            Offset(px + rx * holeR * 0.28f, py + ry * holeR * 0.28f + holeR * 0.12f),
            strokeWidth = ballR * 0.05f,
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.50f), holeR * 0.32f, Offset(px, py + holeR * 0.10f))
    // 内阴影（下半圈更暗）
    drawArc(
        color = Color.Black.copy(alpha = 0.55f),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.22f),
    )
    // 上缘布料/皮革反光
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.12f),
    )
}

private fun DrawScope.drawDiamond(
    cx: Float,
    cy: Float,
    s: Float,
) {
    drawCircle(Color.Black.copy(alpha = 0.18f), s * 1.35f, Offset(cx, cy + s * 0.16f))
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(diamondIvory, Offset(cx - s, cy - s), Size(s * 2f, s * 2f))
    }
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        drawRect(Color.Black.copy(alpha = 0.22f), Offset(cx - s, cy - s), Size(s * 2f, s * 2f), style = Stroke(s * 0.30f))
    }
}

// ---------------------------------------------------------------------
// 球与落袋动画
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolBalls(
    game: PoolGame,
    style: WeatherSceneStyle,
    isDay: Boolean,
) {
    if (game.balls.isEmpty()) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val ballR = game.table.ballRadius * scale
    if (ballR < 0.5f) return
    // 夜色下球体略压暗；用场景内容色做一点环境反光
    val dim = if (isDay) 1f else 0.90f
    val rimLight = lerp(Color.White, style.contentColor, 0.30f)

    for (ball in game.balls) {
        drawOneBall(game, ball, ballR, dim, rimLight)
    }
}

private fun DrawScope.drawOneBall(
    game: PoolGame,
    ball: PoolBall,
    ballR: Float,
    dim: Float,
    rimLight: Color,
) {
    if (!ball.onTable && !ball.potted) return
    var cx = game.toCanvasX(ball.x)
    var cy = game.toCanvasY(ball.y)
    var r = ballR
    var alpha = 1f

    if (ball.potted) {
        val t = ball.pocketAnim.coerceIn(0f, 1f)
        if (t >= 1f) return
        val pocket = game.table.pockets.getOrNull(ball.pocketIndex)
        if (pocket != null) {
            val k = t * t * (3f - 2f * t)
            cx += (game.toCanvasX(pocket.x) - cx) * k
            cy += (game.toCanvasY(pocket.y) - cy) * k
        }
        alpha = 1f - t
        r = ballR * (1f - 0.78f * t)
    }
    if (alpha <= 0.02f || r <= 0.2f) return

    val base = ballBaseColor(ball)
    // 跳球：按离台高度抬高球体，阴影留在台面并随高度变小变淡
    val groundCy = cy
    val heightFrac = (ball.z / (ball.radius * 4f)).coerceIn(0f, 1f)
    val lift = if (ball.airborne) ball.z * game.viewScale else 0f
    if (lift > 0f) cy -= lift

    // 运动拖影（减弱动画/腾空时关闭）
    if (ball.onTable && !ball.airborne && !game.reduceMotion) {
        val speed = ball.speed
        if (speed > 1.4f) {
            val k = (speed / PoolConfig.MAX_SPEED).coerceIn(0f, 0.6f)
            val ux = ball.vx / speed
            val uy = ball.vy / speed
            drawCircle(
                Color.White.copy(alpha = 0.10f * k * alpha),
                r * 0.92f,
                Offset(cx - ux * r * 0.9f, cy - uy * r * 0.9f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.06f * k * alpha),
                r * 0.80f,
                Offset(cx - ux * r * 1.8f, cy - uy * r * 1.8f),
            )
        }
    }

    drawBallShadow(cx, groundCy, r * (1f - 0.28f * heightFrac), alpha * (1f - 0.4f * heightFrac))
    drawSphereBase(cx, cy, r, base, alpha * dim)
    if (ball.kind == PoolBallKind.STRIPE) {
        drawStripeBand(ball, cx, cy, r, alpha)
    }
    drawSphereGloss(cx, cy, r, alpha, rimLight)
    // 8 号/白球加一圈淡淡描边，提升低对比场景与色觉障碍下的可辨识度
    when (ball.kind) {
        PoolBallKind.EIGHT ->
            drawCircle(
                Color.White.copy(alpha = 0.30f * alpha),
                r * 0.985f,
                Offset(cx, cy),
                style =
                    Stroke(
                        width =
                            r * 0.06f,
                    ),
            )
        PoolBallKind.CUE ->
            drawCircle(
                Color(0xFF37474F).copy(alpha = 0.35f * alpha),
                r * 0.985f,
                Offset(cx, cy),
                style =
                    Stroke(
                        width =
                            r * 0.05f,
                    ),
            )
        else -> Unit
    }
    if (ball.kind != PoolBallKind.CUE) {
        drawBallNumber(ball, cx, cy, r, alpha)
    } else {
        // 白球小红点，方便辨认
        drawCircle(Color(0xFFE53935).copy(alpha = 0.9f * alpha), r * 0.13f, Offset(cx + r * 0.30f, cy + r * 0.26f))
    }
}

private fun ballBaseColor(ball: PoolBall): Color =
    when (ball.kind) {
        PoolBallKind.CUE -> cueWhite
        PoolBallKind.EIGHT -> eightBlack
        PoolBallKind.SOLID -> numberHue(ball.number)
        PoolBallKind.STRIPE -> stripeWhite
    }

private fun numberHue(number: Int): Color {
    val i = ((number - 1) % 8).coerceIn(0, ballHues.size - 1)
    return Color(ballHues[i])
}

private fun DrawScope.drawBallShadow(
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val ox = r * 0.24f
    val oy = r * 0.30f
    drawOval(
        Color.Black.copy(alpha = 0.09f * alpha),
        topLeft = Offset(cx - r * 1.06f + ox, cy - r * 0.78f + oy),
        size = Size(r * 2.12f, r * 1.56f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.13f * alpha),
        topLeft = Offset(cx - r * 0.86f + ox, cy - r * 0.66f + oy),
        size = Size(r * 1.72f, r * 1.30f),
    )
    drawOval(
        Color.Black.copy(alpha = 0.16f * alpha),
        topLeft = Offset(cx - r * 0.66f + ox, cy - r * 0.54f + oy),
        size = Size(r * 1.32f, r * 1.06f),
    )
}

private fun DrawScope.drawSphereBase(
    cx: Float,
    cy: Float,
    r: Float,
    base: Color,
    alpha: Float,
) {
    drawCircle(
        Brush.radialGradient(
            colors = listOf(lighten(base, 0.42f), base, darken(base, 0.55f)),
            center = Offset(cx - r * 0.34f, cy - r * 0.36f),
            radius = r * 1.52f,
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha,
    )
}

private fun DrawScope.drawSphereGloss(
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
    rimLight: Color,
) {
    // 边缘暗化 + 环境反光
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.34f)),
            center = Offset(cx - r * 0.30f, cy - r * 0.32f),
            radius = r * 1.45f,
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha,
    )
    // 菲涅尔边缘环境光：贴边一圈柔和反光，让球体轮廓更立体
    drawCircle(
        Brush.radialGradient(
            0.60f to Color.Transparent,
            0.84f to rimLight.copy(alpha = 0.10f),
            1.00f to rimLight.copy(alpha = 0.30f),
            center = Offset(cx, cy),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha,
    )
    drawCircle(
        Brush.radialGradient(
            colors = listOf(rimLight.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(cx + r * 0.26f, cy + r * 0.34f),
            radius = r * 0.95f,
        ),
        radius = r,
        center = Offset(cx, cy),
        alpha = alpha,
    )
    // 主高光：柔和光斑衰减 + 高亮小核
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(cx - r * 0.34f, cy - r * 0.37f),
            radius = r * 0.44f,
        ),
        radius = r * 0.44f,
        center = Offset(cx - r * 0.34f, cy - r * 0.37f),
        alpha = alpha,
    )
    drawCircle(Color.White.copy(alpha = 0.82f * alpha), r * 0.15f, Offset(cx - r * 0.34f, cy - r * 0.37f))
    drawCircle(Color.White.copy(alpha = 0.28f * alpha), r * 0.075f, Offset(cx - r * 0.12f, cy - r * 0.20f))
}

private fun DrawScope.drawStripeBand(
    ball: PoolBall,
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val n = ball.markingNormal()
    val angleDeg = atan2(n[1], n[0]) * 180f / PI.toFloat()
    val half = r * 0.44f
    val path = PoolRenderCache.ballClip
    withTransform({
        translate(cx, cy)
        rotate(degrees = angleDeg, pivot = Offset.Zero)
    }) {
        path.reset()
        path.addOval(Rect(-r, -r, r, r))
        clipPath(path) {
            drawRect(
                numberHue(ball.number),
                topLeft = Offset(-r, -half),
                size = Size(r * 2f, half * 2f),
                alpha = 0.97f * alpha,
            )
            drawRect(
                Color.Black.copy(alpha = 0.12f * alpha),
                topLeft = Offset(-r, -half),
                size = Size(r * 2f, r * 0.07f),
            )
            drawRect(
                Color.Black.copy(alpha = 0.12f * alpha),
                topLeft = Offset(-r, half - r * 0.07f),
                size = Size(r * 2f, r * 0.07f),
            )
        }
    }
}

private fun DrawScope.drawBallNumber(
    ball: PoolBall,
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
) {
    val n = ball.markingNormal()
    val nz = n[2]
    if (nz <= 0.12f) return
    val nx = n[0]
    val ny = n[1]
    val deg = atan2(ny, nx) * 180f / PI.toFloat()
    val visibility = ((nz - 0.12f) / 0.5f).coerceIn(0f, 1f)
    val spot = r * 0.40f
    val px = cx + r * nx * 0.92f
    val py = cy + r * ny * 0.92f
    val visualAlpha = alpha * visibility

    withTransform({
        translate(px, py)
        rotate(degrees = deg, pivot = Offset.Zero)
    }) {
        drawOval(
            Color(0xFFF6F1E4).copy(alpha = 0.96f * visualAlpha),
            topLeft = Offset(-spot * nz, -spot),
            size = Size(spot * 2f * nz, spot * 2f),
        )
    }

    val paint = PoolRenderCache.numberPaint
    paint.textSize = r * 0.80f
    paint.alpha = (255f * visualAlpha).toInt().coerceIn(0, 255)
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.translate(px, py)
    canvas.rotate(deg)
    canvas.drawText(ball.number.toString(), 0f, paint.textSize * 0.34f, paint)
    canvas.restore()
}

// ---------------------------------------------------------------------
// 瞄准：球杆 / 幽灵球 / 辅助线
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolAim(
    game: PoolGame,
    aids: PoolGame.Aids,
) {
    if (game.phase != PoolGame.Phase.AIMING) return
    val cue = game.cueBall() ?: return
    if (!cue.onTable || cue.potted) return
    val scale = game.viewScale
    if (scale <= 0f) return
    val ballR = game.table.ballRadius * scale
    if (ballR < 0.5f) return

    val cx = game.toCanvasX(cue.x)
    val cy = game.toCanvasY(cue.y)
    val angle = game.aimAngle
    val dx = cos(angle)
    val dy = sin(angle)

    if (game.placingCue || game.ballInHand) {
        drawPlacementPulse(cx, cy, ballR, game.time)
    }

    when (aids) {
        PoolGame.Aids.OFF -> Unit
        PoolGame.Aids.GHOST -> drawAimAssist(game, cue, cx, cy, ballR, dx, dy, full = false)
        PoolGame.Aids.FULL -> drawAimAssist(game, cue, cx, cy, ballR, dx, dy, full = true)
    }

    // 弧线暗示：抬杆 + 侧旋时，在瞄准方向一侧画一段弯曲提示
    if (game.elevation > 0.01f && abs(game.spin.side) > 0.01f) {
        drawCurveHint(cx, cy, ballR, dx, dy, game.elevation, game.spin.side)
    }

    drawCueStick(cx, cy, ballR, angle, dx, dy, game.power, game.spin)
}

/** 弧线瞄准暗示：从白球出发、向侧旋一侧弯曲的虚线弧与端点。 */
private fun DrawScope.drawCurveHint(
    cx: Float,
    cy: Float,
    ballR: Float,
    dx: Float,
    dy: Float,
    elevation: Float,
    side: Float,
) {
    val elev = elevation.coerceIn(0f, 1f)
    val sign = if (side >= 0f) 1f else -1f
    val perpX = -dy
    val perpY = dx
    val len = ballR * (2.6f + 3.2f * elev)
    val bow = ballR * (1.3f + 2.4f * elev) * sign
    val path = PoolRenderCache.curveHint
    path.reset()
    val steps = 10
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val along = len * t
        val lateral = bow * t * t
        val px = cx + dx * along + perpX * lateral
        val py = cy + dy * along + perpY * lateral
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(
        path,
        curveHintColor.copy(alpha = 0.55f),
        style = Stroke(width = ballR * 0.08f, cap = StrokeCap.Round),
    )
    val tipX = cx + dx * len + perpX * bow
    val tipY = cy + dy * len + perpY * bow
    drawCircle(curveHintColor.copy(alpha = 0.80f), ballR * 0.13f, Offset(tipX, tipY))
}

private fun DrawScope.drawCueStick(
    cx: Float,
    cy: Float,
    ballR: Float,
    angle: Float,
    dx: Float,
    dy: Float,
    power: Float,
    spin: PoolGame.Spin,
) {
    val backX = -dx
    val backY = -dy
    val pull = ballR * (1.35f + power * 6.5f)
    val tipX = cx + backX * pull
    val tipY = cy + backY * pull
    val stickLen = ballR * 18f
    val buttX = tipX + backX * stickLen
    val buttY = tipY + backY * stickLen
    val perpX = -backY
    val perpY = backX
    val tipW = ballR * 0.15f
    val buttW = ballR * 0.42f

    val path = PoolRenderCache.shape
    path.reset()
    path.moveTo(tipX + perpX * tipW, tipY + perpY * tipW)
    path.lineTo(buttX + perpX * buttW, buttY + perpY * buttW)
    path.lineTo(buttX - perpX * buttW, buttY - perpY * buttW)
    path.lineTo(tipX - perpX * tipW, tipY - perpY * tipW)
    path.close()
    drawPath(
        path,
        Brush.linearGradient(
            colors = listOf(cueWoodLight, cueWoodMid, cueWoodDark),
            start = Offset(tipX, tipY),
            end = Offset(buttX, buttY),
        ),
    )
    // 前节白箍
    val ferruleX = tipX + backX * ballR * 1.6f
    val ferruleY = tipY + backY * ballR * 1.6f
    drawLine(
        cueFerrule.copy(alpha = 0.9f),
        Offset(tipX, tipY),
        Offset(ferruleX, ferruleY),
        strokeWidth = ballR * 0.26f,
        cap = StrokeCap.Round,
    )
    // 皮头
    drawCircle(cueTipBlue, tipW * 1.35f, Offset(tipX, tipY))
    drawCircle(Color.White.copy(alpha = 0.25f), tipW * 0.6f, Offset(tipX - perpX * tipW * 0.4f, tipY - perpY * tipW * 0.4f))
    // 杆身高光
    drawLine(
        Color.White.copy(alpha = 0.22f),
        Offset(tipX + perpX * tipW * 0.4f, tipY + perpY * tipW * 0.4f),
        Offset(buttX + perpX * buttW * 0.45f, buttY + perpY * buttW * 0.45f),
        strokeWidth = ballR * 0.06f,
        cap = StrokeCap.Round,
    )
    // 杆法接触点提示
    if (spin.side != 0f || spin.vertical != 0f) {
        val sx = cx + perpX * spin.side * ballR * 0.55f + dx * spin.vertical * ballR * 0.55f
        val sy = cy + perpY * spin.side * ballR * 0.55f + dy * spin.vertical * ballR * 0.55f
        drawCircle(Color.White.copy(alpha = 0.55f), ballR * 0.15f, Offset(sx, sy))
        drawCircle(Color(0xFF37474F).copy(alpha = 0.75f), ballR * 0.08f, Offset(sx, sy))
    }
}

/**
 * 白球碰后分离线方向（单位向量）：白球切线速度 `v - (v·n)n` 的方向。
 * [targetDirX]/[targetDirY] 为幽灵球指向目标球的单位法线 n，入射方向取当前白球到
 * 幽灵球的方向，因此分离符号为 `-(cue - ghost)·perp(n)`（perp 为 n 的 90° 法向）。
 */
internal fun cueSeparationDirection(
    targetDirX: Float,
    targetDirY: Float,
    cueX: Float,
    cueY: Float,
    ghostX: Float,
    ghostY: Float,
): Pair<Float, Float> {
    val perpX = -targetDirY
    val perpY = targetDirX
    val sign = if ((cueX - ghostX) * perpX + (cueY - ghostY) * perpY >= 0f) -1f else 1f
    return perpX * sign to perpY * sign
}

private fun DrawScope.drawAimAssist(
    game: PoolGame,
    cue: PoolBall,
    cx: Float,
    cy: Float,
    ballR: Float,
    dx: Float,
    dy: Float,
    full: Boolean,
) {
    val table = game.table
    val fc = PoolPhysics.predictFirstContact(game.balls, table, cue, game.aimAngle)
    if (fc != null && fc.target != null) {
        val ghostX = game.toCanvasX(fc.x)
        val ghostY = game.toCanvasY(fc.y)
        val target = fc.target

        if (full) {
            drawGuideLine(
                Offset(cx, cy),
                Offset(ghostX, ghostY),
                aimLineColor,
                ballR * 0.10f,
                ballR * 0.60f,
                ballR * 0.48f,
            )
        }

        // 幽灵球：外发光 + 高亮描边，命中点更醒目
        drawCircle(ghostFill, ballR, Offset(ghostX, ghostY))
        drawCircle(ghostRing.copy(alpha = 0.20f), ballR * 1.16f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.18f))
        drawCircle(ghostRing, ballR, Offset(ghostX, ghostY), style = Stroke(ballR * 0.14f))
        drawCircle(ghostInner, ballR * 0.55f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.06f))

        // 目标球行进方向
        val tvx = target.x - fc.x
        val tvy = target.y - fc.y
        val tl = hypot(tvx, tvy)
        if (tl > 1e-4f) {
            val ux = tvx / tl
            val uy = tvy / tl
            val startX = game.toCanvasX(target.x)
            val startY = game.toCanvasY(target.y)
            val endX = startX + ux * ballR * 9f
            val endY = startY + uy * ballR * 9f
            drawArrowLine(Offset(startX, startY), Offset(endX, endY), targetLineColor, ballR * 0.11f, ballR * 0.85f)

            if (full) {
                // 白球分离线（切线方向，符号取白球碰后实际切线速度方向）
                val (sepX, sepY) = cueSeparationDirection(ux, uy, cue.x, cue.y, fc.x, fc.y)
                drawGuideLine(
                    Offset(ghostX, ghostY),
                    Offset(ghostX + sepX * ballR * 6f, ghostY + sepY * ballR * 6f),
                    cueBreakColor,
                    ballR * 0.10f,
                    ballR * 0.55f,
                    ballR * 0.45f,
                )
            }
        }
        return
    }

    // 无有效目标：瞄准线到库，并做一次反射预测
    val tRail = PoolPhysics.raycastRail(cue.x, cue.y, dx, dy, table, table.height * 2f)
    val endTableX = if (tRail != null) cue.x + dx * tRail else cue.x + dx * table.height * 2f
    val endTableY = if (tRail != null) cue.y + dy * tRail else cue.y + dy * table.height * 2f
    val endX = game.toCanvasX(endTableX)
    val endY = game.toCanvasY(endTableY)
    drawGuideLine(Offset(cx, cy), Offset(endX, endY), aimLineColor, ballR * 0.10f, ballR * 0.60f, ballR * 0.48f)

    if (full && tRail != null) {
        val rail = nearestRail(table, endTableX, endTableY)
        if (rail != null) {
            val (nx, ny) = PoolPhysics.railInwardNormal(rail, table)
            val dot = dx * nx + dy * ny
            val rdx = dx - 2f * dot * nx
            val rdy = dy - 2f * dot * ny
            drawGuideLine(
                Offset(endX, endY),
                Offset(game.toCanvasX(endTableX + rdx * ballR * 8f), game.toCanvasY(endTableY + rdy * ballR * 8f)),
                reflectLineColor,
                ballR * 0.09f,
                ballR * 0.60f,
                ballR * 0.50f,
            )
        }
    }
}

private fun nearestRail(
    table: PoolTable,
    x: Float,
    y: Float,
): PoolRail? {
    var best: PoolRail? = null
    var bestD = Float.MAX_VALUE
    for (rail in table.rails) {
        val dx = rail.x2 - rail.x1
        val dy = rail.y2 - rail.y1
        val len2 = dx * dx + dy * dy
        val d =
            if (len2 < 1e-9f) {
                hypot(x - rail.x1, y - rail.y1)
            } else {
                val t = (((x - rail.x1) * dx + (y - rail.y1) * dy) / len2).coerceIn(0f, 1f)
                hypot(x - (rail.x1 + dx * t), y - (rail.y1 + dy * t))
            }
        if (d < bestD) {
            bestD = d
            best = rail
        }
    }
    return best
}

private fun DrawScope.drawPlacementPulse(
    cx: Float,
    cy: Float,
    ballR: Float,
    time: Float,
) {
    val pulse = 0.5f + 0.5f * sin(time * 5f)
    drawCircle(
        placeRing.copy(alpha = 0.16f + 0.20f * pulse),
        ballR * (2.4f + pulse * 0.5f),
        Offset(cx, cy),
    )
    drawCircle(
        Color.White.copy(alpha = 0.60f),
        ballR * 1.45f,
        Offset(cx, cy),
        style = Stroke(ballR * 0.14f),
    )
}

// ---------------------------------------------------------------------
// 粒子特效
// ---------------------------------------------------------------------

internal fun DrawScope.drawPoolEffects(game: PoolGame) {
    if (game.effects.isEmpty()) return
    val scale = game.viewScale
    if (scale <= 0f) return
    for (e in game.effects) {
        val k = (e.life / 0.5f).coerceIn(0f, 1f)
        if (k <= 0f) continue
        val x = game.toCanvasX(e.x)
        val y = game.toCanvasY(e.y)
        val r = (e.size * scale).coerceAtLeast(0.8f) * (0.5f + 0.5f * k)
        val c = Color(e.color)
        drawCircle(c.copy(alpha = 0.18f * k), r * 2.4f, Offset(x, y))
        drawCircle(c.copy(alpha = 0.85f * k), r, Offset(x, y))
    }
}

// ---------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------

private fun DrawScope.drawDashedLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    dash: Float,
    gap: Float,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx, dy)
    if (len < dash) return
    val ux = dx / len
    val uy = dy / len
    val path = PoolRenderCache.dashed
    path.reset()
    var d = 0f
    while (d < len) {
        val e = min(d + dash, len)
        path.moveTo(from.x + ux * d, from.y + uy * d)
        path.lineTo(from.x + ux * e, from.y + uy * e)
        d += dash + gap
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

/** 虚线辅助线 + 两端小圆端点，用于瞄准提示，使起止点更清晰。 */
private fun DrawScope.drawGuideLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    dash: Float,
    gap: Float,
) {
    drawDashedLine(from, to, color, strokeWidth, dash, gap)
    val dot = strokeWidth * 1.25f
    drawCircle(color, dot, from)
    drawCircle(color, dot, to)
}

private fun DrawScope.drawArrowLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    headLen: Float,
) {
    drawLine(color, from, to, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    val ang = atan2(to.y - from.y, to.x - from.x)
    val path = PoolRenderCache.shape
    path.reset()
    path.moveTo(to.x, to.y)
    path.lineTo(to.x - cos(ang - 0.5f) * headLen, to.y - sin(ang - 0.5f) * headLen)
    path.lineTo(to.x - cos(ang + 0.5f) * headLen, to.y - sin(ang + 0.5f) * headLen)
    path.close()
    drawPath(path, color)
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

/** 确定性伪随机 [0,1]，用于织纹，保证同一 index 每帧结果一致。 */
private fun hash01(n: Int): Float {
    var h = n * 374761393 + 668265263
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}
