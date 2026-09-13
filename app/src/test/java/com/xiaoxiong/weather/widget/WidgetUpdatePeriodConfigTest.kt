package com.xiaoxiong.weather.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `updatePeriodMillis != 0` 会让系统每 30 分钟强制唤醒小组件联网，
 * 绕过用户设置的 1/3/6 小时后台档位。这里守住三个 widget 配置始终为 0。
 */
class WidgetUpdatePeriodConfigTest {
    @Test
    fun allWidgetProviders_disableSystemPeriodicUpdates() {
        val configs =
            listOf(
                "weather_widget_info.xml",
                "weather_widget_wide_info.xml",
                "weather_widget_4x2_info.xml",
            )
        configs.forEach { name ->
            val file = File("src/main/res/xml/$name")
            assertTrue("$name not found at ${file.absolutePath}", file.isFile)
            val content = file.readText()
            assertTrue("$name should set android:updatePeriodMillis=\"0\"", content.contains("android:updatePeriodMillis=\"0\""))
            assertFalse("$name must not keep the 30-minute period", content.contains("1800000"))
        }
    }
}
