package com.xiaoxiong.weather.ui.game

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import com.xiaoxiong.weather.ui.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun BearFarmScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // 入口预加载全部音效样本，消除首次播放的加载延迟
    LaunchedEffect(Unit) { GameAudio.preload(context) }
    val appContext = remember(context) { context.applicationContext }
    val farm = remember(appContext) { BearFarm(appContext).also { it.load() } }
    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val fx = remember(weather) { farmWeatherEffect(weather) }
    // 减弱动画：跳过农场雨雪/星空/萤火虫等动态氛围，只保留静态天空与田地
    val reduceMotion = LocalReduceMotion.current
    var phase by remember(isDay) { mutableStateOf(farmSkyPhase(isDay)) }

    // 昼夜时段随钟点慢速更新（5 分钟一次），长时间挂着也能跨过黄昏/入夜
    LaunchedEffect(isDay) {
        while (isActive) {
            delay(5 * 60_000L)
            phase = farmSkyPhase(isDay)
        }
    }

    LaunchedEffect(farm.toast) {
        val t = farm.toast ?: return@LaunchedEffect
        delay(2500)
        if (farm.toast?.id == t.id) farm.clearToast()
    }

    // 帧循环：dt 钳制到 50ms。farm.tick 每帧调用（模拟持续推进），
    // farm.time 按 weather 重置的节流变量累积 animDt，慢速时每 5 帧才写一次。
    var slowTick by remember(weather) { mutableStateOf(0) }
    var animDt by remember(weather) { mutableFloatStateOf(0f) }
    // 弹窗打开时没有需要逐帧推进的内容：暂停帧循环（tick 走离线追赶），避免空转唤醒 vsync
    val dialogOpen =
        farm.showShop ||
            farm.showCatalog ||
            farm.showInventory ||
            farm.showSeasonConfirm ||
            farm.unlockCard != null
    GameFrameLoop(key = weather, maxDtSeconds = 0.05f, paused = { dialogOpen }) { dt, _ ->
        // 雨/雪/雷暴有粒子动画需要 60fps；晴/多云/雾降频到约 12fps 即可
        val needsHighFps =
            weather == FarmWeather.RAINY ||
                weather == FarmWeather.STORMY ||
                weather == FarmWeather.SNOWY
        farm.tick(System.currentTimeMillis(), weather)
        // 动画时钟与重绘节流同步推进：farm.time 只在 Canvas 绘制中被读取，
        // 这样晴/多云/雾时 Canvas 才真正按约 12fps 重绘，省电优化才生效
        animDt += dt
        if (needsHighFps || slowTick % 5 == 0) {
            farm.time += animDt
            animDt = 0f
        }
        slowTick++
    }

    // 自动保存与渲染帧解耦：独立 5 秒定时，不再依赖帧循环
    GameRepeatingInterval(intervalMs = 5_000L) { farm.save() }

    // 退出兜底：切到其它游戏/主页时补记最后一次改动
    DisposableEffect(Unit) {
        onDispose { farm.save() }
    }

    // 农场是挂机经营玩法，不设暂停菜单：返回键先关内容弹窗，否则保存后直接退出
    BackHandler {
        when {
            farm.showShop -> farm.showShop = false
            farm.showCatalog -> farm.showCatalog = false
            farm.showInventory -> farm.showInventory = false
            farm.showSeasonConfirm -> farm.showSeasonConfirm = false
            farm.unlockCard != null -> farm.dismissUnlockCard()
            else -> {
                farm.save()
                onExit()
            }
        }
    }

    val skyBrush =
        when (phase) {
            FarmSkyPhase.DAY ->
                Brush.verticalGradient(
                    skyGradient(Color(fx.skyTop), Color(fx.skyBottom)),
                )
            else -> {
                val g = farmSkyGradient(phase)
                Brush.verticalGradient(
                    listOf(Color(g.top), Color(g.mid), Color(g.bottom)),
                )
            }
        }

    // 静态远景（远山/丘陵/树线/地面/暗角）只随「天气配色 + 时段 + 尺寸」变化，
    // 烘焙成一张位图后每帧只 drawImage；雨雪/星空/萤火虫仍逐帧绘制。
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val backdropCache = remember { BakedLayerCache<FarmBackdropKey>(maxEntries = 2) }
    DisposableEffect(Unit) {
        onDispose { backdropCache.clear() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(skyBrush),
    ) {
        // 氛围装饰
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                val layer =
                    backdropCache.getOrBake(
                        key =
                            FarmBackdropKey(
                                widthPx = w.toInt(),
                                heightPx = h.toInt(),
                                phase = phase,
                                weather = weather,
                                skyTop = fx.skyTop,
                                skyBottom = fx.skyBottom,
                            ),
                        widthPx = w.toInt(),
                        heightPx = h.toInt(),
                        logicalWidth = w,
                        logicalHeight = h,
                        density = density,
                        layoutDirection = layoutDirection,
                    ) {
                        drawFarmBackdropStatic(phase, weather, fx.skyTop, fx.skyBottom)
                    }
                // 烘焙失败（OOM）时 layer 为 null，直接绘制仍能正常玩，只是少了远景
                if (layer != null) {
                    drawImage(layer.image, Offset.Zero)
                } else {
                    drawFarmBackdropStatic(phase, weather, fx.skyTop, fx.skyBottom)
                }
            }
            drawFarmAtmosphere(weather, farm.time, phase, reduceMotion = reduceMotion)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(12.dp),
        ) {
            // 顶栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    farm.save()
                    onExit()
                }) {
                    Icon(Icons.Default.Close, stringResource(R.string.cd_close_game), tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.bear_farm_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(fx.labelRes),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 金币胶囊：改用公共深色玻璃面板，亮天空上的对比度也一起解决
                GamePanel(
                    style = GamePanelStyleDefaults,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.bear_farm_coins, farm.coins),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFE082),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = { farm.showShop = true }) {
                    Text(stringResource(R.string.bear_farm_shop), color = Color.White)
                }
                IconButton(onClick = { farm.showCatalog = true }) {
                    Text(stringResource(R.string.bear_farm_catalog_icon), fontSize = 18.sp)
                }
                IconButton(onClick = { farm.showInventory = true }) {
                    Text(stringResource(R.string.bear_farm_inventory_icon), fontSize = 18.sp)
                }
            }

            farm.toast?.let { t ->
                GamePanel(
                    style = GamePanelStyleDefaults,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                ) {
                    Text(
                        t.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 田地（BoxWithConstraints 提供坐标，把收获飘字锚定到对应格子）
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
            ) {
                val gridWidthPx = constraints.maxWidth.toFloat()
                val gridHeightPx = constraints.maxHeight.toFloat()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(FARM_COLS),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(farm.plots) { index, plot ->
                        FarmPlotCell(
                            plot = plot,
                            index = index,
                            // 减弱动画：冻结共享时钟 → 需求气泡不上下浮动、成熟作物不脉冲
                            time = { decorativeTime(farm.time, reduceMotion) },
                            onClick = {
                                farm.onPlot(index, weather)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        )
                    }
                }
                farm.floatingRewards.forEach { reward ->
                    FloatingRewardItem(
                        reward = reward,
                        areaWidthPx = gridWidthPx,
                        areaHeightPx = gridHeightPx,
                        onDone = { farm.removeReward(reward.id) },
                    )
                }
            }

            // 种子条（播种工具时）
            if (farm.selectedTool == FarmTool.PLANT) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FARM_CROPS.filter { it.id in farm.unlocked }.forEach { crop ->
                        val sel = farm.selectedSeedId == crop.id
                        Surface(
                            onClick = { farm.selectedSeedId = crop.id },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) Color(0xFFFFF59D) else farmScheme().surface.copy(alpha = 0.92f),
                            border =
                                if (sel) {
                                    BorderStroke(2.dp, Color(0xFFFF8F00))
                                } else {
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                },
                            shadowElevation = if (sel) 3.dp else 1.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    bitmap = rememberGameImage(FarmCropArt.ripe(crop), 256),
                                    contentDescription = cropName(crop),
                                    modifier = Modifier.size(34.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    cropName(crop),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = farmScheme().onSurface,
                                )
                                Text(
                                    stringResource(R.string.bear_farm_seed_cost, crop.seedCost),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = farmScheme().onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            FarmToolBar(
                selected = farm.selectedTool,
                onSelect = { farm.selectedTool = it },
                onCareAll = { farm.careAll() },
            )
        }

        // 夜色/黄昏的柔和氛围叠加（不拦截点击）
        if (phase == FarmSkyPhase.NIGHT) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFF0A1226).copy(alpha = 0.10f)),
            )
        } else if (phase == FarmSkyPhase.DUSK) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFFF2A65E).copy(alpha = 0.05f)),
            )
        }

        // 解锁弹卡退出动画期间仍需要作物信息：记住最后一个非空值
        val lastUnlockCrop = remember { mutableStateOf<CropDef?>(null) }
        LaunchedEffect(farm.unlockCard) {
            farm.unlockCard?.let { lastUnlockCrop.value = it }
        }

        AnimatedVisibility(visible = farm.showShop, enter = farmDialogEnter, exit = farmDialogExit) {
            FarmShopDialog(
                farm = farm,
                onDismiss = { farm.showShop = false },
            )
        }
        AnimatedVisibility(visible = farm.showCatalog, enter = farmDialogEnter, exit = farmDialogExit) {
            FarmCatalogDialog(
                farm = farm,
                onDismiss = { farm.showCatalog = false },
            )
        }
        AnimatedVisibility(visible = farm.showInventory, enter = farmDialogEnter, exit = farmDialogExit) {
            FarmInventoryDialog(
                farm = farm,
                weather = weather,
                onDismiss = { farm.showInventory = false },
            )
        }
        AnimatedVisibility(visible = farm.showSeasonConfirm, enter = farmDialogEnter, exit = farmDialogExit) {
            NewSeasonConfirmDialog(
                farm = farm,
                onDismiss = { farm.showSeasonConfirm = false },
            )
        }
        AnimatedVisibility(visible = farm.unlockCard != null, enter = farmDialogEnter, exit = farmDialogExit) {
            lastUnlockCrop.value?.let { crop ->
                FarmUnlockDialog(
                    crop = crop,
                    onDismiss = { farm.dismissUnlockCard() },
                )
            }
        }
    }
}
