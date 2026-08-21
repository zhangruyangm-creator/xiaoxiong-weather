package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 重构差分验证：用同一组输入，逐字节对比"纯逻辑函数输出"与"原始内联逻辑输出"，
 * 证明档位 B 引擎分离未引入数值/边界偏差。
 */
class RefactorParityTest {
    private val s = 1f

    /** 递归复刻 git HEAD 中 BearGame.tick 的物理段，返回 (y, vy, jumps, grounded, landed)。 */
    private fun originalPhysics(
        y: Float,
        vy: Float,
        jumps: Int,
        grounded: Boolean,
        floorY: Float,
        dt: Float,
    ): FloatArray {
        val g = 2500f * s
        val ovy = vy + g * dt
        val oy = y + ovy * dt
        if (oy >= floorY) {
            val landed = !grounded
            return floatArrayOf(floorY, 0f, 2f, 1f, if (landed) 1f else 0f)
        }
        return floatArrayOf(oy, ovy, jumps.toFloat(), 0f, 0f)
    }

    @Test
    fun stepBearPhysics_matchesOriginalInline() {
        val floorY = 0f
        val dt = 0.016f
        for (g in listOf(true, false)) {
            for (y in listOf(-300f, -100f, -1f, 0f, 10f, 500f)) {
                for (vy0 in listOf(-960f, 0f, 300f, 800f, 1500f)) {
                    val jumps = 2
                    val p =
                        BearGameLogic.stepBearPhysics(
                            bearY = y,
                            bearVY = vy0,
                            jumpsLeft = jumps,
                            grounded = g,
                            floorY = floorY,
                            s = s,
                            dt = dt,
                        )
                    val o = originalPhysics(y, vy0, jumps, g, floorY, dt)
                    assertEquals("y@ $y,$vy0,$g:", o[0], p.bearY, 1e-4f)
                    assertEquals("vy:", o[1], p.bearVY, 1e-4f)
                    assertEquals("jumps:", o[2].toInt(), p.jumpsLeft)
                    assertEquals("grounded:", o[3].toInt() == 1, p.grounded)
                    assertEquals("landed:", o[4].toInt() == 1, p.landed)
                }
            }
        }
    }

    @Test
    fun speedAt_matchesOriginalInline() {
        for (d in listOf(0f, 100f, 1000f, 2500f, 5000f, 9999f, 20000f)) {
            val p = (d / 5000f).coerceIn(0f, 1f)
            val expected = (340f + 610f * sqrt(p)) * s
            assertEquals(expected, BearGameLogic.speedAt(d, s), 1e-4f)
        }
    }

    @Test
    fun runPhaseRate_matchesOriginalInline() {
        for (sp in listOf(0f, 300f, 640f, 800f, 2000f)) {
            val expected = min(sp, 640f * s) * 0.031f / s
            assertEquals(expected, BearGameLogic.runPhaseRate(sp, s), 1e-5f)
        }
    }

    @Test
    fun score_matchesOriginalInline() {
        for (d in listOf(0f, 60f, 123f, 5000f)) {
            for (c in listOf(0, 1, 5)) {
                val expected = if (s <= 0f) 0 else (d / (60f * s)).toInt() + c * 10
                assertEquals(expected, BearGameLogic.score(d, c, s))
            }
        }
    }
}
