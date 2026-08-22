package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
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
        assertEquals(11, slots.size)
        val explore = slots.first { it.moment.name == "WEEKEND_EXPLORE" }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(explore, LocalTime.of(10, 0)))
        // 周末旅行拆成两段：上午 09:00–11:30 与下午 12:30–15:00，午后 15:00 起为周末下午
        val afternoonExplore = slots.first { it.moment.name == "WEEKEND_EXPLORE" && it.startMinute == 12 * 60 + 30 }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(afternoonExplore, LocalTime.of(13, 30)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(afternoonExplore, LocalTime.of(15, 30)))
        val afternoon = slots.first { it.moment.name == "WEEKEND_AFTERNOON" }
        assertEquals(15 * 60, afternoon.startMinute)
        assertEquals(18 * 60, afternoon.endMinute)
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(afternoon, LocalTime.of(16, 0)))
        assertEquals(HomeRoutineStatus.DONE, homeRoutineStatus(afternoon, LocalTime.of(18, 30)))
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
}
