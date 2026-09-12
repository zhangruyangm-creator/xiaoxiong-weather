package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolConfig.BALL_RADIUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PoolPhysicsTest {
    private val table = PoolTable.standard()

    private fun cue(
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
    ) = PoolBall(0, PoolBallKind.CUE, x, y, vx, vy)

    private fun solid(
        id: Int,
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
    ) = PoolBall(id, PoolBallKind.SOLID, x, y, vx, vy)

    @Test
    fun settled_afterIdle_isTrue() {
        val balls = listOf(cue(table.centerX, table.centerY))
        assertTrue(PoolPhysics.settled(balls))
    }

    @Test
    fun headOnCollision_transfersMostVelocity() {
        val a = cue(table.centerX - 0.05f, table.centerY, vx = 3f)
        val b = solid(1, table.centerX + 0.05f, table.centerY)
        val balls = listOf(a, b)
        for (i in 0 until 120) {
            PoolPhysics.step(balls, table)
            if (b.speed > 0.01f) break
        }
        // 碰撞后：b 获得大部分动量，a 基本停下
        assertTrue("b speed=${b.speed}", b.speed > 2.4f)
        assertTrue("a speed=${a.speed}", a.speed < 0.9f)
    }

    @Test
    fun railReflection_reversesNormalVelocityAndStaysInside() {
        // 贴近上库右侧实库、以高速向上运动，逐步模拟直到反射
        val ball = cue(table.centerX + 0.25f, table.top + BALL_RADIUS * 1.2f, vy = -2f)
        var reflected = false
        for (i in 0 until 600) {
            PoolPhysics.step(listOf(ball), table)
            if (ball.vy > 0.01f) {
                reflected = true
                break
            }
        }
        assertTrue("should reflect off top rail", reflected)
        assertTrue("should stay below top rail: y=${ball.y}", ball.y >= table.top + BALL_RADIUS - 1e-3f)
    }

    @Test
    fun railReflection_preservesTangentialVelocityRoughly() {
        // 斜射上库：X 分量应基本保留（库边摩擦很小）
        val ball = cue(table.centerX + 0.25f, table.top + BALL_RADIUS * 1.1f, vx = 1.5f, vy = -2f)
        for (i in 0 until 600) {
            PoolPhysics.step(listOf(ball), table)
            if (ball.vy > 0.01f) break
        }
        assertTrue("vx kept: ${ball.vx}", ball.vx > 1.0f)
    }

    @Test
    fun noTunneling_highSpeedBreakHitsRack() {
        val rack = PoolRack.rack(table, PoolRack.standardOrder())
        val cueBall = rack.first { it.kind == PoolBallKind.CUE }
        // 白球从开球线向下高速冲入球堆
        cueBall.vy = PoolConfig.MAX_SPEED
        val objectBefore = rack.filter { it.kind != PoolBallKind.CUE }.associate { it.id to it.y }
        PoolPhysics.simulateUntilSettled(rack, table, maxSeconds = 20f)

        val moved =
            rack
                .filter { it.kind != PoolBallKind.CUE }
                .count { kotlin.math.abs(it.y - (objectBefore[it.id] ?: it.y)) > 0.01f }
        assertTrue("some object balls should be struck, moved=$moved", moved > 0)
        // 所有仍在台面上的球都应落在台面范围内（袋口附近允许少量外扩）
        for (b in rack) {
            if (!b.onTable) continue
            assertTrue("x in bounds: ${b.x}", b.x > table.left - 0.06f && b.x < table.right + 0.06f)
            assertTrue("y in bounds: ${b.y}", b.y > table.top - 0.06f && b.y < table.bottom + 0.06f)
        }
    }

    @Test
    fun break_settlesWithinTimeLimit() {
        val rack = PoolRack.rack(table, PoolRack.standardOrder())
        rack.first { it.kind == PoolBallKind.CUE }.apply {
            vx = 0.15f
            vy = PoolConfig.MAX_SPEED
        }
        PoolPhysics.simulateUntilSettled(rack, table, maxSeconds = 45f)
        assertTrue("should settle", PoolPhysics.settled(rack))
    }

    @Test
    fun pocketCapture_ballAtCornerPocketIsPotted() {
        val pocket = table.pockets.first { it.corner && it.x == table.left && it.y == table.top }
        val ball = solid(1, pocket.x + 0.004f, pocket.y + 0.004f)
        val stats = PoolPhysics.step(listOf(ball), table)
        assertTrue("potted=${ball.potted}", ball.potted)
        assertEquals(1, stats.potted)
        assertFalse(ball.onTable)
    }

    @Test
    fun railRollPastPocketMouth_isNotPotted() {
        // 贴库直线滚过中袋口正上方：不应被捕获
        val sidePocket = table.pockets.first { !it.corner && it.y == table.top }
        val ball = cue(sidePocket.x - 0.06f, table.top + BALL_RADIUS * 1.02f, vx = 2f)
        repeat(180) { PoolPhysics.step(listOf(ball), table) }
        assertFalse("grazing the mouth should not pot", ball.potted)
    }

    @Test
    fun escapeNet_marksPocketIndexAndNotifies() {
        val ball = solid(1, table.right + BALL_RADIUS * 5f, table.centerY)
        var pocketNotified = false
        val listener =
            object : PoolPhysics.Listener {
                override fun onPocket(ball: PoolBall) {
                    pocketNotified = true
                }
            }
        PoolPhysics.step(listOf(ball), table, listener = listener)
        assertTrue(ball.potted)
        assertTrue("pocketIndex should be set", ball.pocketIndex >= 0)
        assertTrue("listener should be notified", pocketNotified)
    }

    @Test
    fun spinDecaysOverTime() {
        val ball = cue(table.centerX, table.centerY)
        ball.wz = 60f
        repeat(1500) { PoolPhysics.step(listOf(ball), table) }
        assertTrue("spin should decay: wz=${ball.wz}", kotlin.math.abs(ball.wz) < 15f)
    }

    @Test
    fun equalMassMomentumConservedInCollision() {
        val a = cue(table.centerX - 0.04f, table.centerY, vx = 2f)
        val b = solid(1, table.centerX + 0.04f, table.centerY)
        val balls = listOf(a, b)
        val before = 2f
        for (i in 0 until 120) {
            PoolPhysics.step(balls, table)
            if (b.speed > 0.01f) break
        }
        val after = a.vx + b.vx
        assertEquals("momentum", before, after, 0.08f)
    }

    @Test
    fun determinism_sameInputSameOutput() {
        fun run(): List<FloatArray> {
            val balls = PoolRack.rack(table, PoolRack.standardOrder(seed = 7L))
            balls.first { it.kind == PoolBallKind.CUE }.apply {
                vx = 0.07f
                vy = 9f
            }
            repeat(1200) { PoolPhysics.step(balls, table) }
            return balls.map { floatArrayOf(it.x, it.y, it.vx, it.vy, it.wz) }
        }
        val r1 = run()
        val r2 = run()
        for (i in r1.indices) {
            for (k in 0 until 5) {
                assertEquals("ball $i component $k", r1[i][k], r2[i][k], 0f)
            }
        }
    }

    @Test
    fun predictFirstContact_findsStraightAheadBall() {
        val cueBall = cue(table.centerX, table.top + 0.3f)
        val target = solid(1, table.centerX, table.top + 0.8f)
        val contact =
            PoolPhysics.predictFirstContact(
                balls = listOf(cueBall, target),
                table = table,
                cue = cueBall,
                angle = (Math.PI / 2).toFloat(),
            )
        assertNotNull(contact)
        assertEquals(target.id, contact!!.target?.id)
    }

    @Test
    fun predictFirstContact_nullWhenRailsBlockBefore() {
        val cueBall = cue(table.centerX, table.centerY)
        // 朝下方 45°：附近无球，射线先撞库 → 无首个目标
        val contact =
            PoolPhysics.predictFirstContact(
                balls = listOf(cueBall),
                table = table,
                cue = cueBall,
                angle = (Math.PI / 4).toFloat(),
            )
        assertNull(contact?.target)
    }

    @Test
    fun ballRadius_relativeToTableIsSane() {
        val rack = PoolRack.rack(table, PoolRack.standardOrder())
        // 三角架内任意两球不重叠
        for (i in rack.indices) {
            for (j in i + 1 until rack.size) {
                val a = rack[i]
                val b = rack[j]
                if (!a.onTable || !b.onTable) continue
                val d = hypot(a.x - b.x, a.y - b.y)
                assertTrue("overlap ${a.id}/${b.id} d=$d", d >= a.radius + b.radius - 1e-4f)
            }
        }
    }
}
