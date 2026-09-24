package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 静态层烘焙结果。[key] 是实际命中的缓存键：走近似复用时可能与请求键不同，
 * 仅用于诊断与调试展示。
 */
class BakedLayer<K : Any>(
    val key: K,
    val image: ImageBitmap,
)

/**
 * 静态层烘焙缓存：把"只依赖 key、不随时间变化"的内容预渲染成一张位图，
 * 之后每帧只做一次 drawImage。
 *
 * 适用于台面、迷宫棋盘、田地底纹这类静态内容；粒子、角色、天气等动态元素
 * 必须在烘焙函数之外绘制。
 *
 * 内存约定（对齐冰球 / 台球 / 迷宫现有做法）：
 *  - 默认只缓存 [maxEntries] 张，典型 1~2 张；
 *  - 分配新位图前先淘汰最旧条目，避免"旧图 + 新图"峰值双份常驻；
 *  - 烘焙失败（OOM 等）返回 null 且不入缓存，调用方回退直接绘制，不影响可玩性。
 */
class BakedLayerCache<K : Any>(
    maxEntries: Int = 2,
    /** 近似复用判定；默认要求完全相等。纯尺寸类键可放宽以复用旧图。 */
    reusable: (cached: K, target: K) -> Boolean = { cached, target -> cached == target },
) {
    private val lru = BakedLayerLru<K, BakedLayer<K>>(maxEntries, reusable)

    /** 当前缓存条目数（测试 / 诊断用）。 */
    val size: Int get() = lru.size

    /**
     * 命中则返回缓存层；未命中则烘焙一张位图。
     *
     * [widthPx]/[heightPx] 是位图像素尺寸（按需 ceil），
     * [logicalWidth]/[logicalHeight] 是绘制坐标系尺寸——两者分开传入，
     * 使烘焙出的图与"直接绘制"坐标完全一致，位图只多出边缘空像素。
     *
     * [bake] 收到与逻辑尺寸同大的 [DrawScope]，坐标原点即位图左上角。
     */
    fun getOrBake(
        key: K,
        widthPx: Int,
        heightPx: Int,
        logicalWidth: Float,
        logicalHeight: Float,
        density: Density,
        layoutDirection: LayoutDirection,
        bake: DrawScope.() -> Unit,
    ): BakedLayer<K>? {
        if (widthPx <= 0 || heightPx <= 0) return null
        lru.get(key)?.let { return it }
        val image =
            runCatching {
                val bitmap = ImageBitmap(widthPx, heightPx)
                CanvasDrawScope().draw(
                    density = density,
                    layoutDirection = layoutDirection,
                    canvas = Canvas(bitmap),
                    size = Size(logicalWidth, logicalHeight),
                    block = bake,
                )
                bitmap
            }.getOrNull() ?: return null
        val layer = BakedLayer(key = key, image = image)
        lru.put(key, layer)
        return layer
    }

    /** 直接写入已烘焙好的层（如精灵图）。 */
    fun put(
        key: K,
        image: ImageBitmap,
    ) {
        lru.put(key, BakedLayer(key, image))
    }

    /** 按 key 查找（精确命中优先，必要时近似复用）。 */
    fun get(key: K): BakedLayer<K>? = lru.get(key)

    /** 释放全部位图引用（退出对局时调用）。 */
    fun clear() = lru.clear()
}
