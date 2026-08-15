package com.xiaoxiong.weather

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.data.ThemeMode
import com.xiaoxiong.weather.di.appContainer
import com.xiaoxiong.weather.ui.LocalReduceMotion
import com.xiaoxiong.weather.ui.theme.XiaoxiongWeatherTheme
import com.xiaoxiong.weather.ui.weather.WeatherScreen
import com.xiaoxiong.weather.util.WeatherLauncherIcon

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = applicationContext.appContainer().settingsStore
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(
                initialValue =
                    com.xiaoxiong.weather.data
                        .AppSettings(),
            )
            val darkTheme =
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            val baseDensity = LocalDensity.current
            val appDensity =
                remember(baseDensity, settings.fontScale) {
                    Density(
                        density = baseDensity.density,
                        fontScale = settings.fontScale.resolve(baseDensity.fontScale),
                    )
                }
            val context = LocalContext.current
            // 系统「移除动画」开启时自动减弱动画（用户改了系统设置也生效）
            val systemReduceMotion =
                remember(context) {
                    runCatching {
                        Settings.Global.getFloat(
                            context.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
                    }.getOrDefault(false)
                }
            val reduceMotion = settings.reduceMotion || systemReduceMotion
            CompositionLocalProvider(LocalDensity provides appDensity) {
                XiaoxiongWeatherTheme(
                    darkTheme = darkTheme,
                    dynamicColor = settings.dynamicColor,
                ) {
                    // 必须带上 onBackground，否则透明 Surface 可能让子树继承成深色字
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                            WeatherScreen(settingsStore = settingsStore)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        // 前台切换图标会在部分机型杀进程；真正退后台再改（旋转屏幕不触发）
        if (!isChangingConfigurations) {
            WeatherLauncherIcon.flushPending(this)
        }
        super.onStop()
    }

    // singleTop：通知/外部 scheme 拉起时复用栈顶实例，走 onNewIntent 而非新建
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 不在此直接刷新：LifecycleStartEffect 会在 ON_START 触发前台静默刷新，
        // 避免 scheme 入口与生命周期刷新重复请求。
    }
}
