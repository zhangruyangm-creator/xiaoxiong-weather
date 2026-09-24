package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 装饰性动画时钟。
 *
 * 关键不变式：**默认路径必须与开启减弱动画之前逐值相同**。
 * 只要 reduceMotion=false，decorativeTime 就是恒等函数——任何"顺手改成
 * 乘系数/加偏移"的改动都会在这里被挡住，避免悄悄改变默认动画手感。
 */
class DecorativeTimeTest {
    @Test
    fun defaultPath_isIdentity_soNormalExperienceUnchanged() {
        assertEquals(0f, decorativeTime(0f, false), 0f)
        assertEquals(1.25f, decorativeTime(1.25f, false), 0f)
        assertEquals(987.5f, decorativeTime(987.5f, false), 0f)
    }

    @Test
    fun reduceMotion_freezesClockAtZero() {
        assertEquals(0f, decorativeTime(0f, true), 0f)
        assertEquals(0f, decorativeTime(3.7f, true), 0f)
        assertEquals(0f, decorativeTime(999f, true), 0f)
    }

    @Test
    fun frozenClockIsStableAcrossFrames() {
        // 冻结必须是常数：sin(0)/cos(0) 固定，不能像"乘 0.15 慢动作"那样仍在动
        val a = decorativeTime(1.1f, true)
        val b = decorativeTime(2.9f, true)
        assertEquals(a, b, 0f)
    }
}
