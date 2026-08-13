package com.xiaoxiong.weather.ui.game

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.xiaoxiong.weather.R
import kotlinx.serialization.json.Json

internal enum class FarmTool { CARE, PLANT, CLEAR, INFO, WATER, WEED, BUG, HARVEST, FERTILIZE }

internal val PRIMARY_TOOLS = listOf(FarmTool.CARE, FarmTool.PLANT, FarmTool.CLEAR)
internal val EXTRA_TOOLS = listOf(FarmTool.INFO, FarmTool.WATER, FarmTool.WEED, FarmTool.BUG, FarmTool.HARVEST, FarmTool.FERTILIZE)

/** toast 消息：带自增 id，同文案连点时也能重启 2.5s 自动清除定时器。 */
internal data class FarmToast(
    val id: Int,
    val text: String,
)

/** 收获金币飘字：按网格行列锚定到对应格子。 */
internal data class FarmReward(
    val id: Int,
    val col: Int,
    val row: Int,
    val label: String,
)

/** 商店一次性道具。 */
internal data class FarmShopItem(
    val id: String,
    @param:StringRes val nameRes: Int,
    val icon: String,
    val price: Int,
)

internal val FARM_SHOP_ITEMS =
    listOf(
        FarmShopItem("rain_can", R.string.bear_farm_item_rain_can, "🌧️", RAIN_CAN_COST),
        FarmShopItem("bug_spray", R.string.bear_farm_item_bug_spray, "🧴", BUG_SPRAY_COST),
        FarmShopItem("weed_killer", R.string.bear_farm_item_weed_killer, "🧹", WEED_KILLER_COST),
    )

internal val farmJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * 农场的 UI 状态壳：持有 Compose 状态并委托 [FarmSimulator] 执行规则。
 * 规则与持久化分别在 FarmSimulator / FarmSave 中，本类只做同步与反馈翻译。
 */
