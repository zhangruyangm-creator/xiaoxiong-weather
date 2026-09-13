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

/** 初始解锁种子；回档后选中种子失效时的默认回退（与 [FarmState] 初始解锁集合一致）。 */
internal const val DEFAULT_SEED_ID = "radish"

/**
 * 回档后把选中的种子归位到当前已解锁作物：优先保留原选择；
 * 原选择已随回档失效时回落到默认种子，再不行取任一已解锁作物。
 * 纯逻辑便于单测。
 */
internal fun seedIdAfterLoad(
    unlocked: Set<String>,
    selected: String,
): String =
    when {
        selected in unlocked -> selected
        DEFAULT_SEED_ID in unlocked -> DEFAULT_SEED_ID
        else -> unlocked.minOrNull() ?: DEFAULT_SEED_ID
    }

/** 回档后需复位的瞬态 UI：全部清空 + 选中种子归位到已解锁作物。 */
internal data class FarmLoadedUi(
    val toast: FarmToast?,
    val floatingRewards: List<FarmReward>,
    val unlockCard: CropDef?,
    val unlockQueue: List<String>,
    val selectedSeedId: String,
)

/** 回档结果：纯数据状态 + 复位后的瞬态 UI。 */
internal data class FarmLoadResult(
    val state: FarmState,
    val ui: FarmLoadedUi,
)

/**
 * 由存档计算回档结果（纯逻辑，可单测）：状态按 [FarmState.fromSave] 恢复，
 * 瞬态 UI（toast/飘字/解锁卡与队列）一律清空，选中种子归位到已解锁作物。
 */
internal fun loadFarmFromSave(
    save: FarmSave,
    currentSelectedSeedId: String,
    backfillUnlockedAsCollected: Boolean,
): FarmLoadResult {
    val state = FarmState.fromSave(save, backfillUnlockedAsCollected)
    return FarmLoadResult(
        state = state,
        ui =
            FarmLoadedUi(
                toast = null,
                floatingRewards = emptyList(),
                unlockCard = null,
                unlockQueue = emptyList(),
                selectedSeedId = seedIdAfterLoad(state.unlocked, currentSelectedSeedId),
            ),
    )
}

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

/** 非收获类成功操作的提示文案 → UI_CLICK（收获带 rewards，走 COIN 分支）。 */
private val FARM_SUCCESS_TOASTS =
    setOf(
        R.string.bear_farm_toast_planted,
        R.string.bear_farm_toast_watered,
        R.string.bear_farm_toast_weeded,
        R.string.bear_farm_toast_debugged,
        R.string.bear_farm_toast_cleared,
        R.string.bear_farm_toast_fertilize,
        R.string.bear_farm_toast_item_rain,
        R.string.bear_farm_toast_item_bug,
        R.string.bear_farm_toast_item_weed,
        R.string.bear_farm_care_all_summary,
    )

/** 无效操作的提示文案 → BUMP。 */
private val FARM_FAILURE_TOASTS =
    setOf(
        R.string.bear_farm_toast_occupied,
        R.string.bear_farm_toast_locked_seed,
        R.string.bear_farm_toast_need_coins,
        R.string.bear_farm_toast_nothing_water,
        R.string.bear_farm_toast_no_weed,
        R.string.bear_farm_toast_no_bug,
        R.string.bear_farm_toast_already_empty,
        R.string.bear_farm_toast_not_ripe,
        R.string.bear_farm_toast_fertilize_target,
        R.string.bear_farm_toast_fertilize_blocked,
        R.string.bear_farm_toast_unlock_need,
        R.string.bear_farm_toast_sell_nothing,
        R.string.bear_farm_care_all_nothing,
    )

internal val farmJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * 一次操作要播的音效序列（纯映射，便于单测）：
 * - 主音：收获（带 rewards）/ 卖出进账 COIN，解锁新作物 STAR，换季 GOAL，
 *   其余成功操作 UI_CLICK，失败 BUMP；无事发生返回空列表（静默）；
 * - 收获同时解锁新作物：主音之外补一声 STAR，主音已是 STAR 时不重复；
 * - 每次点击至多两条且互不重复，不会有同一事件叠播。
 */
