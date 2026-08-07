package com.xiaoxiong.weather

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.xiaoxiong.weather.data.City
import com.xiaoxiong.weather.di.appContainer
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * 主流程冒烟：主壳 → 北京 → Tab / 设置。
 * 需连接设备或模拟器：`./gradlew :app:connectedDebugAndroidTest`
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class WeatherSmokeTest {
    private val beijing =
        City(
            id = 1816670,
            name = "北京",
            latitude = 39.9042,
            longitude = 116.4074,
            country = "中国",
            admin1 = "北京",
        )

    private val seedSettingsRule =
        object : TestWatcher() {
            override fun starting(description: Description) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                runBlocking {
                    val container = context.appContainer()
                    container.settingsStore.setPrivacyAccepted(true)
                    // 降低冒烟对 GPS 的依赖，固定落到北京
                    container.settingsStore.setPreferLocationOnLaunch(false)
                    container.selectedCityStore.save(beijing, useLocation = false)
                }
            }
        }

    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            *buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray(),
        )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(seedSettingsRule)
            .around(permissionRule)
            .around(composeRule)

    @Test
    fun mainShell_showsBrandAndBottomTabs() {
        dismissPrivacyIfNeeded()
        waitForText("小熊天气")
        composeRule.onNodeWithText("小熊天气").assertIsDisplayed()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("小时").assertIsDisplayed()
        composeRule.onNodeWithText("七日").assertIsDisplayed()
    }

    @Test
    fun coldStart_showsBeijingCity() {
        dismissPrivacyIfNeeded()
        waitForText("小熊天气")
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule
                .onAllNodesWithText("北京", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("北京", substring = true).assertIsDisplayed()
    }

    @Test
    fun bottomTabs_canSwitchPages() {
        dismissPrivacyIfNeeded()
        waitForText("小熊天气")

        composeRule.onNodeWithText("小时").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("小时趋势").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("暂无小时趋势数据").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("正在加载天气…").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("七日").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("七日预报").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("暂无七日预报数据").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("正在加载天气…").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("今天").performClick()
        composeRule.onNodeWithText("小熊天气").assertIsDisplayed()
    }

    @Test
    fun settings_opensAndBack() {
        dismissPrivacyIfNeeded()
        waitForText("小熊天气")

        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("温度单位").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("小熊天气").assertIsDisplayed()
    }

    private fun dismissPrivacyIfNeeded() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("小熊天气").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("同意并继续").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun waitForText(
        text: String,
        timeoutMs: Long = 15_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