internal class BearFarm(
    private val appContext: Context,
) {
    private val prefs = appContext.getSharedPreferences("bear_game", Context.MODE_PRIVATE)

    // Compose 状态（镜像自 [state]，动作后经 syncFromState 同步；相同值不会触发重组）
    var coins by mutableIntStateOf(80)
    var plots by mutableStateOf(List(FARM_PLOT_COUNT) { FarmPlotState(lastTickMs = System.currentTimeMillis()) })
    var unlocked by mutableStateOf(setOf("radish", "cabbage", "wheat", "tomato"))
    var harvestedTotal by mutableIntStateOf(0)
    var harvestCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var inventory by mutableStateOf<Map<String, Int>>(emptyMap())
    var completedSeasons by mutableIntStateOf(0)
    var lifetimeHarvests by mutableIntStateOf(0)

    var toast by mutableStateOf<FarmToast?>(null)
    private var toastSeq = 0
    var floatingRewards by mutableStateOf<List<FarmReward>>(emptyList())
    private var rewardSeq = 0
    var unlockCard by mutableStateOf<CropDef?>(null)
    var showCatalog by mutableStateOf(false)
    var showInventory by mutableStateOf(false)
    var showSeasonConfirm by mutableStateOf(false)
    var selectedTool by mutableStateOf(FarmTool.CARE)
    var selectedSeedId by mutableStateOf("radish")
    var showShop by mutableStateOf(false)
    var time by mutableFloatStateOf(0f)

    /** 纯数据状态：所有规则走 FarmSimulator。 */
    private var state = FarmState()

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

    val currentSeason: Int get() = state.currentSeason
    val collectedCount: Int get() = state.collectedCount
    val allCollected: Boolean get() = state.allCollected

    fun load() {
        val raw = prefs.getString("farm_save_v1", null) ?: return
        runCatching {
            val save = farmJson.decodeFromString(FarmSave.serializer(), raw)
            state =
                FarmState.fromSave(
                    save = save,
                    // 旧存档（1.x，无图鉴字段）一次性回填：已解锁的作物视为收集过
                    backfillUnlockedAsCollected = save.schemaVersion < 2 && save.unlockedCropIds.size > 4,
                )
            syncFromState()
        }
    }

    fun save() {
        val s = state.toSave()
        prefs.edit { putString("farm_save_v1", farmJson.encodeToString(FarmSave.serializer(), s)) }
    }

    fun toast(msg: String) {
        toast = FarmToast(++toastSeq, msg)
    }

    fun clearToast() {
        toast = null
    }

    fun addReward(
        label: String,
        plotIndex: Int,
    ) {
        floatingRewards =
            floatingRewards +
            FarmReward(
                id = ++rewardSeq,
                col = plotIndex % FARM_COLS,
                row = plotIndex / FARM_COLS,
                label = label,
            )
    }

    fun removeReward(id: Int) {
        floatingRewards = floatingRewards.filterNot { it.id == id }
    }

    /** 把纯状态同步回 Compose 状态。 */
    private fun syncFromState() {
        coins = state.coins
        plots = state.plots
        unlocked = state.unlocked
        harvestedTotal = state.harvestedTotal
        harvestCounts = state.harvestCounts
        inventory = state.inventory
        completedSeasons = state.completedSeasons
        lifetimeHarvests = state.lifetimeHarvests
    }

    /** 执行一次规则并同步状态。 */
    private inline fun runAction(action: () -> FarmEffect) {
        val effect = action()
        syncFromState()
        applyEffect(effect)
    }

    /** 把规则产出的反馈翻译成 UI：toast / 飘字 / 解锁弹卡。 */
    private fun applyEffect(effect: FarmEffect) {
        effect.toastRes?.let { res ->
            toast(str(res, *effect.toastArgs.toTypedArray()))
        }
        effect.rewards.forEach { addReward(it.label, it.plotIndex) }
        effect.unlockCropId?.let { unlockCard = cropById(it) }
    }

    fun tick(
        now: Long,
        weather: FarmWeather,
    ) {
        FarmSimulator.tick(state, now, weather)
        syncFromState()
    }

    fun onPlot(
        index: Int,
        weather: FarmWeather,
    ) {
        when (selectedTool) {
            FarmTool.CARE -> applyCare(index)
            FarmTool.INFO -> state.plots.getOrNull(index)?.let { showInfo(it) }
            FarmTool.PLANT -> runAction { FarmSimulator.plant(state, index, selectedSeedId) }
            FarmTool.WATER -> runAction { FarmSimulator.water(state, index) }
            FarmTool.WEED -> runAction { FarmSimulator.weed(state, index) }
            FarmTool.BUG -> runAction { FarmSimulator.bug(state, index) }
            FarmTool.HARVEST -> runAction { FarmSimulator.harvest(state, index) }
            FarmTool.CLEAR -> runAction { FarmSimulator.clear(state, index) }
            FarmTool.FERTILIZE -> runAction { FarmSimulator.fertilize(state, index, weather) }
        }
        save()
    }

    /** 智能照料：害虫 > 杂草 > 成熟收获 > 浇水 > 枯萎清理 > 空地提示 > 查看。 */
    private fun applyCare(index: Int) {
        val plot = state.plots.getOrNull(index) ?: return
        when {
            plot.bug -> runAction { FarmSimulator.bug(state, index) }
            plot.weed -> runAction { FarmSimulator.weed(state, index) }
            plot.stage == 4 -> runAction { FarmSimulator.harvest(state, index) }
            plot.stage in 1..4 && plot.moisture < 0.35f -> runAction { FarmSimulator.water(state, index) }
            plot.stage == 5 -> runAction { FarmSimulator.clear(state, index) }
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

    /** 一键照料：按智能照料优先级处理全部格子，收尾汇总成一条提示。 */
    fun careAll() {
        runAction { FarmSimulator.careAll(state) }
        save()
    }

    /** 小雨壶：全田浇水（一次性道具）。 */
    fun rainAll() {
        runAction { FarmSimulator.rainAll(state) }
        save()
    }

    /** 除虫喷雾：清除全田害虫（一次性道具）。 */
    fun bugAll() {
        runAction { FarmSimulator.bugAll(state) }
        save()
    }

    /** 除草剂：清除全田杂草（一次性道具）。 */
    fun weedAll() {
        runAction { FarmSimulator.weedAll(state) }
        save()
    }

    /** 开启新一季：重置金币/田地/解锁，保留背包/图鉴/记录，获得永久生长加成。 */
    fun startNewSeason() {
        runAction { FarmSimulator.startNewSeason(state) }
        showCatalog = false
        showSeasonConfirm = false
        save()
    }

    fun buyUnlock(cropId: String): Boolean {
        val effect = FarmSimulator.buyUnlock(state, cropId)
        syncFromState()
        applyEffect(effect)
        save()
        return effect.toastRes == R.string.bear_farm_toast_unlocked
    }

    /** 卖出背包里某作物的全部；售价按当前天气加成。 */
    fun sellAll(
        cropId: String,
        weather: FarmWeather,
    ) {
        runAction { FarmSimulator.sellAll(state, cropId, weather) }
        save()
    }

    /** 一键卖出背包全部；售价按当前天气加成。 */
    fun sellAllInventory(weather: FarmWeather) {
        runAction { FarmSimulator.sellAllInventory(state, weather) }
        save()
    }
}
