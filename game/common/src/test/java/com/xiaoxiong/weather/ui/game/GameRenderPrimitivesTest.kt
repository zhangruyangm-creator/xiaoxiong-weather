package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [strokeOf] 与 [gameHash01] 的 JVM 单测（均为纯逻辑，无 Android 依赖）。 */
class GameRenderPrimitivesTest {
    // ── strokeOf ──────────────────────────────────────────────

    @Test
    fun `strokeOf quantizes width to 0_1px`() {
        assertEquals(2.0f, strokeOf(2.04f).width, 1e-6f)
        assertEquals(2.1f, strokeOf(2.06f).width, 1e-6f)
        assertEquals(1.5f, strokeOf(1.46f).width, 1e-6f)
    }

    @Test
    fun `strokeOf same quantized width reuses instance`() {
        val first = strokeOf(2.0f)
        val second = strokeOf(2.04f)
        assertSame(first, second)
    }

    @Test
    fun `strokeOf different widths do not share instance`() {
        assertNotSame(strokeOf(1.0f), strokeOf(2.0f))
    }

    @Test
    fun `strokeOf clamps non-positive width`() {
        assertEquals(0.1f, strokeOf(0f).width, 1e-6f)
        assertEquals(0.1f, strokeOf(-3f).width, 1e-6f)
    }

    // ── gameHash01 ────────────────────────────────────────────

    @Test
    fun `gameHash01 is deterministic`() {
        assertEquals(gameHash01(42), gameHash01(42), 0f)
        assertEquals(gameHash01(42, 7), gameHash01(42, 7), 0f)
    }

    @Test
    fun `gameHash01 stays in unit range`() {
        val samples = listOf(-1_000_000, -1, 0, 1, 2, 7, 12345, Int.MAX_VALUE, Int.MIN_VALUE)
        samples.forEach { i ->
            val value = gameHash01(i)
            assertTrue("gameHash01($i)=$value 必须落在 [0,1)", value >= 0f && value < 1f)
        }
    }

    @Test
    fun `gameHash01 matches legacy formula`() {
        // 与原天气特效 / 迷宫实现（ushr 变体）逐位一致，替换后视觉不变
        assertEquals(0f, gameHash01(0), 1e-6f)
        assertEquals(0.01624892f, gameHash01(1), 1e-6f)
        assertEquals(0.24412268f, gameHash01(7, 3), 1e-6f)
        assertEquals(0.32770234f, gameHash01(1, 1), 1e-6f)
        // 固定非 1 的更大样例，防止未来把 ushr 改回 shr 而无声改变纹理
        assertEquals(0.67249666f, gameHash01(3), 1e-6f)
    }

    @Test
    fun `gameHash01 salt changes value`() {
        assertNotEquals(gameHash01(5), gameHash01(5, 1))
    }

    private fun assertNotEquals(
        unexpected: Float,
        actual: Float,
    ) {
        assertTrue("期望 $unexpected != $actual", unexpected != actual)
    }
}
