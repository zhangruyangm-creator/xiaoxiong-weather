package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BearHomeLogicTest {
    @Test
    fun mealGroups_coverAllVariantsWithoutOverlap() {
        for (slot in HomeMealSlot.entries) {
            val groups = slot.groups()
            assertEquals(5, groups.size)
            val flat = groups.flatMap { it.dishes.map { d -> d.drawableRes } }
            // 图鉴切片必须与 SunPathMath 真实三餐池完全一致（顺序、内容、数量）
            assertEquals(slot.moment.bearVariantDrawables().toList(), flat)
            assertTrue(groups.all { it.dishes.isNotEmpty() })
        }
    }

    @Test
    fun everyDish_hasNameAndDetail() {
        for (slot in HomeMealSlot.entries) {
            val dishes = slot.dishes
            assertTrue(dishes.isNotEmpty())
            assertTrue(dishes.all { it.nameRes != 0 && it.detailRes != 0 })
        }
    }

    @Test
    fun routineStatus_marksCurrentSlotAsNow() {
        val date = java.time.LocalDate.of(2026, 8, 6) // Thursday
        val slots = homeRoutineSlots(date)
        val lunch = slots.first { it.moment.name == "LUNCH" }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(lunch, LocalTime.of(12, 0)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(lunch, LocalTime.of(15, 0)))
        assertEquals(HomeRoutineStatus.SOON, homeRoutineStatus(lunch, LocalTime.of(9, 0)))
    }

    @Test
    fun routineStatus_afterMidnight_onlySleepIsNow() {
        val date = java.time.LocalDate.of(2026, 8, 6) // Thursday
        val slots = homeRoutineSlots(date)
        val bedtime = slots.first { it.moment.name == "BEDTIME_PREP" }
        val sleep = slots.first { it.moment.name == "SLEEP" }
        for (t in listOf(LocalTime.of(0, 0), LocalTime.of(1, 30), LocalTime.of(4, 59))) {
            assertEquals("$t 时入睡准备应已过", HomeRoutineStatus.DONE, homeRoutineStatus(bedtime, t))
            assertEquals("$t 时睡觉应进行中", HomeRoutineStatus.NOW, homeRoutineStatus(sleep, t))
        }
    }

    @Test
    fun routineStatus_bedtimePrepStartsAt23h() {
        val date = java.time.LocalDate.of(2026, 8, 6) // Thursday
        val slots = homeRoutineSlots(date)
        val bedtime = slots.first { it.moment.name == "BEDTIME_PREP" }
        assertEquals(HomeRoutineStatus.SOON, homeRoutineStatus(bedtime, LocalTime.of(22, 59)))
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(bedtime, LocalTime.of(23, 0)))
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(bedtime, LocalTime.of(23, 59)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(bedtime, LocalTime.of(0, 0)))
    }

    @Test
    fun routineStatus_weekendRoutineCovered() {
        val date = java.time.LocalDate.of(2026, 8, 9) // Sunday
        val slots = homeRoutineSlots(date)
        assertEquals(12, slots.size)
        // 周末 9:00–9:45 也吃早餐，9:45 起进入第一段旅行
        val explore = slots.first { it.moment.name == "WEEKEND_EXPLORE" }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(explore, LocalTime.of(10, 0)))
        // 周末旅行拆成两段：上午 9:45–11:30 与下午 12:30–15:00，午后 15:00 起为周末下午
        val afternoonExplore = slots.first { it.moment.name == "WEEKEND_EXPLORE" && it.startMinute == 12 * 60 + 30 }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(afternoonExplore, LocalTime.of(13, 30)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(afternoonExplore, LocalTime.of(15, 30)))
        val afternoon = slots.first { it.moment.name == "WEEKEND_AFTERNOON" }
        assertEquals(15 * 60, afternoon.startMinute)
        assertEquals(18 * 60, afternoon.endMinute)
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(afternoon, LocalTime.of(16, 0)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(afternoon, LocalTime.of(18, 30)))
        // 周末 9:15 应在早餐段
        val breakfast = slots.first { it.moment.name == "BREAKFAST" }
        assertEquals(9 * 60, breakfast.startMinute)
        assertEquals(9 * 60 + 45, breakfast.endMinute)
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(breakfast, LocalTime.of(9, 15)))
    }

    @Test
    fun weekdayRoutine_includesMorningStrollAfterExercise() {
        val date = java.time.LocalDate.of(2026, 8, 6) // Thursday
        val slots = homeRoutineSlots(date)
        assertEquals(16, slots.size)
        val exercise = slots.first { it.moment.name == "MORNING_EXERCISE" }
        val stroll = slots.first { it.moment.name == "MORNING_STROLL" }
        assertEquals(6 * 60, exercise.startMinute)
        assertEquals(6 * 60 + 30, exercise.endMinute)
        assertEquals(6 * 60 + 30, stroll.startMinute)
        assertEquals(7 * 60 + 15, stroll.endMinute)
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(stroll, LocalTime.of(7, 0)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(exercise, LocalTime.of(7, 0)))
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(exercise, LocalTime.of(6, 15)))
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(stroll, LocalTime.of(6, 30)))
    }

    @Test
    fun routineDay_usesPreviousDayBefore5am() {
        val date = java.time.LocalDate.of(2026, 8, 6) // Thursday
        assertEquals(date, homeRoutineDay(date, LocalTime.of(6, 0)))
        assertEquals(date, homeRoutineDay(date, LocalTime.of(23, 59)))
        // 凌晨 [00:00, 05:00) 属于前一天（以 5:00 为日界）
        assertEquals(date.minusDays(1), homeRoutineDay(date, LocalTime.of(0, 0)))
        assertEquals(date.minusDays(1), homeRoutineDay(date, LocalTime.of(4, 59)))
        // 5:00 整点起为新一天
        assertEquals(date, homeRoutineDay(date, LocalTime.of(5, 0)))
    }

    @Test
    fun homeNarration_coversEveryMoment() {
        for (moment in com.xiaoxiong.weather.ui.weather.SunPathBearMoment.entries) {
            val res = homeMomentNarrationRes(moment)
            assertTrue("${moment.name} narration missing", res != 0)
        }
    }

    @Test
    fun balconySceneArt_coversAllWeatherDayAndNight() {
        val day = java.time.LocalDate.of(2026, 9, 5)
        for (weather in FarmWeather.entries) {
            val dayArt = balconySceneArt(weather, isNight = false, date = day)
            val nightArt = balconySceneArt(weather, isNight = true, date = day)
            assertTrue("$weather day art missing", dayArt != 0)
            assertTrue("$weather night art missing", nightArt != 0)
            assertTrue("$weather tip missing", balconyWeatherTip(weather, false) != 0)
        }
        // 晴天昼夜应不同主图；雨天昼夜可同图
        assertTrue(
            balconySceneArt(FarmWeather.SUNNY, false, day) !=
                balconySceneArt(FarmWeather.SUNNY, true, day),
        )
    }

    @Test
    fun homeWeatherLabel_resolvesRainStrengthAndFreezingRain() {
        // 降水强度细分：51/61/82 → 小/中/大雨
        assertEquals(R.string.balcony_weather_rain_light, homeWeatherLabelRes(51, isDay = true))
        assertEquals(R.string.balcony_weather_rain_moderate, homeWeatherLabelRes(61, isDay = true))
        assertEquals(R.string.balcony_weather_rain_heavy, homeWeatherLabelRes(82, isDay = true))
        // 冻雨 56/57/66/67 单独成类，与昼夜无关
        assertEquals(R.string.balcony_weather_freezing_rain, homeWeatherLabelRes(56, isDay = true))
        assertEquals(R.string.balcony_weather_freezing_rain, homeWeatherLabelRes(67, isDay = false))
        // 其余天气
        assertEquals(R.string.balcony_weather_storm, homeWeatherLabelRes(95, isDay = true))
        assertEquals(R.string.balcony_weather_snow, homeWeatherLabelRes(71, isDay = true))
        assertEquals(R.string.balcony_weather_fog, homeWeatherLabelRes(45, isDay = true))
        assertEquals(R.string.balcony_weather_cloudy, homeWeatherLabelRes(3, isDay = true))
        assertEquals(R.string.balcony_weather_sunny, homeWeatherLabelRes(0, isDay = true))
        assertEquals(R.string.bear_home_summary_weather_unknown, homeWeatherLabelRes(null, null))
    }

    @Test
    fun clearNight_isSpecialCasedInHome() {
        // common 口径不变：0/1 夜晚仍归多云
        assertEquals(FarmWeather.CLOUDY, farmWeatherFromScene(0, isDay = false))
        assertTrue(isClearNight(0, false))
        assertTrue(isClearNight(1, false))
        assertFalse(isClearNight(0, true))
        assertFalse(isClearNight(2, false))
        assertFalse(isClearNight(null, false))
        // home 内特判为晴天以复用晴夜插画/文案
        assertEquals(FarmWeather.SUNNY, homeDisplayWeather(0, isDay = false))
        assertEquals(FarmWeather.CLOUDY, homeDisplayWeather(3, isDay = false))
        assertEquals(R.string.balcony_weather_clear_night, homeWeatherLabelRes(1, isDay = false))
    }

    @Test
    fun streetSpots_haveArtAndCopy() {
        for (spot in StreetSpot.entries) {
            assertTrue("${spot.name} art", spot.artRes != 0)
            assertTrue("${spot.name} name", spot.nameRes != 0)
            assertTrue("${spot.name} tip day", spot.tipRes != 0)
            assertTrue("${spot.name} tip night", spot.tipNightRes != 0)
            assertTrue("${spot.name} status day", streetSpotStatusRes(spot, false) != 0)
            assertTrue("${spot.name} status night", streetSpotStatusRes(spot, true) != 0)
        }
        assertEquals(3, StreetSpot.entries.size)
    }
}
