package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路 / 墙明度差：节日关曾把地板画成红毯，和红墙糊在一起（战役第 22 关即节日）。
 * 各主题 floor / floorAlt 相对 wall 至少 3:1，才分得清哪是路。
 */
class MazeThemeContrastTest {
    @Test
    fun campaignLevel22_isFestival() {
        assertEquals(MazeTheme.FESTIVAL, mazeCampaignMeta(21).theme)
        assertEquals(22, mazeCampaignMeta(21).id)
    }

    @Test
    fun everyTheme_floorContrastsWithWall() {
        MazeTheme.entries.forEach { theme ->
            val style = mazeThemeStyle(theme)
            assertTrue(
                "${theme.name} floor/wall ${contrast(style.floor, style.wall)} < 3",
                contrast(style.floor, style.wall) >= 3.0 - 1e-6,
            )
            assertTrue(
                "${theme.name} floorAlt/wall ${contrast(style.floorAlt, style.wall)} < 3",
                contrast(style.floorAlt, style.wall) >= 3.0 - 1e-6,
            )
        }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}
