package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PoolPocketJawTest {
    private val table = PoolTable.standard()

    @Test
    fun everyPocket_hasTwoJawSegments() {
        // 原始 6 段库边 + 6 个袋口各 2 段钳口 = 18
        assertEquals(6 + 6 * 2, table.rails.size)
    }

    @Test
    fun pocketEntrances_areWiderThanBallDiameter() {
        // 角袋：两库端沿对角线的净宽
        val cornerOpen = PoolConfig.CORNER_MOUTH * kotlin.math.sqrt(2f)
        assertTrue("corner mouth=$cornerOpen", cornerOpen > PoolConfig.BALL_RADIUS * 2f + PoolConfig.BALL_RADIUS * 0.3f)
        // 中袋：库端之间的净宽
        assertTrue(
            "side mouth=${PoolConfig.SIDE_MOUTH}",
            PoolConfig.SIDE_MOUTH > PoolConfig.BALL_RADIUS * 2f + PoolConfig.BALL_RADIUS * 0.3f,
        )
    }

    /** 袋口「自然进袋方向」：角袋沿对角线、中袋沿库法线（指向台面中心）。 */
    private fun approachBase(pocket: PoolPocket): Float {
        val tx = table.centerX - pocket.x
        val ty = table.centerY - pocket.y
        return if (pocket.corner) {
            atan2(if (ty >= 0f) 1f else -1f, if (tx >= 0f) 1f else -1f)
        } else {
            atan2(ty, tx)
        }
    }

    private fun shootAt(
        pocket: PoolPocket,
        angleOffset: Float,
    ): Boolean {
        val base = approachBase(pocket)
        val dir = base + angleOffset
        val sx = pocket.x + cos(dir) * 0.4f
        val sy = pocket.y + sin(dir) * 0.4f
        val ball = PoolBall(1, PoolBallKind.SOLID, sx, sy)
        val aim = atan2(pocket.y - sy, pocket.x - sx)
        ball.vx = cos(aim) * 2.5f
        ball.vy = sin(aim) * 2.5f
        var steps = 0
        while (steps < 1500 && ball.onTable) {
            PoolPhysics.step(listOf(ball), table)
            steps++
        }
        return ball.potted && ball.pocketIndex >= 0
    }

    @Test
    fun pocketCapture_acceptsBallsFromMultipleAngles() {
        val offsets = listOf(-0.35f, -0.18f, 0f, 0.18f, 0.35f)
        for ((index, pocket) in table.pockets.withIndex()) {
            val potted = offsets.count { shootAt(pocket, it) }
            assertTrue(
                "pocket $index (corner=${pocket.corner}) should mostly accept, potted=$potted/${offsets.size}",
                potted >= 4,
            )
        }
    }

    @Test
    fun randomBreakShots_neverEscapeTableUnpotted() {
        val rng = Random(20240L)
        repeat(120) {
            val balls = PoolRack.rack(table, PoolRack.standardOrder(rng.nextLong())).toMutableList()
            val cue = balls.first { it.kind == CUE }
            val angle = rng.nextFloat() * (2f * PI.toFloat())
            val power = 0.4f + rng.nextFloat() * 0.6f
            val speed = 0.7f + power * (PoolConfig.MAX_SPEED - 0.7f)
            cue.vx = cos(angle) * speed
            cue.vy = sin(angle) * speed
            PoolPhysics.simulateUntilSettled(balls, table, maxSeconds = 25f)
            for (ball in balls) {
                if (ball.potted) {
                    assertTrue(
                        "ball ${ball.id} left the table without a pocket (escape net)",
                        ball.pocketIndex >= 0,
                    )
                }
                if (ball.onTable) {
                    assertTrue(
                        "ball ${ball.id} x out of bounds",
                        ball.x > table.left - 0.06f && ball.x < table.right + 0.06f,
                    )
                    assertTrue(
                        "ball ${ball.id} y out of bounds",
                        ball.y > table.top - 0.06f && ball.y < table.bottom + 0.06f,
                    )
                }
            }
        }
    }

    @Test
    fun touchedRestingBalls_doNotLetFastBallTunnelThroughRail() {
        // 两球静止相切（旧实现会因 0 时间碰撞占满 TOI 迭代而让快球穿库）
        val a = PoolBall(1, PoolBallKind.SOLID, table.centerX - PoolConfig.BALL_RADIUS, table.centerY)
        val b = PoolBall(2, PoolBallKind.SOLID, table.centerX + PoolConfig.BALL_RADIUS, table.centerY)
        // 快球从下方撞向上库
        val fast = PoolBall(3, PoolBallKind.SOLID, table.centerX, table.top + 0.2f, vy = -8f)
        val balls = listOf(a, b, fast)
        repeat(600) { PoolPhysics.step(balls, table) }
        assertTrue("fast ball must stay on table: y=${fast.y}", fast.y >= table.top - 0.01f)
    }
}
