package com.xiaoxiong.weather.ui.weather

import com.xiaoxiong.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.xiaoxiong.weather.resillustrations.R as IllustR

class SunPathMathTest {
    private val sunrise = LocalDateTime.of(2026, 8, 6, 6, 0)
    private val sunset = LocalDateTime.of(2026, 8, 6, 18, 0)

    @Test
    fun midday_isHalfwayOnDayArc() {
        val phase = sunPathPhase(LocalDateTime.of(2026, 8, 6, 12, 0), sunrise, sunset)!!
        assertTrue(phase.isDay)
        assertEquals(0.5f, phase.progress, 0.001f)
    }

    @Test
    fun atSunrise_progressZero() {
        val phase = sunPathPhase(sunrise, sunrise, sunset)!!
        assertTrue(phase.isDay)
        assertEquals(0f, phase.progress, 0.001f)
    }

    @Test
    fun atSunset_stillDayEnd() {
        val phase = sunPathPhase(sunset, sunrise, sunset)!!
        assertTrue(phase.isDay)
        assertEquals(1f, phase.progress, 0.001f)
    }

    @Test
    fun afterSunset_nightProgress() {
        val phase = sunPathPhase(LocalDateTime.of(2026, 8, 6, 21, 0), sunrise, sunset)!!
        assertFalse(phase.isDay)
        // night 18:00 → next 06:00 = 12h; 21:00 is 3/12
        assertEquals(0.25f, phase.progress, 0.001f)
    }

    @Test
    fun beforeSunrise_nightNearEnd() {
        val phase = sunPathPhase(LocalDateTime.of(2026, 8, 6, 3, 0), sunrise, sunset)!!
        assertFalse(phase.isDay)
        // prev sunset 18:00 → 06:00; 03:00 is 9/12
        assertEquals(0.75f, phase.progress, 0.001f)
    }

    @Test
    fun invalidWindow_returnsNull() {
        assertNull(sunPathPhase(sunrise, sunset, sunrise))
    }

