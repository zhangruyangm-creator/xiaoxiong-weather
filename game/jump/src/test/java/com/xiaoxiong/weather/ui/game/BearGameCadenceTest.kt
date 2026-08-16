package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BearGameCadenceTest {
    private fun sim(
        game: BearGame,
        ticks: Int,
    ) {
        // 清掉障碍/金币避免碰撞死亡，只测奔跑相位推进
        repeat(ticks) {
            game.obstacles.clear()
            game.coins.clear()
            game.tick(1f / 60f, onCoin = {}, onDeath = {})
        }
    }

    @Test
    fun tick_startCadence_isRelaxed() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        val before = game.runPhase
        game.tick(1f / 60f, onCoin = {}, onDeath = {})
        val perTick = game.runPhase - before

        // 新版起步步频 ~10.54 rad/s（旧版 13.6）：每帧 ~0.176 rad @60fps，节奏更从容
        assertEquals(0.1757f, perTick, 0.01f)
    }

    @Test
    fun tick_cadenceGrowsSlowly_andCapsAtHighSpeed() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()

        val start = game.runPhase
        game.tick(1f / 60f, onCoin = {}, onDeath = {})
        val startPerTick = game.runPhase - start

        // 直接推到高速段：score ~2136（速度 739·s > 封顶线 640·s）
        game.distance = 300_000f
        game.tick(1f / 60f, onCoin = {}, onDeath = {})
        val lateA = game.runPhase
        sim(game, 10)
        val latePerTick = (game.runPhase - lateA) / 10f

        // 步频只随速度增长不到 2 倍（旧版约 2.8 倍），且封顶在 ~19.84 rad/s
        assertTrue("起步每帧 $startPerTick，高速每帧 $latePerTick", latePerTick / startPerTick < 2.0f)
        assertEquals(0.3307f, latePerTick, 0.01f)

        // 再跑一段，步频保持不变（封顶不随速度继续涨）
        val lateB = game.runPhase
        sim(game, 10)
        val laterPerTick = (game.runPhase - lateB) / 10f
        assertEquals(latePerTick, laterPerTick, 0.005f)
    }
}
