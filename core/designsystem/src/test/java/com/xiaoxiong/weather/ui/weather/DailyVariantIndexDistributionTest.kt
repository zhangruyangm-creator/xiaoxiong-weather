package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * dailyVariantIndex 分布与稳定性回归。
 *
 * 该函数既要保证当天稳定（同一日期 + 同一 salt + 同一 size 始终同一索引），
 * 又要在跨天/跨周上有足够的多样性，避免用户感觉"周末早晨总是同一张"。
 *
 * 历史问题：原 `epochDay * 1_000_003 + salt.hashCode()` 在 size=32（WEEKEND_MORNING
 * 池）下，相邻日期模 31 步长固定，连续几天视觉重复感强。改进后乘子改为
 * 1_000_033，并对 salt 乘 31 加扰，确保多种 size 下分布都均匀。
 */
class DailyVariantIndexDistributionTest {
    private val base = LocalDate.of(2026, 8, 6)

    @Test
    fun evening_poolCoversAllSlotsWithin129Days() {
        val seen = mutableSetOf<Int>()
        for (i in 0L..128L) {
            seen += dailyVariantIndex(base.plusDays(i), "EVENING", 69)
        }
        assertEquals(69, seen.size)
    }

    @Test
    fun weekendMorning_poolCoversAllSlotsWithin32Days() {
        // WEEKEND_DAY_DRAWABLES 池 = 32 张；32 天连续抽样应能覆盖所有 32 张
        val seen = mutableSetOf<Int>()
        for (i in 0L..31L) {
            seen += dailyVariantIndex(base.plusDays(i), "WEEKEND_MORNING", 32)
        }
        assertEquals(32, seen.size)
    }

    @Test
    fun commute_poolCoversAllSlotsWithin9Days() {
        // COMMUTE_OUT_DRAWABLES = 9 张；连续 9 天应覆盖全部
        val seen = mutableSetOf<Int>()
        for (i in 0L..8L) {
            seen += dailyVariantIndex(base.plusDays(i), "COMMUTE", 9)
        }
        assertEquals(9, seen.size)
    }

    @Test
    fun stableForSameDay() {
        val day = base
        repeat(10) {
            assertEquals(
                dailyVariantIndex(day, "EVENING", 60),
                dailyVariantIndex(day, "EVENING", 60),
            )
        }
    }

    @Test
    fun distinctSaltsProduceDifferentSeries() {
        val a = (0L..30L).map { dailyVariantIndex(base.plusDays(it), "SALT_A", 16) }
        val b = (0L..30L).map { dailyVariantIndex(base.plusDays(it), "SALT_B", 16) }
        // 同一日期不同 salt 必须给出不同索引
        val diffs = a.zip(b).count { (x, y) -> x != y }
        assertTrue("diffs=$diffs", diffs >= 25)
    }

    @Test
    fun outputAlwaysInRange() {
        for (size in listOf(1, 2, 8, 20, 31, 47, 60, 100)) {
            for (i in 0L..60L) {
                val v = dailyVariantIndex(base.plusDays(i), "STABILITY", size)
                assertTrue("size=$size out of range: $v", v in 0 until size.coerceAtLeast(1))
            }
        }
    }

    @Test
    fun weekend_morning_consecutiveSundaysDoNotCollide() {
        // 真实场景：用户连续几个周末早晨都打开 App。
        // 期望相邻两个周日不会落在同一张图（避免"周末总是同一张"的体验）。
        val sundays =
            listOf(
                "2026-08-23",
                "2026-08-16",
                "2026-08-09",
                "2026-08-02",
                "2026-07-26",
                "2026-07-19",
                "2026-07-12",
                "2026-07-05",
            ).map(LocalDate::parse)
        val indices = sundays.map { dailyVariantIndex(it, "WEEKEND_MORNING", 32) }
        assertEquals("相邻周日不应落到同一张图", indices.size, indices.toSet().size)
    }
}
