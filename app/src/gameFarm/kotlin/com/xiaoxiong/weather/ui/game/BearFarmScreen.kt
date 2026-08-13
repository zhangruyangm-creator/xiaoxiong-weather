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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
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

/** 主工具：照料（智能）/ 播种 / 铲除；其余放进「更多」。 */
private enum class FarmTool { CARE, PLANT, CLEAR, INFO, WATER, WEED, BUG, HARVEST }

private val PRIMARY_TOOLS = listOf(FarmTool.CARE, FarmTool.PLANT, FarmTool.CLEAR)
private val EXTRA_TOOLS = listOf(FarmTool.INFO, FarmTool.WATER, FarmTool.WEED, FarmTool.BUG, FarmTool.HARVEST)

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
    var selectedTool by mutableStateOf(FarmTool.CARE)
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
        prefs.edit { putString("farm_save_v1", farmJson.encodeToString(FarmSave.serializer(), s)) }
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
                var elapsed = now - plot.lastTickMs
                if (elapsed < 0L) {
                    // 时钟回拨（NTP 校时/手动改时间）：把 lastTickMs 收敛到 now，
                    // 否则 elapsed 恒为 0 且 lastTickMs 停在未来，农场会冻结到墙钟追平
                    plot.lastTickMs = now
                    elapsed = 0L
                    dirty = true
                }
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
            FarmTool.CARE -> applyCare(index, plot, weather)
            FarmTool.INFO -> showInfo(plot)
            FarmTool.PLANT -> plantAt(index, plot)
            FarmTool.WATER -> waterAt(index, plot)
            FarmTool.WEED -> weedAt(index, plot)
            FarmTool.BUG -> bugAt(index, plot)
            FarmTool.HARVEST -> harvestAt(index, plot, weather)
            FarmTool.CLEAR -> clearAt(index, plot)
        }
        save()
    }

    /** 智能照料：害虫 > 杂草 > 成熟收获 > 浇水 > 枯萎清理 > 空地提示 > 查看。 */
    private fun applyCare(
        index: Int,
        plot: FarmPlotState,
        weather: FarmWeather,
    ) {
        when {
            plot.bug -> bugAt(index, plot)
            plot.weed -> weedAt(index, plot)
            plot.stage == 4 -> harvestAt(index, plot, weather)
            plot.stage in 1..4 && plot.moisture < 0.35f -> waterAt(index, plot)
            plot.stage == 5 -> clearAt(index, plot)
            plot.stage == 0 -> toast(str(R.string.bear_farm_toast_use_plant))
            else -> showInfo(plot)
        }
    }

    private fun showInfo(plot: FarmPlotState) {
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

    private fun plantAt(
        index: Int,
        plot: FarmPlotState,
    ) {
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

    private fun waterAt(
        index: Int,
        plot: FarmPlotState,
    ) {
        if (plot.stage == 0 || plot.stage == 5) {
            toast(str(R.string.bear_farm_toast_nothing_water))
            return
        }
        updatePlot(index) { moisture = (moisture + 0.45f).coerceAtMost(1f) }
        toast(str(R.string.bear_farm_toast_watered))
    }

    private fun weedAt(
        index: Int,
        plot: FarmPlotState,
    ) {
        if (!plot.weed) {
            toast(str(R.string.bear_farm_toast_no_weed))
            return
        }
        updatePlot(index) { weed = false }
        toast(str(R.string.bear_farm_toast_weeded))
    }

    private fun bugAt(
        index: Int,
        plot: FarmPlotState,
    ) {
        if (!plot.bug) {
            toast(str(R.string.bear_farm_toast_no_bug))
            return
        }
        updatePlot(index) { bug = false }
        toast(str(R.string.bear_farm_toast_debugged))
    }

    private fun harvestAt(
        index: Int,
        plot: FarmPlotState,
        weather: FarmWeather,
    ) {
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

    private fun clearAt(
        index: Int,
        plot: FarmPlotState,
    ) {
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
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF8E1).copy(alpha = 0.94f),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        msg,
                        color = Color(0xFF5D4037),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // 田地
            LazyVerticalGrid(
                columns = GridCells.Fixed(FARM_COLS),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                itemsIndexed(farm.plots) { index, plot ->
                    FarmPlotCell(
                        plot = plot,
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
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FARM_CROPS.filter { it.id in farm.unlocked }.forEach { crop ->
                        val sel = farm.selectedSeedId == crop.id
                        Surface(
                            onClick = { farm.selectedSeedId = crop.id },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) Color(0xFFFFF59D) else Color.White.copy(alpha = 0.92f),
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
                                    painter = painterResource(FarmCropArt.ripe(crop)),
                                    contentDescription = crop.nameZh,
                                    modifier = Modifier.size(34.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    crop.nameZh,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF5D4037),
                                )
                                Text(
                                    "${crop.seedCost}币",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8D6E63),
                                )
                            }
                        }
                    }
                }
            }

            FarmToolBar(
                selected = farm.selectedTool,
                onSelect = { farm.selectedTool = it },
            )
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
private fun FarmToolBar(
    selected: FarmTool,
    onSelect: (FarmTool) -> Unit,
) {
    var showExtra by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRIMARY_TOOLS.forEach { tool ->
                FarmToolChip(
                    tool = tool,
                    selected = selected == tool,
                    onClick = {
                        showExtra = false
                        onSelect(tool)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            FarmToolChip(
                tool = null,
                label = stringResource(R.string.bear_farm_tool_more),
                icon = "⋯",
                selected = showExtra || selected in EXTRA_TOOLS,
                onClick = { showExtra = !showExtra },
                modifier = Modifier.weight(1f),
            )
        }
        if (showExtra) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EXTRA_TOOLS.forEach { tool ->
                    FarmToolChip(
                        tool = tool,
                        selected = selected == tool,
                        onClick = {
                            onSelect(tool)
                            showExtra = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FarmToolChip(
    tool: FarmTool?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: String? = null,
) {
    val resolvedLabel =
        label
            ?: tool
                ?.let {
                    stringResource(
                        when (it) {
                            FarmTool.CARE -> R.string.bear_farm_tool_care
                            FarmTool.INFO -> R.string.bear_farm_tool_info
                            FarmTool.PLANT -> R.string.bear_farm_tool_plant
                            FarmTool.WATER -> R.string.bear_farm_tool_water
                            FarmTool.WEED -> R.string.bear_farm_tool_weed
                            FarmTool.BUG -> R.string.bear_farm_tool_bug
                            FarmTool.HARVEST -> R.string.bear_farm_tool_harvest
                            FarmTool.CLEAR -> R.string.bear_farm_tool_clear
                        },
                    )
                }.orEmpty()
    val resolvedIcon =
        icon
            ?: when (tool) {
                FarmTool.CARE -> "🧺"
                FarmTool.INFO -> "👀"
                FarmTool.PLANT -> "🌱"
                FarmTool.WATER -> "💧"
                FarmTool.WEED -> "✂️"
                FarmTool.BUG -> "🛡️"
                FarmTool.HARVEST -> "✨"
                FarmTool.CLEAR -> "🧹"
                null -> "⋯"
            }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF81C784) else Color.White.copy(alpha = 0.92f),
        shadowElevation = if (selected) 3.dp else 1.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(resolvedIcon, fontSize = 18.sp)
            Text(
                resolvedLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF1B5E20) else Color(0xFF5D4037),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FarmPlotCell(
    plot: FarmPlotState,
    onClick: () -> Unit,
) {
    val crop = cropById(plot.cropId)
    val dry = plot.stage in 1..5 && plot.moisture < 0.25f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 3.dp,
        modifier = Modifier.aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FarmPlotVisual(
                plot = plot,
                crop = crop,
                dry = dry,
                modifier = Modifier.fillMaxSize(),
            )
            if (plot.weed || plot.bug) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (plot.weed) {
                        Image(
                            painter = painterResource(FarmCropArt.weed()),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (plot.bug) {
                        Image(
                            painter = painterResource(FarmCropArt.bug()),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            if (plot.stage in 1..4) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(plot.moisture.coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(
                                    when {
                                        plot.moisture < 0.25f -> Color(0xFFE53935)
                                        plot.moisture < 0.5f -> Color(0xFFFFB300)
                                        else -> Color(0xFF42A5F5)
                                    },
                                    RoundedCornerShape(3.dp),
                                ),
                    )
                }
            }
        }
    }
}

/** 田地格子：卡通土壤底图 + 阶段/成熟作物图 + 呼吸动画。 */
@Composable
private fun FarmPlotVisual(
    plot: FarmPlotState,
    crop: CropDef?,
    dry: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(FarmCropArt.soil(dry = dry)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        when (plot.stage) {
            0 -> {
                Text(
                    "＋",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            4 -> {
                val c = crop ?: return
                val transition = rememberInfiniteTransition(label = "ripe")
                val pulse by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 700),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "pulse",
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        Color(0xFFFFF59D).copy(alpha = 0.12f + 0.14f * pulse),
                        radius = size.minDimension * 0.42f,
                        center = center,
                    )
                }
                Image(
                    painter = painterResource(FarmCropArt.ripe(c)),
                    contentDescription = c.nameZh,
                    modifier =
                        Modifier
                            .fillMaxSize(0.78f)
                            .graphicsLayer {
                                val s = 1f + 0.08f * pulse
                                scaleX = s
                                scaleY = s
                            },
                    contentScale = ContentScale.Fit,
                )
            }
            else -> {
                val stageRes = FarmCropArt.stage(plot.stage)
                if (stageRes != null) {
                    val scale =
                        when (plot.stage) {
                            1 -> 0.52f
                            2 -> 0.62f
                            3 -> 0.70f + 0.08f * plot.stageProgress
                            else -> 0.72f
                        }
                    if (plot.stage in 1..3) {
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(5.dp),
                        ) {
                            drawArc(
                                Color.White.copy(alpha = 0.55f),
                                startAngle = -90f,
                                sweepAngle = 360f * plot.stageProgress,
                                useCenter = false,
                                style = Stroke(width = 2.5f),
                            )
                        }
                    }
                    Image(
                        painter = painterResource(stageRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(scale),
                        contentScale = ContentScale.Fit,
                    )
                }
                // 成长阶段角落小预览：成熟后的作物长相
                if (plot.stage in 2..3 && crop != null) {
                    Image(
                        painter = painterResource(FarmCropArt.ripe(crop)),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .alpha(0.85f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
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
    val categories =
        remember {
            listOf(null) + FARM_CROPS.map { it.category }.distinct()
        }
    var selectedCat by remember { mutableStateOf<String?>(null) }
    val filtered =
        remember(selectedCat) {
            if (selectedCat == null) FARM_CROPS else FARM_CROPS.filter { it.category == selectedCat }
        }
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
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF2)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.bear_farm_shop_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037),
                )
                Text(
                    stringResource(R.string.bear_farm_shop_hint, farm.harvestedTotal) +
                        " · ${farm.unlocked.size}/${FARM_CROPS.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8D6E63),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        val label = cat ?: stringResource(R.string.bear_farm_shop_cat_all)
                        val sel = selectedCat == cat
                        Surface(
                            onClick = { selectedCat = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (sel) Color(0xFF81C784) else Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE0D2C0)),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) Color(0xFF1B5E20) else Color(0xFF6D4C41),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    itemsIndexed(filtered, key = { _, c -> c.id }) { _, crop ->
                        val owned = crop.id in farm.unlocked
                        val unlockPrice = crop.seedCost * 5
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (owned) {
                                            Color(0xFFE8F5E9)
                                        } else {
                                            Color(0xFFFFF3E0).copy(alpha = 0.85f)
                                        },
                                ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .padding(8.dp)
                                        .alpha(if (owned) 1f else 0.92f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    painter = painterResource(FarmCropArt.ripe(crop)),
                                    contentDescription = crop.nameZh,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    crop.nameZh,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF5D4037),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "约${"%.0f".format(crop.totalHours)}时 · 售${crop.sellPrice}",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    fontSize = 10.sp,
                                    color = Color(0xFF8D6E63),
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
