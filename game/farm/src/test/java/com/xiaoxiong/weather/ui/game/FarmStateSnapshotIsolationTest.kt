package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FarmState.toSave] 的快照隔离测试。
 *
 * 背景：曾把 `plots = plots` 直接交给后台线程编码，而 `FarmPlotState` 带 `var`，
 * 60fps 帧循环的 tick / advanceGrowth 会原地改这些对象。后果有二：
 * 后台编码读到撕裂数据；存盘去重基准 `lastSaved` 与活状态共享元素，于是
 * 「只有地块变化」（作物生长）时 `toSave() == lastSaved` 恒为真 → 静默丢存档。
 */
class FarmStateSnapshotIsolationTest {
    private fun stateWithPlantedPlot(): FarmState {
        val state = FarmState()
        state.plots = List(FARM_PLOT_COUNT) { FarmPlotState() }
        state.plots[0].cropId = "radish"
        state.plots[0].stage = 1
        state.plots[0].moisture = 0.8f
        return state
    }

    @Test
    fun `toSave deep-copies plots so later ticks cannot rewrite the snapshot`() {
        val state = stateWithPlantedPlot()
        val snapshot = state.toSave()

        // 模拟后台编码前的又一次帧推进：原地改活状态的地块
        advanceGrowth(state.plots[0], requireNotNull(cropById("radish")), 1f, farmWeatherEffect(FarmWeather.SUNNY), 1f)

        assertEquals(
            "快照里的 stageProgress 不应被之后的 tick 改写",
            snapshot.plots[0].stageProgress,
            0f,
            1e-6f,
        )
    }

    @Test
    fun `toSave snapshots do not share element identity with live state`() {
        val state = stateWithPlantedPlot()
        val snapshot = state.toSave()
        assertNotSame(state.plots[0], snapshot.plots[0])
        state.plots[0].stage = 4
        assertEquals(1, snapshot.plots[0].stage)
    }

    @Test
    fun `growth-only change makes toSave differ from an earlier snapshot`() {
        val state = stateWithPlantedPlot()
        val before = state.toSave()
        advanceGrowth(state.plots[0], requireNotNull(cropById("radish")), 1f, farmWeatherEffect(FarmWeather.SUNNY), 1f)
        val after = state.toSave()
        assertTrue(
            "作物生长后必须与旧快照不等，否则存盘会被去重跳过（静默丢档）",
            before != after,
        )
    }
}
