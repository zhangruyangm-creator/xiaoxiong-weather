package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PoolNapTest {
    private val table = PoolTable.standard()

    /** 让一个纯滚动的球沿 ±Y 滚到停下，返回滚过的距离（带符号）。 */
    private fun rollDistance(vy: Float): Float {
        val ball = PoolBall(1, PoolBallKind.SOLID, table.centerX, table.centerY, vy = vy)
        // 初始设为纯滚动（接触点相对速度为 0）
        ball.wx = -vy / ball.radius
        ball.wy = 0f
        var steps = 0
        while (steps < 30_000 && ball.isMoving()) {
            PoolPhysics.step(listOf(ball), table)
            steps++
        }
        return ball.y - table.centerY
    }

    @Test
    fun rollingWithNap_travelsFartherThanAgainstNap() {
        // +Y 为顺毛（阻力小、滚得远），-Y 为逆毛（阻力大、滚得近）
        val withNap = rollDistance(1f)
        val againstNap = rollDistance(-1f)
        assertTrue("with nap distance=$withNap", withNap > 0.2f)
        assertTrue("against nap distance=$againstNap", againstNap < -0.2f)
        assertTrue(
            "顺毛应比逆毛滚得更远: with=$withNap against=$againstNap",
            abs(withNap) > abs(againstNap) + 0.05f,
        )
    }

    @Test
    fun nap_isDeterministic() {
        fun run(): FloatArray {
            val ball = PoolBall(1, PoolBallKind.SOLID, table.centerX, table.top + 0.2f, vx = 0.3f, vy = 1.4f)
            ball.wx = -ball.vy / ball.radius
            ball.wy = ball.vx / ball.radius
            repeat(800) { PoolPhysics.step(listOf(ball), table) }
            return floatArrayOf(ball.x, ball.y, ball.vx, ball.vy, ball.wx, ball.wy)
        }
        assertArrayEquals(run(), run(), 0f)
    }

    @Test
    fun napFactor_isBoundedAroundOne() {
        // 各向异性幅度应较小，不改变整体手感量级
        assertTrue(PoolConfig.NAP_ANISOTROPY > 0f)
        assertTrue(PoolConfig.NAP_ANISOTROPY <= 0.25f)
    }
}
