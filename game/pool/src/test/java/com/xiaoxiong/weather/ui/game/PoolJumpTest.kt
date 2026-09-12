package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import com.xiaoxiong.weather.ui.game.PoolBallKind.SOLID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PoolJumpTest {
    private val table = PoolTable.standard()

    private fun newGame(): PoolGame =
        PoolGame().apply {
            resize(420f, 820f)
            mode = PoolMode.PRACTICE
            startMatch(PoolSide.PLAYER, seed = 1L)
        }

    /**
     * 摆一个「白球 + 正前方挡球」的局面并以给定抬杆角出杆，
     * 返回挡球最终位置（挡球位移即代表是否被撞到）。
     */
    private fun runShot(elevation: Float): Pair<Float, Float> {
        val game = newGame()
        val cx = game.table.centerX
        game.balls.clear()
        val cue = PoolBall(0, CUE, cx, game.table.top + 0.2f)
        val blocker = PoolBall(1, SOLID, cx, game.table.top + 0.65f)
        game.balls += cue
        game.balls += blocker
        game.updateElevation(elevation)
        game.shoot((PI / 2).toFloat(), 1f)
        var t = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && t < 5f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
        return blocker.x to blocker.y
    }

    @Test
    fun jumpShot_clearsBlockerWhileFlatShotHits() {
        val flat = runShot(0f)
        // 平杆会撞上挡球并把它推走
        assertTrue("flat shot should hit blocker: $flat", kotlin.math.abs(flat.second - (table.top + 0.65f)) > 0.05f)

        val jump = runShot(1f)
        // 跳球从挡球上方越过，挡球纹丝不动
        assertTrue(
            "jump shot should clear blocker: $jump",
            PoolMath.approximately(jump.first, table.centerX, 1e-3f) &&
                PoolMath.approximately(jump.second, table.top + 0.65f, 1e-3f),
        )
    }

    @Test
    fun airborneBall_doesNotCollideWithOthersNorRails() {
        val cueBall =
            PoolBall(0, CUE, table.centerX, table.top + 0.2f).apply {
                z = 0.15f
                vz = 3f
                vy = 4f
            }
        val blocker = PoolBall(1, SOLID, table.centerX, table.top + 0.45f)
        val balls = listOf(cueBall, blocker)
        val blockerY = blocker.y
        val vy0 = cueBall.vy
        var steps = 0
        while (steps < 300 && cueBall.onTable && cueBall.y < blocker.y + 0.05f) {
            PoolPhysics.step(balls, table)
            steps++
        }
        assertTrue("cue should pass the blocker", cueBall.y > blocker.y + 0.05f)
        assertTrue("cue should stay airborne", cueBall.airborne)
        // 飞行中水平速度不受台呢摩擦与碰撞影响
        assertEquals(vy0, cueBall.vy, 1e-4f)
        // 挡球完全没动
        assertEquals(blockerY, blocker.y, 1e-5f)
    }

    @Test
    fun airborneBall_flyingOffTable_isPottedWithListener() {
        var pocketEvents = 0
        val listener =
            object : PoolPhysics.Listener {
                override fun onPocket(ball: PoolBall) {
                    pocketEvents++
                }
            }
        val ball =
            PoolBall(0, CUE, table.centerX, table.top + 0.5f).apply {
                z = 0.15f
                vz = 3f
                vy = -5f
            }
        var steps = 0
        while (steps < 2000 && ball.onTable) {
            PoolPhysics.step(listOf(ball), table, listener = listener)
            steps++
        }
        assertTrue("airborne ball should leave the table", ball.potted)
        assertFalse(ball.onTable)
        assertEquals(1, pocketEvents)
    }

    @Test
    fun stop_clearsVerticalMotion() {
        val ball = PoolBall(0, CUE, table.centerX, table.centerY)
        ball.z = 0.2f
        ball.vz = 1.5f
        assertTrue(ball.airborne)
        assertTrue(ball.isMoving())
        ball.stop()
        assertEquals(0f, ball.z, 0f)
        assertEquals(0f, ball.vz, 0f)
        assertFalse(ball.airborne)
        assertFalse(ball.isMoving())
    }

    @Test
    fun flatShot_neverLeavesGround() {
        val game = newGame()
        game.shoot((PI / 2).toFloat(), 1f)
        val cue = game.cueBall()!!
        assertEquals(0f, cue.vz, 0f)
        assertEquals(0f, cue.z, 0f)
        assertFalse(cue.airborne)
    }

    @Test
    fun lowElevationOrPower_belowThresholdDoesNotJump() {
        val game = newGame()
        game.updateElevation(0.5f)
        game.shoot((PI / 2).toFloat(), 1f)
        assertEquals(0f, game.cueBall()!!.vz, 0f)

        val game2 = newGame()
        game2.updateElevation(1f)
        game2.shoot((PI / 2).toFloat(), 0.5f)
        assertEquals(0f, game2.cueBall()!!.vz, 0f)
    }
}
