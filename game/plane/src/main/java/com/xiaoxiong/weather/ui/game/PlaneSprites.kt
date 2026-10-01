package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 精灵烘焙缓存（玩家机 / 10 种敌机 / Boss 三阶段 / 道具）。
 *
 * 键含「类型（kind/variant）+ 尺寸（像素半径取整）+ 阵营 / 昼夜」，同一键只烘焙一次；
 * 新增敌人类型通过 [PlaneEnemyType.ordinal] 进入 variant，缓存键天然区分新类型。
 * 小图（最长边约 2×290px）做 LRU 限制常驻，先淘汰最旧再分配，分配 / 绘制失败静默降级。
 * 仅主线程绘制，无需加锁。
 */
internal data class PlaneSpriteKey(
    val kind: Int,
    val variant: Int,
    val radiusPx: Int,
    val isDay: Boolean,
)

internal class PlaneSprite(
    val bitmap: ImageBitmap,
    val half: Float,
    val key: PlaneSpriteKey,
)

internal object PlaneSprites {
    const val KIND_PLAYER = 0
    const val KIND_ENEMY = 1
    const val KIND_POWERUP = 2

    private const val MAX_ENTRIES = 40

    private val entries = ArrayList<PlaneSprite>(MAX_ENTRIES)

    /** 玩家机：[radiusPx] 为机身视觉半径（像素）；[tint] 写入 variant。 */
    fun player(
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
        tint: PlaneShipTint = PlaneShipTint.CLASSIC,
    ): PlaneSprite? = sprite(KIND_PLAYER, tint.ordinal, radiusPx, isDay, 1.30f, density, layoutDirection)

    /**
     * 敌机：[variant] = 类型序号×4+阶段；Boss 用独立高位区间
     * `1000 + kind×4 + phase`，避免与小兵 ordinal 冲突。
     */
    fun enemy(
        type: PlaneEnemyType,
        bossPhase: Int,
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
        bossKind: PlaneBossKind = PlaneBossKind.STANDARD,
    ): PlaneSprite? {
        val variant =
            if (type == PlaneEnemyType.BOSS) {
                1000 + bossKind.ordinal * 4 + bossPhase.coerceIn(0, 2)
            } else {
                type.ordinal * 4 + bossPhase.coerceIn(0, 2)
            }
        val extent = if (type == PlaneEnemyType.BOSS) 1.30f else 1.22f
        return sprite(KIND_ENEMY, variant, radiusPx, isDay, extent, density, layoutDirection)
    }

    /** 道具（P / B / S / L）。 */
    fun powerUp(
        kind: PlanePowerUpKind,
        radiusPx: Float,
        isDay: Boolean,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? = sprite(KIND_POWERUP, kind.ordinal, radiusPx, isDay, 1.45f, density, layoutDirection)

    private fun sprite(
        kind: Int,
        variant: Int,
        radiusPx: Float,
        isDay: Boolean,
        extent: Float,
        density: Density,
        layoutDirection: LayoutDirection,
    ): PlaneSprite? {
        if (!radiusPx.isFinite() || radiusPx < 1f || radiusPx > 2048f) return null
        val keyR = round(radiusPx).toInt().coerceAtLeast(1)
        // 命中路径按字段比较，不构造临时 Key（每帧每实体调用一次，避免热路径分配）。
        var i = entries.size - 1
        while (i >= 0) {
            val e = entries[i]
            if (e.key.kind == kind && e.key.variant == variant && e.key.radiusPx == keyR && e.key.isDay == isDay) {
                entries.removeAt(i)
                entries.add(e)
                return e
            }
            i--
        }
        val key = PlaneSpriteKey(kind, variant, keyR, isDay)
        val half = keyR * extent
        val side = ceil(half * 2f).toInt().coerceAtLeast(2)
        // 先淘汰最旧再分配：控制位图常驻量，分配失败可直接降级为不绘制精灵
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        val bitmap = runCatching { ImageBitmap(side, side) }.getOrNull() ?: return null
        runCatching {
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
                translate(half, half) {
                    when (kind) {
                        KIND_PLAYER ->
                            drawPlayerArt(
                                half / 1.30f,
                                PlaneShipTint.entries.getOrNull(variant) ?: PlaneShipTint.CLASSIC,
                            )
                        KIND_ENEMY -> drawEnemyArt(variant, half)
                        else -> drawPowerUpArt(variant, half / 1.45f)
                    }
                }
            }
        }.onFailure { return null }
        val sprite = PlaneSprite(bitmap, half, key)
        entries.add(sprite)
        return sprite
    }

