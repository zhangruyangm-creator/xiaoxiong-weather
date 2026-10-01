package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BearGameCoinTest {
    @Test
    fun tick_collectsAllOverlappingCoinsInOneFrame() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        val cx = game.bearX + game.bearSize / 2f
        val cy = game.bearY + game.bearSize / 2f
        val r = 11f * game.s
        repeat(5) { i ->
            game.coins += Coin(cx + i * 2f, cy, r)
        }
        var coinCallbacks = 0
        var gainedTotal = 0
        game.tick(1f / 60f, onCoin = {
            coinCallbacks++
            gainedTotal += it
        }, onDeath = {})
        assertEquals(5, coinCallbacks)
        assertEquals(5, game.coinCount)
        assertEquals(5, game.combo)
        // 10+15+20+25+30 = 100
        assertEquals(100, gainedTotal)
        assertEquals(100, game.coinScore)
        assertEquals(0, game.coins.size)
        assertTrue(game.texts.any { it.text == "+30" })
    }

    @Test
    fun tick_challengeScoreMul_scalesCoinGain() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.difficulty = RunDifficulty.CHALLENGE
        game.start()
        val cx = game.bearX + game.bearSize / 2f
        val cy = game.bearY + game.bearSize / 2f
        game.coins += Coin(cx, cy, 11f * game.s)
        var gained = 0
        game.tick(1f / 60f, onCoin = { gained = it }, onDeath = {})
        assertEquals(15, gained) // 10 * 1.5
        assertEquals(15, game.coinScore)
    }
}
