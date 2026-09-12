package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameOverSlowMotionTest {
    @Test
    fun overPhase_usesSlowMotion() {
        assertEquals(GAME_OVER_SLOW_MOTION_SCALE, gameOverTimeScale(GamePhase.OVER), 0.0001f)
    }

    @Test
    fun readyAndRunning_useRealTime() {
        assertEquals(1f, gameOverTimeScale(GamePhase.READY), 0.0001f)
        assertEquals(1f, gameOverTimeScale(GamePhase.RUNNING), 0.0001f)
    }

    @Test
    fun slowMotionScale_isPronouncedButNotFrozen() {
        // 确实慢于真实时间，又不能为 0（否则死亡动画卡住）
        assertTrue(GAME_OVER_SLOW_MOTION_SCALE in 0.05f..0.95f)
    }
}
