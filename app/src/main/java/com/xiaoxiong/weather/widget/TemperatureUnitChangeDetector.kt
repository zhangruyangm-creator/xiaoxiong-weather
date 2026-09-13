package com.xiaoxiong.weather.widget

import com.xiaoxiong.weather.data.TemperatureUnit

/**
 * 温度单位变化检测：设置流首次发射只是当前初值，不应触发小组件刷新；
 * 只有后续真实变化才返回 true。
 */
internal class TemperatureUnitChangeDetector {
    private var lastUnit: TemperatureUnit? = null

    fun onUnit(unit: TemperatureUnit): Boolean {
        val previous = lastUnit
        lastUnit = unit
        return previous != null && previous != unit
    }
}
