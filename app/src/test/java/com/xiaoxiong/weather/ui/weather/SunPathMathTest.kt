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
        assertEquals(
            SunPathBearMoment.MORNING_EXERCISE,
            sunPathBearMoment(day, weekday, LocalTime.of(7, 0))
        )
        assertEquals(
            SunPathBearMoment.MORNING_OUT,
            sunPathBearMoment(day, weekday, LocalTime.of(8, 15))
        )
        assertEquals(
            SunPathBearMoment.WORK_MORNING,
            sunPathBearMoment(day, weekday, LocalTime.of(10, 0))
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, weekday, LocalTime.of(12, 0))
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(14, 0))
        )
        assertEquals(
            SunPathBearMoment.AFTERNOON_TEA,
            sunPathBearMoment(day, weekday, LocalTime.of(15, 30))
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(16, 45))
        )
        assertEquals(
            SunPathBearMoment.HEAD_HOME,
            sunPathBearMoment(day, weekday, LocalTime.of(18, 0))
        )
        assertEquals(
            SunPathBearMoment.EVENING_EXERCISE,
            sunPathBearMoment(day, weekday, LocalTime.of(19, 0))
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, weekday, LocalTime.of(11, 30))
        )
        assertEquals(
            SunPathBearMoment.WORK_AFTERNOON,
            sunPathBearMoment(day, weekday, LocalTime.of(13, 30))
        )
    }

    @Test
    fun afternoonTea_drinkMatchesCaptionAndIsWeekdayOnly() {
        val weekday = LocalDate.of(2026, 8, 6)
        val saturday = LocalDate.of(2026, 8, 8)
        val day = SunPathPhase(true, 0.5f)
        assertEquals(
            SunPathBearMoment.WEEKEND_AFTERNOON,
            sunPathBearMoment(day, saturday, LocalTime.of(15, 30))
        )
        val drinks = listOf(
            R.drawable.ic_sun_bear_tea_coffee to R.string.sun_bear_tea_coffee,
            R.drawable.ic_sun_bear_tea_tea to R.string.sun_bear_tea_tea,
            R.drawable.ic_sun_bear_tea_milk to R.string.sun_bear_tea_milk,
            R.drawable.ic_sun_bear_tea_juice to R.string.sun_bear_tea_juice,
            R.drawable.ic_sun_bear_tea_cocoa to R.string.sun_bear_tea_cocoa,
            R.drawable.ic_sun_bear_tea_dessert to R.string.sun_bear_tea_dessert
        )
        val seen = mutableSetOf<Int>()
        for (i in 0L..50L) {
            val d = weekday.plusDays(i)
            val res = SunPathBearMoment.AFTERNOON_TEA.bearResFor(d)
            val caption = SunPathBearMoment.AFTERNOON_TEA.captionResFor(d)
            val pair = drinks.first { it.first == res }
            assertEquals(pair.second, caption)
            seen += res
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun bearMoment_weekendUsesTravelInsteadOfWork() {
        val saturday = LocalDate.of(2026, 8, 8)
        val day = SunPathPhase(true, 0.5f)
        assertTrue(isWeekend(saturday))
        assertEquals(
            SunPathBearMoment.WEEKEND_MORNING,
            sunPathBearMoment(day, saturday, LocalTime.of(8, 0))
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_EXPLORE,
            sunPathBearMoment(day, saturday, LocalTime.of(10, 0))
        )
        assertEquals(
            SunPathBearMoment.LUNCH,
            sunPathBearMoment(day, saturday, LocalTime.of(12, 15))
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_AFTERNOON,
            sunPathBearMoment(day, saturday, LocalTime.of(15, 0))
        )
        assertEquals(
            SunPathBearMoment.WEEKEND_RETURN,
            sunPathBearMoment(day, saturday, LocalTime.of(18, 0))
        )
        val a = SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday)
        assertEquals(a, SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday))
        val seen = (0L..20L).map {
            SunPathBearMoment.WEEKEND_EXPLORE.bearResFor(saturday.plusWeeks(it))
        }.toSet()
        assertTrue(seen.size > 1)
    }

    @Test
    fun bearMoment_nightDinnerThenSleep() {
        val night = SunPathPhase(false, 0.5f)
        val weekday = LocalDate.of(2026, 8, 6) // Thursday
        val saturday = LocalDate.of(2026, 8, 8)
        // 工作日入夜后：先补通勤/锻炼，再晚饭
        assertEquals(
            SunPathBearMoment.HEAD_HOME,
            sunPathBearMoment(night, weekday, LocalTime.of(17, 30))
        )
        assertEquals(
            SunPathBearMoment.EVENING_EXERCISE,
            sunPathBearMoment(night, weekday, LocalTime.of(19, 0))
        )
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(night, weekday, LocalTime.of(19, 45))
        )
        // 周末入夜不补工作日通勤/锻炼
        assertEquals(
            SunPathBearMoment.DINNER,
            sunPathBearMoment(night, saturday, LocalTime.of(19, 0))
        )
        assertEquals(
            SunPathBearMoment.EVENING,
            sunPathBearMoment(night, weekday, LocalTime.of(21, 0))
        )
        assertEquals(
            SunPathBearMoment.EVENING,
            sunPathBearMoment(night, weekday, LocalTime.of(22, 30))
        )
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(23, 0))
        )
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(23, 30))
        )
        assertEquals(
            SunPathBearMoment.SLEEP,
            sunPathBearMoment(night, weekday, LocalTime.of(2, 0))
        )
        assertEquals(
            SunPathBearMoment.ALMOST_DAWN,
            sunPathBearMoment(night, weekday, LocalTime.of(5, 30))
        )
    }

    @Test
    fun evening_restVariantsRotateWithMatchingCaptions() {
        val day = LocalDate.of(2026, 8, 6)
        val pairs = listOf(
            R.drawable.ic_sun_bear_evening to R.string.sun_bear_evening_tea,
            R.drawable.ic_sun_bear_evening_game to R.string.sun_bear_evening_game,
            R.drawable.ic_sun_bear_evening_book to R.string.sun_bear_evening_book,
            R.drawable.ic_sun_bear_evening_phone to R.string.sun_bear_evening_phone,
            R.drawable.ic_sun_bear_evening_tv to R.string.sun_bear_evening_tv,
            R.drawable.ic_sun_bear_evening_music to R.string.sun_bear_evening_music,
            R.drawable.ic_sun_bear_evening_knit to R.string.sun_bear_evening_knit,
            R.drawable.ic_sun_bear_evening_diary to R.string.sun_bear_evening_diary,
            R.drawable.ic_sun_bear_evening_pet to R.string.sun_bear_evening_pet
        )
        val seen = mutableSetOf<Int>()
        for (i in 0L..60L) {
            val d = day.plusDays(i)
            val res = SunPathBearMoment.EVENING.bearResFor(d)
            val caption = SunPathBearMoment.EVENING.captionResFor(d)
            val pair = pairs.first { it.first == res }
            assertEquals(pair.second, caption)
            seen += res
        }
        assertEquals(9, seen.size)
        assertEquals(
            SunPathBearMoment.EVENING.bearResFor(day),
            SunPathBearMoment.EVENING.bearResFor(day)
        )
    }

    @Test
    fun sleep_variantsRotate_captionStaysSimple() {
        val day = LocalDate.of(2026, 8, 6)
        val pool = setOf(
            R.drawable.ic_sun_bear_sleep,
            R.drawable.ic_sun_bear_sleep_bed_pink,
            R.drawable.ic_sun_bear_sleep_bed_yellow,
            R.drawable.ic_sun_bear_sleep_bed_purple,
            R.drawable.ic_sun_bear_sleep_tatami,
            R.drawable.ic_sun_bear_sleep_tatami_orange,
            R.drawable.ic_sun_bear_sleep_kang,
            R.drawable.ic_sun_bear_sleep_hammock,
            R.drawable.ic_sun_bear_sleep_tent,
            R.drawable.ic_sun_bear_sleep_sofa,
            R.drawable.ic_sun_bear_sleep_train,
            R.drawable.ic_sun_bear_sleep_igloo,
            R.drawable.ic_sun_bear_sleep_treehouse
        )
        val seen = mutableSetOf<Int>()
        for (i in 0L..80L) {
            val d = day.plusDays(i)
            val res = SunPathBearMoment.SLEEP.bearResFor(d)
            assertTrue(res in pool)
            assertEquals(R.string.sun_bear_sleep, SunPathBearMoment.SLEEP.captionResFor(d))
            seen += res
        }
        assertEquals(13, seen.size)
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
            if (am == R.drawable.ic_sun_bear_work_morning &&
                pm == R.drawable.ic_sun_bear_work_afternoon
            ) {
                officeDay = d
            } else if (am == pm && am != R.drawable.ic_sun_bear_work_morning) {
                fieldDay = d
            }
            if (officeDay != null && fieldDay != null) break
        }
        assertTrue(officeDay != null)
        assertTrue(fieldDay != null)
        assertEquals(
            R.drawable.ic_sun_bear_work_morning,
            SunPathBearMoment.WORK_MORNING.bearResFor(officeDay!!)
        )
        assertEquals(
            R.drawable.ic_sun_bear_work_afternoon,
            SunPathBearMoment.WORK_AFTERNOON.bearResFor(officeDay)
        )
        assertEquals(
            SunPathBearMoment.WORK_MORNING.bearResFor(fieldDay!!),
            SunPathBearMoment.WORK_AFTERNOON.bearResFor(fieldDay)
        )
    }

    @Test
    fun mealVariants_stableWithinDay_canDifferAcrossDays() {
        val day = LocalDate.of(2026, 8, 6)
        val lunchPool = setOf(
            R.drawable.ic_sun_bear_lunch,
            R.drawable.ic_sun_bear_lunch_2,
            R.drawable.ic_sun_bear_lunch_3,
            R.drawable.ic_sun_bear_lunch_4,
            R.drawable.ic_sun_bear_lunch_5,
            R.drawable.ic_sun_bear_lunch_6,
            R.drawable.ic_sun_bear_lunch_7,
            R.drawable.ic_sun_bear_lunch_8,
            R.drawable.ic_sun_bear_lunch_9,
            R.drawable.ic_sun_bear_lunch_10
        )
        val lunchA = SunPathBearMoment.LUNCH.bearResFor(day)
        assertEquals(lunchA, SunPathBearMoment.LUNCH.bearResFor(day))
        assertTrue(lunchA in lunchPool)
        assertTrue(dailyVariantIndex(day, "LUNCH", 10) in 0..9)
        val seen = (0L..40L).map {
            SunPathBearMoment.LUNCH.bearResFor(day.plusDays(it))
        }.toSet()
        assertTrue(seen.size >= 6)
        assertNotEquals(
            SunPathBearMoment.LUNCH.bearResFor(day),
            SunPathBearMoment.DINNER.bearResFor(day)
        )
    }

    @Test
    fun commute_sameModeMorningAndEvening_rotatesByDay() {
        val day = LocalDate.of(2026, 8, 6)
        val outPool = listOf(
            R.drawable.ic_sun_bear_morning_out,
            R.drawable.ic_sun_bear_morning_metro,
            R.drawable.ic_sun_bear_morning_bus,
            R.drawable.ic_sun_bear_morning_taxi,
            R.drawable.ic_sun_bear_morning_drive
        )
        val homePool = listOf(
            R.drawable.ic_sun_bear_head_home,
            R.drawable.ic_sun_bear_home_metro,
            R.drawable.ic_sun_bear_home_bus,
            R.drawable.ic_sun_bear_home_taxi,
            R.drawable.ic_sun_bear_home_drive
        )
        val out = SunPathBearMoment.MORNING_OUT.bearResFor(day)
        val home = SunPathBearMoment.HEAD_HOME.bearResFor(day)
        assertEquals(out, SunPathBearMoment.MORNING_OUT.bearResFor(day))
        val i = outPool.indexOf(out)
        assertTrue(i >= 0)
        assertEquals(homePool[i], home)
        // 文案与插画同索引
        assertEquals(
            when (i) {
                0 -> R.string.sun_bear_commute_walk_out
                1 -> R.string.sun_bear_commute_metro_out
                2 -> R.string.sun_bear_commute_bus_out
                3 -> R.string.sun_bear_commute_taxi_out
                else -> R.string.sun_bear_commute_drive_out
            },
            SunPathBearMoment.MORNING_OUT.captionResFor(day)
        )
        val modes = (0L..40L).map {
            outPool.indexOf(SunPathBearMoment.MORNING_OUT.bearResFor(day.plusDays(it)))
        }.toSet()
        assertEquals(5, modes.size)
    }

    @Test
    fun exercise_morningAndEveningIndependent_rotatesByDay() {
        val day = LocalDate.of(2026, 8, 6)
        val pool = listOf(
            R.drawable.ic_sun_bear_exercise_run,
            R.drawable.ic_sun_bear_exercise_basketball,
            R.drawable.ic_sun_bear_exercise_pingpong,
            R.drawable.ic_sun_bear_exercise_bar,
            R.drawable.ic_sun_bear_exercise_badminton,
            R.drawable.ic_sun_bear_exercise_jumprope,
            R.drawable.ic_sun_bear_exercise_bike,
            R.drawable.ic_sun_bear_exercise_soccer,
            R.drawable.ic_sun_bear_exercise_yoga,
            R.drawable.ic_sun_bear_exercise_taichi
        )
        val morningCaptions = listOf(
            R.string.sun_bear_exercise_run_morning,
            R.string.sun_bear_exercise_basketball_morning,
            R.string.sun_bear_exercise_pingpong_morning,
            R.string.sun_bear_exercise_bar_morning,
            R.string.sun_bear_exercise_badminton_morning,
            R.string.sun_bear_exercise_jumprope_morning,
            R.string.sun_bear_exercise_bike_morning,
            R.string.sun_bear_exercise_soccer_morning,
            R.string.sun_bear_exercise_yoga_morning,
            R.string.sun_bear_exercise_taichi_morning
        )
        val eveningCaptions = listOf(
            R.string.sun_bear_exercise_run_evening,
            R.string.sun_bear_exercise_basketball_evening,
            R.string.sun_bear_exercise_pingpong_evening,
            R.string.sun_bear_exercise_bar_evening,
            R.string.sun_bear_exercise_badminton_evening,
            R.string.sun_bear_exercise_jumprope_evening,
            R.string.sun_bear_exercise_bike_evening,
            R.string.sun_bear_exercise_soccer_evening,
            R.string.sun_bear_exercise_yoga_evening,
            R.string.sun_bear_exercise_taichi_evening
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
        val differDays = (0L..60L).count {
            val d = day.plusDays(it)
            SunPathBearMoment.MORNING_EXERCISE.bearResFor(d) !=
                SunPathBearMoment.EVENING_EXERCISE.bearResFor(d)
        }
        assertTrue(differDays > 20)
        val morningSeen = (0L..40L).map {
            SunPathBearMoment.MORNING_EXERCISE.bearResFor(day.plusDays(it))
        }.toSet()
        assertEquals(10, morningSeen.size)
    }
}
