package com.xiaoxiong.weather.ui.game

import kotlin.math.hypot

/**
 * 袋口「口线」几何（纯函数，台面坐标系，无 Compose 依赖，可裸 JVM 单测）。
 *
 * 口线 = 两库端鼻点的连线：
 *  - 角袋：两库各沿库方向退 [PoolConfig.CORNER_MOUTH] 的鼻点连线（左上角即 (x+mouth, y) 与 (x, y+mouth)）；
 *  - 中袋：口线即库线本身，两鼻点为袋心沿库方向各 [PoolConfig.SIDE_MOUTH]/2 处。
 *
 * [outwardX]/[outwardY] 是口线的单位外法线（指向袋外/台外）。渲染时保留该侧
 * （袋心所在侧），把另一侧（台面侧）裁掉，袋口就从「贴圆币」变成沿口线切开的真实洞口。
 */
internal data class PocketMouth(
    val noseAX: Float,
    val noseAY: Float,
    val noseBX: Float,
    val noseBY: Float,
    val outwardX: Float,
    val outwardY: Float,
) {
    /** 点到口线的带符号外法向距离：>= 0 在保留侧（袋外），< 0 在台面侧（裁掉）。 */
    fun outwardDistance(
        x: Float,
        y: Float,
    ): Float = (x - noseAX) * outwardX + (y - noseAY) * outwardY
}

/** 计算 [pocket] 的口线两端鼻点与外法线；[table] 用于判断袋口朝向台心的一侧。 */
internal fun pocketMouth(
    pocket: PoolPocket,
    table: PoolTable,
): PocketMouth {
    val sx = if (pocket.x <= table.centerX) 1f else -1f
    val sy = if (pocket.y <= table.centerY) 1f else -1f
    return if (pocket.corner) {
        val mouth = PoolConfig.CORNER_MOUTH
        PocketMouth(
            noseAX = pocket.x + sx * mouth,
            noseAY = pocket.y,
            noseBX = pocket.x,
            noseBY = pocket.y + sy * mouth,
            outwardX = -sx * INV_SQRT2,
            outwardY = -sy * INV_SQRT2,
        )
    } else {
        val half = PoolConfig.SIDE_MOUTH * 0.5f
        PocketMouth(
            noseAX = pocket.x,
            noseAY = pocket.y - half,
            noseBX = pocket.x,
            noseBY = pocket.y + half,
            outwardX = -sx,
            outwardY = 0f,
        )
    }
}

/**
 * 钳口（jaw）线段判定：短线段且两端点都落在同一个袋口的收口范围内。
 * 用于从 [PoolTable.rails] 里挑出 12 段钳口，避免把 6 段主库当钳口画。
 */
internal fun isPocketJaw(
    rail: PoolRail,
    table: PoolTable,
): Boolean {
    val length = hypot(rail.x2 - rail.x1, rail.y2 - rail.y1)
    if (length > table.ballRadius * 2f) return false
    return table.pockets.any { pocket ->
        val reach = pocket.radius * 1.5f
        hypot(rail.x1 - pocket.x, rail.y1 - pocket.y) <= reach &&
            hypot(rail.x2 - pocket.x, rail.y2 - pocket.y) <= reach
    }
}

private const val INV_SQRT2 = 0.70710677f
