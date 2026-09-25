package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 台球回放文本的容错解码。
 *
 * 背景：原先用 `f[0].toFloat()` 解析，遇到脏数据抛 NumberFormatException，
 * 被外层 runCatching 吞掉 → **整段回放作废**（与已修的城市列表
 * 「单条坏数据抹掉整批」同型）。这里改为逐杆容错：坏杆只丢自己。
 */
class PoolReplayCodecTest {
    private fun line(
        angle: String = "0.5",
        power: String = "0.8",
        side: String = "0.1",
        vertical: String = "-0.1",
        elevation: String = "0.2",
        x: String = "100.0",
        y: String = "200.0",
    ) = "$angle,$power,$side,$vertical,$elevation,$x,$y"

    private fun raw(vararg shots: String): String = "NINE_BALL|42|PLAYER|${shots.joinToString(";")}"

    @Test
    fun `parses a well-formed replay`() {
        val replay = parseReplay(raw(line(), line(angle = "0.6")))
        assertNotNull(replay)
        assertEquals("NINE_BALL", replay!!.mode)
        assertEquals(42L, replay.seed)
        assertEquals("PLAYER", replay.breaker)
        assertEquals(2, replay.shots.size)
        assertEquals(0.5f, replay.shots[0].angle, 1e-6f)
        assertEquals(100f, replay.shots[0].x, 1e-6f)
        assertEquals(200f, replay.shots[0].y, 1e-6f)
    }

    @Test
    fun `one malformed shot does not discard the rest`() {
        val replay = parseReplay(raw(line(), line(angle = "BAD"), line(angle = "0.7")))
        assertNotNull("坏杆只应丢自己", replay)
        assertEquals(2, replay!!.shots.size)
        assertEquals(0.5f, replay.shots[0].angle, 1e-6f)
        assertEquals(0.7f, replay.shots[1].angle, 1e-6f)
    }

    @Test
    fun `non-finite shot values are dropped`() {
        val replay = parseReplay(raw(line(angle = "NaN"), line(power = "Infinity"), line()))
        assertNotNull(replay)
        assertTrue("非有限值不得进入物理", replay!!.shots.none { !it.angle.isFinite() })
        assertEquals(1, replay.shots.size)
    }

    @Test
    fun `shot with too few fields is dropped`() {
        val replay = parseReplay(raw(line(), "1.0,2.0"))
        assertNotNull(replay)
        assertEquals(1, replay!!.shots.size)
    }

    @Test
    fun `optional x and y default to zero`() {
        val replay = parseReplay(raw("1.0,2.0,0.0,0.0,0.0"))
        assertNotNull(replay)
        assertEquals(0f, replay!!.shots.single().x, 0f)
        assertEquals(0f, replay.shots.single().y, 0f)
    }

    @Test
    fun `present-but-invalid x drops the shot instead of silently using zero`() {
        val replay = parseReplay(raw(line(x = "BAD"), line(angle = "0.7")))
        assertNotNull(replay)
        assertEquals(
            "字段存在却非法时不能当 0（会把白球摆到球桌角落），应丢这一杆",
            1,
            replay!!.shots.size,
        )
        assertEquals(0.7f, replay.shots.single().angle, 1e-6f)
    }

    @Test
    fun `present-but-empty y drops the shot`() {
        val replay = parseReplay(raw(line(y = ""), line(angle = "0.7")))
        assertNotNull(replay)
        assertEquals(1, replay!!.shots.size)
        assertEquals(0.7f, replay.shots.single().angle, 1e-6f)
    }

    @Test
    fun `replay with no valid shot is null`() {
        assertNull(parseReplay(raw(line(angle = "BAD"), line(power = "oops"))))
    }

    @Test
    fun `malformed header is null`() {
        assertNull(parseReplay("NINE_BALL|42"))
    }

    @Test
    fun `non-numeric seed is null`() {
        assertNull(parseReplay("NINE_BALL|xx|PLAYER|${line()}"))
    }

    @Test
    fun `empty shot list is null`() {
        assertNull(parseReplay("NINE_BALL|42|PLAYER|"))
    }
}
