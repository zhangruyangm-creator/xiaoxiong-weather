package com.xiaoxiong.weather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WeatherWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        // 调用方通常刚写入新缓存/快照：清空去重缓存，避免拿到 30s 内的旧渲染
        WeatherWidgetData.invalidateLoadCache()
        val manager = GlanceAppWidgetManager(appContext)
        // 三类 widget 共用渲染实现但各自独立实例：没有实例的类型直接跳过，
        // 否则每个类型都会进 provideGlance 触发一次 WeatherWidgetData.load。
        updateIfPresent(manager, appContext, WeatherAppWidget(), WeatherAppWidget::class.java)
        updateIfPresent(manager, appContext, WeatherAppWideWidget(), WeatherAppWideWidget::class.java)
        updateIfPresent(manager, appContext, WeatherAppWide4x2Widget(), WeatherAppWide4x2Widget::class.java)
    }

    private suspend fun updateIfPresent(
        manager: GlanceAppWidgetManager,
        context: Context,
        widget: GlanceAppWidget,
        provider: Class<out GlanceAppWidget>,
    ) {
        // 查询失败时保守刷新（宁可多一次渲染，不可漏更新）
        val hasInstances = runCatching { manager.getGlanceIds(provider).isNotEmpty() }.getOrDefault(true)
        if (!hasInstances) return
        widget.updateAll(context)
    }
}
