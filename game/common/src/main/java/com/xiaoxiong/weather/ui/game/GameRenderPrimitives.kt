package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.round

/**
 * 小型 LRU 容器：用于静态层 / 精灵的烘焙结果缓存（对齐冰球 / 台球的静态层缓存写法）。
 *
 * 语义：
 *  - [get] 先精确命中（命中后提升到最新），再按 [reusable] 近似命中（近似命中不刷新热度）；
 *  - [getOrPut] 未命中时**先淘汰最旧再调用 [bake]**，避免烘焙瞬间「旧图 + 新图」双份常驻
 *    （全屏静态层内存峰值敏感）；
 *  - 容量为 0 时只烘焙不入缓存（便于单测 / 关闭缓存）。
 *
 * 仅主线程绘制路径使用，无锁；容量为个位数时线性扫描足够快。
 */
class BakedLayerLru<K : Any, V : Any>(
    private val maxEntries: Int,
    private val reusable: (cached: K, target: K) -> Boolean = { cached, target -> cached == target },
) {
    private class Entry<K, V>(
        val key: K,
        val value: V,
    )

    private val entries = ArrayList<Entry<K, V>>()

    /** 当前缓存条目数。 */
    val size: Int get() = entries.size

    /** 缓存键列表，按「最旧 → 最新」排列（仅供测试 / 诊断）。 */
    val keys: List<K> get() = entries.map { it.key }

    /**
     * 查找可复用结果：先精确命中并提升到最新，再按 [reusable] 近似命中（不刷新热度）。
     * 未命中返回 null。
     */
    fun get(target: K): V? {
        var i = entries.size - 1
        while (i >= 0) {
            val entry = entries[i]
            if (entry.key == target) {
                entries.removeAt(i)
                entries.add(entry)
                return entry.value
            }
            i--
        }
        // 近似复用（如全屏静态层尺寸容差）保持原位置，语义与 hockey/pool 现有实现一致
        for (entry in entries) {
            if (reusable(entry.key, target)) return entry.value
        }
        return null
    }

    /** 未命中时先淘汰最旧条目（腾出内存）再烘焙；[bake] 返回 null 时不入缓存。 */
    fun getOrPut(
        target: K,
        bake: (K) -> V?,
    ): V? {
        get(target)?.let { return it }
        if (maxEntries <= 0) return bake(target)
        while (entries.size >= maxEntries) entries.removeAt(0)
        val value = bake(target) ?: return null
        entries.add(Entry(target, value))
        return value
    }

    /** 直接写入烘焙结果；同 key 覆盖并提升到最新，超出容量淘汰最旧。 */
    fun put(
        target: K,
        value: V,
    ) {
        if (maxEntries <= 0) return
        var i = entries.size - 1
        while (i >= 0) {
            if (entries[i].key == target) entries.removeAt(i)
            i--
        }
        while (entries.size >= maxEntries) entries.removeAt(0)
        entries.add(Entry(target, value))
    }

    /** 清空缓存（退出对局时释放位图 / 精灵）。 */
    fun clear() {
        entries.clear()
    }
}

/** 描边宽度量化精度：0.1px，避免浮点尾差导致同一视觉宽度反复建对象。 */
internal fun quantizeStrokeWidth(width: Float): Float = round(max(width, 0.1f) * 10f) / 10f

private class SharedStrokeSlot {
    var stroke: Stroke? = null
    var width = Float.NaN
}

private val sharedStrokeSlotA = SharedStrokeSlot()
private val sharedStrokeSlotB = SharedStrokeSlot()

/**
 * 量化宽度后的 [Stroke] 小缓存（最近两个宽度，命中即复用）：
 * 与迷宫 `mazeStroke` 同款的 2 槽实现，统一供各游戏热路径描边绘制使用，
 * 避免每帧为同一宽度反复分配 [Stroke]。
 */
fun strokeOf(width: Float): Stroke {
    val w = quantizeStrokeWidth(width)
    val a = sharedStrokeSlotA
    if (a.stroke != null && a.width == w) return a.stroke!!
    val b = sharedStrokeSlotB
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

/**
 * 确定性伪随机 `[0, 1]`：同一 `(i, salt)` 永远同值，避免逐帧抖动 / 纹理规律感。
 *
 * 与 `GameWeatherFx.hash01` / `MazeThemeArt.mazeHash01` 的 **ushr 变体**逐位一致。
 * 注意：台球 `PoolTableStatic.hash01` 与冰球 `hockeyHash01` 历史上用的是 `shr`（算术右移）变体，
 * 与本函数在溢出样例上不同；迁移这些调用点前必须做逐位对拍，不能直接等价替换。
 */
fun gameHash01(
    i: Int,
    salt: Int = 0,
): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}
