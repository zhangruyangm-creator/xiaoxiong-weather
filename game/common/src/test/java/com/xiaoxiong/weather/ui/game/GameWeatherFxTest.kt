package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [positiveModulo] 的纯函数单测：验证雨/雹纵向取模在负偏移（高空视差）下
 * 仍能把粒子回绕进 `[0, span)`，不会整体移出屏幕。
 */
class GameWeatherFxTest {
    @Test
    fun positiveModulo_nonNegativeInput_keepsRemainder() {
        assertEquals(3f, positiveModulo(3f, 10f), 1e-6f)
        assertEquals(0f, positiveModulo(10f, 10f), 1e-6f)
    }

    @Test
    fun positiveModulo_negativeInput_wrapsIntoRange() {
        assertEquals(7f, positiveModulo(-3f, 10f), 1e-6f)
        assertEquals(9.5f, positiveModulo(-0.5f, 10f), 1e-6f)
    }

    @Test
    fun positiveModulo_largeParallaxOffset_staysOnScreen() {
        // 模拟摘星高空视差：基值 150 + 偏移 -3000，span 2000 → 仍落在 [0, 2000)
        val y = positiveModulo(150f - 3000f, 2000f)
        assertTrue("y=$y 必须仍在 [0, span)", y >= 0f && y < 2000f)
    }

    @Test
    fun positiveModulo_zeroSpan_isSafe() {
        assertEquals(0f, positiveModulo(5f, 0f), 1e-6f)
    }
}