    @Test
    fun bearMoment_byClock_weekday() {
        val weekday = LocalDate.of(2026, 8, 6) // Thursday
        val day = SunPathPhase(true, 0.5f)
        val sunrise = LocalTime.of(5, 30)
        assertEquals(
            SunPathBearMoment.MORNING_EXERCISE,
            sunPathBearMoment(day, weekday, LocalTime.of(5, 45), sunrise = sunrise),
        )
        assertEquals(
            SunPathBearMoment.MORNING_STROLL,
            sunPathBearMoment(day, weekday, LocalTime.of(6, 20), sunrise = sunrise),
        )
        assertEquals(
            SunPathBearMoment.MORNING_STROLL,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 0), sunrise = sunrise),
        )
        assertEquals(
            SunPathBearMoment.BREAKFAST,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 15), sunrise = sunrise),
        )
        assertEquals(
            SunPathBearMoment.BREAKFAST,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 45)),
        )
        assertEquals(
            SunPathBearMoment.MORNING_OUT,
            sunPathBearMoment(day, weekday, LocalTime.of(8, 15)),
        )
        assertEquals(
            SunPathBearMoment.WORK_MORNING,
            sunPathBearMoment(day, weekday, LocalTime.of(10, 0)),
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, weekday, LocalTime.of(12, 0)),
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(14, 0)),
        )
        assertEquals(
            SunPathBearMoment.AFTERNOON_TEA,
            sunPathBearMoment(day, weekday, LocalTime.of(15, 30)),
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(16, 45)),
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(17, 45)),
        )
        assertEquals(
            SunPathBearMoment.HEAD_HOME,
            sunPathBearMoment(day, weekday, LocalTime.of(18, 0)),
        )
        assertEquals(
            SunPathBearMoment.HEAD_HOME,
            sunPathBearMoment(day, weekday, LocalTime.of(18, 30)),
        )
        assertEquals(
            SunPathBearMoment.EVENING_EXERCISE,
            sunPathBearMoment(day, weekday, LocalTime.of(18, 45)),
        )
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(day, weekday, LocalTime.of(19, 0)),
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, weekday, LocalTime.of(11, 30)),
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(13, 30)),
        )
    }

    @Test
    fun bearMoment_weekdayMorningExerciseCappedBySunrise() {
        val weekday = LocalDate.of(2026, 8, 6)
        val day = SunPathPhase(true, 0.5f)
        // 日出 06:00 → 晨练封顶 06:30，其后逛逛
        val sunriseSix = LocalTime.of(6, 0)
        assertEquals(
            SunPathBearMoment.MORNING_EXERCISE,
            sunPathBearMoment(day, weekday, LocalTime.of(6, 15), sunrise = sunriseSix),
        )
        assertEquals(
            SunPathBearMoment.MORNING_STROLL,
            sunPathBearMoment(day, weekday, LocalTime.of(6, 30), sunrise = sunriseSix),
        )
        assertEquals(
            SunPathBearMoment.MORNING_STROLL,
            sunPathBearMoment(day, weekday, LocalTime.of(6, 50), sunrise = sunriseSix),
        )
        // 日出 07:00 → 白天开始即已过封顶，直接逛逛至早餐
        val afterCap = LocalTime.of(7, 0)
        assertEquals(
            SunPathBearMoment.MORNING_STROLL,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 5), sunrise = afterCap),
        )
        assertEquals(
            SunPathBearMoment.BREAKFAST,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 15), sunrise = afterCap),
        )
    }

    @Test
    fun morningStroll_sharesExplorePoolWithWeekend() {
        val weekday = LocalDate.of(2026, 8, 6)
        val explore = SunPathBearMoment.WEEKEND_EXPLORE.bearVariantDrawables().toSet()
        val stroll = SunPathBearMoment.MORNING_STROLL.bearVariantDrawables().toSet()
        assertEquals(explore, stroll)
        assertTrue(explore.size > 1)
        val a = SunPathBearMoment.MORNING_STROLL.bearResFor(weekday)
        assertEquals(a, SunPathBearMoment.MORNING_STROLL.bearResFor(weekday))
        assertEquals(
            R.string.sun_bear_morning_stroll,
            SunPathBearMoment.MORNING_STROLL.captionResFor(weekday),
        )
    }

    @Test
    fun afternoonTea_drinkMatchesCaptionAndIsWeekdayOnly() {
        val weekday = LocalDate.of(2026, 8, 6)
        val saturday = LocalDate.of(2026, 8, 8)
        val day = SunPathPhase(true, 0.5f)
        assertEquals(
            SunPathBearMoment.WEEKEND_AFTERNOON,
            sunPathBearMoment(day, saturday, LocalTime.of(15, 30)),
        )
        val drinks =
            listOf(
                IllustR.drawable.ic_sun_bear_tea_coffee to R.string.sun_bear_tea_coffee,
                IllustR.drawable.ic_sun_bear_tea_tea to R.string.sun_bear_tea_tea,
                IllustR.drawable.ic_sun_bear_tea_milk to R.string.sun_bear_tea_milk,
                IllustR.drawable.ic_sun_bear_tea_juice to R.string.sun_bear_tea_juice,
                IllustR.drawable.ic_sun_bear_tea_cocoa to R.string.sun_bear_tea_cocoa,
                IllustR.drawable.ic_sun_bear_tea_dessert to R.string.sun_bear_tea_dessert,
                IllustR.drawable.ic_sun_bear_tea_lemontea to R.string.sun_bear_tea_lemontea,
                IllustR.drawable.ic_sun_bear_tea_icecream to R.string.sun_bear_tea_icecream,
                IllustR.drawable.ic_sun_bear_tea_eggtart to R.string.sun_bear_tea_eggtart,
                IllustR.drawable.ic_sun_bear_tea_fruit to R.string.sun_bear_tea_fruit,
                IllustR.drawable.ic_sun_bear_tea_instantnoodles to R.string.sun_bear_tea_instantnoodles,
                IllustR.drawable.ic_sun_bear_tea_conveniencesnack to R.string.sun_bear_tea_conveniencesnack,
                IllustR.drawable.ic_sun_bear_tea_yogurt to R.string.sun_bear_tea_yogurt,
                IllustR.drawable.ic_sun_bear_tea_croissant to R.string.sun_bear_tea_croissant,
            )
        val seen = mutableSetOf<Int>()
        for (i in 0L..80L) {
            val d = weekday.plusDays(i)
            val res = SunPathBearMoment.AFTERNOON_TEA.bearResFor(d)
            val caption = SunPathBearMoment.AFTERNOON_TEA.captionResFor(d)
            val pair = drinks.first { it.first == res }
            assertEquals(pair.second, caption)
            seen += res
        }
        assertEquals(14, seen.size)
    }

    @Test
    fun bearMoment_weekendUsesTravelInsteadOfWork() {
        val saturday = LocalDate.of(2026, 8, 8)
        val day = SunPathPhase(true, 0.5f)
        assertTrue(isWeekend(saturday))
        // 凌晨 5–7 点仍属「临近天亮」，避免 5 点就显示「周末出门玩啦」
        assertEquals(
            SunPathBearMoment.ALMOST_DAWN,
            sunPathBearMoment(day, saturday, LocalTime.of(5, 30)),
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_MORNING,
            sunPathBearMoment(day, saturday, LocalTime.of(8, 0)),
        )
        // 9:00–9:45 周末也吃早午餐，与卧室作息对齐
        assertEquals(
            SunPathBearMoment.BREAKFAST,
            sunPathBearMoment(day, saturday, LocalTime.of(9, 15)),
        )
        // 9:45 起进入第一段旅行
        assertEquals(
            SunPathBearMoment.WEEKEND_EXPLORE,
            sunPathBearMoment(day, saturday, LocalTime.of(10, 0)),
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, saturday, LocalTime.of(12, 15)),
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_EXPLORE,
            sunPathBearMoment(day, saturday, LocalTime.of(13, 30)),
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_AFTERNOON,
            sunPathBearMoment(day, saturday, LocalTime.of(15, 0)),
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_RETURN,
            sunPathBearMoment(day, saturday, LocalTime.of(18, 0)),
        )
        val a = SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday)
        assertEquals(a, SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday))
        val seen =
            (0L..20L)
                .map {
                    SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday.plusWeeks(it))
                }.toSet()
        assertTrue(seen.size > 1)
        // 上午/下午共用大池，各自按日抽取（盐不同），同一天可以不同图
        val morningPool = SunPathBearMoment.WEEKEND_MORNING.bearVariantDrawables().toSet()
        val afternoonPool = SunPathBearMoment.WEEKEND_AFTERNOON.bearVariantDrawables().toSet()
        assertEquals(morningPool, afternoonPool)
        assertTrue(morningPool.size >= 28)
        val morning = SunPathBearMoment.WEEKEND_MORNING.bearResFor(saturday)
        val afternoon = SunPathBearMoment.WEEKEND_AFTERNOON.bearResFor(saturday)
        assertTrue(morning in morningPool)
        assertTrue(afternoon in afternoonPool)
    }

    @Test
    fun bearMoment_nightDinnerThenSleep() {
        val night = SunPathPhase(false, 0.5f)
        val weekday = LocalDate.of(2026, 8, 6) // Thursday
        val saturday = LocalDate.of(2026, 8, 8)
        // 工作日入夜后：先补通勤/锻炼，再晚饭
        assertEquals(
            SunPathBearMoment.HEAD_HOME,
            sunPathBearMoment(night, weekday, LocalTime.of(17, 30)),
        )
        assertEquals(
            SunPathBearMoment.EVENING_EXERCISE,
            sunPathBearMoment(night, weekday, LocalTime.of(18, 45)),
        )
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(night, weekday, LocalTime.of(19, 0)),
        )
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(night, weekday, LocalTime.of(19, 45)),
        )
        // 周末入夜不补工作日通勤/锻炼
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(night, saturday, LocalTime.of(19, 0)),
        )
        assertEquals(
            SunPathBearMoment.EVENING,
            sunPathBearMoment(night, weekday, LocalTime.of(21, 0)),
        )
        assertEquals(
            SunPathBearMoment.EVENING,
            sunPathBearMoment(night, weekday, LocalTime.of(22, 30)),
        )
        assertEquals(
            SunPathBearMoment.BEDTIME_PREP,
            sunPathBearMoment(night, weekday, LocalTime.of(23, 0)),
        )
        assertEquals(
            SunPathBearMoment.BEDTIME_PREP,
            sunPathBearMoment(night, weekday, LocalTime.of(23, 30)),
        )
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(0, 0)),
        )
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(2, 0)),
        )
        // 快天亮：日出前 30 分钟，需传入 sunrise
        assertEquals(
            SunPathBearMoment.ALMOST_DAWN,
            sunPathBearMoment(night, weekday, LocalTime.of(5, 30), sunrise = LocalTime.of(6, 0)),
        )
        // 没传 sunrise 时走 progress 兜底，progress=0.5 不触发（5:00 前仍是睡觉）
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(4, 0)),
        )
    }

    @Test
    fun evening_restVariantsRotateWithMatchingCaptions() {
        val day = LocalDate.of(2026, 8, 6)
        val pairs =
            listOf(
                IllustR.drawable.ic_sun_bear_evening to R.string.sun_bear_evening_tea,
                IllustR.drawable.ic_sun_bear_evening_game to R.string.sun_bear_evening_game,
                IllustR.drawable.ic_sun_bear_evening_book to R.string.sun_bear_evening_book,
                IllustR.drawable.ic_sun_bear_evening_phone to R.string.sun_bear_evening_phone,
                IllustR.drawable.ic_sun_bear_evening_tv to R.string.sun_bear_evening_tv,
                IllustR.drawable.ic_sun_bear_evening_music to R.string.sun_bear_evening_music,
                IllustR.drawable.ic_sun_bear_evening_knit to R.string.sun_bear_evening_knit,
                IllustR.drawable.ic_sun_bear_evening_diary to R.string.sun_bear_evening_diary,
                IllustR.drawable.ic_sun_bear_evening_pet to R.string.sun_bear_evening_pet,
                IllustR.drawable.ic_sun_bear_evening_guitar to R.string.sun_bear_evening_guitar,
                IllustR.drawable.ic_sun_bear_evening_treadmill to R.string.sun_bear_evening_treadmill,
                IllustR.drawable.ic_sun_bear_evening_yoga to R.string.sun_bear_evening_yoga,
                IllustR.drawable.ic_sun_bear_evening_draw to R.string.sun_bear_evening_draw,
                IllustR.drawable.ic_sun_bear_evening_balcony to R.string.sun_bear_evening_balcony,
                IllustR.drawable.ic_sun_bear_evening_cityview to R.string.sun_bear_evening_cityview,
                IllustR.drawable.ic_sun_bear_evening_lounge to R.string.sun_bear_evening_lounge,
                IllustR.drawable.ic_sun_bear_evening_videocall to R.string.sun_bear_evening_videocall,
                IllustR.drawable.ic_sun_bear_evening_boardgame to R.string.sun_bear_evening_boardgame,
                IllustR.drawable.ic_sun_bear_evening_lego to R.string.sun_bear_evening_lego,
                IllustR.drawable.ic_sun_bear_evening_study to R.string.sun_bear_evening_study,
                IllustR.drawable.ic_sun_bear_evening_balconysnack to R.string.sun_bear_evening_balconysnack,
                IllustR.drawable.ic_sun_bear_evening_dramacry to R.string.sun_bear_evening_dramacry,
                IllustR.drawable.ic_sun_bear_evening_balconymoon to R.string.sun_bear_evening_balconymoon,
                IllustR.drawable.ic_sun_bear_evening_balconyhang to R.string.sun_bear_evening_balconyhang,
                IllustR.drawable.ic_sun_bear_evening_balconyphoto to R.string.sun_bear_evening_balconyphoto,
                IllustR.drawable.ic_sun_bear_evening_balconybreeze to R.string.sun_bear_evening_balconybreeze,
                IllustR.drawable.ic_sun_bear_evening_balconybbq to R.string.sun_bear_evening_balconybbq,
                IllustR.drawable.ic_sun_bear_evening_livingroom to R.string.sun_bear_evening_livingroom,
                IllustR.drawable.ic_sun_bear_evening_projector to R.string.sun_bear_evening_projector,
                IllustR.drawable.ic_sun_bear_evening_bookshelf to R.string.sun_bear_evening_bookshelf,
                IllustR.drawable.ic_sun_bear_evening_puzzle to R.string.sun_bear_evening_puzzle,
                IllustR.drawable.ic_sun_bear_evening_karaoke to R.string.sun_bear_evening_karaoke,
                IllustR.drawable.ic_sun_bear_evening_toycars to R.string.sun_bear_evening_toycars,
                IllustR.drawable.ic_sun_bear_evening_plushplay to R.string.sun_bear_evening_plushplay,
                IllustR.drawable.ic_sun_bear_evening_balconyroom to R.string.sun_bear_evening_balconyroom,
                IllustR.drawable.ic_sun_bear_evening_balconystreet to R.string.sun_bear_evening_balconystreet,
                IllustR.drawable.ic_sun_bear_evening_attic to R.string.sun_bear_evening_attic,
                IllustR.drawable.ic_sun_bear_evening_balconyapartment to R.string.sun_bear_evening_balconyapartment,
                IllustR.drawable.ic_sun_bear_evening_loft to R.string.sun_bear_evening_loft,
                IllustR.drawable.ic_sun_bear_evening_rainwindow to R.string.sun_bear_evening_rainwindow,
                IllustR.drawable.ic_sun_bear_evening_windowseat to R.string.sun_bear_evening_windowseat,
                IllustR.drawable.ic_sun_bear_evening_rainhome to R.string.sun_bear_evening_rainhome,
                IllustR.drawable.ic_sun_bear_evening_raintv to R.string.sun_bear_evening_raintv,
                IllustR.drawable.ic_sun_bear_evening_rainsofa to R.string.sun_bear_evening_rainsofa,
                IllustR.drawable.ic_sun_bear_evening_rainlaptop to R.string.sun_bear_evening_rainlaptop,
                IllustR.drawable.ic_sun_bear_evening_raincouch to R.string.sun_bear_evening_raincouch,
                IllustR.drawable.ic_sun_bear_evening_hutong to R.string.sun_bear_evening_hutong,
                IllustR.drawable.ic_sun_bear_evening_balconysunset to R.string.sun_bear_evening_balconysunset,
                IllustR.drawable.ic_sun_bear_evening_riverside to R.string.sun_bear_evening_riverside,
                IllustR.drawable.ic_sun_bear_evening_kitchen to R.string.sun_bear_evening_kitchen,
                IllustR.drawable.ic_sun_bear_evening_kitchendinner to R.string.sun_bear_evening_kitchendinner,
                IllustR.drawable.ic_sun_bear_evening_balconynight to R.string.sun_bear_evening_balconynight,
                IllustR.drawable.ic_sun_bear_evening_bundview to R.string.sun_bear_evening_bundview,
                IllustR.drawable.ic_sun_bear_evening_openwindow to R.string.sun_bear_evening_openwindow,
                IllustR.drawable.ic_sun_bear_evening_rainsnack to R.string.sun_bear_evening_rainsnack,
                IllustR.drawable.ic_sun_bear_evening_stallwindow to R.string.sun_bear_evening_stallwindow,
                IllustR.drawable.ic_sun_bear_evening_rainwork to R.string.sun_bear_evening_rainwork,
                IllustR.drawable.ic_sun_bear_evening_snowwindow to R.string.sun_bear_evening_snowwindow,
                IllustR.drawable.ic_sun_bear_evening_watertown to R.string.sun_bear_evening_watertown,
                IllustR.drawable.ic_sun_bear_evening_raincoffee to R.string.sun_bear_evening_raincoffee,
                IllustR.drawable.ic_sun_bear_evening_convenience to R.string.sun_bear_evening_convenience,
                IllustR.drawable.ic_sun_bear_evening_oden to R.string.sun_bear_evening_oden,
                IllustR.drawable.ic_sun_bear_evening_rainbed to R.string.sun_bear_evening_rainbed,
                IllustR.drawable.ic_sun_bear_evening_yatai to R.string.sun_bear_evening_yatai,
                IllustR.drawable.ic_sun_bear_evening_rainystreet to R.string.sun_bear_evening_rainystreet,
                IllustR.drawable.ic_sun_bear_evening_bikeride to R.string.sun_bear_evening_bikeride,
                IllustR.drawable.ic_sun_bear_evening_seafood to R.string.sun_bear_evening_seafood,
                IllustR.drawable.ic_sun_bear_evening_hillsidewalk to R.string.sun_bear_evening_hillsidewalk,
                IllustR.drawable.ic_sun_bear_evening_raindesk to R.string.sun_bear_evening_raindesk,
            )
        val seen = mutableSetOf<Int>()
        for (i in 0L..120L) {
            val d = day.plusDays(i)
            val res = SunPathBearMoment.EVENING.bearResFor(d)
            val caption = SunPathBearMoment.EVENING.captionResFor(d)
            val pair = pairs.first { it.first == res }
            assertEquals(pair.second, caption)
            seen += res
        }
        assertEquals(69, seen.size)
        assertEquals(
            SunPathBearMoment.EVENING.bearResFor(day),
            SunPathBearMoment.EVENING.bearResFor(day),
        )
    }

    @Test
    fun bedtimePrep_variantsMatchCaptions() {
        val day = LocalDate.of(2026, 8, 6)
        val pairs =
            listOf(
                IllustR.drawable.ic_sun_bear_bedtime_pajamas to R.string.sun_bear_bedtime_pajamas,
                IllustR.drawable.ic_sun_bear_bedtime_bath to R.string.sun_bear_bedtime_bath,
                IllustR.drawable.ic_sun_bear_bedtime_nightlight to R.string.sun_bear_bedtime_nightlight,
                IllustR.drawable.ic_sun_bear_bedtime_makebed to R.string.sun_bear_bedtime_makebed,
                IllustR.drawable.ic_sun_bear_bedtime_story to R.string.sun_bear_bedtime_story,
                IllustR.drawable.ic_sun_bear_bedtime_plush to R.string.sun_bear_bedtime_plush,
                IllustR.drawable.ic_sun_bear_bedtime_alarm to R.string.sun_bear_bedtime_alarm,
                IllustR.drawable.ic_sun_bear_bedtime_footbath to R.string.sun_bear_bedtime_footbath,
                IllustR.drawable.ic_sun_bear_bedtime_book to R.string.sun_bear_bedtime_book,
            )
        val seen = mutableSetOf<Int>()
        for (i in 0L..40L) {
            val d = day.plusDays(i)
            val res = SunPathBearMoment.BEDTIME_PREP.bearResFor(d)
            val caption = SunPathBearMoment.BEDTIME_PREP.captionResFor(d)
            val idx = pairs.indexOfFirst { it.first == res }
            assertTrue(idx >= 0)
            assertEquals(pairs[idx].second, caption)
            seen += res
        }
        assertEquals(9, seen.size)
    }

    @Test
    fun sleep_variantsRotate_captionStaysSimple() {
        val day = LocalDate.of(2026, 8, 6)
        val pool =
            setOf(
                IllustR.drawable.ic_sun_bear_sleep,
                IllustR.drawable.ic_sun_bear_sleep_bed_pink,
                IllustR.drawable.ic_sun_bear_sleep_bed_yellow,
                IllustR.drawable.ic_sun_bear_sleep_bed_purple,
                IllustR.drawable.ic_sun_bear_sleep_tatami,
                IllustR.drawable.ic_sun_bear_sleep_tatami_orange,
                IllustR.drawable.ic_sun_bear_sleep_kang,
                IllustR.drawable.ic_sun_bear_sleep_hammock,
                IllustR.drawable.ic_sun_bear_sleep_tent,
                IllustR.drawable.ic_sun_bear_sleep_sofa,
                IllustR.drawable.ic_sun_bear_sleep_train,
                IllustR.drawable.ic_sun_bear_sleep_igloo,
                IllustR.drawable.ic_sun_bear_sleep_treehouse,
                IllustR.drawable.ic_sun_bear_sleep_sleepingbag,
                IllustR.drawable.ic_sun_bear_sleep_bunk,
                IllustR.drawable.ic_sun_bear_sleep_campcot,
                IllustR.drawable.ic_sun_bear_sleep_lounge,
                IllustR.drawable.ic_sun_bear_sleep_crib,
                IllustR.drawable.ic_sun_bear_sleep_petpile,
                IllustR.drawable.ic_sun_bear_sleep_sofa_lie,
                IllustR.drawable.ic_sun_bear_sleep_dorm,
            )
        val seen = mutableSetOf<Int>()
        for (i in 0L..80L) {
            val d = day.plusDays(i)
            val res = SunPathBearMoment.SLEEP.bearResFor(d)
            assertTrue(res in pool)
            assertEquals(R.string.sun_bear_sleep, SunPathBearMoment.SLEEP.captionResFor(d))
            seen += res
        }
        assertEquals(21, seen.size)
    }

    @Test
    fun almostDawn_variantsRotateByDay() {
        val day = LocalDate.of(2026, 8, 6)
        val pool =
            SunPathBearMoment.ALMOST_DAWN.bearVariantDrawables().toSet()
        assertEquals(9, pool.size)
        val seen =
            (0L..80L)
                .map { SunPathBearMoment.ALMOST_DAWN.bearResFor(day.plusDays(it)) }
                .toSet()
        assertEquals(9, seen.size)
        assertEquals(
            SunPathBearMoment.ALMOST_DAWN.bearResFor(day),
            SunPathBearMoment.ALMOST_DAWN.bearResFor(day),
        )
        assertEquals(
            R.string.sun_bear_almost_dawn,
            SunPathBearMoment.ALMOST_DAWN.captionResFor(day),
        )
    }

    @Test
    fun workDay_officeSplitsMorningAfternoon_fieldJobStaysSame() {
        val weekday = LocalDate.of(2026, 8, 6)
        // 找到一个办公室日与一个现场工种日
        var officeDay: LocalDate? = null
        var fieldDay: LocalDate? = null
        for (i in 0L..80L) {
            val d = weekday.plusDays(i)
            val am = workDayBearRes(d, afternoon = false)
            val pm = workDayBearRes(d, afternoon = true)
            if (am == IllustR.drawable.ic_sun_bear_work_morning &&
                pm == IllustR.drawable.ic_sun_bear_work_afternoon
            ) {
                officeDay = d
            } else if (am == pm && am != IllustR.drawable.ic_sun_bear_work_morning) {
                fieldDay = d
            }
            if (officeDay != null && fieldDay != null) break
        }
        assertTrue(officeDay != null)
        assertTrue(fieldDay != null)
        assertEquals(
            IllustR.drawable.ic_sun_bear_work_morning,
            SunPathBearMoment.WORK_MORNING.bearResFor(officeDay!!),
        )
        assertEquals(
            IllustR.drawable.ic_sun_bear_work_afternoon,
            SunPathBearMoment.WORK_AFTERNOON.bearResFor(officeDay),
        )
        assertEquals(
            SunPathBearMoment.WORK_MORNING.bearResFor(fieldDay!!),
            SunPathBearMoment.WORK_AFTERNOON.bearResFor(fieldDay),
        )
    }

    @Test
    fun mealVariants_stableWithinDay_canDifferAcrossDays() {
        val day = LocalDate.of(2026, 8, 6)
        val lunchPool =
            setOf(
                IllustR.drawable.ic_sun_bear_lunch_bento,
                IllustR.drawable.ic_sun_bear_lunch_ramen,
                IllustR.drawable.ic_sun_bear_lunch_jiaozi,
                IllustR.drawable.ic_sun_bear_lunch_friedrice,
                IllustR.drawable.ic_sun_bear_lunch_sushi,
                IllustR.drawable.ic_sun_bear_lunch_burger,
                IllustR.drawable.ic_sun_bear_lunch_picnic,
                IllustR.drawable.ic_sun_bear_lunch_takeout,
                IllustR.drawable.ic_sun_bear_lunch_pokebowl,
                IllustR.drawable.ic_sun_bear_lunch_burgerfries,
                IllustR.drawable.ic_sun_bear_lunch_shawarma,
                IllustR.drawable.ic_sun_bear_lunch_kebab,
                IllustR.drawable.ic_sun_bear_lunch_mezze,
                IllustR.drawable.ic_sun_bear_lunch_kabsa,
                IllustR.drawable.ic_sun_bear_lunch_falafel,
                IllustR.drawable.ic_sun_bear_lunch_fattoush,
                IllustR.drawable.ic_sun_bear_lunch_bibimbap,
                IllustR.drawable.ic_sun_bear_lunch_kbbq,
                IllustR.drawable.ic_sun_bear_lunch_tteokbokki,
                IllustR.drawable.ic_sun_bear_lunch_kimchijjigae,
                IllustR.drawable.ic_sun_bear_lunch_ramyeon,
                IllustR.drawable.ic_sun_bear_lunch_padthai,
                IllustR.drawable.ic_sun_bear_lunch_greencurry,
                IllustR.drawable.ic_sun_bear_lunch_nasigoreng,
                IllustR.drawable.ic_sun_bear_lunch_tomyum,
                IllustR.drawable.ic_sun_bear_lunch_satay,
            )
        val lunchA = SunPathBearMoment.LUNCH.bearResFor(day)
        assertEquals(lunchA, SunPathBearMoment.LUNCH.bearResFor(day))
        assertTrue(lunchA in lunchPool)
        assertTrue(dailyVariantIndex(day, "LUNCH", 26) in 0..25)
        val seen =
            (0L..120L)
                .map {
                    SunPathBearMoment.LUNCH.bearResFor(day.plusDays(it))
                }.toSet()
        assertEquals(26, seen.size)
        val dinnerPool =
            setOf(
                IllustR.drawable.ic_sun_bear_dinner_teishoku,
                IllustR.drawable.ic_sun_bear_dinner_hotpot,
                IllustR.drawable.ic_sun_bear_dinner_sushi,
                IllustR.drawable.ic_sun_bear_dinner_yakitori,
                IllustR.drawable.ic_sun_bear_dinner_shabu,
                IllustR.drawable.ic_sun_bear_dinner_spaghetti,
                IllustR.drawable.ic_sun_bear_dinner_bbq,
                IllustR.drawable.ic_sun_bear_dinner_pizza,
                IllustR.drawable.ic_sun_bear_dinner_steak,
                IllustR.drawable.ic_sun_bear_dinner_italian,
                IllustR.drawable.ic_sun_bear_dinner_shawarma,
                IllustR.drawable.ic_sun_bear_dinner_tagine,
                IllustR.drawable.ic_sun_bear_dinner_kebab,
                IllustR.drawable.ic_sun_bear_dinner_mansaf,
                IllustR.drawable.ic_sun_bear_dinner_mezze,
                IllustR.drawable.ic_sun_bear_dinner_couscous,
                IllustR.drawable.ic_sun_bear_dinner_galbi,
                IllustR.drawable.ic_sun_bear_dinner_budaejjigae,
                IllustR.drawable.ic_sun_bear_dinner_jajangmyeon,
                IllustR.drawable.ic_sun_bear_dinner_soondubu,
                IllustR.drawable.ic_sun_bear_dinner_chimaek,
                IllustR.drawable.ic_sun_bear_dinner_laksa,
                IllustR.drawable.ic_sun_bear_dinner_massaman,
                IllustR.drawable.ic_sun_bear_dinner_hainanese,
                IllustR.drawable.ic_sun_bear_dinner_lau,
                IllustR.drawable.ic_sun_bear_dinner_banhxeo,
                IllustR.drawable.ic_sun_bear_dinner_crayfish,
            )
        val dinnerA = SunPathBearMoment.DINNER.bearResFor(day)
        assertEquals(dinnerA, SunPathBearMoment.DINNER.bearResFor(day))
        assertTrue(dinnerA in dinnerPool)
        assertTrue(dailyVariantIndex(day, "DINNER", 27) in 0..26)
        val dinnerSeen =
            (0L..120L)
                .map { SunPathBearMoment.DINNER.bearResFor(day.plusDays(it)) }
                .toSet()
        assertEquals(27, dinnerSeen.size)
        assertNotEquals(lunchA, dinnerA)
    }

    @Test
    fun commute_sameModeMorningAndEvening_rotatesByDay() {
        val day = LocalDate.of(2026, 8, 6)
        val outPool =
            listOf(
                IllustR.drawable.ic_sun_bear_morning_out,
                IllustR.drawable.ic_sun_bear_morning_metro,
                IllustR.drawable.ic_sun_bear_morning_bus,
                IllustR.drawable.ic_sun_bear_morning_taxi,
                IllustR.drawable.ic_sun_bear_morning_drive,
                IllustR.drawable.ic_sun_bear_morning_ebike,
                IllustR.drawable.ic_sun_bear_morning_cycle,
                IllustR.drawable.ic_sun_bear_morning_ferry,
                IllustR.drawable.ic_sun_bear_morning_maglev,
            )
        val homePool =
            listOf(
                IllustR.drawable.ic_sun_bear_head_home,
                IllustR.drawable.ic_sun_bear_home_metro,
                IllustR.drawable.ic_sun_bear_home_bus,
                IllustR.drawable.ic_sun_bear_home_taxi,
                IllustR.drawable.ic_sun_bear_home_drive,
                IllustR.drawable.ic_sun_bear_home_ebike,
                IllustR.drawable.ic_sun_bear_home_cycle,
                IllustR.drawable.ic_sun_bear_home_ferry,
                IllustR.drawable.ic_sun_bear_home_maglev,
            )
        val outCaptions =
            listOf(
                R.string.sun_bear_commute_walk_out,
                R.string.sun_bear_commute_metro_out,
                R.string.sun_bear_commute_bus_out,
                R.string.sun_bear_commute_taxi_out,
                R.string.sun_bear_commute_drive_out,
                R.string.sun_bear_commute_ebike_out,
                R.string.sun_bear_commute_cycle_out,
                R.string.sun_bear_commute_ferry_out,
                R.string.sun_bear_commute_maglev_out,
            )
        val out = SunPathBearMoment.MORNING_OUT.bearResFor(day)
        val home = SunPathBearMoment.HEAD_HOME.bearResFor(day)
        assertEquals(out, SunPathBearMoment.MORNING_OUT.bearResFor(day))
        val i = outPool.indexOf(out)
        assertTrue(i >= 0)
        assertEquals(homePool[i], home)
        // 文案与插画同索引
        assertEquals(
            outCaptions[i],
            SunPathBearMoment.MORNING_OUT.captionResFor(day),
        )
        val modes =
            (0L..80L)
                .map {
                    outPool.indexOf(SunPathBearMoment.MORNING_OUT.bearResFor(day.plusDays(it)))
                }.toSet()
        assertEquals(9, modes.size)
    }

    @Test
    fun breakfast_rotatesByDay_independentFromLunch() {
        val day = LocalDate.of(2026, 8, 6)
        val pool =
            setOf(
                IllustR.drawable.ic_sun_bear_breakfast_youtiao,
                IllustR.drawable.ic_sun_bear_breakfast_baozi,
                IllustR.drawable.ic_sun_bear_breakfast_congee,
                IllustR.drawable.ic_sun_bear_breakfast_jianbing,
                IllustR.drawable.ic_sun_bear_breakfast_milkbread,
                IllustR.drawable.ic_sun_bear_breakfast_eggtoast,
                IllustR.drawable.ic_sun_bear_breakfast_noodles,
                IllustR.drawable.ic_sun_bear_breakfast_sandwich,
                IllustR.drawable.ic_sun_bear_breakfast_oatmeal,
                IllustR.drawable.ic_sun_bear_breakfast_changfen,
                IllustR.drawable.ic_sun_bear_breakfast_bacon_eggs,
                IllustR.drawable.ic_sun_bear_breakfast_waffle,
                IllustR.drawable.ic_sun_bear_breakfast_bagel,
                IllustR.drawable.ic_sun_bear_breakfast_frenchtoast,
                IllustR.drawable.ic_sun_bear_breakfast_omelette,
                IllustR.drawable.ic_sun_bear_breakfast_cereal,
                IllustR.drawable.ic_sun_bear_breakfast_english,
                IllustR.drawable.ic_sun_bear_breakfast_hummus,
                IllustR.drawable.ic_sun_bear_breakfast_arabtea,
                IllustR.drawable.ic_sun_bear_breakfast_falafel,
                IllustR.drawable.ic_sun_bear_breakfast_shakshuka,
                IllustR.drawable.ic_sun_bear_breakfast_dates,
                IllustR.drawable.ic_sun_bear_breakfast_manakish,
                IllustR.drawable.ic_sun_bear_breakfast_kimbap,
                IllustR.drawable.ic_sun_bear_breakfast_koreantoast,
                IllustR.drawable.ic_sun_bear_breakfast_juk,
                IllustR.drawable.ic_sun_bear_breakfast_gyeranbap,
                IllustR.drawable.ic_sun_bear_breakfast_banchan,
                IllustR.drawable.ic_sun_bear_breakfast_kayatoast,
                IllustR.drawable.ic_sun_bear_breakfast_nasilemak,
                IllustR.drawable.ic_sun_bear_breakfast_banhmi,
                IllustR.drawable.ic_sun_bear_breakfast_roticanai,
                IllustR.drawable.ic_sun_bear_breakfast_pho,
            )
        val a = SunPathBearMoment.BREAKFAST.bearResFor(day)
        assertTrue(a in pool)
        assertEquals(a, SunPathBearMoment.BREAKFAST.bearResFor(day))
        assertEquals(R.string.sun_bear_breakfast, SunPathBearMoment.BREAKFAST.captionResFor(day))
        val seen =
            (0L..120L)
                .map { SunPathBearMoment.BREAKFAST.bearResFor(day.plusDays(it)) }
                .toSet()
        assertEquals(33, seen.size)
        // 与午饭插画池互不共用
        assertTrue(
            a !in
                setOf(
                    IllustR.drawable.ic_sun_bear_lunch_bento,
                    IllustR.drawable.ic_sun_bear_lunch_shawarma,
                ),
        )
    }

    @Test
    fun exercise_morningAndEveningIndependent_rotatesByDay() {
        val day = LocalDate.of(2026, 8, 6)
        val pool =
            listOf(
                IllustR.drawable.ic_sun_bear_exercise_run,
                IllustR.drawable.ic_sun_bear_exercise_basketball,
                IllustR.drawable.ic_sun_bear_exercise_pingpong,
                IllustR.drawable.ic_sun_bear_exercise_bar,
                IllustR.drawable.ic_sun_bear_exercise_badminton,
                IllustR.drawable.ic_sun_bear_exercise_jumprope,
                IllustR.drawable.ic_sun_bear_exercise_bike,
                IllustR.drawable.ic_sun_bear_exercise_soccer,
                IllustR.drawable.ic_sun_bear_exercise_yoga,
                IllustR.drawable.ic_sun_bear_exercise_taichi,
                IllustR.drawable.ic_sun_bear_exercise_baduanjin,
                IllustR.drawable.ic_sun_bear_exercise_squaredance,
                IllustR.drawable.ic_sun_bear_exercise_jianzi,
                IllustR.drawable.ic_sun_bear_exercise_taichisword,
                IllustR.drawable.ic_sun_bear_exercise_dogwalk,
                IllustR.drawable.ic_sun_bear_exercise_briskwalk,
                IllustR.drawable.ic_sun_bear_exercise_diabolo,
                IllustR.drawable.ic_sun_bear_exercise_tennis,
                IllustR.drawable.ic_sun_bear_exercise_hulahoop,
            )
        val morningCaptions =
            listOf(
                R.string.sun_bear_exercise_run_morning,
                R.string.sun_bear_exercise_basketball_morning,
                R.string.sun_bear_exercise_pingpong_morning,
                R.string.sun_bear_exercise_bar_morning,
                R.string.sun_bear_exercise_badminton_morning,
                R.string.sun_bear_exercise_jumprope_morning,
                R.string.sun_bear_exercise_bike_morning,
                R.string.sun_bear_exercise_soccer_morning,
                R.string.sun_bear_exercise_yoga_morning,
                R.string.sun_bear_exercise_taichi_morning,
                R.string.sun_bear_exercise_baduanjin_morning,
                R.string.sun_bear_exercise_squaredance_morning,
                R.string.sun_bear_exercise_jianzi_morning,
                R.string.sun_bear_exercise_taichisword_morning,
                R.string.sun_bear_exercise_dogwalk_morning,
                R.string.sun_bear_exercise_briskwalk_morning,
                R.string.sun_bear_exercise_diabolo_morning,
                R.string.sun_bear_exercise_tennis_morning,
                R.string.sun_bear_exercise_hulahoop_morning,
            )
        val eveningCaptions =
            listOf(
                R.string.sun_bear_exercise_run_evening,
                R.string.sun_bear_exercise_basketball_evening,
                R.string.sun_bear_exercise_pingpong_evening,
                R.string.sun_bear_exercise_bar_evening,
                R.string.sun_bear_exercise_badminton_evening,
                R.string.sun_bear_exercise_jumprope_evening,
                R.string.sun_bear_exercise_bike_evening,
                R.string.sun_bear_exercise_soccer_evening,
                R.string.sun_bear_exercise_yoga_evening,
                R.string.sun_bear_exercise_taichi_evening,
                R.string.sun_bear_exercise_baduanjin_evening,
                R.string.sun_bear_exercise_squaredance_evening,
                R.string.sun_bear_exercise_jianzi_evening,
                R.string.sun_bear_exercise_taichisword_evening,
                R.string.sun_bear_exercise_dogwalk_evening,
                R.string.sun_bear_exercise_briskwalk_evening,
                R.string.sun_bear_exercise_diabolo_evening,
                R.string.sun_bear_exercise_tennis_evening,
                R.string.sun_bear_exercise_hulahoop_evening,
            )
        val morning = SunPathBearMoment.MORNING_EXERCISE.bearResFor(day)
        val evening = SunPathBearMoment.EVENING_EXERCISE.bearResFor(day)
        val mi = pool.indexOf(morning)
        val ei = pool.indexOf(evening)
        assertTrue(mi >= 0)
        assertTrue(ei >= 0)
        assertEquals(morning, SunPathBearMoment.MORNING_EXERCISE.bearResFor(day))
        assertEquals(evening, SunPathBearMoment.EVENING_EXERCISE.bearResFor(day))
        assertEquals(morningCaptions[mi], SunPathBearMoment.MORNING_EXERCISE.captionResFor(day))
        assertEquals(eveningCaptions[ei], SunPathBearMoment.EVENING_EXERCISE.captionResFor(day))
        // 早晚用不同盐，多数日子会抽到不同运动
        val differDays =
            (0L..60L).count {
                val d = day.plusDays(it)
                SunPathBearMoment.MORNING_EXERCISE.bearResFor(d) !=
                    SunPathBearMoment.EVENING_EXERCISE.bearResFor(d)
            }
        assertTrue(differDays > 20)
        val morningSeen =
            (0L..80L)
                .map {
                    SunPathBearMoment.MORNING_EXERCISE.bearResFor(day.plusDays(it))
                }.toSet()
        assertEquals(19, morningSeen.size)
    }
}
