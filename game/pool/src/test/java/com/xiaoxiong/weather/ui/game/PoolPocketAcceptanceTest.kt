package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 袋口接受度回归：按「幽灵球」瞄准（辅助线给出的方向）出杆，直球与切球都应稳定落袋。
 *
 * 背景：曾有 ①中袋错放在短库、②角袋口只有约 1.45 个球直径（真实台约 2.2 个）、
 * ③中袋捕获半径仅 0.78 球半径 三处问题，导致辅助线对准洞口却打不进。
 */
class PoolPocketAcceptanceTest {
    private val table = PoolTable.standard()

    private fun unit(
        fx: Float,
        fy: Float,
        tx: Float,
        ty: Float,
    ): Pair<Float, Float> {
        val dx = tx - fx
        val dy = ty - fy
        val l = hypot(dx, dy).coerceAtLeast(1e-6f)
        return dx / l to dy / l
    }

    /** 在 [pocket] 自然方向 [dist] 处放目标球，横向偏移 [lateral] 个球半径，按幽灵球瞄准出杆。 */
    private fun shootAt(
        pocket: PoolPocket,
        dist: Float,
        lateral: Float,
        cut: Float,
        power: Float = 0.6f,
    ): Boolean {
        val radius = PoolConfig.BALL_RADIUS
        val (ux, uy) = unit(pocket.x, pocket.y, table.centerX, table.centerY)
        val bx = pocket.x + ux * dist - uy * lateral * radius
        val by = pocket.y + uy * dist + ux * lateral * radius
        val (gx, gy) = unit(pocket.x, pocket.y, bx, by)
        val ghostX = bx + gx * radius * 2f
        val ghostY = by + gy * radius * 2f
        val towardBall = atan2(by - ghostY, bx - ghostX)
        val cueAngle = towardBall + cut
        val cue =
            PoolBall(
                0,
                PoolBallKind.CUE,
                ghostX - cos(cueAngle) * 0.4f,
                ghostY - sin(cueAngle) * 0.4f,
            )
        val ball = PoolBall(1, PoolBallKind.SOLID, bx, by)
        val aim = atan2(ghostY - cue.y, ghostX - cue.x)
        val speed = PoolConfig.shotSpeedMps(power)
        cue.vx = cos(aim) * speed
        cue.vy = sin(aim) * speed
        val balls = listOf(cue, ball)
        var steps = 0
        while (steps < 24000 && !PoolPhysics.settled(balls)) {
            PoolPhysics.step(balls, table)
            steps++
        }
        return ball.potted
    }

    @Test
    fun straightShots_potOnEveryPocket() {
        for ((index, pocket) in table.pockets.withIndex()) {
            for (dist in listOf(0.3f, 0.6f)) {
                for (lateral in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
                    assertTrue(
                        "pocket $index corner=${pocket.corner} dist=$dist lateral=$lateral",
                        shootAt(pocket, dist, lateral, cut = 0f),
                    )
                }
            }
        }
    }

    @Test
    fun cutShots_potOnEveryPocket() {
        for ((index, pocket) in table.pockets.withIndex()) {
            for (cut in listOf(-0.35f, -0.15f, 0.15f, 0.35f)) {
                assertTrue(
                    "pocket $index corner=${pocket.corner} cut=$cut",
                    shootAt(pocket, dist = 0.5f, lateral = 0f, cut = cut),
                )
            }
        }
    }

    @Test
    fun guidePrediction_matchesPocket() {
        val radius = PoolConfig.BALL_RADIUS
        for ((index, pocket) in table.pockets.withIndex()) {
            val (ux, uy) = unit(pocket.x, pocket.y, table.centerX, table.centerY)
            val bx = pocket.x + ux * 0.4f
            val by = pocket.y + uy * 0.4f
            val (gx, gy) = unit(bx, by, pocket.x, pocket.y)
            val path = PoolPhysics.predictObjectPath(listOf(), table, bx, by, gx, gy, radius, exclude = null)
            assertNotNull("pocket $index should be predicted as pot", path.pocket)
            assertTrue("pocket $index predicted to same pocket", table.pockets[path.pocketIndex] == pocket)
        }
    }

