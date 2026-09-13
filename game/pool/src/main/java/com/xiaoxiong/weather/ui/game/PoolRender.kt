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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
private val potLineColor = Color(0xFF9CF29C)
private val potRingColor = Color(0xFF7DE39A)
private val cueBreakColor = Color(0xFF90CAF9).copy(alpha = 0.62f)
private val reflectLineColor = Color.White.copy(alpha = 0.26f)
private val ghostFill = Color.White.copy(alpha = 0.24f)
private val ghostRing = Color.White.copy(alpha = 0.78f)
private val ghostInner = Color(0xFFFFF59D).copy(alpha = 0.55f)
private val placeRing = Color(0xFFFFE082)

/** 瞄准警示色：首碰非法或白球将落袋时的辅助线与幽灵球描边。 */
private val aimWarnColor = Color(0xFFE57373)

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

    /** 袋口「口外半平面」裁剪多边形（逐袋口重置复用）。 */
    val pocketClip = Path()

    /** 号码文字画笔，逐球只改 alpha/textSize。 */
    val numberPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = android.graphics.Color.rgb(0x20, 0x1A, 0x14)
        }
}

// ---------------------------------------------------------------------
// 热路径笔刷缓存：渐变统一以 Offset.Zero 为参考点缓存，绘制时 translate 到目标位置，
// 避免每帧为每颗球新建 Brush 与颜色 list。
// ---------------------------------------------------------------------

