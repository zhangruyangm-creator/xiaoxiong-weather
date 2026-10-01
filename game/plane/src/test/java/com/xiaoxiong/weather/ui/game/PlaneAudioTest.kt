package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音效节流纯函数 + 有状态门控 + Boss 血条分段填充的单测。 */
class PlaneAudioTest {
    // ---------------------------------------------------------------------
    // 纯函数：planeCueAllowed
    // ---------------------------------------------------------------------

    @Test
    fun cueAllowed_rejectsWithinWindow_acceptsAtBoundary() {
        val last = 1_000L
        assertFalse(planeCueAllowed(nowMs = 1_109L, lastPlayedMs = last, minIntervalMs = 110L))
        assertTrue("到达最小间隔即允许", planeCueAllowed(nowMs = 1_110L, lastPlayedMs = last, minIntervalMs = 110L))
        assertTrue(planeCueAllowed(nowMs = 2_000L, lastPlayedMs = last, minIntervalMs = 110L))
    }

    @Test
    fun cueAllowed_neverPlayed_isAlwaysAllowed() {
        assertTrue(planeCueAllowed(nowMs = 0L, lastPlayedMs = Long.MIN_VALUE, minIntervalMs = 120L))
    }

    @Test
    fun cueAllowed_nonPositiveInterval_disablesThrottle() {
        assertTrue(planeCueAllowed(nowMs = 5L, lastPlayedMs = 5L, minIntervalMs = 0L))
        assertTrue(planeCueAllowed(nowMs = 5L, lastPlayedMs = 5L, minIntervalMs = -1L))
    }

    // ---------------------------------------------------------------------
    // 纯函数：planeShotCueAllowed（逻辑开火计数）
    // ---------------------------------------------------------------------

    @Test
    fun shotCueAllowed_onlySkipsSameVolley() {
        assertFalse("同一开火轮次不重复播放", planeShotCueAllowed(shotCount = 5L, lastPlayedShotCount = 5L))
        assertTrue("轮次前进即放行", planeShotCueAllowed(shotCount = 6L, lastPlayedShotCount = 5L))
        assertTrue(planeShotCueAllowed(shotCount = 1L, lastPlayedShotCount = Long.MIN_VALUE))
        assertTrue("恢复后计数归零不能被旧值吞掉", planeShotCueAllowed(shotCount = 0L, lastPlayedShotCount = 9L))
    }

    // ---------------------------------------------------------------------
    // 门控：PlaneCueGate
    // ---------------------------------------------------------------------

    @Test
    fun gate_shot_allowsEveryVolley_withoutWallClockJitterDrop() {
        val gate = PlaneCueGate()
        assertTrue("首轮立即播放", gate.allowShot(1L))
        assertFalse("同一轮次不重复", gate.allowShot(1L))
        // L4 激光 7 tick 一轮：120Hz / 补步下相邻事件墙钟可能 <110ms，计数门控必须逐发放行
        assertTrue("下一轮立即放行", gate.allowShot(2L))
        assertTrue(gate.allowShot(3L))
        assertTrue(gate.allowShot(4L))
    }

    @Test
    fun gate_shot_countRegressionAfterRestore_allowsFirstVolley() {
        val gate = PlaneCueGate()
        assertTrue(gate.allowShot(42L))
        assertFalse(gate.allowShot(42L))
        assertTrue("快照恢复后 shotCount 归零，不能被旧轮次吞掉", gate.allowShot(0L))
    }

    @Test
    fun gate_throttlesExplosionsAt80ms() {
        val gate = PlaneCueGate()
        assertTrue(gate.allowExplosion(0L))
        assertFalse(gate.allowExplosion(79L))
        assertTrue(gate.allowExplosion(80L))
    }

    @Test
    fun gate_shotAndExplosionTimersAreIndependent() {
        val gate = PlaneCueGate()
        assertTrue(gate.allowShot(1L))
        assertTrue(gate.allowExplosion(1_000L))
        assertFalse(gate.allowShot(1L))
        assertFalse(gate.allowExplosion(1_010L))
        assertTrue(gate.allowShot(2L))
        assertTrue(gate.allowExplosion(1_080L))
    }

    @Test
    fun gate_reset_allowsFirstCueImmediately() {
        val gate = PlaneCueGate()
        assertTrue(gate.allowShot(7L))
        assertFalse(gate.allowShot(7L))
        gate.reset()
        assertTrue("开新局后首声不被上一局吞掉", gate.allowShot(1L))
    }

    @Test
    fun shotCue_usesLaserFromLevel4() {
        assertEquals(GameCue.SHOT, planeShotCue(PlaneConfig.MIN_WEAPON_LEVEL))
        assertEquals(GameCue.SHOT, planeShotCue(3))
        assertEquals(GameCue.LASER, planeShotCue(4))
        assertEquals(GameCue.LASER, planeShotCue(PlaneConfig.MAX_WEAPON_LEVEL))
        assertEquals(GameCue.LASER, planeShotCue(PlaneConfig.MAX_WEAPON_LEVEL + 3))
    }

    // ---------------------------------------------------------------------
    // Boss 血条分段
    // ---------------------------------------------------------------------

    @Test
    fun bossSegmentFill_fullHp_fillsEverySegment() {
        repeat(PLANE_BOSS_HP_SEGMENTS) { seg ->
            assertEquals(1f, planeBossSegmentFill(hp = 300, maxHp = 300, segment = seg), 1e-4f)
        }
    }

    @Test
    fun bossSegmentFill_depletesRightToLeft() {
        // 血量 = 2/3 → 前两段满，最后一段空
        assertEquals(1f, planeBossSegmentFill(hp = 200, maxHp = 300, segment = 0), 1e-4f)
        assertEquals(1f, planeBossSegmentFill(hp = 200, maxHp = 300, segment = 1), 1e-4f)
        assertEquals(0f, planeBossSegmentFill(hp = 200, maxHp = 300, segment = 2), 1e-4f)
        // 血量 = 1/3 → 第 0 段满，第 1、2 段空
        assertEquals(1f, planeBossSegmentFill(hp = 100, maxHp = 300, segment = 0), 1e-4f)
        assertEquals(0f, planeBossSegmentFill(hp = 100, maxHp = 300, segment = 1), 1e-4f)
        assertEquals(0f, planeBossSegmentFill(hp = 100, maxHp = 300, segment = 2), 1e-4f)
        // 中间值按比例：总量 2.5 段 → 前两段满、第三段 0.5
        assertEquals(1f, planeBossSegmentFill(hp = 250, maxHp = 300, segment = 1), 1e-4f)
        assertEquals(0.5f, planeBossSegmentFill(hp = 250, maxHp = 300, segment = 2), 1e-4f)
    }

    @Test
    fun bossSegmentFill_degenerateInputs_returnZero() {
        assertEquals(0f, planeBossSegmentFill(hp = 10, maxHp = 0, segment = 0), 0f)
        assertEquals(0f, planeBossSegmentFill(hp = 10, maxHp = 100, segment = -1), 0f)
        assertEquals(0f, planeBossSegmentFill(hp = 10, maxHp = 100, segment = 3), 0f)
        assertEquals(0f, planeBossSegmentFill(hp = -5, maxHp = 100, segment = 0), 0f)
    }
}