    /**
     * 反向一致性：幽灵球瞄准袋心、真实物理能进的直球，预测必须提示 pocket（不得显示为碰库）。
     *
     * 覆盖 6 个袋口 × 距离网格 × 入射角网格：浅擦库边（含 ≤18° 平行滚入）是历史误判高发区，
     * 由钳口/库端擦碰后的反射二次判定修复。
     */
    @Test
    fun physicallyPottedGhostBallShots_arePredictedAsPocket() {
        val radius = PoolConfig.BALL_RADIUS
        var physicallyPotted = 0
        var samples = 0
        for ((index, pocket) in table.pockets.withIndex()) {
            val towardCenter = atan2(table.centerY - pocket.y, table.centerX - pocket.x)
            for (offDeg in -20..20 step 5) {
                for (dist in listOf(0.25f, 0.4f, 0.6f)) {
                    val around = towardCenter + PoolMath.radians(offDeg.toFloat())
                    val ox = pocket.x + cos(around) * dist
                    val oy = pocket.y + sin(around) * dist
                    if (!table.contains(ox, oy, margin = -radius)) continue
                    samples++
                    val aim = atan2(pocket.y - oy, pocket.x - ox)
                    val dx = cos(aim)
                    val dy = sin(aim)
                    val path = PoolPhysics.predictObjectPath(listOf(), table, ox, oy, dx, dy, radius, exclude = null)
                    val ball = PoolBall(1, PoolBallKind.SOLID, ox, oy)
                    ball.vx = dx * 2.5f
                    ball.vy = dy * 2.5f
                    PoolPhysics.simulateUntilSettled(listOf(ball), table, maxSeconds = 6f)
                    if (!ball.potted) continue
                    physicallyPotted++
                    assertTrue(
                        "physical pot must be predicted as pocket: pocket=$index dist=$dist offDeg=$offDeg",
                        path.pocket != null,
                    )
                }
            }
        }
        println("Pocket reverse regression: physicallyPotted=$physicallyPotted/$samples")
        assertTrue("physically potted samples=$physicallyPotted/$samples", physicallyPotted >= 100)
    }

    /**
     * 一致性回归：凡是 [PoolPhysics.predictObjectPath] 提示 `pocket != null` 的路径，
     * 按同一方向真实推演必须落袋。擦边路径已由保守系数 + 球半径扫掠库边过滤，不再谎报。
     */
    @Test
    fun predictedPocketPaths_actuallyPotInRealPhysics() {
        val radius = PoolConfig.BALL_RADIUS
        val rng = Random(20240913L)
        var predicted = 0
        var checked = 0
        while (checked < 200) {
            checked++
            val pocket = table.pockets[checked % table.pockets.size]
            val towardCenter = atan2(table.centerY - pocket.y, table.centerX - pocket.x)
            val around = towardCenter + (rng.nextFloat() - 0.5f) * 0.6f
            val distance = 0.25f + rng.nextFloat() * 0.55f
            val ox = pocket.x + cos(around) * distance
            val oy = pocket.y + sin(around) * distance
            if (!table.contains(ox, oy, margin = -radius)) continue
            val aim = atan2(pocket.y - oy, pocket.x - ox) + (rng.nextFloat() - 0.5f) * 0.05f
            val dx = cos(aim)
            val dy = sin(aim)
            val path = PoolPhysics.predictObjectPath(listOf(), table, ox, oy, dx, dy, radius, exclude = null)
            if (path.pocket == null) continue
            predicted++
            val ball = PoolBall(1, PoolBallKind.SOLID, ox, oy)
            ball.vx = dx * PoolConfig.shotSpeedMps(1f)
            ball.vy = dy * PoolConfig.shotSpeedMps(1f)
            PoolPhysics.simulateUntilSettled(listOf(ball), table, maxSeconds = 6f)
            assertTrue(
                "predicted pocket ${path.pocketIndex} at origin=($ox,$oy) aim=$aim must pot",
                ball.potted,
            )
        }
        assertTrue("sample should contain predicted pockets: $predicted", predicted >= 20)
    }
}