/** 固定容量的小型 LRU 笔刷缓存（仅主线程绘制，无需加锁）。 */
private class BrushCache(
    private val capacity: Int,
) {
    private val entries = LinkedHashMap<Long, Brush>()

    operator fun get(key: Long): Brush? {
        val cached = entries[key] ?: return null
        entries.remove(key)
        entries[key] = cached
        return cached
    }

    operator fun set(
        key: Long,
        brush: Brush,
    ) {
        entries[key] = brush
        if (entries.size > capacity) {
            val oldest = entries.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
    }
}

/** (颜色, 半径) 缓存键。 */
private fun brushKey(
    radius: Float,
    colorArgb: Int,
): Long = (colorArgb.toLong() shl 32) or (radius.toRawBits().toLong() and 0xFFFFFFFFL)

/** 仅按半径缓存（颜色固定的渐变）。 */
private fun radiusKey(radius: Float): Long = radius.toRawBits().toLong()

private val sphereBaseBrushes = BrushCache(capacity = 16)
private val sphereShadeBrushes = BrushCache(capacity = 4)
private val sphereRimBrushes = BrushCache(capacity = 8)
private val sphereAmbientBrushes = BrushCache(capacity = 8)
private val sphereHighlightBrushes = BrushCache(capacity = 4)

/** 球杆木质线性渐变缓存：局部坐标下沿杆长，绘制时旋转平移。 */
private class CueStickBrushCache {
    private var length = Float.NaN
    private var brush: Brush? = null

    fun get(length: Float): Brush {
        val cached = brush
        if (cached != null && this.length == length) return cached
        return Brush
            .linearGradient(
                colors = listOf(cueWoodLight, cueWoodMid, cueWoodDark),
                start = Offset.Zero,
                end = Offset(length, 0f),
            ).also {
                brush = it
                this.length = length
            }
    }
}

private val cueStickBrushes = CueStickBrushCache()

/** 场景整屏渐变 / 太阳光晕缓存（style、昼夜或画布尺寸不变时复用）。 */
private class SceneBrushCache {
    private var style: WeatherSceneStyle? = null
    private var isDay = false
    private var overlay: Brush? = null
    private var glowW = Float.NaN
    private var glowH = Float.NaN
    private var glow: Brush? = null

    fun overlay(
        style: WeatherSceneStyle,
        isDay: Boolean,
    ): Brush? {
        val grad = style.gradient
        if (grad.isEmpty()) return null
        val cached = overlay
        if (cached != null && this.style == style && this.isDay == isDay) return cached
        val a = if (isDay) 0.10f else 0.16f
        val colors = grad.map { it.copy(alpha = a) } + Color.Transparent
        return Brush
            .verticalGradient(colors)
            .also {
                overlay = it
                this.style = style
                this.isDay = isDay
            }
    }

    fun sunGlow(
        w: Float,
        h: Float,
    ): Brush {
        val cached = glow
        if (cached != null && glowW == w && glowH == h) return cached
        val radius = w * 0.55f
        return Brush
            .radialGradient(
                colors = listOf(Color(0xFFFFE082).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.88f, h * 0.05f),
                radius = radius,
            ).also {
                glow = it
                glowW = w
                glowH = h
            }
    }
}

private val sceneBrushes = SceneBrushCache()

// ---------------------------------------------------------------------
// 静态台面层烘焙缓存
// ---------------------------------------------------------------------

/**
 * 静态层缓存键：画布位图尺寸 + 视图变换 + 球桌几何 + 场景样式 + 台呢主题。
 * 纯数据（不含位图），便于把「主题是否参与缓存」抽成纯函数做单测。
 */
internal data class PoolLayerKey(
    val width: Int,
    val height: Int,
    val viewScale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val tableLeft: Float,
    val tableTop: Float,
    val tableWidth: Float,
    val tableHeight: Float,
    val style: WeatherSceneStyle,
    val isDay: Boolean,
    val clothTheme: Int,
)

/** 拖动窗口时允许复用的位图尺寸误差（px）。 */
private const val POOL_LAYER_RESIZE_TOLERANCE_PX = 24

/**
 * 静态层是否可近似复用：台呢主题 / 场景样式 / 昼夜 / 球桌几何必须一致，位图尺寸在容差内。
 * 主题不一致必须重建——否则切台呢时几何/样式/尺寸都没变，会错误复用旧主题的静态层，
 * 画面看起来「无论怎么选都一样」。
 */
internal fun poolLayerKeyReusable(
    cached: PoolLayerKey,
    target: PoolLayerKey,
): Boolean =
    cached.clothTheme == target.clothTheme &&
        cached.style == target.style &&
        cached.isDay == target.isDay &&
        cached.tableLeft == target.tableLeft &&
        cached.tableTop == target.tableTop &&
        cached.tableWidth == target.tableWidth &&
        cached.tableHeight == target.tableHeight &&
        abs(cached.width - target.width) <= POOL_LAYER_RESIZE_TOLERANCE_PX &&
        abs(cached.height - target.height) <= POOL_LAYER_RESIZE_TOLERANCE_PX

/**
 * 台面静态层（木框 / 台呢暗角与织纹 / 库边明暗 / 开球线与置球点 / 菱形 / 袋口）
 * 每帧不变，按 [PoolLayerKey] 烘焙成 [ImageBitmap]，之后每帧只 drawImage。仅主线程
 * 绘制，无需加锁；最多保留 [PoolTableLayerCache.MAX_ENTRIES] 张做 LRU。
 */
private class PoolTableLayer(
    val bitmap: ImageBitmap,
    val key: PoolLayerKey,
)

private object PoolTableLayerCache {
    const val MAX_ENTRIES = 2
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
        val key =
            PoolLayerKey(
                width = w,
                height = h,
                viewScale = scale,
                offsetX = game.viewOffsetX,
                offsetY = game.viewOffsetY,
                tableLeft = game.table.left,
                tableTop = game.table.top,
                tableWidth = game.table.width,
                tableHeight = game.table.height,
                style = style,
                isDay = isDay,
                clothTheme = game.clothTheme,
            )

        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.key == key) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        // 连续拖动 resize 时避免每像素重建整屏位图：尺寸相近且几何/主题一致时复用并按需拉伸绘制
        i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (poolLayerKeyReusable(e.key, key)) return e
            i--
        }

        val bitmap = ImageBitmap(w, h)
        // 用原始浮点 size 布局，保证坐标与直接绘制一致；位图取 ceil 仅多出边缘空像素。
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
            drawPoolTableStatic(game, style, isDay)
        }
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(PoolTableLayer(bitmap, key))
        return entries.last()
    }

    /** 释放缓存的全屏 Bitmap（退出对局时调用，二次进入会按需重建）。 */
    fun clear() {
        entries.clear()
    }
}

