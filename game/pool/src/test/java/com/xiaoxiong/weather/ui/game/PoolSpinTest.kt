package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

class PoolSpinTest {
    private val table = PoolTable.standard()

    private fun cue(
        x: Float,
        y: Float,
    ) = PoolBall(0, PoolBallKind.CUE, x, y)

    @Test
    fun cushionRestitution_decreasesWithImpactSpeed() {
        assertTrue(PoolConfig.cushionRestitution(0.5f) > PoolConfig.cushionRestitution(6f))
        assertTrue(PoolConfig.cushionRestitution(6f) >= 0.45f)
    }

    @Test
    fun squirt_deflectsOppositeToSideSpin() {
        val game = PoolGame().apply { resize(420f, 820f) }
        game.startMatch(PoolSide.PLAYER, seed = 1L)
        val aim = (Math.PI / 2).toFloat()
        game.shoot(aim, 0.8f, PoolGame.Spin(side = 1f, vertical = 0f))
        val cue = game.cueBall()!!
        val actual = atan2(cue.vy, cue.vx)
        // 右塞（side=+1）→ 白球向左偏（角度变小）
        assertTrue("squirt should deflect left: aim=$aim actual=$actual", actual < aim - 1e-4f)
    }

    @Test
    fun curve_bendsPath() {
        val ball = cue(table.centerX, table.centerY)
        ball.vy = 2f
        ball.curve = 3f
        val startX = ball.x
        repeat(360) { PoolPhysics.step(listOf(ball), table) }
        // curve>0 → 朝速度左法向偏转，+Y 运动的左侧为 -X
        assertTrue("should bend left: dx=${ball.x - startX}", ball.x < startX - 1e-3f)
    }

    @Test
    fun curve_decaysToZero() {
        val ball = cue(table.centerX, table.centerY)
        ball.vy = 2f
        ball.curve = 3f
        repeat(1500) { PoolPhysics.step(listOf(ball), table) }
        assertEquals(0f, ball.curve, 0.02f)
    }

    @Test
    fun curveZero_travelsStraight() {
        val ball = cue(table.centerX, table.bottom - 0.15f)
        ball.vx = 1.5f
        val y0 = ball.y
        repeat(120) { PoolPhysics.step(listOf(ball), table) }
        assertTrue("no curve -> no lateral drift", abs(ball.y - y0) < 1e-4f)
    }

    @Test
    fun highCurve_isBoundedAndDoesNotExplode() {
        val ball = cue(table.centerX, table.centerY)
        ball.vy = 3f
        ball.curve = 20f
        repeat(1200) { PoolPhysics.step(listOf(ball), table) }
        assertTrue("speed bounded: ${ball.speed}", ball.speed <= PoolConfig.MAX_SPEED + 1e-3f)
        for (r in table.rails) {
            // 不穿库（仍应落在台面范围内外扩容差内）
        }
        assertTrue(ball.x > table.left - 0.08f && ball.x < table.right + 0.08f)
    }

    @Test
    fun ballBallTangentialFriction_spinsBothBallsSameDirectionAndDecays() {
        val spinBefore = 40f
        val a =
            cue(table.centerX - 0.06f, table.centerY).apply {
                vx = 3f
                wz = spinBefore
            }
        val b = PoolBall(1, PoolBallKind.SOLID, table.centerX + 0.05f, table.centerY + 0.008f)
        val balls = listOf(a, b)
        var steps = 0
        while (steps < 600 && abs(b.wz) < 1e-4f) {
            PoolPhysics.step(balls, table)
            steps++
        }
        val deltaA = a.wz - spinBefore
        val deltaB = b.wz
        assertTrue("碰撞应传递侧旋: dA=$deltaA dB=$deltaB", abs(deltaA) > 1e-3f && abs(deltaB) > 1e-3f)
        assertTrue("两球 wz 变化应同号（均为 -r·jt/I）: dA=$deltaA dB=$deltaB", deltaA * deltaB > 0f)
        assertTrue("白球侧旋应衰减而非反向: ${a.wz}", abs(a.wz) < spinBefore)
    }
}
