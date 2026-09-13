package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场地主题系统回归：id 稳定性 / 非法回退、摩擦从滑到涩的单调性、
 * [HockeyLogic.integratePuck] 与整局 tick 都使用当前主题摩擦。
 */
class HockeyRinkThemeTest {
    @Test
    fun fromId_roundTripsAllStableIds() {
        RinkTheme.entries.forEach { theme ->
            assertEquals(theme, RinkTheme.fromId(theme.id))
        }
        assertEquals("ice", RinkTheme.ICE.id)
        assertEquals("bear", RinkTheme.BEAR.id)
        assertEquals("wood", RinkTheme.WOOD.id)
        assertEquals("concrete", RinkTheme.CONCRETE.id)
        assertEquals(RinkTheme.ICE, RinkTheme.DEFAULT)
    }

    @Test
    fun fromId_invalidNullOrDifferentCase_fallsBackToIce() {
        assertEquals(RinkTheme.ICE, RinkTheme.fromId(null))
        assertEquals(RinkTheme.ICE, RinkTheme.fromId(""))
        assertEquals(RinkTheme.ICE, RinkTheme.fromId("ICE"))
        assertEquals(RinkTheme.ICE, RinkTheme.fromId("lava"))
    }

    @Test
    fun friction_increasesFromIceToConcrete() {
        val ordered = RinkTheme.entries.map { it.friction }
        assertEquals(0.18f, RinkTheme.ICE.friction, 1e-6f)
        assertEquals(0.22f, RinkTheme.BEAR.friction, 1e-6f)
        assertEquals(0.28f, RinkTheme.WOOD.friction, 1e-6f)
        assertEquals(0.38f, RinkTheme.CONCRETE.friction, 1e-6f)
        assertEquals(ordered.sorted(), ordered)
        ordered.zipWithNext().forEach { (slippery, grippy) ->
            assertTrue("摩擦必须严格递增：$slippery -> $grippy", grippy > slippery)
        }
    }

    /**
     * 同初速、固定 dt 推 2 秒：位移 ICE > BEAR > WOOD > CONCRETE。
     * 用 `minSpeedMul = 0` 去掉最低速度钳制，让摩擦差异完全体现。
     */
    @Test
    fun displacementAfterTwoSeconds_orderedSlipperyFirst() {
        fun displacement(theme: RinkTheme): Float {
            val table = HockeyLogic.layoutTable(100f, 100f, 0f, 0f)
            val puck = Body(table.midX, table.midY, vx = 10f, vy = 0f, r = table.puckR)
            val dt = 1f / 120f
            repeat(240) {
                HockeyLogic.integratePuck(puck, table, dt, friction = theme.friction, minSpeedMul = 0f)
            }
            return puck.x - table.midX
        }

        val ice = displacement(RinkTheme.ICE)
        val bear = displacement(RinkTheme.BEAR)
        val wood = displacement(RinkTheme.WOOD)
        val concrete = displacement(RinkTheme.CONCRETE)
        assertTrue("ICE($ice) 应比 BEAR($bear) 滑", ice > bear)
        assertTrue("BEAR($bear) 应比 WOOD($wood) 滑", bear > wood)
        assertTrue("WOOD($wood) 应比 CONCRETE($concrete) 滑", wood > concrete)
    }

    /** 整局 tick 也必须吃到主题摩擦：冰面主题 2 秒位移明显大于水泥地。 */
    @Test
    fun gameTick_usesActiveRinkThemeFriction() {
        fun run(theme: RinkTheme): Float {
            val game = HockeyGame()
            game.rinkTheme = theme
            game.resize(4000f, 8000f, 20f, 40f)
            game.startMatch()
            game.tick(2.1f)
            assertEquals(Phase.PLAYING, game.phase)
            val t = game.table!!
            game.puck.x = t.midX
            game.puck.y = t.midY
            game.puck.vx = t.shortSide * 0.15f
            game.puck.vy = 0f
            val startX = game.puck.x
            val dt = 1f / 120f
            repeat(240) { game.tick(dt) }
            return game.puck.x - startX
        }

        val ice = run(RinkTheme.ICE)
        val concrete = run(RinkTheme.CONCRETE)
        assertTrue("冰面位移($ice) 应大于水泥地($concrete)", ice > concrete)
    }
}
