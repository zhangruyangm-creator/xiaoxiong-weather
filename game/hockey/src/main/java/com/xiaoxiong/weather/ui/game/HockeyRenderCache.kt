package com.xiaoxiong.weather.ui.game

import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.xiaoxiong.weather.ui.weather.WeatherSceneStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

// ---------------------------------------------------------------------
// 复用缓存：Path / Paint / Stroke
// ---------------------------------------------------------------------

internal object HockeyRenderCache {
    val icePath = Path()
    val recessPath = Path()
    val shape = Path()
    val shard = Path()
    val stitchPath = Path()
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
internal fun strokeOf(width: Float): Stroke {
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
 * 静态层缓存键：画布位图尺寸 + 球桌几何 + 场景样式。纯数据（不含位图），
 * 便于把「近似复用」条件抽成纯函数做单测。
 */
internal data class HockeyLayerKey(
    val width: Int,
    val height: Int,
    val tableLeft: Float,
    val tableTop: Float,
    val tableWidth: Float,
    val tableHeight: Float,
    val goalWidth: Float,
    val theme: RinkTheme,
    val style: WeatherSceneStyle,
    val isDay: Boolean,
)

/**
 * 场地静态层（桌框 / 主题地面 / 标记 / 库边 / 球门洞与网）：几何固定，
 * 材质与配色由 [RinkTheme] 决定，切主题时按新键重新烘焙。
 * 每帧不变，烘焙成一张整屏 [ImageBitmap]，每帧一次 drawImage。
 * 仅主线程绘制，无需加锁；最多保留 [HockeyTableLayerCache.MAX_ENTRIES] 张做 LRU。
 */
internal class HockeyTableLayer(
    val bitmap: ImageBitmap,
    val key: HockeyLayerKey,
)

/** 位图尺寸容差（窗口连续拖动时不逐像素重建）。 */
private const val HOCKEY_LAYER_RESIZE_TOLERANCE_PX = 24

/** 球桌几何容差（约 1px）：超出必须重建，否则错位球桌会随缓存残留整局。 */
private const val HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX = 1f

/** 球门宽容差。 */
private const val HOCKEY_LAYER_GOAL_TOLERANCE = 2f

/**
 * 静态层是否可近似复用：主题 / 样式 / 昼夜一致，位图尺寸在容差内，且**球桌几何一致**
 * （left/top/width/height 各自容差约 1px）。任一不一致必须重建。
 */
internal fun hockeyLayerKeyReusable(
    cached: HockeyLayerKey,
    target: HockeyLayerKey,
): Boolean =
    cached.theme == target.theme &&
        cached.style == target.style &&
        cached.isDay == target.isDay &&
        abs(cached.goalWidth - target.goalWidth) <= HOCKEY_LAYER_GOAL_TOLERANCE &&
        abs(cached.tableLeft - target.tableLeft) <= HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX &&
        abs(cached.tableTop - target.tableTop) <= HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX &&
        abs(cached.tableWidth - target.tableWidth) <= HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX &&
        abs(cached.tableHeight - target.tableHeight) <= HOCKEY_LAYER_GEOMETRY_TOLERANCE_PX &&
        abs(cached.width - target.width) <= HOCKEY_LAYER_RESIZE_TOLERANCE_PX &&
        abs(cached.height - target.height) <= HOCKEY_LAYER_RESIZE_TOLERANCE_PX

internal object HockeyTableLayerCache {
    const val MAX_ENTRIES = 2
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
        val key = HockeyLayerKey(w, h, t.left, t.top, t.width, t.height, t.goalWidth, game.rinkTheme, style, isDay)

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
        // 窗口连续拖动时避免逐像素重建整屏位图：尺寸相近且几何一致时复用并拉伸绘制
        i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (hockeyLayerKeyReusable(e.key, key)) return e
            i--
        }

        val bitmap = ImageBitmap(w, h)
        // 用原始浮点 size 布局，保证坐标与直接绘制一致；位图取 ceil 仅多出边缘空像素
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
            drawHockeyTableStatic(game, style, isDay)
        }
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(HockeyTableLayer(bitmap, key))
        return entries.last()
    }

    fun clear() {
        entries.clear()
    }
}

// ---------------------------------------------------------------------
// 挡板 / 蜜罐精灵图缓存
// ---------------------------------------------------------------------

internal class HockeySprite(
    val bitmap: ImageBitmap,
    val half: Float,
    val kind: Int,
    val radius: Float,
    val paletteId: Int,
)

internal object HockeySpriteCache {
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
        // 最外层投影椭圆右/下边缘到 1.48r：烘焙范围留到 1.55r，避免裁掉阴影
        val half = keyR * 1.55f
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

    fun clear() {
        entries.clear()
    }
}

/**
 * 释放全屏静态层与挡板/蜜罐精灵缓存：退出游戏时调用，避免 2 张全屏位图 + 精灵常驻；
 * 二次进入会重新烘焙，不影响正确性。
 */
internal fun clearHockeyRenderCaches() {
    HockeyTableLayerCache.clear()
    HockeySpriteCache.clear()
}

/** 确定性伪随机 [0,1]：同一 index 每帧结果一致，冰面纹理不闪烁。 */
internal fun hockeyHash01(n: Int): Float {
    var h = n * 374761393 + 668265263
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}
