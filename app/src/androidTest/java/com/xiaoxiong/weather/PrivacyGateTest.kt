package com.xiaoxiong.weather

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.xiaoxiong.weather.di.appContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * 隐私门仪器测试：覆盖「同意前不初始化/不可达，同意后放行并落盘」。
 *
 * - 每个用例启动前把 `privacyAccepted` 重置为 false（TestWatcher 外层规则，
 *   在 Activity 启动前执行），保证门禁必现；
 * - 未同意时断言门禁对话框可见且主窗口失去焦点（模态拦截主界面与标题连点游戏枢纽入口）；
 * - 点击「同意并继续」后断言门禁消失、主窗口恢复焦点、`privacyAccepted=true` 写入设置。
 *
 * 需连接设备或模拟器：`./gradlew :app:connectedFullDebugAndroidTest`
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PrivacyGateTest {
    private val seedPrivacyRule =
        object : TestWatcher() {
            override fun starting(description: Description) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                runBlocking {
                    context.appContainer().settingsStore.setPrivacyAccepted(false)
                }
            }
        }

    // 同意后 WeatherScreen 会申请运行时权限；不预授权会让系统权限弹窗抢焦点并干扰断言
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
            .outerRule(seedPrivacyRule)
            .around(permissionRule)
            .around(composeRule)

    @Test
    fun privacyGate_visibleBeforeConsent_blocksMainUiAndGameHub() {
        waitForText("欢迎使用小熊天气")
        // 模态 Dialog 持有焦点：主窗口不聚焦意味着主界面与「小熊的家」标题连点入口均不可达
        val decorView = composeRule.activity.window.decorView
        composeRule.waitUntil(timeoutMillis = 5_000) { !decorView.hasWindowFocus() }
        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText("同意并继续").assertIsDisplayed()
        composeRule.onNodeWithText("不同意并退出").assertIsDisplayed()
        composeRule.onNodeWithText("查看隐私政策").assertIsDisplayed()
        assertTrue(
            "未同意时游戏枢纽不应被打开",
            composeRule.onAllNodesWithText("小熊的家").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun acceptPrivacy_gateDisappearsAndConsentPersists() {
        waitForText("同意并继续")
        composeRule.onNodeWithText("同意并继续").performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("欢迎使用小熊天气").fetchSemanticsNodes().isEmpty()
        }
        // 门禁关闭后主窗口恢复焦点，主界面可交互
        val decorView = composeRule.activity.window.decorView
        composeRule.waitUntil(timeoutMillis = 8_000) { decorView.hasWindowFocus() }
        composeRule.onNodeWithText("小熊天气").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsStore = context.appContainer().settingsStore
        assertTrue(
            "同意后 privacyAccepted 必须写入设置",
            runBlocking { settingsStore.getOnce().privacyAccepted },
        )
    }

    private fun waitForText(
        text: String,
        timeoutMs: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
