package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaneConfig.wave] 无尽波次表单测：Boss 节奏永久循环、编队内容、软上限与刷怪确定性。
 */
class PlaneWaveTest {
    /** 覆盖前 6 轮编队 + 一个远超软上限的波次。 */
    private val sampledWaves = 1..30

    @Test
    fun bossWaves_areEveryFifth_forever() {
        for (index in sampledWaves) {
            val wave = PlaneConfig.wave(index)
            assertEquals(index, wave.index)
            assertEquals("第 $index 波 Boss 标记错误", index % PLANE_BOSS_EVERY == 0, wave.isBoss)
            if (wave.isBoss) {
                assertEquals(1, wave.spawns.size)
                assertEquals(PlaneEnemyType.BOSS, wave.spawns[0].type)
            }
        }
        assertTrue("第 5000 波应为 Boss", PlaneConfig.wave(5000).isBoss)
        assertTrue("安全上限附近仍有 Boss（9995）", PlaneConfig.wave(9995).isBoss)
        assertTrue(PlaneConfig.wave(0).spawns.isEmpty())
    }

    @Test
    fun nonBossWaves_haveSortedSpawns() {
        for (index in sampledWaves) {
            val wave = PlaneConfig.wave(index)
            if (wave.isBoss) continue
            assertTrue("第 $index 波应有刷怪条目", wave.spawns.isNotEmpty())
            val delays = wave.spawns.map { it.delayTicks }
            assertEquals("刷怪延迟必须递增（确定性调度前提）", delays.sorted(), delays)
        }
    }

    @Test
    fun allSevenEnemyTypes_appearAcrossWaves() {
        val types = (1 until PLANE_BOSS_EVERY * 2).flatMap { PlaneConfig.wave(it).spawns }.map { it.type }.toSet()
        listOf(
            PlaneEnemyType.GRUNT,
            PlaneEnemyType.DIVER,
            PlaneEnemyType.WEAVER,
            PlaneEnemyType.TURRET,
            PlaneEnemyType.TRANSPORT,
            PlaneEnemyType.HELI,
            PlaneEnemyType.TANK,
        ).forEach { type ->
            assertTrue("波次表缺少敌人类型 $type", type in types)
        }
    }

    @Test
    fun newEnemies_enterFromTopAndBottom_respectively() {
        val earlyTypes = (1..PLANE_BOSS_EVERY).flatMap { PlaneConfig.wave(it).spawns }
        val helis = earlyTypes.filter { it.type == PlaneEnemyType.HELI }
        val tanks = earlyTypes.filter { it.type == PlaneEnemyType.TANK }
        assertTrue("直升机应在第 5 波前登场", helis.isNotEmpty())
        assertTrue("装甲车应在第 5 波前登场", tanks.isNotEmpty())
        assertTrue("直升机从顶部常规入场", helis.all { !it.fromBottom })
        assertTrue("装甲车从画面下方进场", tanks.all { it.fromBottom })
        assertTrue("装甲车横向巡行方向非零", tanks.all { it.vx != 0f })
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
        for (index in sampledWaves) {
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
    fun highStages_softCapFormationSize() {
        // stage 9 与 stage 10 均已超过编队软上限：同编队规模必须一致，避免数值爆炸
        for (local in 1..4) {
            val waveA = local + PLANE_BOSS_EVERY * 8
            val waveB = local + PLANE_BOSS_EVERY * 9
            assertEquals(
                "编队 $local 在软上限后规模应恒定（$waveA vs $waveB）",
                PlaneConfig.wave(waveA).spawns.size,
                PlaneConfig.wave(waveB).spawns.size,
            )
        }
    }

    @Test
    fun spawnDelays_fitWaveSchedule() {
        // 非 Boss 波最后一波怪应在合理时间内入场，避免波次永不结束
        val maxDelay = sampledWaves.flatMap { PlaneConfig.wave(it).spawns }.maxOf { it.delayTicks }
        assertTrue("刷怪延迟应在 600 tick 内", maxDelay <= 600)
        assertFalse(PlaneConfig.wave(3).spawns.any { it.type == PlaneEnemyType.BOSS })
    }
}
