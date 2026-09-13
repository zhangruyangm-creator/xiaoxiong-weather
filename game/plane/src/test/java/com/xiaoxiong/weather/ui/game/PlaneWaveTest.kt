package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaneConfig.wave] 波次表单测：Boss 节奏、编队内容、刷怪确定性。
 */
class PlaneWaveTest {
    @Test
    fun bossWaves_areEveryFifth() {
        for (index in 1..PLANE_TOTAL_WAVES) {
            val wave = PlaneConfig.wave(index)
            assertEquals(index, wave.index)
            assertEquals("第 $index 波 Boss 标记错误", index % PLANE_BOSS_EVERY == 0, wave.isBoss)
            if (wave.isBoss) {
                assertEquals(1, wave.spawns.size)
                assertEquals(PlaneEnemyType.BOSS, wave.spawns[0].type)
            }
        }
        assertTrue(PlaneConfig.wave(0).spawns.isEmpty())
    }

    @Test
    fun nonBossWaves_haveSortedSpawns() {
        for (index in 1..PLANE_TOTAL_WAVES) {
            val wave = PlaneConfig.wave(index)
            if (wave.isBoss) continue
            assertTrue("第 $index 波应有刷怪条目", wave.spawns.isNotEmpty())
            val delays = wave.spawns.map { it.delayTicks }
            assertEquals("刷怪延迟必须递增（确定性调度前提）", delays.sorted(), delays)
        }
    }

    @Test
    fun allFiveEnemyTypes_appearAcrossWaves() {
        val types = (1..PLANE_TOTAL_WAVES).flatMap { PlaneConfig.wave(it).spawns }.map { it.type }.toSet()
        listOf(
            PlaneEnemyType.GRUNT,
            PlaneEnemyType.DIVER,
            PlaneEnemyType.WEAVER,
            PlaneEnemyType.TURRET,
            PlaneEnemyType.TRANSPORT,
        ).forEach { type ->
            assertTrue("波次表缺少敌人类型 $type", type in types)
        }
    }

    @Test
    fun formationTwo_entersFromBothSides() {
        // 第 2 / 7 波为左右切入编队
        listOf(2, 7).forEach { index ->
            val sides =
                PlaneConfig
                    .wave(index)
                    .spawns
                    .map { it.side }
                    .toSet()
            assertTrue("第 $index 波应有左侧入场", -1 in sides)
            assertTrue("第 $index 波应有右侧入场", 1 in sides)
        }
    }

    @Test
    fun waveTable_isDeterministic() {
        for (index in 1..PLANE_TOTAL_WAVES) {
            assertEquals(PlaneConfig.wave(index), PlaneConfig.wave(index))
        }
    }

    @Test
    fun secondStage_hasMoreOrEqualEnemiesThanFirst() {
        // 同一编队位（1..4）在第二轮数量不少于第一轮
        for (local in 1..4) {
            val first = PlaneConfig.wave(local).spawns.size
            val second = PlaneConfig.wave(local + PLANE_BOSS_EVERY).spawns.size
            assertTrue("第二轮编队 $local 数量应不少于第一轮", second >= first)
        }
    }

    @Test
    fun spawnDelays_fitWaveSchedule() {
        // 非 Boss 波最后一波怪应在合理时间内入场，避免波次永不结束
        val maxDelay = (1..PLANE_TOTAL_WAVES).flatMap { PlaneConfig.wave(it).spawns }.maxOf { it.delayTicks }
        assertTrue("刷怪延迟应在 600 tick 内", maxDelay <= 600)
        assertFalse(PlaneConfig.wave(3).spawns.any { it.type == PlaneEnemyType.BOSS })
    }
}
