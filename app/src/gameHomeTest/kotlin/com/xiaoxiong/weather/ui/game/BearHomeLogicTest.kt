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
        assertEquals(10, slots.size)
        val explore = slots.first { it.moment.name == "WEEKEND_EXPLORE" }
        assertEquals(HomeRoutineStatus.NOW, homeRoutineStatus(explore, LocalTime.of(10, 0)))
    }
}
