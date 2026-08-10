package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
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
        game.tick(1f / 60f, onCoin = { coinCallbacks++ }, onDeath = {})
        assertEquals(5, coinCallbacks)
        assertEquals(5, game.coinCount)
        assertEquals(0, game.coins.size)
    }
}