internal fun farmEffectCues(effect: FarmEffect): List<GameCue> {
    val primary =
        when {
            effect.rewards.isNotEmpty() -> GameCue.COIN
            effect.toastRes == R.string.bear_farm_toast_sell ||
                effect.toastRes == R.string.bear_farm_toast_sell_all -> GameCue.COIN
            effect.toastRes == R.string.bear_farm_toast_unlocked -> GameCue.STAR
            effect.toastRes == R.string.bear_farm_new_season_started -> GameCue.GOAL
            effect.toastRes in FARM_SUCCESS_TOASTS -> GameCue.UI_CLICK
            effect.toastRes in FARM_FAILURE_TOASTS -> GameCue.BUMP
            else -> null
        }
    val cues = mutableListOf<GameCue>()
    primary?.let(cues::add)
    if (effect.unlockCropIds.isNotEmpty() && primary != GameCue.STAR) cues += GameCue.STAR
    return cues
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
    private val unlockQueue = ArrayDeque<String>()
    var showCatalog by mutableStateOf(false)
    var showInventory by mutableStateOf(false)
    var showSeasonConfirm by mutableStateOf(false)
    var selectedTool by mutableStateOf(FarmTool.CARE)
    var selectedSeedId by mutableStateOf("radish")
    var showShop by mutableStateOf(false)
    var time by mutableFloatStateOf(0f)

    /** 纯数据状态：所有规则走 FarmSimulator。 */
    private var state = FarmState()

    /** 上次落盘的快照：与当前一致时跳过 JSON 编码与写盘。 */
    private var lastSaved: FarmSave? = null

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
    val seasonCollectedCount: Int get() = state.seasonCollectedCount
    val seasonGoal: Int get() = state.seasonGoal
    val seasonReady: Boolean get() = state.seasonReady
    val allCollected: Boolean get() = state.allCollected

    fun load() {
        val raw = prefs.getString("farm_save_v1", null) ?: return
        runCatching {
            val save = farmJson.decodeFromString(FarmSave.serializer(), raw)
            val loaded =
                loadFarmFromSave(
                    save = save,
                    currentSelectedSeedId = selectedSeedId,
                    // 旧存档（1.x，无图鉴字段）一次性回填：已解锁的作物视为收集过
                    backfillUnlockedAsCollected = save.schemaVersion < 2 && save.unlockedCropIds.size > 4,
                )
            state = loaded.state
            syncFromState()
            lastSaved = state.toSave()
            // 回档会覆盖当前进度：清空与旧局面绑定的瞬态 UI，并把选中种子归位到已解锁作物
            toast = loaded.ui.toast
            floatingRewards = loaded.ui.floatingRewards
            unlockCard = loaded.ui.unlockCard
            unlockQueue.clear()
            selectedSeedId = loaded.ui.selectedSeedId
        }
    }

    fun save() {
        val s = state.toSave()
        if (s == lastSaved) return
        prefs.edit { putString("farm_save_v1", farmJson.encodeToString(FarmSave.serializer(), s)) }
        lastSaved = s
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

    /** 把规则产出的反馈翻译成 UI：toast / 飘字 / 解锁弹卡 / 音效。 */
    private fun applyEffect(effect: FarmEffect) {
        playEffectCue(effect)
        effect.toastRes?.let { res ->
            toast(str(res, *effect.toastArgs.toTypedArray()))
        }
        effect.rewards.forEach { addReward(it.label, it.plotIndex) }
        effect.unlockCropIds.forEach { enqueueUnlockCard(it) }
    }

    /**
     * 一次用户操作结束后播放对应音效（映射见 [farmEffectCues]）。
     * 仅由 [applyEffect] 调用（每次点击至多一次），后台 tick/离线追赶不会触发。
     */
    private fun playEffectCue(effect: FarmEffect) {
        farmEffectCues(effect).forEach { GameAudio.play(appContext, it) }
    }

    /** 解锁弹卡逐个展示：已有卡在显示时先排队，关闭当前卡后再弹下一张。 */
    private fun enqueueUnlockCard(cropId: String) {
        val crop = cropById(cropId) ?: return
        if (unlockCard == null) {
            unlockCard = crop
        } else {
            unlockQueue.addLast(crop.id)
        }
    }

    /** 关闭当前解锁卡；队列里还有则继续弹下一张。 */
    fun dismissUnlockCard() {
        unlockCard = unlockQueue.removeFirstOrNull()?.let { cropById(it) }
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
            plot.stage == 0 -> {
                toast(str(R.string.bear_farm_toast_use_plant))
                GameAudio.play(appContext, GameCue.BUMP)
            }
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
        runAction { FarmSimulator.startNewSeason(state, System.currentTimeMillis()) }
        // 新一季只解锁初始四种作物，选中的种子若停在已锁定作物会导致无法种植
        selectedSeedId = "radish"
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
