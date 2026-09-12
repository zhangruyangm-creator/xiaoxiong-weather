package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolChallengesTest {
    @Test
    fun campaigns_haveTwelveLegalLayouts() {
        val campaigns = PoolChallenges.campaigns()
        assertEquals(12, campaigns.size)
        campaigns.forEach { c ->
            assertTrue("level ${c.id} layout should be valid", PoolChallenges.isLayoutValid(c))
            assertEquals("cue ball expected", 1, c.balls.count { it.kind == CUE })
            assertTrue("par should be positive", c.par > 0)
        }
    }

    @Test
    fun campaigns_escalateBallCountFromThreeToNine() {
        val counts = PoolChallenges.campaigns().map { it.targetCount }
        assertEquals(counts, counts.sorted())
        assertEquals(3, counts.first())
        assertEquals(9, counts.last())
        assertTrue("every level has at least 3 targets", counts.all { it >= 3 })
    }

    @Test
    fun campaigns_idsAreUniqueAndSequential() {
        val ids = PoolChallenges.campaigns().map { it.id }
        assertEquals((1..12).toList(), ids)
    }

    @Test
    fun daily_sameDayIsDeterministic() {
        val a = PoolChallenges.daily(20_000L)
        val b = PoolChallenges.daily(20_000L)
        assertEquals(a, b)
        assertEquals(a.balls, b.balls)
        assertTrue(PoolChallenges.isLayoutValid(a))
    }

    @Test
    fun daily_differentDaysDiffer() {
        assertNotEquals(PoolChallenges.daily(20_000L).balls, PoolChallenges.daily(20_001L).balls)
    }

    @Test
    fun daily_hasSixToEightBallsAndValidLayout() {
        for (day in 20_000L..20_020L) {
            val c = PoolChallenges.daily(day)
            assertEquals(PoolChallenges.DAILY_ID, c.id)
            assertTrue("targets=${c.targetCount}", c.targetCount in 6..8)
            assertTrue("par should be positive", c.par > 0)
            assertTrue("day $day layout should be valid", PoolChallenges.isLayoutValid(c))
            assertEquals("cue ball expected", 1, c.balls.count { it.kind == CUE })
        }
    }

    @Test
    fun scoreFor_fewerShotsScoreHigher() {
        val par = 5
        val oneShot = PoolChallenges.scoreFor(par, 1)
        val threeShots = PoolChallenges.scoreFor(par, 3)
        val eightShots = PoolChallenges.scoreFor(par, 8)
        assertTrue(oneShot > threeShots)
        assertTrue(threeShots > eightShots)
        assertEquals(10, PoolChallenges.scoreFor(par, 1_000))
    }
}
