package com.xiaoxiong.weather

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaoxiong.weather.data.HistoryRangeSummary
import com.xiaoxiong.weather.data.HistoryWeather
import com.xiaoxiong.weather.data.TemperatureUnit
import com.xiaoxiong.weather.ui.weather.HistoryWeatherScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 历史天气页面（单日查询 / 区间统计）的 Compose UI 测试。
 * 需连接设备或模拟器：`./gradlew :app:connectedFullDebugAndroidTest`
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HistoryWeatherScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        historyWeather: HistoryWeather? = null,
        isHistoryLoading: Boolean = false,
        historyFailed: Boolean = false,
        historyNetworkFailed: Boolean = false,
        onPickDate: () -> Unit = {},
        historyRange: HistoryRangeSummary? = null,
        isHistoryRangeLoading: Boolean = false,
        historyRangeFailed: Boolean = false,
        historyRangeNetworkFailed: Boolean = false,
        onFetchRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                HistoryWeatherScreen(
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    historyWeather = historyWeather,
                    isHistoryLoading = isHistoryLoading,
                    historyFailed = historyFailed,
                    historyNetworkFailed = historyNetworkFailed,
                    onPickDate = { onPickDate() },
                    historyRange = historyRange,
                    isHistoryRangeLoading = isHistoryRangeLoading,
                    historyRangeFailed = historyRangeFailed,
                    historyRangeNetworkFailed = historyRangeNetworkFailed,
                    onFetchRange = onFetchRange,
                    onBack = onBack,
                )
            }
        }
    }

    @Test
    fun dayMode_empty_showsHintAndPickButton() {
        setContent()
        composeRule.onNodeWithText("历史天气").assertIsDisplayed()
        composeRule.onNodeWithText("单日查询").assertIsDisplayed()
        composeRule.onNodeWithText("区间统计").assertIsDisplayed()
        composeRule.onNodeWithText("选择一个日期，查看当天天气").assertIsDisplayed()
    }

    @Test
    fun dayMode_loading_showsProgressIndicator() {
        setContent(isHistoryLoading = true)
        composeRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).onFirst().assertIsDisplayed()
    }

    @Test
    fun dayMode_failed_showsErrorAndRetryReopensPicker() {
        val retried = mutableStateOf(false)
        setContent(historyFailed = true, onPickDate = { retried.value = true })
        composeRule.onNodeWithText("该日期暂无数据").assertIsDisplayed()
        composeRule.onNodeWithText("重新选择").performClick()
        assertTrue(retried.value)
    }

    @Test
    fun dayMode_networkFailed_showsNetworkMessage() {
        setContent(historyNetworkFailed = true)
        composeRule.onNodeWithText("网络不可用，查询失败，请稍后重试").assertIsDisplayed()
    }

    @Test
    fun dayMode_withWeather_rendersDateAndTemps() {
        setContent(
            historyWeather =
                HistoryWeather(
                    date = "2026-08-12",
                    weatherCode = 0,
                    tempMaxC = 28.0,
                    tempMinC = 20.5,
                    tempMeanC = 24.0,
                    precipSumMm = 0.0,
                ),
        )
        composeRule.onNodeWithText("08/12").assertIsDisplayed()
        composeRule.onNodeWithText("晴朗").assertIsDisplayed()
        composeRule.onNodeWithText("最高 28.0°C · 最低 20.5°C").assertIsDisplayed()
        composeRule.onNodeWithText("平均 24.0°C").assertIsDisplayed()
    }

    @Test
    fun rangeMode_switch_showsQueryControls() {
        setContent()
        composeRule.onNodeWithText("区间统计").performClick()
        composeRule.onNodeWithText("查询").assertIsDisplayed()
        composeRule.onNodeWithText("开始日期").assertIsDisplayed()
        composeRule.onNodeWithText("结束日期").assertIsDisplayed()
    }

    @Test
    fun rangeMode_loading_showsProgressIndicator() {
        setContent(isHistoryRangeLoading = true)
        composeRule.onNodeWithText("区间统计").performClick()
        composeRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).onFirst().assertIsDisplayed()
    }

    @Test
    fun rangeMode_failed_showsError() {
        setContent(historyRangeFailed = true)
        composeRule.onNodeWithText("区间统计").performClick()
        composeRule.onNodeWithText("该区间暂无数据").assertIsDisplayed()
    }

    @Test
    fun rangeMode_networkFailed_showsNetworkMessage() {
        setContent(historyRangeNetworkFailed = true)
        composeRule.onNodeWithText("区间统计").performClick()
        composeRule.onNodeWithText("网络不可用，查询失败，请稍后重试").assertIsDisplayed()
    }

    @Test
    fun rangeMode_empty_showsHint() {
        setContent()
        composeRule.onNodeWithText("区间统计").performClick()
        composeRule.onNodeWithText("选择起止日期后点查询，查看区间统计").assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        val backed = mutableStateOf(false)
        setContent(onBack = { backed.value = true })
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertTrue(backed.value)
    }
}
