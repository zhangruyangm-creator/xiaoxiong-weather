package com.xiaoxiong.weather.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 ./gradlew :app:generateReleaseBaselineProfile 用的冷启动 + 首屏遍历 Profile。
 * 所有 UI 交互都做了「不存在就跳过」处理，保证不同系统语言下都能跑完。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.xiaoxiong.weather"
    ) {
        pressHome()
        startActivityAndWait()

        // 首次安装会弹隐私协议，先同意进入主界面（按钮文案随系统语言）
        listOf("同意并继续", "Agree and continue").forEach { text ->
            device.wait(Until.findObject(By.text(text)), 2_000)?.click()
        }
        device.waitForIdle()

        // 滚动今日页，覆盖 LazyColumn / 图表 / 卡片的绘制路径
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(device.displayWidth / 5)
            fling(Direction.DOWN)
            fling(Direction.UP)
        }
        device.waitForIdle()
    }
}
