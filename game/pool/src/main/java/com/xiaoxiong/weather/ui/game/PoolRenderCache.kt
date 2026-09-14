package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

internal val feltDayBase = Color(0xFF2C9E8C)
internal val feltNightBase = Color(0xFF1D6C66)

/** 非晴/多云场景叠加的冷色（internal 供单测核对叠加比例，见 PoolClothThemeTest）。 */
internal val feltColdTint = Color(0xFF3D6E9C)

/** 可选台呢主题：0 经典湖蓝 / 1 蜂蜜棕 / 2 午夜墨绿 / 3 樱花粉。 */
internal val clothDayThemes =
    intArrayOf(0xFF2C9E8C.toInt(), 0xFF8A5A2B.toInt(), 0xFF1F5A46.toInt(), 0xFFC97A96.toInt())
internal val clothNightThemes =
    intArrayOf(0xFF1D6C66.toInt(), 0xFF5A3A1B.toInt(), 0xFF123A2C.toInt(), 0xFF8A4C63.toInt())

internal val honeyWoodLight = Color(0xFFC0894A)
internal val honeyWoodMid = Color(0xFF9B6533)
internal val honeyWoodDark = Color(0xFF5E3A1C)
internal val woodEdge = Color(0xFF3A2210)
internal val diamondIvory = Color(0xFFF2E2C2)
internal val cushionShade = Color(0xFF0C4640)
internal val pocketInk = Color(0xFF020202)
internal val pocketDeep = Color(0xFF14100B)
internal val pocketRim = Color(0xFF8A5A2C)
internal val pocketNetLine = Color(0xFF0B0906).copy(alpha = 0.70f)
internal val woodGrainDark = Color.Black.copy(alpha = 0.07f)
internal val woodGrainLight = Color.White.copy(alpha = 0.05f)
internal val cushionSeam = Color.Black.copy(alpha = 0.14f)
internal val curveHintColor = Color(0xFF80DEEA)

internal val cueWhite = Color(0xFFF7F4EC)
internal val stripeWhite = Color(0xFFF3EFE6)
internal val eightBlack = Color(0xFF17161A)
internal val cueTipBlue = Color(0xFF2B4C7E)
internal val cueWoodLight = Color(0xFFE2C08D)
internal val cueWoodMid = Color(0xFFC1934F)
internal val cueWoodDark = Color(0xFF6E4321)
internal val cueFerrule = Color(0xFFF2EFE6)

internal val aimLineColor = Color.White.copy(alpha = 0.50f)
internal val targetLineColor = Color(0xFFFFF176).copy(alpha = 0.85f)
internal val potLineColor = Color(0xFF9CF29C)
internal val potRingColor = Color(0xFF7DE39A)
internal val cueBreakColor = Color(0xFF90CAF9).copy(alpha = 0.62f)
internal val reflectLineColor = Color.White.copy(alpha = 0.26f)
internal val ghostFill = Color.White.copy(alpha = 0.24f)
internal val ghostRing = Color.White.copy(alpha = 0.78f)
internal val ghostInner = Color(0xFFFFF59D).copy(alpha = 0.55f)
internal val placeRing = Color(0xFFFFE082)

/** 瞄准警示色：首碰非法或白球将落袋时的辅助线与幽灵球描边。 */
internal val aimWarnColor = Color(0xFFE57373)

internal val weatherRainTint = Color(0xFF29364A).copy(alpha = 0.10f)
internal val weatherStormTint = Color(0xFF151B26).copy(alpha = 0.22f)
internal val weatherSnowTint = Color(0xFFB9C7D6).copy(alpha = 0.09f)
internal val weatherHailTint = Color(0xFF141B28).copy(alpha = 0.24f)
internal val weatherFreezingTint = Color(0xFF5E7390).copy(alpha = 0.16f)
internal val weatherFogTint = Color(0xFFD7DEE3).copy(alpha = 0.10f)

/** 1..7 的全色色相；花色球用 (number - 1) % 8 取色。 */
internal val ballHues =
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

internal object PoolRenderCache {
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
internal class BrushCache(
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
internal fun brushKey(
    radius: Float,
    colorArgb: Int,
): Long = (colorArgb.toLong() shl 32) or (radius.toRawBits().toLong() and 0xFFFFFFFFL)

/** 仅按半径缓存（颜色固定的渐变）。 */
internal fun radiusKey(radius: Float): Long = radius.toRawBits().toLong()

internal val sphereBaseBrushes = BrushCache(capacity = 16)
internal val sphereShadeBrushes = BrushCache(capacity = 4)
internal val sphereRimBrushes = BrushCache(capacity = 8)
internal val sphereAmbientBrushes = BrushCache(capacity = 8)
internal val sphereHighlightBrushes = BrushCache(capacity = 4)

/** 球杆木质线性渐变缓存：局部坐标下沿杆长，绘制时旋转平移。 */
internal class CueStickBrushCache {
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

internal val cueStickBrushes = CueStickBrushCache()

/** 场景整屏渐变 / 太阳光晕缓存（style、昼夜或画布尺寸不变时复用）。 */
internal class SceneBrushCache {
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

internal val sceneBrushes = SceneBrushCache()

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
internal class PoolTableLayer(
    val bitmap: ImageBitmap,
    val key: PoolLayerKey,
)

internal object PoolTableLayerCache {
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

internal fun DrawScope.drawDashedLine(
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