    fun clear() {
        entries.clear()
    }
}

/** 受击闪白用的固定白色滤镜：ColorFilter.tint 每次调用都会新建 native 对象，热路径必须复用。 */
private val planeWhiteTint: ColorFilter by lazy { ColorFilter.tint(Color.White) }

/**
 * 把烘焙精灵画进中心点（[cx], [cy]），可选旋转 / 压扁 / 闪白。
 * 热路径只有 drawImage，不分配 Path/Brush。
 */
internal fun DrawScope.drawPlaneSprite(
    sprite: PlaneSprite,
    cx: Float,
    cy: Float,
    rotationDeg: Float = 0f,
    scaleY: Float = 1f,
    alpha: Float = 1f,
    flashAlpha: Float = 0f,
) {
    val side = sprite.bitmap.width
    val left = cx - sprite.half
    val top = cy - sprite.half
    if (rotationDeg == 0f && scaleY == 1f) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(side, side),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    } else {
        withTransform(
            {
                rotate(degrees = rotationDeg, pivot = Offset(cx, cy))
                scale(scaleX = 1f, scaleY = scaleY, pivot = Offset(cx, cy))
            },
        ) {
            drawImage(
                image = sprite.bitmap,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(side, side),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
    if (flashAlpha > 0.01f) {
        drawImage(
            image = sprite.bitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(side, side),
            alpha = flashAlpha.coerceIn(0f, 1f),
            colorFilter = planeWhiteTint,
            filterQuality = FilterQuality.Low,
        )
    }
}

/** 道具精灵的自发光脉冲（与精灵叠加，不参与烘焙；[reduceMotion] 时冻结静态亮度）。 */
internal fun DrawScope.drawPowerUpGlow(
    cx: Float,
    cy: Float,
    r: Float,
    kind: PlanePowerUpKind,
    tick: Long,
    reduceMotion: Boolean,
) {
    val color = planePowerUpColor(kind)
    val pulse = if (reduceMotion) 0.5f else 0.5f + 0.5f * sin(tick * 0.16f)
    drawCircle(color.copy(alpha = 0.10f + 0.10f * pulse), r * 1.6f, Offset(cx, cy))
}

/** 道具主题色：P 绿 / B 琥珀 / S 冰蓝 / L 玫红。 */
internal fun planePowerUpColor(kind: PlanePowerUpKind): Color =
    when (kind) {
        PlanePowerUpKind.WEAPON -> PlaneUi.ok
        PlanePowerUpKind.BOMB -> Color(0xFFFFD740)
        PlanePowerUpKind.SHIELD -> Color(0xFF4FC3F7)
        PlanePowerUpKind.LIFE -> Color(0xFFFF80AB)
    }

/** 弹幕 / 特效用的复用描边：按 0.1px 取整宽度缓存（同一设备宽度集合有限，热路径零分配）。 */
internal object PlaneStroke {
    private const val MAX_ENTRIES = 40

    // 插入序 LinkedHashMap：缓存满时淘汰最旧，而不是整表 clear（避免连带清掉常用宽度）
    private val cache = LinkedHashMap<Int, Stroke>(MAX_ENTRIES)

    fun of(width: Float): Stroke {
        val key = round(width * 10f).toInt()
        cache[key]?.let { return it }
        if (cache.size >= MAX_ENTRIES) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) cache.remove(oldest)
        }
        return Stroke(key / 10f).also { cache[key] = it }
    }
}

/** 确定性伪随机 0..1（同一 (i,salt) 永远同值）。 */
internal fun planeHash01(
    i: Int,
    salt: Int = 0,
): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}

/** 正模运算：结果恒落在 [0, span)。 */
internal fun planeMod(
    value: Float,
    span: Float,
): Float {
    if (span <= 0f) return 0f
    val r = value % span
    return if (r < 0f) r + span else r
}