/** 释放静态台面层缓存：退出对局后避免两张全屏 Bitmap 常驻内存。 */
internal fun clearPoolTableLayerCache() {
    PoolTableLayerCache.clear()
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
    /** 减弱动画：跳过粒子/闪电，仅保留静态底色与光晕。 */
    reduceMotion: Boolean = false,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val w = size.width
    val h = size.height
    val time = game.time

    // 场景底色：整屏铺但极淡，给台外一点氛围，不抢台面
    sceneBrushes.overlay(style, isDay)?.let {
        drawRect(it, topLeft = Offset.Zero, size = Size(w, h))
    }

    // 冻雨 / 冰雹优先识别
    if (freezingRain) {
        drawRect(weatherFreezingTint)
        drawFreezingRain(time, 1f, w, h, splash = false, reduceMotion = reduceMotion)
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
            reduceMotion = reduceMotion,
        )
        drawHailPellets(time, 1f, w, h, count = 52, reduceMotion = reduceMotion)
        drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        return
    }

    when (weather) {
        FarmWeather.RAINY -> {
            drawRect(weatherRainTint)
            drawPoolRain(time, w, h, rain, reduceMotion)
        }
        FarmWeather.STORMY -> {
            drawRect(weatherStormTint)
            drawPoolRain(time, w, h, RainStrength.HEAVY, reduceMotion)
            drawStormLightning(time, 1f, w, h, reduceMotion = reduceMotion)
        }
        FarmWeather.SNOWY -> {
            drawRect(weatherSnowTint)
            if (!reduceMotion) drawPoolSnow(time, w, h)
        }
        FarmWeather.FOGGY -> {
            drawRect(weatherFogTint)
            if (!reduceMotion) drawPoolFog(time, w, h)
        }
        FarmWeather.SUNNY -> drawPoolSunGlow(w, h, isDay)
        FarmWeather.CLOUDY -> if (!reduceMotion) drawPoolClouds(time, w, h, isDay)
    }
}

