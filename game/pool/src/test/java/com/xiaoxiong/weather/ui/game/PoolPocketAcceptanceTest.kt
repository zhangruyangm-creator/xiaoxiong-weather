package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
}
