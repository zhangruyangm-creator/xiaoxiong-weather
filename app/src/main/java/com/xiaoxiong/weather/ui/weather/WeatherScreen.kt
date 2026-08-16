package com.xiaoxiong.weather.ui.weather

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.WeatherApp
import com.xiaoxiong.weather.data.AppSettingsStore
import com.xiaoxiong.weather.game.GameEntry
import com.xiaoxiong.weather.ui.LocalTemperatureUnit
import com.xiaoxiong.weather.ui.debug.DebugWeatherScreen
import com.xiaoxiong.weather.ui.settings.DailyCardsSettingsScreen
import com.xiaoxiong.weather.ui.settings.HourlyCardsSettingsScreen
import com.xiaoxiong.weather.ui.settings.PrivacyGateDialog
import com.xiaoxiong.weather.ui.settings.PrivacyScreen
import com.xiaoxiong.weather.ui.settings.SettingsScreen
import com.xiaoxiong.weather.ui.settings.TodayCardsSettingsScreen
import com.xiaoxiong.weather.util.WeatherCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    settingsStore: AppSettingsStore,
    viewModel: WeatherViewModel? = null,
    gameEntries: List<GameEntry> = emptyList(),
) {
    val app = LocalContext.current.applicationContext as Application
    val resolvedViewModel =
        viewModel ?: composeViewModel(
            factory = remember(app) { WeatherViewModelFactory(app) },
        )
    WeatherScreenContent(
        settingsStore = settingsStore,
        viewModel = resolvedViewModel,
        gameEntries = gameEntries,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherScreenContent(
    settingsStore: AppSettingsStore,
    viewModel: WeatherViewModel,
    gameEntries: List<GameEntry>,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val debugState by viewModel.debugState.collectAsStateWithLifecycle()
    val baseWeather by viewModel.baseWeather.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val pullToRefreshState = rememberPullToRefreshState()
    var overlay by remember { mutableStateOf(OverlayScreen.None) }
    var showPrivacyGate by remember { mutableStateOf(false) }
    var showShareCard by remember { mutableStateOf(false) }
    // 等 DataStore 真实值读出前不渲染隐私门，避免老用户冷启动闪一下
    var privacyGateResolved by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val locationGranted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            viewModel.onPermissionResult(locationGranted)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result[Manifest.permission.POST_NOTIFICATIONS]?.let { granted ->
                    viewModel.onNotificationPermissionResult(granted)
                }
            }
        }

    fun requestRuntimePermissionsIfNeeded() {
        if (!viewModel.consumeRuntimePermissionRequest()) return
        val permissions =
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    // 前台：整点对齐轮询 + 回到前台刷新；退后台停轮询（保留 WorkManager）
    LifecycleStartEffect(Unit) {
        viewModel.onAppForeground()
        onStopOrDispose {
            viewModel.onAppBackground()
        }
    }

    // 先同步读出隐私状态，再决定是否弹窗 / 要权限 / 首启加载
    LaunchedEffect(Unit) {
        val accepted = settingsStore.getOnce().privacyAccepted
        showPrivacyGate = !accepted
        privacyGateResolved = true
        if (accepted) {
            requestRuntimePermissionsIfNeeded()
            viewModel.ensureInitialLoad()
        }
    }

    when (overlay) {
        OverlayScreen.Settings -> {
            SettingsScreen(
                settingsStore = settingsStore,
                scope = scope,
                onBack = { overlay = OverlayScreen.None },
                onOpenPrivacy = { overlay = OverlayScreen.Privacy },
                onOpenTodayCards = { overlay = OverlayScreen.TodayCards },
                onOpenHourlyCards = { overlay = OverlayScreen.HourlyCards },
                onOpenDailyCards = { overlay = OverlayScreen.DailyCards },
                onDebugUnlocked = {
                    viewModel.enableDebugMode()
                    overlay = OverlayScreen.Debug
                },
            )
            return
        }
        OverlayScreen.TodayCards -> {
            TodayCardsSettingsScreen(
                settingsStore = settingsStore,
                scope = scope,
                onBack = { overlay = OverlayScreen.Settings },
            )
            return
        }
        OverlayScreen.HourlyCards -> {
            HourlyCardsSettingsScreen(
                settingsStore = settingsStore,
                scope = scope,
                onBack = { overlay = OverlayScreen.Settings },
            )
            return
        }
        OverlayScreen.DailyCards -> {
            DailyCardsSettingsScreen(
                settingsStore = settingsStore,
                scope = scope,
                onBack = { overlay = OverlayScreen.Settings },
            )
            return
        }
        OverlayScreen.Privacy -> {
            PrivacyScreen(onBack = {
                overlay = if (showPrivacyGate) OverlayScreen.None else OverlayScreen.Settings
            })
            return
        }
        OverlayScreen.Debug -> {
            DebugWeatherScreen(
                baseWeather = baseWeather ?: state.weather,
                initialOverride = debugState.override,
                onBack = { overlay = OverlayScreen.None },
                onApply = viewModel::applyDebugOverride,
                onExitDebug = {
                    viewModel.exitDebugMode()
                    overlay = OverlayScreen.None
                },
                onClearOverride = viewModel::clearDebugOverride,
            )
            return
        }
        OverlayScreen.History -> {
            HistoryWeatherScreen(
                temperatureUnit = appSettings.temperatureUnit,
                historyWeather = state.historyWeather,
                isHistoryLoading = state.isHistoryLoading,
                historyFailed = state.historyFailed,
                onPickDate = viewModel::fetchHistoryDay,
                historyRange = state.historyRange,
                isHistoryRangeLoading = state.isHistoryRangeLoading,
                historyRangeFailed = state.historyRangeFailed,
                onFetchRange = viewModel::fetchHistoryRange,
                onBack = { overlay = OverlayScreen.None },
            )
            return
        }
        OverlayScreen.None -> Unit
    }

    if (privacyGateResolved && showPrivacyGate) {
        PrivacyGateDialog(
            onAccept = {
                scope.launch {
                    settingsStore.setPrivacyAccepted(true)
                    (context.applicationContext as? WeatherApp)?.initializeAfterConsent()
                    showPrivacyGate = false
                    requestRuntimePermissionsIfNeeded()
                    viewModel.ensureInitialLoad()
                }
            },
            // 不同意则退出 App：合规要求给用户提供明确的拒绝路径
            onDecline = { (context as? Activity)?.finish() },
            onReadPrivacy = { overlay = OverlayScreen.Privacy },
        )
    }

    val tabs =
        listOf(
            TabItem(stringResource(R.string.tab_today), Icons.Default.WbSunny),
            TabItem(stringResource(R.string.tab_hourly), Icons.Default.Schedule),
            TabItem(stringResource(R.string.tab_daily), Icons.Default.CalendarMonth),
        )

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(state.infoMessage) {
        val message = state.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearInfo()
    }

    // 折线图横向拖动选点时，暂时关闭左右翻页，避免手势被 Pager 抢走
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    val onChartDragStateChange =
        remember {
            { dragging: Boolean -> pagerScrollEnabled = !dragging }
        }

    // 整屏背景跟随当前天气场景/昼夜渐变；无数据时传 null 回落静态渐变
    val bgCurrent = state.weather?.current
    val bgSceneCode =
        bgCurrent?.let {
            WeatherCodes.displayCode(it.weatherCode, WeatherCodes.intensityPrecipMmPerHour(it))
        }

    CompositionLocalProvider(LocalTemperatureUnit provides appSettings.temperatureUnit) {
        // 放在温度单位 Provider 内，分享卡片才能跟设置一致（°C / °F）
        if (showShareCard) {
            state.weather?.let { weather ->
                ShareWeatherCardDialog(
                    snapshot = weather,
                    onDismiss = { showShareCard = false },
                )
            } ?: run { showShareCard = false }
        }

        AppScreenBackground(
            modifier = Modifier.fillMaxSize(),
            sceneCode = bgSceneCode,
            isDay = bgCurrent?.isDay,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    WeatherTopBar(
                        viewModel = viewModel,
                        state = state,
                        onLocate = {
                            val needPermission = viewModel.requestLocate()
                            if (needPermission) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onShare = { showShareCard = true },
                        onOpenSettings = { overlay = OverlayScreen.Settings },
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                ) {
                    SearchHeader(
                        viewModel = viewModel,
                        query = state.query,
                        selectedCity = state.selectedCity,
                        isSearching = state.isSearching,
                        suggestions = state.suggestions,
                        onQueryChange = viewModel::onQueryChange,
                        onSelectCity = { city ->
                            focusManager.clearFocus()
                            viewModel.selectCity(city)
                        },
                        onClearRecent = viewModel::clearRecentCities,
                    )

                    if (state.debugEnabled) {
                        DebugModeBanner(
                            onOpenEditor = { overlay = OverlayScreen.Debug },
                            onExit = viewModel::exitDebugMode,
                        )
                    }

                    PullToRefreshBox(
                        isRefreshing = state.isLoadingWeather || state.isLocating,
                        onRefresh = viewModel::refresh,
                        state = pullToRefreshState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        indicator = {
                            BearRefreshIndicator(
                                state = pullToRefreshState,
                                isRefreshing = state.isLoadingWeather || state.isLocating,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        },
                    ) {
                        if ((state.isLoadingWeather || state.isLocating) && state.weather == null) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(top = 56.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(
                                        if (state.isLocating) {
                                            R.string.loading_locate
                                        } else {
                                            R.string.loading_weather
                                        },
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // 0：不预组相邻页，避免小时页仍跑雷达拉流与大气动画
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 0,
                                userScrollEnabled = pagerScrollEnabled,
                            ) { page ->
                                when (page) {
                                    0 ->
                                        TodayPage(
                                            weather = state.weather,
                                            isOffline = state.isOffline,
                                            lastUpdatedText = state.lastUpdatedText,
                                            radarMapRepository = viewModel.radarMapRepository,
                                            cardLayout = appSettings.todayCards,
                                        )
                                    1 ->
                                        HourlyPage(
                                            weather = state.weather,
                                            isOffline = state.isOffline,
                                            lastUpdatedText = state.lastUpdatedText,
                                            onChartDragStateChange = onChartDragStateChange,
                                            cardLayout = appSettings.hourlyCards,
                                        )
                                    else ->
                                        DailyPage(
                                            weather = state.weather,
                                            isOffline = state.isOffline,
                                            lastUpdatedText = state.lastUpdatedText,
                                            phenologyShiftDays = state.phenologyShiftDays,
                                            onPhenologyPageShown = viewModel::ensurePhenologyShift,
                                            onOpenHistory = { overlay = OverlayScreen.History },
                                            cardLayout = appSettings.dailyCards,
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 彩蛋口令触发与游戏弹窗：放在温度单位 Provider 内，与背景场景同生命周期
        BearGameOverlay(
            query = state.query,
            onClearQuery = viewModel::clearQueryKeepLocation,
            sceneCode = bgSceneCode,
            isDay = bgCurrent?.isDay,
            cityName = state.selectedCity?.displayName,
            pagerState = pagerState,
            scope = scope,
            entries = gameEntries,
        )
    }
}

/**
 * 小熊主题的下拉刷新指示器：
 * - 下拉：浇水帧随进度切换（壶越来越歪、水滴下落）
 * - 刷新中：奔跑帧循环翻页，配合轻微弹跳
 */
@Composable
private fun BoxScope.BearRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val waterFrames = remember { BearRefreshWaterFrames }
    val runFrames = remember { BearRefreshRunFrames }
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            frameIndex = 0
            return@LaunchedEffect
        }
        frameIndex = 0
        while (true) {
            delay(110)
            frameIndex = (frameIndex + 1) % runFrames.size
        }
    }

    val pullFrame =
        ((state.distanceFraction.coerceIn(0f, 1f) * waterFrames.lastIndex) + 0.001f)
            .toInt()
            .coerceIn(0, waterFrames.lastIndex)
    val frameRes =
        if (isRefreshing) {
            runFrames[frameIndex]
        } else {
            waterFrames[pullFrame]
        }

    val reveal by
        animateFloatAsState(
            targetValue = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 220),
            label = "refreshReveal",
        )

    // 刷新中才创建无限动画；闲置/下拉阶段不创建，避免常驻逐帧重组耗电。
    // 条件组合时把动画值读回局部变量，供下方 graphicsLayer 使用。
    var bounceScale = 1f
    var bobY = 0f
    if (isRefreshing) {
        val bounce by
            rememberInfiniteTransition(label = "refreshBounce")
                .animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(380, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "refreshBounceScale",
                )
        val bob by
            rememberInfiniteTransition(label = "refreshBob")
                .animateFloat(
                    initialValue = -1.5f,
                    targetValue = 1.5f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(180, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "refreshBobY",
                )
        bounceScale = bounce
        bobY = bob
    }
    val scale = if (isRefreshing) bounceScale else 0.4f + 0.6f * reveal
    Box(
        modifier =
            modifier
                .size(48.dp)
                .graphicsLayer {
                    alpha = reveal
                    scaleX = scale
                    scaleY = scale
                    translationY = if (isRefreshing) bobY else 0f
                }.shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    spotColor = scheme.primary.copy(alpha = 0.25f),
                ).clip(CircleShape)
                .background(scheme.surface)
                .border(
                    width = 1.dp,
                    color = scheme.outline.copy(alpha = 0.25f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // 帧动画用瞬时切图，不用 Crossfade，否则会糊成幻灯片
        Image(
            painter = painterResource(frameRes),
            contentDescription = stringResource(R.string.cd_pull_to_refresh),
            modifier = Modifier.size(34.dp),
        )
    }
}

/** 下拉阶段：浇水壶逐渐倾斜、水滴下落。 */
@DrawableRes
private val BearRefreshWaterFrames: IntArray =
    intArrayOf(
        R.drawable.ic_bear_refresh_water_1,
        R.drawable.ic_bear_refresh_water_2,
        R.drawable.ic_bear_refresh_water_3,
    )

/** 刷新阶段：奔跑循环。 */
@DrawableRes
private val BearRefreshRunFrames: IntArray =
    intArrayOf(
        R.drawable.ic_bear_refresh_run_1,
        R.drawable.ic_bear_refresh_run_2,
        R.drawable.ic_bear_refresh_run_3,
        R.drawable.ic_bear_refresh_run_4,
    )