private fun DrawScope.drawPoolRain(
    time: Float,
    w: Float,
    h: Float,
    rain: RainStrength,
    reduceMotion: Boolean,
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
        reduceMotion = reduceMotion,
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
    val radius = w * 0.55f
    drawCircle(
        sceneBrushes.sunGlow(w, h),
        radius = radius,
        center = Offset(w * 0.88f, h * 0.05f),
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
        val same = base.key.width == dstW && base.key.height == dstH
        drawImage(
            image = base.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(base.key.width, base.key.height),
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

        // 织纹：确定性伪随机，短线横竖交错。长度/线宽以球半径为尺度（像素级），
        // 不能用 viewScale 放大——那会让几百条半透明巨线铺满整张台呢，把主题色洗成灰。
        val texCount = 240
        val hi = Color.White.copy(alpha = 0.045f)
        val lo = Color.Black.copy(alpha = 0.05f)
        for (i in 0 until texCount) {
            val fx = hash01(i * 2 + 1)
            val fy = hash01(i * 2 + 2)
            val px = l + fx * (r - l)
            val py = t + fy * (b - t)
            val rnd = hash01(i * 5 + 3)
            val len = clothWeaveLength(ballR, rnd)
            val width = clothWeaveWidth(ballR)
            if (i % 2 == 0) {
                drawLine(hi, Offset(px - len * 0.5f, py), Offset(px + len * 0.5f, py), strokeWidth = width)
            } else {
                drawLine(lo, Offset(px, py - len * 0.5f), Offset(px, py + len * 0.5f), strokeWidth = width)
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

    // 袋口（盖在木框/台呢交界上）：黑洞按口线裁切成真实切口，只保留袋外半平面；
    // 木框收边/高光/内阴影/网线在同一裁剪内，口线处不再出现横跨洞口的描边。
    for (pocket in table.pockets) {
        val px = game.toCanvasX(pocket.x)
        val py = game.toCanvasY(pocket.y)
        // 黑洞填充半径覆盖整个口宽（角袋 1.15×捕获半径 ≥ 鼻点距 3.2 ballR），木缘沿用原视觉基准半径
        val fillR = max(pocket.radius * scale * 1.15f, ballR * 1.9f)
        val rimR = max(pocket.radius * scale * 1.05f, ballR * 1.9f)
        val mouth = pocketMouth(pocket, table)
        val clip =
            pocketClipPath(
                game.toCanvasX(mouth.noseAX),
                game.toCanvasY(mouth.noseAY),
                game.toCanvasX(mouth.noseBX),
                game.toCanvasY(mouth.noseBY),
                mouth.outwardX,
                mouth.outwardY,
                fillR * 3f,
            )
        clipPath(clip) {
            drawPocket(px, py, fillR, rimR, ballR, atan2(mouth.outwardY, mouth.outwardX))
        }
    }

    // 库边 facing（钳口）：把 12 段短斜边用库边色画在洞口上，连通「库边断开→斜口→洞」
    for (rail in table.rails) {
        if (!isPocketJaw(rail, table)) continue
        drawPocketJaw(
            game.toCanvasX(rail.x1),
            game.toCanvasY(rail.y1),
            game.toCanvasX(rail.x2),
            game.toCanvasY(rail.y2),
            ballR,
        )
    }

    // 库边菱形标记：上下短库各 3 个；左右长库每段 3 个（中袋 k==4 处无标记，与真实台一致）
    val dm = ballR * 0.40f
    for (k in 1..3) {
        val fx = l + (r - l) * k / 4f
        drawDiamond(fx, t - frame * 0.5f, dm)
        drawDiamond(fx, b + frame * 0.5f, dm)
    }
    for (k in 1..7) {
        if (k == 4) continue
        val fy = t + (b - t) * k / 8f
        drawDiamond(l - frame * 0.5f, fy, dm)
        drawDiamond(r + frame * 0.5f, fy, dm)
    }
}

/** 台呢主题的基础色（0..3 有效；越界回退默认湖蓝）。纯函数，便于单测四档差异。 */
internal fun clothThemeFeltColor(
    theme: Int,
    isDay: Boolean,
): Color =
    if (theme in clothDayThemes.indices) {
        Color(if (isDay) clothDayThemes[theme] else clothNightThemes[theme])
    } else if (isDay) {
        feltDayBase
    } else {
        feltNightBase
    }

/**
 * 台呢基色 = 主题色（或默认色）叠加场景冷色：非晴/多云场景把台面拉向冷色，
 * 让天气氛围进入台面；晴/多云保持主题原色。纯函数，便于单测天气下主题仍可区分。
 */
internal fun feltBase(
    style: WeatherSceneStyle,
    isDay: Boolean,
    theme: Int,
): Color {
    val base = clothThemeFeltColor(theme, isDay)
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

/** 织纹短线长度（px）：以球半径为尺度（约 2~8px @ 1080p），不随 viewScale 放大。 */
internal fun clothWeaveLength(
    ballR: Float,
    rnd: Float,
): Float = ballR * (0.08f + rnd * 0.21f)

/** 织纹线宽（px）：以球半径为尺度，不随 viewScale 放大。 */
internal fun clothWeaveWidth(ballR: Float): Float = ballR * 0.04f

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

/**
 * 袋口「口外半平面」裁剪多边形（canvas 坐标）：以两鼻点连线为界，向袋外方向
 * 与口线两端各外扩 [reach]，保证袋口圆/木缘外弧/网线只在袋外一侧可见。
 */
private fun pocketClipPath(
    noseAX: Float,
    noseAY: Float,
    noseBX: Float,
    noseBY: Float,
    outX: Float,
    outY: Float,
    reach: Float,
): Path {
    val dx = noseBX - noseAX
    val dy = noseBY - noseAY
    val len = hypot(dx, dy).coerceAtLeast(1e-6f)
    val ux = dx / len
    val uy = dy / len
    val path = PoolRenderCache.pocketClip
    path.reset()
    val aX = noseAX - ux * reach
    val aY = noseAY - uy * reach
    val bX = noseBX + ux * reach
    val bY = noseBY + uy * reach
    path.moveTo(aX, aY)
    path.lineTo(bX, bY)
    path.lineTo(bX + outX * reach, bY + outY * reach)
    path.lineTo(aX + outX * reach, aY + outY * reach)
    path.close()
    return path
}

/**
 * 单个袋口：木框收边 + 黑洞 + 网线 + 内阴影 + 内沿受光。
 * 调用方按 [PocketMouth] 做口外半平面裁剪，因此所有圆形元素在口线处被切断，
 * 不会再出现「贴一枚圆币」的整圈轮廓。
 *
 * [holeR] 为黑洞填充半径（完全覆盖口宽），[rimR] 为木缘视觉基准半径（沿用原比例），
 * [outAngle] 为袋外方向角（弧度），让黑洞最深点与内阴影偏向袋外一侧。
 */
private fun DrawScope.drawPocket(
    px: Float,
    py: Float,
    holeR: Float,
    rimR: Float,
    ballR: Float,
    outAngle: Float,
) {
    // 木框收边：只留口外一侧的外弧（口线处被 clip 切断）
    drawCircle(honeyWoodDark.copy(alpha = 0.95f), rimR * 1.26f, Offset(px, py))
    drawCircle(pocketRim.copy(alpha = 0.85f), rimR * 1.26f, Offset(px, py), style = Stroke(width = ballR * 0.14f))
    drawCircle(woodEdge.copy(alpha = 0.6f), rimR * 1.36f, Offset(px, py), style = Stroke(width = ballR * 0.10f))
    // 洞口外阴影：让袋口整体下沉
    drawCircle(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(px, py),
            radius = rimR * 1.30f,
        ),
        radius = rimR * 1.30f,
        center = Offset(px, py),
    )
    // 洞：径向渐变最深点沿袋外方向偏移，边到纯黑
    drawCircle(
        Brush.radialGradient(
            colors = listOf(pocketInk, pocketDeep, Color.Black),
            center = Offset(px + cos(outAngle) * holeR * 0.14f, py + sin(outAngle) * holeR * 0.14f),
            radius = holeR * 1.20f,
        ),
        radius = holeR,
        center = Offset(px, py),
    )
    // 袋网：从内壁向心的细网线 + 网底阴影（随黑洞一起被口线裁剪）
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
    val outDeg = outAngle * 180f / PI.toFloat()
    // 内阴影：袋外一侧的外弧下半圈更暗，强调洞壁下沉
    drawArc(
        color = Color.Black.copy(alpha = 0.55f),
        startAngle = outDeg - 70f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.22f),
    )
    // 洞口内沿受光：袋外一侧内壁极淡的一圈反光，增强下沉感
    drawArc(
        color = Color.White.copy(alpha = 0.07f),
        startAngle = outDeg - 55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(px - holeR * 0.94f, py - holeR * 0.94f),
        size = Size(holeR * 1.88f, holeR * 1.88f),
        style = Stroke(width = ballR * 0.10f),
    )
    // 口线内侧的布料/皮革反光：让「切口」边缘更清晰
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = outDeg + 110f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(px - holeR, py - holeR),
        size = Size(holeR * 2f, holeR * 2f),
        style = Stroke(width = ballR * 0.12f),
    )
}

/**
 * 钳口 facing：与库边同色的粗圆头短线，从库端鼻点沿钳口线段向袋外延伸，
 * 把「库边断开」与黑洞之间的斜面补上，连成一体。
 */
private fun DrawScope.drawPocketJaw(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    ballR: Float,
) {
    drawLine(
        cushionShade.copy(alpha = 0.92f),
        Offset(ax, ay),
        Offset(bx, by),
        strokeWidth = ballR * 0.62f,
        cap = StrokeCap.Round,
    )
    // 库端鼻点处的细微高光，与库鼻高光一致
    drawLine(
        Color.White.copy(alpha = 0.09f),
        Offset(ax, ay),
        Offset(bx, by),
        strokeWidth = ballR * 0.18f,
        cap = StrokeCap.Round,
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
    val key = brushKey(r, base.toArgb())
    val brush =
        sphereBaseBrushes[key]
            ?: Brush
                .radialGradient(
                    colors = listOf(lighten(base, 0.42f), base, darken(base, 0.55f)),
                    center = Offset(-r * 0.34f, -r * 0.36f),
                    radius = r * 1.52f,
                ).also { sphereBaseBrushes[key] = it }
    translate(cx, cy) {
        drawCircle(brush, radius = r, center = Offset.Zero, alpha = alpha)
    }
}

private fun DrawScope.drawSphereGloss(
    cx: Float,
    cy: Float,
    r: Float,
    alpha: Float,
    rimLight: Color,
) {
    val rimKey = brushKey(r, rimLight.toArgb())
    val shadeKey = radiusKey(r)
    val shade =
        sphereShadeBrushes[shadeKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.34f)),
                    center = Offset(-r * 0.30f, -r * 0.32f),
                    radius = r * 1.45f,
                ).also { sphereShadeBrushes[shadeKey] = it }
    val fresnel =
        sphereRimBrushes[rimKey]
            ?: Brush
                .radialGradient(
                    0.60f to Color.Transparent,
                    0.84f to rimLight.copy(alpha = 0.10f),
                    1.00f to rimLight.copy(alpha = 0.30f),
                    center = Offset.Zero,
                    radius = r,
                ).also { sphereRimBrushes[rimKey] = it }
    val ambient =
        sphereAmbientBrushes[rimKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(rimLight.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(r * 0.26f, r * 0.34f),
                    radius = r * 0.95f,
                ).also { sphereAmbientBrushes[rimKey] = it }
    val highlight =
        sphereHighlightBrushes[shadeKey]
            ?: Brush
                .radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(-r * 0.34f, -r * 0.37f),
                    radius = r * 0.44f,
                ).also { sphereHighlightBrushes[shadeKey] = it }
    val highlightCenter = Offset(-r * 0.34f, -r * 0.37f)

    translate(cx, cy) {
        // 边缘暗化 + 环境反光
        drawCircle(shade, radius = r, center = Offset.Zero, alpha = alpha)
        // 菲涅尔边缘环境光：贴边一圈柔和反光，让球体轮廓更立体
        drawCircle(fresnel, radius = r, center = Offset.Zero, alpha = alpha)
        drawCircle(ambient, radius = r, center = Offset.Zero, alpha = alpha)
        // 主高光：柔和光斑衰减 + 高亮小核
        drawCircle(highlight, radius = r * 0.44f, center = highlightCenter, alpha = alpha)
        drawCircle(Color.White.copy(alpha = 0.82f * alpha), r * 0.15f, highlightCenter)
        drawCircle(Color.White.copy(alpha = 0.28f * alpha), r * 0.075f, Offset(-r * 0.12f, -r * 0.20f))
    }
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

    // 局部坐标构建杆身（x 沿皮头→杆尾），笔刷按杆长缓存后用 translate/rotate 落到世界坐标
    val path = PoolRenderCache.shape
    path.reset()
    path.moveTo(0f, tipW)
    path.lineTo(stickLen, buttW)
    path.lineTo(stickLen, -buttW)
    path.lineTo(0f, -tipW)
    path.close()
    val stickDeg = atan2(backY, backX) * 180f / PI.toFloat()
    withTransform({
        translate(tipX, tipY)
        rotate(degrees = stickDeg, pivot = Offset.Zero)
    }) {
        drawPath(path, cueStickBrushes.get(stickLen))
    }
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
    val scale = game.viewScale
    val fc = PoolPhysics.predictFirstContact(game.balls, table, cue, game.aimAngle)
    if (fc != null && fc.target != null) {
        val ghostX = game.toCanvasX(fc.x)
        val ghostY = game.toCanvasY(fc.y)
        val target = fc.target
        // 首碰是否合法：非法时幽灵球 / 引导线 / 目标线统一切警示色
        val legalContact = PoolRules.isLegalFirstContact(game.match, game.balls, target)
        val ghostStroke = if (legalContact) ghostRing else aimWarnColor
        val ghostInnerStroke = if (legalContact) ghostInner else aimWarnColor.copy(alpha = 0.55f)
        val cueGuideColor = if (legalContact) aimLineColor else aimWarnColor
        val targetGuideColor = if (legalContact) targetLineColor else aimWarnColor
        val potArrowColor = if (legalContact) potLineColor else aimWarnColor
        val breakArrowColor = if (legalContact) cueBreakColor else aimWarnColor

        if (full) {
            drawGuideLine(
                Offset(cx, cy),
                Offset(ghostX, ghostY),
                cueGuideColor,
                ballR * 0.10f,
                ballR * 0.60f,
                ballR * 0.48f,
            )
        }

        // 幽灵球：外发光 + 高亮描边，命中点更醒目
        drawCircle(ghostFill, ballR, Offset(ghostX, ghostY))
        drawCircle(ghostStroke.copy(alpha = 0.20f), ballR * 1.16f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.18f))
        drawCircle(ghostStroke, ballR, Offset(ghostX, ghostY), style = Stroke(ballR * 0.14f))
        drawCircle(ghostInnerStroke, ballR * 0.55f, Offset(ghostX, ghostY), style = Stroke(ballR * 0.06f))

        // 目标球路径：预测到袋口 / 其它球 / 库边（与物理同一套捕获判定，画到哪就打到哪）
        val tvx = target.x - fc.x
        val tvy = target.y - fc.y
        val tl = hypot(tvx, tvy)
        if (tl > 1e-4f) {
            val ux = tvx / tl
            val uy = tvy / tl
            val startX = game.toCanvasX(target.x)
            val startY = game.toCanvasY(target.y)
            val path =
                PoolPhysics.predictObjectPath(
                    game.balls,
                    table,
                    target.x,
                    target.y,
                    ux,
                    uy,
                    target.radius,
                    target,
                )
            val pathEndX = game.toCanvasX(path.endX)
            val pathEndY = game.toCanvasY(path.endY)
            if (path.pocket != null) {
                // 预测落袋：实线 + 袋口高亮圈
                drawArrowLine(
                    Offset(startX, startY),
                    Offset(pathEndX, pathEndY),
                    potArrowColor,
                    ballR * 0.12f,
                    ballR * 0.90f,
                )
                val holeR = max(path.pocket.radius * scale * 1.15f, ballR * 1.9f)
                val holeX = game.toCanvasX(path.pocket.x)
                val holeY = game.toCanvasY(path.pocket.y)
                drawCircle(potRingColor.copy(alpha = 0.20f), holeR, Offset(holeX, holeY))
                drawCircle(potRingColor, holeR, Offset(holeX, holeY), style = Stroke(ballR * 0.16f))
            } else {
                drawGuideLine(
                    Offset(startX, startY),
                    Offset(pathEndX, pathEndY),
                    targetGuideColor,
                    ballR * 0.10f,
                    ballR * 0.60f,
                    ballR * 0.48f,
                )
                val blocker = path.blocker
                if (full && blocker != null) {
                    // 被其它球挡住：给出被撞球沿两球心连线的分离方向
                    val bx = blocker.x - path.endX
                    val by = blocker.y - path.endY
                    val bl = hypot(bx, by)
                    if (bl > 1e-4f) {
                        drawArrowLine(
                            Offset(pathEndX, pathEndY),
                            Offset(pathEndX + bx / bl * ballR * 7f, pathEndY + by / bl * ballR * 7f),
                            breakArrowColor,
                            ballR * 0.10f,
                            ballR * 0.70f,
                        )
                    }
                } else if (full && path.railDistance != null && path.distance > 1e-3f) {
                    val rail = nearestRail(table, path.endX, path.endY)
                    if (rail != null) {
                        val (nx, ny) = PoolPhysics.railInwardNormal(rail, table)
                        val dot = ux * nx + uy * ny
                        val rdx = ux - 2f * dot * nx
                        val rdy = uy - 2f * dot * ny
                        drawGuideLine(
                            Offset(pathEndX, pathEndY),
                            Offset(
                                game.toCanvasX(path.endX + rdx * ballR * 8f),
                                game.toCanvasY(path.endY + rdy * ballR * 8f),
                            ),
                            reflectLineColor,
                            ballR * 0.09f,
                            ballR * 0.60f,
                            ballR * 0.50f,
                        )
                    }
                }
            }

            if (full) {
                // 白球分离线（切线方向，符号取白球碰后实际切线速度方向）
                val (sepX, sepY) = cueSeparationDirection(ux, uy, cue.x, cue.y, fc.x, fc.y)
                // 沿白球分离方向预测白球路径：终点是袋口则预警「这一杆白球会落袋」
                val cuePath =
                    PoolPhysics.predictObjectPath(
                        game.balls,
                        table,
                        fc.x,
                        fc.y,
                        sepX,
                        sepY,
                        cue.radius,
                        cue,
                    )
                val scratchPocket = cuePath.pocket
                val sepColor = if (scratchPocket != null || !legalContact) aimWarnColor else cueBreakColor
                drawGuideLine(
                    Offset(ghostX, ghostY),
                    Offset(ghostX + sepX * ballR * 6f, ghostY + sepY * ballR * 6f),
                    sepColor,
                    ballR * 0.10f,
                    ballR * 0.55f,
                    ballR * 0.45f,
                )
                if (scratchPocket != null) {
                    val holeR = max(scratchPocket.radius * scale * 1.15f, ballR * 1.9f)
                    val holeX = game.toCanvasX(scratchPocket.x)
                    val holeY = game.toCanvasY(scratchPocket.y)
                    drawCircle(aimWarnColor.copy(alpha = 0.20f), holeR, Offset(holeX, holeY))
                    drawCircle(aimWarnColor, holeR, Offset(holeX, holeY), style = Stroke(ballR * 0.16f))
                }
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
