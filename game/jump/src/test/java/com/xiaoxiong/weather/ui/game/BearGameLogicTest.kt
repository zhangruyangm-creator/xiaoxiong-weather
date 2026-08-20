package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小熊跳跳纯逻辑层 [BearGameLogic] 的单元测试：不依赖 Compose / Android，纯 JVM 可跑。
 */
class BearGameLogicTest {

    private val s = 1f // 尺寸缩放 1，便于直接断言像素值

    // ── 物理推进（stepBearPhysics）─────────────────────────────

    @Test
    fun stepBearPhysics_gravityAccumulatesVertically() {
        // 从地面之上静止释放：只受重力向下累积，第一帧不会夹取（未触地）
        val p = BearGameLogic.stepBearPhysics(
            bearY = -100f, bearVY = 0f, jumpsLeft = 2, grounded = false,
            floorY = 0f, s = s, dt = 0.1f,
        )
        assertTrue("vy 应向下增长", p.bearVY > 0f)
        assertTrue("-100 + g*0.01 仍在地面之上，不夹取", p.bearY < 0f)
        assertFalse(p.grounded)
    }

    @Test
    fun stepBearPhysics_jumpGivesUpwardThenFalls() {
        // 二段跳初速向上（负），第一帧应向上移动
        var p = BearGameLogic.stepBearPhysics(
            bearY = -100f, bearVY = BearGameLogic.jumpV(s), jumpsLeft = 1, grounded = false,
            floorY = 0f, s = s, dt = 0.016f,
        )
        assertTrue("起跳第一帧速度应仍向上（负）", p.bearVY < 0f)
        p = BearGameLogic.stepBearPhysics(
            bearY = p.bearY, bearVY = p.bearVY, jumpsLeft = p.jumpsLeft, grounded = p.grounded,
            floorY = 0f, s = s, dt = 0.5f,
        )
        // 长时间后应落回地面并夹在地面高度
        assertTrue(p.bearY >= -0.001f)
        assertEquals(0f, p.bearVY, 1e-4f)
    }

    @Test
    fun stepBearPhysics_landingClampsAndResetsJumps() {
        // 从高处自由落体到地面以下，应夹到 floorY、清空速度、重置二段跳
        val p = BearGameLogic.stepBearPhysics(
            bearY = 200f, bearVY = 500f, jumpsLeft = 0, grounded = false,
            floorY = 0f, s = s, dt = 0.016f,
        )
        assertEquals(0f, p.bearY, 1e-4f)
        assertEquals(0f, p.bearVY, 1e-4f)
        assertEquals(2, p.jumpsLeft)
        assertTrue(p.grounded)
        assertTrue("从空中落地应标记 landed", p.landed)
    }

    @Test
    fun stepBearPhysics_notLanding_keepsState() {
        val p = BearGameLogic.stepBearPhysics(
            bearY = -300f, bearVY = -100f, jumpsLeft = 1, grounded = false,
            floorY = 0f, s = s, dt = 0.016f,
        )
        assertFalse(p.grounded)
        assertFalse(p.landed)
        assertEquals(1, p.jumpsLeft)
        // 高度远高于地面，不应被夹取
        assertTrue(p.bearY < 0f)
    }

    @Test
    fun stepBearPhysics_doubleJumpAllowsSecondJump() {
        // 二段跳后 jumpleft=0，落地应恢复回 2
        val v = BearGameLogic.jumpV2(s)
        assertEquals(-830f, v, 1e-3f)
    }

    @Test
    fun stepBearPhysics_scaleChangesGravityProportionally() {
        // 距地面足够远、dt 足够短，两缩放都不触地，才能干净地比较重力强度
        val near =
            BearGameLogic.stepBearPhysics(-5000f, 0f, 2, false, 0f, s = 1f, dt = 0.1f)
        val far =
            BearGameLogic.stepBearPhysics(-5000f, 0f, 2, false, 0f, s = 2f, dt = 0.1f)
        assertTrue("更大缩放的重力更强，垂直速度增量更大", far.bearVY > near.bearVY)
        assertEquals(BearGameLogic.gravity(2f) * 0.1f, far.bearVY, 1e-3f)
        assertEquals(BearGameLogic.gravity(1f) * 0.1f, near.bearVY, 1e-3f)
    }

    // ── 速度曲线 / 计分 / 步频 ─────────────────────────────────

    @Test
    fun speedAt_isMonotonicSaturating() {
        val early = BearGameLogic.speedAt(0f, s)
        val mid = BearGameLogic.speedAt(2500f, s)
        val max = BearGameLogic.speedAt(10000f, s)
        assertEquals(340f, early, 1e-2f)
        assertTrue("速度应随距离增长", mid > early)
        assertEquals("5000 分后达到极速 950", 950f, max, 1e-2f)
    }

    @Test
    fun score_countsDistanceAndCoins() {
        // 60 单位距离(s=1) = 1 分，每金币 +10
        assertEquals(0, BearGameLogic.score(0f, 0, s))
        assertEquals(1, BearGameLogic.score(60f, 0, s))
        assertEquals(21, BearGameLogic.score(60f, 2, s))
        assertEquals(0, BearGameLogic.score(999f, 5, 0f)) // s<=0 → 0
    }

    @Test
    fun runPhaseRate_scalesWithSpeedUntilCap() {
        val slow = BearGameLogic.runPhaseRate(320f, s)
        val fast = BearGameLogic.runPhaseRate(800f, s)
        assertTrue("步频随速度提升", fast > slow)
        // 封顶后不再增长
        val capped = BearGameLogic.runPhaseRate(2000f, s)
        assertEquals(fast, capped, 1e-3f)
    }

    // ── 冰墙几何 / 门槛 ────────────────────────────────────────

    @Test
    fun iceWallHeight_snowLowerThanColdRain() {
        assertTrue(
            BearGameLogic.iceWallHeight(RunScene.SNOW, s) <
                BearGameLogic.iceWallHeight(RunScene.FREEZING_RAIN, s),
        )
    }

    @Test
    fun iceWallMinScore_snowLateColdRainEarly() {
        assertEquals(300, BearGameLogic.iceWallMinScore(RunScene.SNOW))
        assertEquals(200, BearGameLogic.iceWallMinScore(RunScene.FREEZING_RAIN))
    }
}
