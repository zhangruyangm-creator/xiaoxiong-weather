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
        admin1: String? = null
    ) = SeasonTipGenerator.tipsFor(lat, lon, date, elevationMeters, admin1).map { it.kind }

    @Test
    fun beijing_coversFourBloomSeasons() {
        assertEquals(
            listOf(SeasonTipGenerator.Kind.WINTER_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 2, 10))
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SPRING_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 4, 15))
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SUMMER_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 7, 10))
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM),
            kinds(39.9, 116.4, LocalDate.of(2026, 9, 20))
        )
    }

    @Test
    fun beijing_october_showsAutumnAndFoliage() {
        val tips = SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 10, 20))
        assertEquals(
            setOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM, SeasonTipGenerator.Kind.FOLIAGE),
            tips.map { it.kind }.toSet()
        )
    }

    @Test
    fun beijing_subjectsMatchSeason() {
        assertEquals(
            R.string.season_subjects_winter_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 2, 10)).single().subjectsRes
        )
        assertEquals(
            R.string.season_subjects_spring_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 4, 15)).single().subjectsRes
        )
        assertEquals(
            R.string.season_subjects_summer_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 7, 10)).single().subjectsRes
        )
        assertEquals(
            R.string.season_subjects_autumn_north,
            SeasonTipGenerator.tipsFor(39.9, 116.4, LocalDate.of(2026, 9, 20)).single().subjectsRes
        )
    }

    @Test
    fun hangzhou_september_osmanthus() {
        val tip = SeasonTipGenerator.tipsFor(30.3, 120.2, LocalDate.of(2026, 9, 25)).single()
        assertEquals(SeasonTipGenerator.Kind.AUTUMN_BLOOM, tip.kind)
        assertEquals(R.string.season_subjects_autumn_central, tip.subjectsRes)
    }

    @Test
    fun hainan_noFoliage_butSeasonalBloom() {
        assertNull(SeasonTipGenerator.foliageWindow(18.0))
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SUMMER_BLOOM),
            kinds(18.2, 109.5, LocalDate.of(2026, 7, 10))
        )
        assertEquals(
            listOf(SeasonTipGenerator.Kind.AUTUMN_BLOOM),
            kinds(18.2, 109.5, LocalDate.of(2026, 10, 5))
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
            SeasonTipGenerator.reliefBand(3650.0, 29.6, 91.1, "西藏")
        )
        assertEquals(
            SeasonTipGenerator.ReliefBand.PLAIN,
            SeasonTipGenerator.reliefBand(23.0, 30.5, 114.3, "湖北")
        )
        assertEquals(
            SeasonTipGenerator.ReliefBand.HIGHLAND,
            SeasonTipGenerator.reliefBand(1890.0, 25.0, 102.7, "云南")
        )
    }

    @Test
    fun sameLatitude_plateauElevationDelaysSpringVsYangtzePlain() {
        val date = LocalDate.of(2026, 3, 20)
        // 武汉平原：春花已入窗
        assertEquals(
            listOf(SeasonTipGenerator.Kind.SPRING_BLOOM),
            kinds(30.5, 114.3, date, elevationMeters = 30.0, admin1 = "湖北")
        )
        // 拉萨高原：春花推迟约 25 天，此时多为即将入窗
        val lhasa = SeasonTipGenerator.tipsFor(
            latitude = 29.6,
            longitude = 91.1,
            onDate = date,
            elevationMeters = 3650.0,
            admin1 = "西藏"
        )
        assertEquals(listOf(SeasonTipGenerator.Kind.SPRING_BLOOM), lhasa.map { it.kind })
        assertEquals(SeasonTipGenerator.Phase.UPCOMING, lhasa.single().phase)
        assertEquals(R.string.season_subjects_spring_plateau, lhasa.single().subjectsRes)
    }

    @Test
    fun plateauAdmin_fallbackWithoutElevation() {
        assertEquals(
            SeasonTipGenerator.ReliefBand.PLATEAU,
            SeasonTipGenerator.reliefBand(null, 36.6, 101.8, "青海")
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
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.SPRING_BLOOM, 18.0, false)
        )
        assertEquals(
            R.string.season_subjects_summer_central,
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.SUMMER_BLOOM, 31.0, false)
        )
        assertEquals(
            R.string.season_subjects_winter_north,
            SeasonTipGenerator.subjectsRes(SeasonTipGenerator.Kind.WINTER_BLOOM, 39.0, false)
        )
    }
}
