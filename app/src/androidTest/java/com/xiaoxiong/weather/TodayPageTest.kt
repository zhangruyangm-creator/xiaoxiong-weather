package com.xiaoxiong.weather

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.data.CurrentWeather
import com.xiaoxiong.weather.data.RadarMapRepository
import com.xiaoxiong.weather.data.WeatherSnapshot
import com.xiaoxiong.weather.ui.weather.TodayPage
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 今日页布局与卡片显隐的 Compose UI 测试。
 * 雷达卡片用真实 RadarMapRepository（测试环境网络失败会静默降级为错误态，不会崩）。
 * 需连接模拟器/真机：`./gradlew :app:connectedFullDebugAndroidTest`
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class TodayPageTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // 短超时客户端：UI 测试环境无真实网络，快速走完「加载失败」降级路径
    private val radarRepository =
        RadarMapRepository(
            client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(),
        )

    private fun setContent(snapshot: WeatherSnapshot?) {
        composeRule.setContent {
            MaterialTheme {
                TodayPage(
                    weather = snapshot,
                    isOffline = false,
                    lastUpdatedText = null,
                    radarMapRepository = radarRepository,
                )
            }
        }
    }

    @Test
    fun emptySnapshot_showsEmptyHint() {
        setContent(null)
        composeRule.onNodeWithText("暂无今天的天气数据").assertIsDisplayed()
    }

    @Test
    fun currentWeatherCard_rendersTemperatureAndCondition() {
        setContent(snapshotFixture())
        composeRule.onNodeWithText("晴朗").assertIsDisplayed()
        composeRule.onNodeWithText("22.0°C").assertIsDisplayed()
    }

    @Test
    fun radarCard_titleVisible_skeletonOnNoNetwork() {
        setContent(snapshotFixture())
        // 测试环境无真实网络：雷达数据请求会失败降级为错误态，但卡片骨架恒显示。
        // 这里只断言标题（骨架）存在，不验证数据内容（数据需真机联调/注入假 Repository）。
        composeRule.onNodeWithText("降水与雷达").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun airQualityCard_hiddenWhenNoAirQuality() {
        setContent(snapshotFixture())
        composeRule.onNodeWithText("空气质量").assertIsNotDisplayed()
    }

    @Test
    fun offlineBanner_showsWhenOffline() {
        composeRule.setContent {
            MaterialTheme {
                TodayPage(
                    weather = snapshotFixture(),
                    isOffline = true,
                    lastUpdatedText = "08-02 12:00",
                    radarMapRepository = radarRepository,
                )
            }
        }
        composeRule.onNodeWithText("离线 · 08-02 12:00 更新").assertIsDisplayed()
    }

    private fun snapshotFixture(): WeatherSnapshot =
        WeatherSnapshot(
            city =
                City(
                    id = 1816670,
                    name = "北京",
                    latitude = 39.9042,
                    longitude = 116.4074,
                    country = "中国",
                    admin1 = "北京",
                ),
            timezone = "Asia/Shanghai",
            current =
                CurrentWeather(
                    time = "2026-08-02T12:00",
                    weatherCode = 0,
                    temperatureC = 22.0,
                ),
            hourly = emptyList(),
            daily = emptyList(),
        )
}