package com.xiaoxiong.weather.ui.game

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoxiong.weather.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 彩蛋口令：搜索框输入「#小熊农场」进入农场。 */
const val EASTER_EGG_BEAR_FARM = "#小熊农场"

private enum class FarmTool { INFO, PLANT, WATER, WEED, BUG, HARVEST, CLEAR }

private val farmJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private class BearFarm(
    private val appContext: Context,
) {
    private val prefs = appContext.getSharedPreferences("bear_game", Context.MODE_PRIVATE)
    var coins by mutableIntStateOf(80)
    var plots by mutableStateOf(List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = System.currentTimeMillis()) })
    var unlocked by mutableStateOf(setOf("radish", "cabbage", "wheat", "tomato"))
    var harvestedTotal by mutableIntStateOf(0)
    var toast by mutableStateOf<String?>(null)
    var selectedTool by mutableStateOf(FarmTool.PLANT)
    var selectedSeedId by mutableStateOf("radish")
    var showShop by mutableStateOf(false)
    var time = 0f

    private fun str(
        id: Int,
        vararg args: Any,
    ): String = if (args.isEmpty()) appContext.getString(id) else appContext.getString(id, *args)

    private fun stageLabel(stage: Int) =
        str(
            when (stage) {
                1 -> R.string.bear_farm_stage_seed
                2 -> R.string.bear_farm_stage_sprout
                3 -> R.string.bear_farm_stage_grow
                4 -> R.string.bear_farm_stage_ripe
                5 -> R.string.bear_farm_stage_wilt
                else -> R.string.bear_farm_stage_empty
            },
        )

    fun load() {
        val raw = prefs.getString("farm_save_v1", null) ?: return
        runCatching {
            val s = farmJson.decodeFromString(FarmSave.serializer(), raw)
            coins = s.coins
            plots =
                s.plots.map { it.copy() }.let { list ->
                    if (list.size < FARM_PLOT_COUNT) {
                        list + List(FARM_PLOT_COUNT - list.size) { FarmPlotState() }
                    } else {
                        list.take(FARM_PLOT_COUNT)
                    }
                }
            unlocked =
                s.unlockedCropIds.toSet().ifEmpty {
                    setOf("radish", "cabbage", "wheat", "tomato")
                }
            harvestedTotal = s.harvestedTotal
        }
    }

    fun save() {
        val s =
            FarmSave(
                coins = coins,
                plots = plots,
                unlockedCropIds = unlocked.toList(),
                harvestedTotal = harvestedTotal,
            )
        prefs.edit().putString("farm_save_v1", farmJson.encodeToString(FarmSave.serializer(), s)).apply()
    }

    fun toast(msg: String) {
        toast = msg
    }

    fun clearToast() {
        toast = null
    }

    /**
     * 按真实时间推进：浇水蒸发、下雨补水、杂草害虫、生长。
     * 离线追赶按分钟切片（单次最多追 8 小时），避免一口气跳阶段/枯萎。
     */
    fun tick(
        now: Long,
        weather: FarmWeather,
        dtVisual: Float,
    ) {
        time += dtVisual
        val fx = farmWeatherEffect(weather)
        val maxCatchUp = 8L * 60L * 60L * 1000L
        val minStepMs = 200L
        val sliceMs = 60_000L // 离线追赶按 1 分钟切片
        var dirty = false
        val next =
            plots.map { p ->
                val plot = p.copy()
                if (plot.lastTickMs <= 0L) {
                    plot.lastTickMs = now
                    dirty = true
                }
                var elapsed = (now - plot.lastTickMs).coerceAtLeast(0L)
                if (elapsed > maxCatchUp) elapsed = maxCatchUp
                // 未满 200ms 不推进；但 lastTickMs 初值修正仍要写回
                if (elapsed < minStepMs) return@map plot
                dirty = true

                while (elapsed >= minStepMs) {
                    val step = if (elapsed > sliceMs) sliceMs else elapsed
                    elapsed -= step
                    val hours = step / 3_600_000f
                    val weedP = farmChanceAtLeastOnce(fx.weedChancePerHour, hours)
                    val bugP = farmChanceAtLeastOnce(fx.bugChancePerHour, hours)
                    // 枯萎：按“每天 25%”的极宽松概率触发，缺一两天水远不会枯，
                    // 只有连续干旱好几天（约 4-7 天）才有明显风险。
                    val wiltP = farmChanceAtLeastOnce(0.25f / 24f, hours)

                    plot.moisture =
                        (plot.moisture - fx.dryPerHour * hours + fx.rainPerHour * hours)
                            .coerceIn(0f, 1f)

                    if (plot.stage in 1..4) {
                        if (!plot.weed && Random.nextFloat() < weedP) plot.weed = true
                        if (!plot.bug && Random.nextFloat() < bugP) plot.bug = true
                    }

                    // 枯萎：干旱即可按概率触发（不再要求单次 ≥ 0.5h，否则在线几乎永不枯）
                    if (plot.stage in 1..4 && plot.moisture < 0.08f && Random.nextFloat() < wiltP) {
                        plot.stage = 5
                        plot.stageProgress = 0f
                    }

                    val crop = cropById(plot.cropId)
                    if (crop != null && plot.stage in 1..3) {
                        val blocked = plot.weed || plot.bug || plot.moisture < 0.25f
                        if (!blocked) {
                            val stageIdx = plot.stage - 1
                            val needH = crop.stageHours.getOrElse(stageIdx) { 1f }.coerceAtLeast(0.2f)
                            val mul = fx.growthMul * (0.7f + 0.3f * plot.moisture)
                            plot.stageProgress += (hours / needH) * mul
                            while (plot.stageProgress >= 1f && plot.stage in 1..3) {
                                plot.stageProgress -= 1f
                                plot.stage += 1
                                if (plot.stage >= 4) {
                                    plot.stage = 4
                                    plot.stageProgress = 1f
                                    break
                                }
                            }
                        }
                    }
                }
                plot.lastTickMs = now
                plot
            }
        if (dirty) plots = next
    }

    fun onPlot(
        index: Int,
        weather: FarmWeather,
    ) {
        val plot = plots.getOrNull(index) ?: return
        when (selectedTool) {
            FarmTool.INFO -> {
                val crop = cropById(plot.cropId)
                toast(
                    when (plot.stage) {
                        0 -> str(R.string.bear_farm_toast_empty)
                        5 -> str(R.string.bear_farm_toast_wilted)
                        4 ->
                            str(
                                R.string.bear_farm_toast_ripe,
                                crop?.emoji ?: "",
                                crop?.nameZh ?: "?",
                            )
                        else ->
                            buildString {
                                append(
                                    str(
                                        R.string.bear_farm_toast_status,
                                        crop?.emoji ?: "",
                                        crop?.nameZh ?: "?",
                                        stageLabel(plot.stage),
                                        (plot.stageProgress * 100).toInt(),
                                    ),
                                )
                                if (plot.moisture < 0.3f) append(str(R.string.bear_farm_toast_thirsty))
                                if (plot.weed) append(str(R.string.bear_farm_toast_has_weed))
                                if (plot.bug) append(str(R.string.bear_farm_toast_has_bug))
                            }
                    },
                )
            }
            FarmTool.PLANT -> {
                if (plot.stage != 0) {
                    toast(str(R.string.bear_farm_toast_occupied))
                    return
                }
                val crop = cropById(selectedSeedId) ?: return
                if (crop.id !in unlocked) {
                    toast(str(R.string.bear_farm_toast_locked_seed))
                    return
                }
                if (coins < crop.seedCost) {
                    toast(str(R.string.bear_farm_toast_need_coins, crop.seedCost))
                    return
                }
                coins -= crop.seedCost
                updatePlot(index) {
                    cropId = crop.id
                    stage = 1
                    stageProgress = 0f
                    moisture = 0.55f
                    weed = false
                    bug = false
                }
                toast(str(R.string.bear_farm_toast_planted, crop.emoji, crop.nameZh))
            }
            FarmTool.WATER -> {
                if (plot.stage == 0 || plot.stage == 5) {
                    toast(str(R.string.bear_farm_toast_nothing_water))
                    return
                }
                updatePlot(index) { moisture = (moisture + 0.45f).coerceAtMost(1f) }
                toast(str(R.string.bear_farm_toast_watered))
            }
            FarmTool.WEED -> {
                if (!plot.weed) {
                    toast(str(R.string.bear_farm_toast_no_weed))
                    return
                }
                updatePlot(index) { weed = false }
                toast(str(R.string.bear_farm_toast_weeded))
            }
            FarmTool.BUG -> {
                if (!plot.bug) {
                    toast(str(R.string.bear_farm_toast_no_bug))
                    return
                }
                updatePlot(index) { bug = false }
                toast(str(R.string.bear_farm_toast_debugged))
            }
            FarmTool.HARVEST -> {
                if (plot.stage != 4) {
                    toast(str(R.string.bear_farm_toast_not_ripe))
                    return
                }
                val crop = cropById(plot.cropId) ?: return
                val bonus =
                    when (weather) {
                        FarmWeather.SUNNY -> 1.15f
                        FarmWeather.RAINY -> 1.05f
                        else -> 1f
                    }
                val gain = (crop.sellPrice * bonus).toInt()
                coins += gain
                harvestedTotal++
                val unlockedMsg = unlockByProgress()
                updatePlot(index) {
                    cropId = null
                    stage = 0
                    stageProgress = 0f
                    weed = false
                    bug = false
                    moisture = 0.4f
                }
                val harvestMsg =
                    str(
                        R.string.bear_farm_toast_harvest,
                        crop.emoji,
                        crop.nameZh,
                        gain,
                    )
                toast(if (unlockedMsg != null) "$harvestMsg\n$unlockedMsg" else harvestMsg)
            }
            FarmTool.CLEAR -> {
                if (plot.stage == 0) {
                    toast(str(R.string.bear_farm_toast_already_empty))
                    return
                }
                updatePlot(index) {
                    cropId = null
                    stage = 0
                    stageProgress = 0f
                    weed = false
                    bug = false
                }
                toast(str(R.string.bear_farm_toast_cleared))
            }
        }
        save()
    }

    /** @return 解锁提示文案；未解锁则 null */
    private fun unlockByProgress(): String? {
        val order = FARM_CROPS.map { it.id }
        val next = order.firstOrNull { it !in unlocked } ?: return null
        val need = farmUnlockNeed(unlocked.size)
        if (harvestedTotal < need) return null
        unlocked = unlocked + next
        val c = cropById(next)
        return str(R.string.bear_farm_toast_unlock_crop, c?.emoji ?: "", c?.nameZh ?: "")
    }

    private fun updatePlot(
        index: Int,
        block: FarmPlotState.() -> Unit,
    ) {
        plots =
            plots.mapIndexed { i, p ->
                if (i == index) p.copy().also(block) else p
            }
    }

    fun buyUnlock(cropId: String): Boolean {
        val crop = cropById(cropId) ?: return false
        if (cropId in unlocked) return false
        val price = crop.seedCost * 5
        if (coins < price) {
            toast(str(R.string.bear_farm_toast_unlock_need, price))
            return false
        }
        coins -= price
        unlocked = unlocked + cropId
        toast(str(R.string.bear_farm_toast_unlocked, crop.emoji, crop.nameZh))
        save()
        return true
    }
}

