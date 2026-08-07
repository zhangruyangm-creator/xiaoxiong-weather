package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 出口开门动画的进度基准：以最后一罐蜂蜜被收集的时刻为起点，
 * 而不是用步数（moves）做时间基准。
 */
class MazeDoorAnimationTest {

    @Test
    fun doorOpenProgress_startsAtCollectionMoment() {
        assertEquals(0f, doorOpenProgress(time = 20f, lastHoneyAt = 20f), 0.0001f)
    }

    @Test
    fun doorOpenProgress_easesToFullOpenWithinOneSecond() {
        // smoothstep(0.5) = 0.5，中点单调且 1 秒后完全打开
        assertEquals(0.5f, doorOpenProgress(time = 20.5f, lastHoneyAt = 20f), 0.0001f)
        assertEquals(1f, doorOpenProgress(time = 21f, lastHoneyAt = 20f), 0.0001f)
        assertEquals(1f, doorOpenProgress(time = 60f, lastHoneyAt = 20f), 0.0001f)
    }

    @Test
    fun doorOpenProgress_isMonotonic() {
        var prev = doorOpenProgress(time = 20f, lastHoneyAt = 20f)
        for (i in 1..10) {
            val cur = doorOpenProgress(time = 20f + i / 10f, lastHoneyAt = 20f)
            assert(cur >= prev)
            prev = cur
        }
    }

    @Test
    fun doorOpenProgress_fullyOpenWhenLevelHasNoHoney() {
        assertEquals(1f, doorOpenProgress(time = 0f, lastHoneyAt = -1f), 0.0001f)
    }
}
