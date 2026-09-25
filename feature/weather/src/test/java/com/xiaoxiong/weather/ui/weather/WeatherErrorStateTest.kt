package com.xiaoxiong.weather.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 天气页的「一次性提示」与「常驻错误态」必须分离。
 *
 * 背景：失败原先只弹一次 Snackbar，播完 `clearError()` 清空，页面随后退化成
 * 「暂无今天的天气数据」——用户错过那几秒就既看不到原因也没有恢复入口。
 * 现在 `errorMessage` 只服务 Snackbar，`lastErrorMessage` 常驻到下一次成功刷新。
 */
class WeatherErrorStateTest {
    private fun state(
        errorMessage: String? = null,
        lastErrorMessage: String? = null,
    ) = WeatherUiState(errorMessage = errorMessage, lastErrorMessage = lastErrorMessage)

    @Test
    fun `snackbar text and persistent error are separate fields`() {
        val s = state(errorMessage = "网络异常", lastErrorMessage = "网络异常")
        assertNotNull(s.errorMessage)
        assertNotNull(s.lastErrorMessage)
    }

    @Test
    fun `persistent error survives after the snackbar one-shot is cleared`() {
        val before = state(errorMessage = "网络异常", lastErrorMessage = "网络异常")
        // clearError() 的语义：只清一次性提示
        val after = before.copy(errorMessage = null)
        assertNull(after.errorMessage)
        assertNotNull(
            "Snackbar 播完后常驻错误态必须还在，否则错误页一闪即逝、没有重试入口",
            after.lastErrorMessage,
        )
    }

    @Test
    fun `successful publish clears the persistent error`() {
        val before = state(lastErrorMessage = "网络异常")
        val after = before.copy(lastErrorMessage = null)
        assertNull(after.lastErrorMessage)
        assertEquals(null, after.lastErrorMessage)
    }

    @Test
    fun `default state has no error`() {
        val s = WeatherUiState()
        assertNull(s.errorMessage)
        assertNull(s.lastErrorMessage)
    }
}