@Composable
fun BearFarmScreen(
    sceneCode: Int?,
    isDay: Boolean?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val appContext = remember(context) { context.applicationContext }
    val farm = remember(appContext) { BearFarm(appContext).also { it.load() } }
    val weather = remember(sceneCode, isDay) { farmWeatherFromScene(sceneCode, isDay) }
    val fx = remember(weather) { farmWeatherEffect(weather) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(farm.toast) {
        val msg = farm.toast ?: return@LaunchedEffect
        delay(2500)
        if (farm.toast == msg) farm.clearToast()
    }

    LaunchedEffect(weather) {
        var last = Long.MIN_VALUE
        var slowTick = 0
        // 雨/雪/雷暴有粒子动画需要 60fps；晴/多云/雾降频到约 12fps 即可
        val needsHighFps =
            weather == FarmWeather.RAINY ||
                weather == FarmWeather.STORMY ||
                weather == FarmWeather.SNOWY
        while (isActive) {
            withFrameNanos { now ->
                val dt =
                    if (last == Long.MIN_VALUE) {
                        1f / 60f
                    } else {
                        min((now - last) / 1e9f, 0.05f)
                    }
                last = now
                farm.tick(System.currentTimeMillis(), weather, dt)
                if (needsHighFps || slowTick % 5 == 0) {
                    frame = now
                }
                slowTick++
            }
        }
    }

    // 自动保存与渲染帧解耦：独立 5 秒定时，不再依赖帧循环
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5_000)
            farm.save()
        }
    }

    BackHandler {
        farm.save()
        onExit()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        skyGradient(Color(fx.skyTop), Color(fx.skyBottom)),
                    ),
                ),
    ) {
        // 氛围装饰
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frame
            // 地平线光晕：位于画面上部天空区域
            drawHorizonGlow(
                size.height * 0.32f,
                midColor(Color(fx.skyTop), Color.White),
                size.height * 0.30f,
            )
            drawFarmAtmosphere(weather, farm.time)
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
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF8E1).copy(alpha = 0.92f),
                ) {
                    Text(
                        "🪙 ${farm.coins}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D4C41),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = { farm.showShop = true }) {
                    Text(stringResource(R.string.bear_farm_shop), color = Color.White)
                }
            }

            farm.toast?.let { msg ->
                Text(
                    msg,
                    color = Color.White,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // 田地
            LazyVerticalGrid(
                columns = GridCells.Fixed(FARM_COLS),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                itemsIndexed(farm.plots) { index, plot ->
                    FarmPlotCell(
                        plot = plot,
                        soilColor = Color(fx.soil),
                        onClick = {
                            farm.onPlot(index, weather)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
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
                            .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FARM_CROPS.filter { it.id in farm.unlocked }.forEach { crop ->
                        val sel = farm.selectedSeedId == crop.id
                        Surface(
                            onClick = { farm.selectedSeedId = crop.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) Color(0xFFFFF59D) else Color.White.copy(alpha = 0.85f),
                            border =
                                if (sel) {
                                    BorderStroke(2.dp, Color(0xFFFF8F00))
                                } else {
                                    null
                                },
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(crop.emoji, fontSize = 22.sp)
                                Text(crop.nameZh, style = MaterialTheme.typography.labelSmall)
                                Text("${crop.seedCost}币", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 工具栏
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FarmTool.entries.forEach { tool ->
                    val label =
                        stringResource(
                            when (tool) {
                                FarmTool.INFO -> R.string.bear_farm_tool_info
                                FarmTool.PLANT -> R.string.bear_farm_tool_plant
                                FarmTool.WATER -> R.string.bear_farm_tool_water
                                FarmTool.WEED -> R.string.bear_farm_tool_weed
                                FarmTool.BUG -> R.string.bear_farm_tool_bug
                                FarmTool.HARVEST -> R.string.bear_farm_tool_harvest
                                FarmTool.CLEAR -> R.string.bear_farm_tool_clear
                            },
                        )
                    val icon =
                        when (tool) {
                            FarmTool.INFO -> "👀"
                            FarmTool.PLANT -> "🌱"
                            FarmTool.WATER -> "💧"
                            FarmTool.WEED -> "🌿"
                            FarmTool.BUG -> "🐛"
                            FarmTool.HARVEST -> "🧺"
                            FarmTool.CLEAR -> "🧹"
                        }
                    val sel = farm.selectedTool == tool
                    Surface(
                        onClick = { farm.selectedTool = tool },
                        shape = RoundedCornerShape(14.dp),
                        color = if (sel) Color(0xFF81C784) else Color.White.copy(alpha = 0.88f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(icon, fontSize = 20.sp)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (farm.showShop) {
            FarmShopDialog(
                farm = farm,
                onDismiss = { farm.showShop = false },
            )
        }
    }
}

@Composable
private fun FarmPlotCell(
    plot: FarmPlotState,
    soilColor: Color,
    onClick: () -> Unit,
) {
    val crop = cropById(plot.cropId)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = soilColor),
        modifier = Modifier.aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FarmPlotVisual(
                plot = plot,
                crop = crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 状态角标：杂草 / 害虫
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp),
            ) {
                if (plot.weed) Text("🌿", fontSize = 13.sp)
                if (plot.bug) Text("🐛", fontSize = 13.sp)
            }
            if (plot.stage in 1..4) {
                // 水分条
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(4.dp)
                            .fillMaxWidth(0.8f)
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(plot.moisture.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(
                                    when {
                                        plot.moisture < 0.25f -> Color(0xFFE53935)
                                        plot.moisture < 0.5f -> Color(0xFFFFB300)
                                        else -> Color(0xFF42A5F5)
                                    },
                                    RoundedCornerShape(2.dp),
                                ),
                    )
                }
            }
            // 成熟标识
            if (plot.stage == 4) {
                Text(
                    "✨",
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** 田地格子主体：土壤 + 按阶段渲染的作物（用作物 emoji 让玩家一眼认出种的是什么）。 */
@Composable
private fun FarmPlotVisual(
    plot: FarmPlotState,
    crop: CropDef?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 土壤纹理
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSoilBase(plot)
        }
        when (plot.stage) {
            0 -> {
                // 空地：垄沟 + 播种提示
                Text(
                    "＋",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            5 -> {
                // 枯萎：自绘作物灰化歪倒 + 枯萎花标记
                FarmCropCanvas(
                    crop = crop,
                    stage = plot.stage,
                    progress = 1f,
                    wilted = true,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "🥀",
                    fontSize = 12.sp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                )
            }
            else -> {
                val c = crop ?: return
                // 生长中：进度环
                if (plot.stage in 1..3) {
                    Canvas(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                    ) {
                        drawArc(
                            Color.White.copy(alpha = 0.6f),
                            startAngle = -90f,
                            sweepAngle = 360f * plot.stageProgress,
                            useCenter = false,
                            style = Stroke(width = 2.5f),
                        )
                    }
                }
                if (plot.stage == 4) {
                    // 成熟：金色光晕 + 呼吸缩放
                    val transition = rememberInfiniteTransition(label = "ripe")
                    val pulse by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(durationMillis = 650),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "pulse",
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            Color(0xFFFFF59D).copy(alpha = 0.10f + 0.12f * pulse),
                            radius = size.minDimension * 0.44f,
                            center = center,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = 1f + 0.10f * pulse
                                    scaleX = s
                                    scaleY = s
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        FarmCropCanvas(
                            crop = c,
                            stage = plot.stage,
                            progress = 1f,
                            wilted = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    // 种子/发芽/成长：自绘植株，种子阶段整体下移像埋在土里
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .offset(y = if (plot.stage == 1) 12.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FarmCropCanvas(
                            crop = c,
                            stage = plot.stage,
                            progress = plot.stageProgress,
                            wilted = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** 自绘作物画布：把绘制委托给 [drawFarmCrop]。 */
@Composable
private fun FarmCropCanvas(
    crop: CropDef?,
    stage: Int,
    progress: Float,
    wilted: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val c = crop ?: return@Canvas
        drawFarmCrop(c, stage, progress, wilted)
    }
}

private fun DrawScope.drawSoilBase(plot: FarmPlotState) {
    val w = size.width
    val h = size.height
    when (plot.stage) {
        0 -> {
            // 空地：多道垄沟 + 少量土粒
            val furrow = Color.Black.copy(alpha = 0.12f)
            for (i in 0..2) {
                val y = h * (0.28f + i * 0.22f)
                drawRoundRect(
                    furrow,
                    Offset(w * 0.12f, y),
                    Size(w * 0.76f, h * 0.08f),
                    CornerRadius(3f),
                )
            }
            drawCircle(Color.White.copy(alpha = 0.07f), w * 0.028f, Offset(w * 0.2f, h * 0.45f))
            drawCircle(Color.White.copy(alpha = 0.07f), w * 0.022f, Offset(w * 0.78f, h * 0.62f))
            drawCircle(Color.White.copy(alpha = 0.07f), w * 0.026f, Offset(w * 0.55f, h * 0.75f))
        }
        else -> {
            // 已翻整的土垄（作物根部）
            drawRoundRect(
                Color(0xFF3E2723).copy(alpha = 0.16f),
                Offset(w * 0.10f, h * 0.62f),
                Size(w * 0.80f, h * 0.22f),
                CornerRadius(12f),
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.08f),
                Offset(w * 0.14f, h * 0.60f),
                Size(w * 0.72f, h * 0.10f),
                CornerRadius(6f),
            )
        }
    }
}

private fun DrawScope.drawFarmAtmosphere(
    weather: FarmWeather,
    time: Float,
) {
    when (weather) {
        FarmWeather.RAINY, FarmWeather.STORMY -> {
            for (i in 0 until 50) {
                val x = ((i * 97 + time * 90) % (size.width + 40)) - 20
                val y = ((i * 53 + time * 240) % (size.height + 60)) - 30
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(x, y),
                    Offset(x - 3f, y + 12f),
                    strokeWidth = 2f,
                )
            }
            if (weather == FarmWeather.STORMY && sin(time * 8f) > 0.92f) {
                drawRect(Color.White.copy(alpha = 0.15f))
            }
        }
        FarmWeather.SNOWY -> {
            for (i in 0 until 40) {
                val x = ((i * 83 + time * 25) % (size.width + 20))
                val y = ((i * 61 + time * 40) % (size.height + 20))
                drawCircle(Color.White.copy(alpha = 0.75f), 2f + i % 3, Offset(x, y))
            }
        }
        FarmWeather.SUNNY -> {
            val sun = Offset(size.width * 0.88f, 70f)
            drawCircle(Color(0xFFFFE082), 26f, sun)
            for (i in 0 until 10) {
                val a = (Math.PI * 2 * i / 10 + time * 0.3).toFloat()
                drawLine(
                    Color(0xFFFFD54F),
                    sun + Offset(cos(a) * 32f, sin(a) * 32f),
                    sun + Offset(cos(a) * 42f, sin(a) * 42f),
                    strokeWidth = 3f,
                )
            }
        }
        FarmWeather.FOGGY -> {
            drawRect(Color.White.copy(alpha = 0.18f))
        }
        FarmWeather.CLOUDY -> {
            for (i in 0 until 4) {
                val x = ((i * 160f + time * 12f) % (size.width + 120f)) - 60f
                val y = 60f + i * 18f
                drawCircle(Color.White.copy(alpha = 0.45f), 26f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.45f), 20f, Offset(x + 22f, y + 4f))
            }
        }
    }
}

@Composable
private fun FarmShopDialog(
    farm: BearFarm,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.bear_farm_shop_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.bear_farm_shop_hint, farm.harvestedTotal) +
                        " · ${farm.unlocked.size}/${FARM_CROPS.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    itemsIndexed(FARM_CROPS) { _, crop ->
                        val owned = crop.id in farm.unlocked
                        val unlockPrice = crop.seedCost * 5
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = if (owned) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(crop.emoji, fontSize = 24.sp)
                                Text(crop.nameZh, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    crop.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "约${"%.1f".format(crop.totalHours)}时 · 售${crop.sellPrice}",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (owned) {
                                    Text(
                                        stringResource(R.string.bear_farm_shop_owned),
                                        color = Color(0xFF2E7D32),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                } else {
                                    Button(
                                        onClick = { farm.buyUnlock(crop.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.bear_farm_shop_unlock_btn, unlockPrice),
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.bear_farm_close_shop)) }
            }
        }
    }
}
