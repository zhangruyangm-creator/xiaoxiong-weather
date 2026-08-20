package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小熊摘星纯逻辑层 [StarJumpLogic] 的单元测试：不依赖 Compose / Android，纯 JVM 可跑。
 */
class StarJumpLogicTest {

    private val s = 1f

    private fun platform(
        type: PlatType,
        x: Float = 0f,
        y: Float = -300f,
        w: Float = 100f,
        broken: Boolean = false,
    ): Platform = Platform(x, y, w, type, broken = broken)

    private fun contact(
        bearX: Float,
        bearY: Float,
        prevY: Float,
        bearVY: Float,
        platforms: List<Platform>,
        cameraY: Float = -600f,
        w: Float = 1000f,
        h: Float = 800f,
    ): StarJumpLogic.Contact =
        StarJumpLogic.resolveContact(
            bearX = bearX,
            prevFeetY = prevY + StarJumpLogic.bearSize(s) / 2f,
            newFeetY = bearY + StarJumpLogic.bearSize(s) / 2f,
            bearVY = bearVY,
            platforms = platforms,
            w = w, h = h, s = s,
            cameraY = cameraY,
        )

    // ── 垂直物理 ───────────────────────────────────────────────

    @Test
    fun stepFalling_gravityPullsDown() {
        val (ny, nvy) = StarJumpLogic.stepFalling(bearY = -100f, bearVY = 0f, s = s, dt = 0.1f)
        assertTrue("垂直速度应向下增长", nvy > 0f)
        assertTrue("位置应下移", ny > -100f)
    }

    // ── 平台接触 ───────────────────────────────────────────────

    @Test
    fun resolveContact_hitsPlatform_andBounces() {
        // 熊中心 x 对准平台中心，下落穿过台面 → 命中，反弹速度为普通 bounceV
        val c =
            contact(bearX = 0f, bearY = -260f, prevY = -330f, bearVY = 500f, platforms = listOf(platform(PlatType.NORMAL, y = -300f)))
        assertTrue(c.hit)
        assertNotNull(c.platform)
        assertEquals(StarJumpLogic.bounceV(s), c.bounceVY, 1e-3f)
        assertEquals(-300f - StarJumpLogic.bearSize(s) / 2f, c.landY, 1e-3f)
    }

    @Test
    fun resolveContact_notFalling_ignored() {
        // 上升中不打平台
        val c =
            contact(bearX = 0f, bearY = -260f, prevY = -330f, bearVY = -300f, platforms = listOf(platform(PlatType.NORMAL, y = -300f)))
        assertFalse(c.hit)
    }

    @Test
    fun resolveContact_sprintGivesStrongerBounce() {
        val c =
            contact(bearX = 0f, bearY = -260f, prevY = -330f, bearVY = 500f, platforms = listOf(platform(PlatType.SPRING, y = -300f)))
        assertTrue(c.hit)
        assertEquals(StarJumpLogic.springV(s), c.bounceVY, 1e-3f)
    }

    @Test
    fun resolveContact_horizontalMiss_ignored() {
        // 平台中心 x=0、宽 100，熊在 x=500 → 太远，不踩
        val c =
            contact(bearX = 500f, bearY = -260f, prevY = -330f, bearVY = 500f, platforms = listOf(platform(PlatType.NORMAL, y = -300f)))
        assertFalse(c.hit)
        assertNull(c.platform)
    }

    @Test
    fun resolveContact_belowDeathLine_ignored() {
        // 平台 y - cameraY > h + bs → 死亡线以下不可踩
        val c =
            contact(
                bearX = 0f, bearY = 500f, prevY = 300f, bearVY = 500f,
                platforms = listOf(platform(PlatType.NORMAL, y = 400f)), // 400 - (-600) = 1000 > 800+58
            )
        assertFalse(c.hit)
    }

    @Test
    fun resolveContact_brokenPlatform_ignored() {
        val c =
            contact(bearX = 0f, bearY = -260f, prevY = -330f, bearVY = 500f, platforms = listOf(platform(PlatType.NORMAL, y = -300f, broken = true)))
        assertFalse(c.hit)
    }

    @Test
    fun resolveContact_touchesFirstHitTopDown() {
        // 两个平台 y 都在脚线穿越范围，应命中位置更高的那个（y 更小 = 更靠上）
        val upper = platform(PlatType.NORMAL, y = -300f)
        val lower = platform(PlatType.NORMAL, y = -270f)
        val c =
            contact(bearX = 0f, bearY = -240f, prevY = -330f, bearVY = 500f, platforms = listOf(upper, lower))
        assertTrue(c.hit)
        assertEquals(upper, c.platform)
    }

    // ── 星星收集 ───────────────────────────────────────────────

    @Test
    fun starPickup_hitWhenClose() {
        assertTrue(StarJumpLogic.starPickup(bearX = 0f, bearY = 0f, starX = 10f, starY = 10f, w = 1000f, s = s))
    }

    @Test
    fun starPickup_missWhenFar() {
        assertFalse(StarJumpLogic.starPickup(bearX = 0f, bearY = 0f, starX = 500f, starY = 500f, w = 1000f, s = s))
    }

    // ── 计分 / 海拔 ────────────────────────────────────────────

    @Test
    fun scoreFor_combinesHeightAndStars() {
        // maxHeight=-600, s=1 → 600/60=10，+25*2=50 → 60
        assertEquals(60, StarJumpLogic.scoreFor(maxHeight = -600f, starsCollected = 2, s = s))
        assertEquals(0, StarJumpLogic.scoreFor(maxHeight = 10f, starsCollected = 0, s = s)) // 上探不算分
    }

    @Test
    fun altitudeKm_nonNegativeProportional() {
        assertEquals(0f, StarJumpLogic.altitudeKm(cameraY = 0f, s = s), 1e-3f)
        assertTrue(StarJumpLogic.altitudeKm(cameraY = -24000f, s = s) > 9.9f)
        assertEquals(0f, StarJumpLogic.altitudeKm(cameraY = 100f, s = s), 1e-3f)
    }

    // ── 平台生成决策 ───────────────────────────────────────────

    @Test
    fun platformShape_earlyIsNormal() {
        val (type, _) = StarJumpLogic.platformShape(progress = 0f, early = true, roll = 0.99f, w = 1000f, s = s)
        assertEquals(PlatType.NORMAL, type)
    }

    @Test
    fun platformShape_rollMapsToSpringBreakMovingNormal() {
        assertEquals(PlatType.SPRING, StarJumpLogic.platformShape(0f, false, roll = 0.05f, w = 1000f, s = s).first)
        assertEquals(PlatType.BREAK, StarJumpLogic.platformShape(0f, false, roll = 0.10f, w = 1000f, s = s).first)
        assertEquals(PlatType.MOVING, StarJumpLogic.platformShape(0f, false, roll = 0.30f, w = 1000f, s = s).first)
        assertEquals(PlatType.NORMAL, StarJumpLogic.platformShape(0f, false, roll = 0.99f, w = 1000f, s = s).first)
    }

    @Test
    fun platformShape_movingClampsWidth() {
        val (type, pw) = StarJumpLogic.platformShape(0f, false, roll = 0.30f, w = 200f, s = s)
        assertEquals(PlatType.MOVING, type)
        // 移动台宽必须 ≤ maxTravel * 0.55 且 ≥ 48
        val maxTravel = (200f - 16f).coerceAtLeast(1f)
        assertTrue(pw <= maxTravel * 0.55f + 1e-4f)
        assertTrue(pw >= 48f - 1e-4f)
    }
}
