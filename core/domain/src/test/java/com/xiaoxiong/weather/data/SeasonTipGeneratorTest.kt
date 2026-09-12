package com.xiaoxiong.weather.data

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class SeasonTipGeneratorTest {
    private fun kinds(
        lat: Double,
        lon: Double,
        date: LocalDate,
        elevationMeters: Double? = null,
        admin1: String? = null,
    ) = SeasonTipGenerator.tipsFor(lat, lon, date, elevationMeters, admin1).map { it.kind }

    @Test
    fun beijing_coversFourBloomSeasons() {
        assertEquals(
            listOf(SeasonTipGenerator.Kind.WINTER_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 2, 10)),
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SPRING_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 4, 15)),
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SUMMER_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 7, 10)),
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 9, 20)),
        )
    }

    @Test
    fun beijing_october_showsAutumnAndFoliage() {
        val tips = SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 10, 20))
        assertEquals(
            setOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM, SeasonTipGenerator.Kind.FOLIAGE),
            tips.map { it.kind }.toSet(),
        )
    }

    @Test
    fun beijing_subjectsMatchSeason() {
        assertEquals(
            R.string.season_subjects_winter_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 2, 10)).single().subjectsRes,
        )
        assertEquals(
            R.string.season_subjects_spring_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 4, 15)).single().subjectsRes,
        )
        assertEquals(
            R.string.season_subjects_summer_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 7, 10)).single().subjectsRes,
        )
        assertEquals(
            R.string.season_subjects_autumn_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 9, 20)).single().subjectsRes,
        )
    }

    @Test
    fun hangzhou_september_osmanthus() {
        val tip = SeasonTipGenerator.tipsFor(30.3, 120.2, LocalDate.of(2026, 9, 25)).single()
        assertEquals(SeasonTipGenerator.Kind.AUTUMN_BLOOM, tip.kind)
        assertEquals(R.string.season_subjects_autumn_central, tip.subjectsRes)
    }

    @Test
    fun jinan_midAugust_showsSummerBloom() {
        // 济南真实坐标：盛夏应在夏花窗内并显示“时令物候”卡
        val tips = SeasonTipGenerator.tipsFor(36.6512, 117.1201, LocalDate.of(2026, 8, 21))
        assertTrue(tips.any { it.kind == SeasonTipGenerator.Kind.SUMMER_BLOOM })
    }

    @Test
    fun jinan_midLatitudeHasGapsButTodayIsInWindow() {
        // 中纬城市存在物候空窗期（如 8/26-8/31，夏花刚结束、秋花未始）；
        // 物候卡必须按“今天”推算，否则缓存快照的旧日期会把整卡顶成空。
        assertTrue(
            SeasonTipGenerator
                .tipsFor(36.6512, 117.1201, LocalDate.of(2026, 8, 21))
                .any { it.kind == SeasonTipGenerator.Kind.SUMMER_BLOOM },
        )
    }

    @Test
    fun jinan_endOfAugustCanHitEmptyWindow() {
        // 根因复现：8/26（夏花 8/25 收尾、桂花 9/10 才开始）存在空窗，
        // 缓存快照日期若落在这里，时令物候整卡会消失。
        assertTrue(
            SeasonTipGenerator
                .tipsFor(36.6512, 117.1201, LocalDate.of(2026, 8, 26))
                .isEmpty(),
        )
    }

    @Test
    fun cherrapunji_midAugust_showsSummerBloomAndUpcomingAutumn() {
        // 乞拉朋齐真实坐标（印度）：25°N 纬度带，8 月底除夏花外还临近桂花季
        val tips = SeasonTipGenerator.tipsFor(25.2847, 91.7217, LocalDate.of(2026, 8, 21))
        assertTrue(tips.any { it.kind == SeasonTipGenerator.Kind.SUMMER_BLOOM })
        assertTrue(tips.any { it.kind == SeasonTipGenerator.Kind.AUTUMN_BLOOM })
    }

    @Test
    fun hainan_noFoliage_butSeasonalBloom() {
        assertNull(SeasonTipGenerator.foliageWindow(18.0))
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SUMMER_BLOOM),
            kinds(18.2, 109.5, LocalDate.of(2026, 7, 10)),
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM),
            kinds(18.2, 109.5, LocalDate.of(2026, 10, 5)),
        )
    }

    @Test
    fun southernHemisphere_summerInJanuary() {
        val tip = SeasonTipGenerator.tipsFor(-33.9, 151.2, LocalDate.of(2026, 1, 20)).single()
        assertEquals(SeasonTipGenerator.Kind.SUMMER_BLOOM, tip.kind)
    }

    @Test
    fun southernHemisphere_springWindowMappedLocally() {
        val tip = SeasonTipGenerator.tipsFor(-33.9, 151.2, LocalDate.of(2026, 9, 20)).single()
        assertEquals(SeasonTipGenerator.Kind.SPRING_BLOOM, tip.kind)
        assertEquals(9, tip.windowStart.monthValue)
        assertEquals(10, tip.windowEnd.monthValue)
    }

    @Test
    fun plateau_detectedForLhasaArea() {
        assertTrue(SeasonTipGenerator.isHighPlateau(29.6, 91.1))
        assertEquals(
            SeasonTipGenerator.ReliefBand.PLATEAU,
            SeasonTipGenerator.reliefBand(3650.0, 29.6, 91.1, "西藏"),
        )
        assertEquals(
            SeasonTipGenerator.ReliefBand.PLAIN,
            SeasonTipGenerator.reliefBand(23.0, 30.5, 114.3, "湖北"),
        )
        assertEquals(
            SeasonTipGenerator.ReliefBand.HIGHLAND,
            SeasonTipGenerator.reliefBand(1890.0, 25.0, 102.7, "云南"),
        )
    }

    @Test
    fun sameLatitude_plateauElevationDelaysSpringVsYangtzePlain() {
        val date = LocalDate.of(2026, 3, 20)
        // 武汉平原：春花已入窗
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SPRING_BLOOM),
            kinds(30.5, 114.3, date, elevationMeters = 30.0, admin1 = "湖北"),
        )
        // 拉萨高原：春花推迟约 25 天，此时多为即将入窗
        val lhasa =
            SeasonTipGenerator.tipsFor(
                latitude = 29.6,
                longitude = 91.1,
                onDate = date,
                elevationMeters = 3650.0,
                admin1 = "西藏",
            )
        assertEquals(listOf(SeasonTipGenerator.Kind.SPRING_BLOOM), lhasa.map { it.kind })
        assertEquals(SeasonTipGenerator.Phase.UPCOMING, lhasa.single().phase)
        assertEquals(R.string.season_subjects_spring_plateau, lhasa.single().subjectsRes)
    }

    @Test
    fun plateauAdmin_fallbackWithoutElevation() {
        assertEquals(
            SeasonTipGenerator.ReliefBand.PLATEAU,
            SeasonTipGenerator.reliefBand(null, 36.6, 101.8, "青海"),
        )
    }

    @Test
    fun inWindow_crossYear() {
        val start = MonthDay.of(12, 15)
        val end = MonthDay.of(2, 28)
        assertTrue(SeasonTipGenerator.inWindow(MonthDay.of(1, 10), start, end))
        assertTrue(SeasonTipGenerator.inWindow(MonthDay.of(12, 20), start, end))
        assertTrue(!SeasonTipGenerator.inWindow(MonthDay.of(6, 1), start, end))
    }

    @Test
    fun subjects_byLatitudeBand() {
        assertEquals(
            R.string.season_subjects_spring_tropical,
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.SPRING_BLOOM, 18.0, false),
        )
        assertEquals(
            R.string.season_subjects_summer_central,
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.SUMMER_BLOOM, 31.0, false),
        )
        assertEquals(
            R.string.season_subjects_winter_north,
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.WINTER_BLOOM, 39.0, false),
        )
    }

    @Test
    fun warmerYear_springBloomWindowMovesEarlier() {
        // 武汉 4/20 本在春花窗（3/1–4/25）内；今年提前 10 天 → 窗口偏移 -10 天变为 2/19–4/15
        val warm =
            SeasonTipGenerator.tipsFor(
                30.5,
                114.3,
                LocalDate.of(2026, 4, 20),
                elevationMeters = 30.0,
                admin1 = "湖北",
                phenologyShiftDays = -10,
            )
        val plain =
            SeasonTipGenerator.tipsFor(
                30.5,
                114.3,
                LocalDate.of(2026, 4, 20),
                elevationMeters = 30.0,
                admin1 = "湖北",
            )
        assertTrue(plain.any { it.kind == SeasonTipGenerator.Kind.SPRING_BLOOM })
        // 提前 10 天后 4/20 已过新窗口且距下一年开窗超过 14 天 → 不再命中
        assertEquals(emptyList<SeasonTipGenerator.Kind>(), warm.map { it.kind })
    }

    @Test
    fun warmerYear_autumnFoliageMovesLater() {
        // 北京 10/10 本在红叶窗起点附近；今年提前 10 天 → 秋花/红叶相反：推迟 10 天
        val cold =
            SeasonTipGenerator.tipsFor(
                39.9,
                116.4,
                LocalDate.of(2026, 10, 10),
                phenologyShiftDays = -10,
            )
        val foliage = cold.firstOrNull { it.kind == SeasonTipGenerator.Kind.FOLIAGE }
        assertTrue("expected foliage tip, got ${cold.map { it.kind }}", foliage != null)
        // 红叶窗 10/10–11/15，推迟 10 天 → 10/20–11/25，10/10 已过（>14 天） → 不命中
        assertTrue(cold.none { it.kind == SeasonTipGenerator.Kind.FOLIAGE && it.phase == SeasonTipGenerator.Phase.PEAK })
    }

    @Test
    fun phenologyShift_windowEndOnLeapDay_doesNotCrashInNonLeapYear() {
        // 31.2°N 冬花窗 12/15–2/28，偏移 +1 → 端点落到 2/29；2026 非闰年不得抛 DateTimeException
        val tips =
            SeasonTipGenerator.tipsFor(
                latitude = 31.2,
                longitude = 120.0,
                onDate = LocalDate.of(2026, 1, 15),
                phenologyShiftDays = 1,
            )
        val winter = tips.firstOrNull { it.kind == SeasonTipGenerator.Kind.WINTER_BLOOM }
        assertTrue("expected winter bloom tip, got ${tips.map { it.kind }}", winter != null)
        assertEquals(MonthDay.of(2, 29), winter?.windowEnd)
        assertEquals(SeasonTipGenerator.Phase.PEAK, winter?.phase)
    }

    @Test
    fun phenologyShift_windowStartOnLeapDay_doesNotCrashAtHighLatitude() {
        // 52.9°N 冬花窗 3/1–4/10，偏移 -1 → 起点落到 2/29；4 月下旬走 nextOccurrence 分支
        val tips =
            SeasonTipGenerator.tipsFor(
                latitude = 52.9,
                longitude = 13.4,
                onDate = LocalDate.of(2026, 4, 25),
                phenologyShiftDays = -1,
            )
        assertTrue(tips.any { it.kind == SeasonTipGenerator.Kind.SPRING_BLOOM })
    }

    @Test
    fun phenologyShiftDays_carriedIntoTip() {
        val tips =
            SeasonTipGenerator.tipsFor(
                30.5,
                114.3,
                LocalDate.of(2026, 4, 15),
                elevationMeters = 30.0,
                admin1 = "湖北",
                phenologyShiftDays = 5,
            )
        val spring = tips.firstOrNull { it.kind == SeasonTipGenerator.Kind.SPRING_BLOOM }
        assertEquals(5, spring?.phenologyShiftDays)
    }
}
