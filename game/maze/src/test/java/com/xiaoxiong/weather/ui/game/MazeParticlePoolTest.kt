package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 粒子环形池的压缩与空闲判定：压缩后写入游标必须跟随存活区间，
 * 否则环形回绕会覆盖仍存活的粒子；同时覆盖 CLEAR 停帧所需的 isIdle。
 */
class MazeParticlePoolTest {
    @Test
    fun compact_syncsCursor_soNewParticlesStayVisible() {
        val fx = MazeFxHost()
        // bump 每次派发 6 枚尘土粒子（发布时间 0）
        fx.bump(time = 0f, dr = 0, dc = 1, r = 0, c = 0)
        assertEquals(6, fx.liveCount(0f))
        assertEquals(6, fx.compact(0f))

        // 全部过期后压缩：count 与 cursor 一起归零
        assertEquals(0, fx.compact(10f))

        // 再次派发：新粒子必须写进绘制区间 [0, count)，否则画面看不到
        fx.bump(time = 10f, dr = 0, dc = 1, r = 0, c = 0)
        assertEquals(6, fx.liveCount(10f))
        assertEquals(6, fx.compact(10f))
    }

    @Test
    fun compact_removesExpiredParticles() {
        val fx = MazeFxHost()
        fx.bump(time = 0f, dr = 0, dc = 1, r = 0, c = 0)
        assertEquals(6, fx.liveCount(0f))
        // 尘土 life 约 0.32s..0.52s，1 秒后全部过期
        assertEquals(0, fx.compact(1f))
        assertEquals(0, fx.liveCount(1f))
    }

    @Test
    fun isIdle_holdsUntilClearCelebrationIsObserved() {
        val fx = MazeFxHost()
        val game = MazeGame().apply { startLevel(0) }
        assertTrue("无粒子且未通关时应静止", fx.isIdle(game))

        game.cleared = true
        assertFalse("通关庆祝尚未被 observe 观测，不应判定静止", fx.isIdle(game))
    }

    @Test
    fun isIdle_trueOnlyAfterParticlesExpire() {
        val fx = MazeFxHost()
        val game = MazeGame().apply { startLevel(0) }
        fx.bump(time = 0f, dr = 0, dc = 1, r = 0, c = 0)
        game.time = 0f
        assertFalse("有存活粒子时不应判定静止", fx.isIdle(game))
        game.time = 10f
        assertTrue("粒子全部过期后应判定静止", fx.isIdle(game))
    }
}
