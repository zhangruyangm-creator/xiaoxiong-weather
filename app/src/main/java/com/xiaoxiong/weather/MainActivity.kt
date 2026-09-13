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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = applicationContext.appContainer()
        val settingsStore = container.settingsStore
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
            // 系统「移除动画」开启时自动减弱动画。注册 ContentObserver 监听系统设置变化，
            // 此前实现用 remember(context) 但 context 在 Activity 生命周期内不变，用户改了
            // 系统「移除动画」必须重启 App 才生效，与注释承诺的"用户改了系统设置也生效"矛盾
            var systemReduceMotion by remember {
                mutableStateOf(
                    runCatching {
                        Settings.Global.getFloat(
                            context.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1f,
                        ) == 0f
                    }.getOrDefault(false),
                )
            }
            DisposableEffect(context) {
                val observer =
                    object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            systemReduceMotion =
                                runCatching {
                                    Settings.Global.getFloat(
                                        context.contentResolver,
                                        Settings.Global.ANIMATOR_DURATION_SCALE,
                                        1f,
                                    ) == 0f
                                }.getOrDefault(false)
                        }
                    }
                context.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                    false,
                    observer,
                )
                onDispose { context.contentResolver.unregisterContentObserver(observer) }
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
                            // ServiceLoader 扫描已移到 WeatherApp 后台预载到 AppContainer；
                            // 这里订阅结果，加载完成后自动重组，首帧不再阻塞主线程
                            val gameEntries by container.gameEntries.collectAsStateWithLifecycle()
                            WeatherScreen(
                                settingsStore = settingsStore,
                                gameEntries = gameEntries,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        // 前台切换图标会在部分机型杀进程；真正退后台再改（旋转屏幕不触发）。
        // PackageManager IPC + prefs 写盘放到应用级后台协程，避免主线程卡顿
        if (!isChangingConfigurations) {
            (application as? WeatherApp)?.flushLauncherIconPending()
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
