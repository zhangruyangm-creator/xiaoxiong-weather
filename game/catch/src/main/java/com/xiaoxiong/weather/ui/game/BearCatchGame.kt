package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.core.content.edit
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty
import kotlin.math.max
import kotlin.math.min

/** 对局快照 KEY：紧凑文本，仅存对局进行中的状态。 */
private const val CATCH_SNAPSHOT_KEY = "catch_snapshot_v1"

/** 快照头最少字段数（旧版无 difficulty 字段，详见 [BearCatchGame.saveSnapshot]）。 */
private const val CATCH_HEADER_FIELDS_LEGACY = 19

/** 快照头当前字段数（旧版 19 字段 + difficulty）。 */
private const val CATCH_HEADER_FIELDS = 20

/**
 * 安全水平夹取：窗口极窄（width < 2·r）时 [coerceIn] 区间非法会抛 IllegalArgumentException，
 * 此时退化为区间中点（= 0.5·width），保证细高窗口不崩且小熊居中。
 */
private fun safeClampX(
    value: Float,
    min: Float,
    max: Float,
): Float = if (min > max) (min + max) * 0.5f else value.coerceIn(min, max)

/**
 * 小熊接雨雪的 Compose 状态容器。物理量在普通字段里（每帧变化不走快照），
 * 只有阶段/最高分/心数/场景用 Compose state 驱动 HUD 重组（对齐 BearGame 写法）。
 *
 * [tick] 把当前帧交给 [BearCatchLogic]（纯引擎），拿到事件后更新分数/心数，
 * 供渲染层播放效果（飘分、红闪、震动）。
 */
class BearCatchGame {
    var phase by mutableStateOf(GamePhase.READY)
    var best by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var hearts by mutableIntStateOf(MAX_HEARTS)

    /** 是否拥有一次性护盾：接到 BUBBLE 后获得，被一次伤害消耗，进入冷却。 */
    var hasShield by mutableStateOf(false)

    /** 护盾冷却剩余时间（秒）；冷却结束后再次可获取护盾。 */
    var shieldCooldownT by mutableFloatStateOf(0f)

    /** 连击：连续接住得分物不中断；断连（漏接得分物）归零。 */
    var combo by mutableIntStateOf(0)

    /** 双倍得分剩余时间（秒）；<=0 时无加成。 */
    var doubleT by mutableFloatStateOf(0f)

    /** 最近一次是否处于风暴爆发波次（供 HUD 闪烁）。 */
    var inBurstWave by mutableStateOf(false)

    /** 当前天气场景；由 Screen 在天气变化时刷新。 */
    var scene by mutableStateOf(BearCatchLogic.CatchScene.SUNNY)

    /** 当前难度档位；由标题界面选择并持久化，快照保存 / 恢复（旧档缺省 STANDARD）。 */
    var difficulty by mutableStateOf(CatchDifficulty.STANDARD)

    var size = IntSize.Zero
    var time = 0f

    /** 小熊中心 x（屏幕坐标）。 */
    var catchX = 0f

    /** 生成累计时钟（跨帧保留，交给引擎继续计）。 */
    var spawnAccum = 0f

    var touchX: Float? = null

    val items = mutableListOf<BearCatchLogic.Item>()
    val particles = mutableListOf<Particle>()

    /** 引擎随机源：进程内复用一个实例，避免每帧 new java.util.Random() 的分配开销。 */
    val rng = java.util.Random()

    /** 接住挤压计时；< 0 表示未触发。 */
    var squashT = -1f

    /** 命中惩罚后的屏幕震动剩余时间。 */
    var shakeT = 0f

    /** 受击后无敌时间，避免同帧多重扣血瞬死。 */
    var invincibleT by mutableFloatStateOf(0f)

    /** 恢复出的快照尺寸：首次 resize 若与之一致则保留恢复状态，避免被整局回准备态。 */
    private var restoredDims: IntSize? = null

    /** 死亡后累计时间（秒），用于死亡淡出；重开/重置归零。 */
    var deadT = 0f
        private set

    /** 主动放弃/结束/改窗口重置后置位：抑制兜底落盘并把陈旧档清掉，避免僵尸续玩。 */
    private var snapshotDiscarded = false

    val s get() = if (size.height > 0) size.height / 820f else 1f
    val catchY get() = size.height * 0.90f
    val catchR get() = size.height * 0.085f

    fun resize(
        newSize: IntSize,
        preserveRunning: Boolean = false,
    ) {
        if (newSize == size || newSize.width <= 0 || newSize.height <= 0) return
        // 恢复快照后首次拿到与存档一致的尺寸：保留恢复出的对局，只重算坐标相关量；
        // 真实旋转导致尺寸变化时 restoredDims 不匹配，仍按原逻辑重置。
        // preserveRunning：暂停中改窗口尺寸（分屏折叠等）不应清掉正在进行的对局，
        // 否则暂停菜单仍显示旧分、继续后却回到标题，数据与 UI 不一致。
        val keepRestored = phase == GamePhase.RUNNING && (newSize == restoredDims || preserveRunning)
        size = newSize
        restoredDims = null
        if (keepRestored) {
            catchX = safeClampX(catchX, catchR, size.width - catchR)
            return
        }
        catchX = safeClampX(size.width / 2f, catchR, size.width - catchR)
        when (phase) {
            // 结算界面改窗口（旋转/分屏）：只更新尺寸，保留本局分数与心数
            GamePhase.OVER -> Unit
            GamePhase.RUNNING -> {
                // 改窗口重开会放弃当前对局：标记快照失效，防止旧档被兜底落盘写回复活
                snapshotDiscarded = true
                phase = GamePhase.READY
                reset()
            }
            else -> {
                snapshotDiscarded = true
                reset()
            }
        }
    }

    fun reset() {
        score = 0
        hearts = MAX_HEARTS
        hasShield = false
        shieldCooldownT = 0f
        combo = 0
        doubleT = 0f
        inBurstWave = false
        spawnAccum = 0f
        time = 0f
        deadT = 0f
        squashT = -1f
        shakeT = 0f
        invincibleT = 0f
        items.clear()
        particles.clear()
        if (size.width > 0) {
            catchX = safeClampX(size.width / 2f, catchR, size.width - catchR)
        }
    }

    fun start() {
        snapshotDiscarded = false
        reset()
        phase = GamePhase.RUNNING
    }

    /**
     * 局中落盘：进程被回收（或窗口重建）后由 [restoreSnapshot] 续玩。
     * 仅对局进行中保存；标题/结算不写（避免在标题界面覆盖仍可续玩的档）。
     *
     * 编码：第 0 组为表头（phase/score/hearts/...），其后每组成一个物品（首字段 1）
     * 或一个粒子（首字段 2）；随机源 [rng] 不落盘，恢复后新 seed 不影响续玩。
     */
    fun saveSnapshot(prefs: SharedPreferences) {
        // 已放弃/改窗口重开：兜底落盘也不得把陈旧档写回复活
        if (snapshotDiscarded) {
            GameSnapshot.clear(prefs, CATCH_SNAPSHOT_KEY)
            return
        }
        // 结算后清档：死亡回调可能因进程被冻结/回收而没来得及执行，
        // ON_STOP/onDispose 的兜底落盘也要保证已结束的对局不能被续上（与 jump/star 一致）。
        if (phase == GamePhase.OVER) {
            clearSnapshot(prefs)
            return
        }
        if (phase != GamePhase.RUNNING) return
        runCatching {
            val groups = mutableListOf<List<Number>>()
            groups +=
                listOf(
                    phase.ordinal,
                    score,
                    hearts,
                    combo,
                    doubleT,
                    if (hasShield) 1 else 0,
                    shieldCooldownT,
                    invincibleT,
                    spawnAccum,
                    time,
                    if (inBurstWave) 1 else 0,
                    shakeT,
                    squashT,
                    scene.ordinal,
                    size.width,
                    size.height,
                    catchX,
                    // 触摸点不落盘（恢复时为 null，避免残留旧坐标让小熊自动漂移）；
                    // 保留 -1 占位以兼容旧版解析
                    -1f,
                    best,
                    difficulty.ordinal,
                )
            items.forEach { item ->
                groups +=
                    listOf(
                        1,
                        item.kind.ordinal,
                        item.x,
                        item.y,
                        item.r,
                        item.vy,
                        item.phase,
                    )
            }
            particles.forEach { p ->
                groups +=
                    listOf(
                        2,
                        p.x,
                        p.y,
                        p.vx,
                        p.vy,
                        p.life,
                        p.maxLife,
                        p.color.red,
                        p.color.green,
                        p.color.blue,
                        p.color.alpha,
                    )
            }
            GameSnapshot.save(prefs, CATCH_SNAPSHOT_KEY, SnapshotCodec.encodeGroups(groups))
        }
    }

    /** 恢复局中快照；无档 / 坏档返回 false（坏档会被清除，当作无档）。 */
    fun restoreSnapshot(prefs: SharedPreferences): Boolean {
        val raw = GameSnapshot.load(prefs, CATCH_SNAPSHOT_KEY) ?: return false
        val groups =
            SnapshotCodec.decodeGroups(raw) ?: run {
                clearSnapshot(prefs)
                return false
            }
        val applied =
            runCatching { applySnapshot(groups) }.getOrElse {
                clearSnapshot(prefs)
                return false
            }
        // 内容不合法（阶段/场景过期、生命为 0、物品类型损坏）：坏档不再每次进入都尝试
        if (!applied) clearSnapshot(prefs)
        return applied
    }

    /** 清档：结算、暂停重开、退出时调用，避免陈旧续玩。 */
    fun clearSnapshot(prefs: SharedPreferences) {
        snapshotDiscarded = true
        GameSnapshot.clear(prefs, CATCH_SNAPSHOT_KEY)
    }

    private fun applySnapshot(groups: List<FloatArray>): Boolean {
        val header = groups.firstOrNull() ?: return false
        // 旧档只有 19 个表头字段：difficulty 缺省沿用当前值（通常来自 prefs，未设置则 STANDARD）
        if (header.size < CATCH_HEADER_FIELDS_LEGACY) return false
        if (GamePhase.entries.getOrNull(header[0].toInt()) != GamePhase.RUNNING) return false
        // 合法对局不可能以 0 心落盘（死亡会先切 OVER 并清档），0 心档直接判坏。
        if (header[2].toInt() <= 0) return false
        val newScene = BearCatchLogic.CatchScene.entries.getOrNull(header[13].toInt()) ?: return false
        // 先完整解析到临时容器，全部合法后再提交，避免坏档恢复出半个局面
        val newItems = ArrayList<BearCatchLogic.Item>()
        val newParticles = ArrayList<Particle>()
        for (i in 1 until groups.size) {
            val g = groups[i]
            if (g.isEmpty()) continue
            // 逐个实体容错：字段不足/类型非法的实体只丢自己，不让整局进度被清档
            when (g[0].toInt()) {
                1 -> {
                    if (g.size < 7) continue
                    val kind = BearCatchLogic.ItemKind.entries.getOrNull(g[1].toInt()) ?: continue
                    newItems +=
                        BearCatchLogic
                            .Item(
                                kind = kind,
                                x = g[2],
                                y = g[3],
                                r = g[4],
                                phase = g[6],
                            ).also { it.vy = g[5] }
                }
                2 -> {
                    if (g.size < 11) continue
                    newParticles +=
                        Particle(
                            x = g[1],
                            y = g[2],
                            vx = g[3],
                            vy = g[4],
                            life = g[5],
                            maxLife = g[6],
                            color = Color(g[7], g[8], g[9], g[10]),
                        )
                }
                else -> continue
            }
        }
        val savedW = header[14].toInt()
        val savedH = header[15].toInt()
        // 恢复可能早于首次 onSizeChanged：用存档尺寸兜底，避免随后的 resize 把对局 reset 掉
        if (size == IntSize.Zero && savedW > 0 && savedH > 0) {
            size = IntSize(savedW, savedH)
        }
        // 记录存档尺寸：随后若 onSizeChanged 与之一致，resize 保留对局而非重置
        if (savedW > 0 && savedH > 0) restoredDims = IntSize(savedW, savedH)
        score = header[1].toInt()
        hearts = header[2].toInt().coerceIn(0, MAX_HEARTS)
        combo = header[3].toInt()
        doubleT = header[4]
        hasShield = header[5] != 0f
        shieldCooldownT = header[6]
        invincibleT = header[7]
        spawnAccum = header[8]
        time = header[9]
        inBurstWave = header[10] != 0f
        shakeT = header[11]
        squashT = header[12]
        scene = newScene
        catchX = safeClampX(header[16], catchR, size.width - catchR)
        // 触摸点不恢复（即使旧档写了坐标也忽略）：否则恢复瞬间小熊会朝旧触点漂移
        touchX = null
        if (header[18].toInt() > best) best = header[18].toInt()
        if (header.size >= CATCH_HEADER_FIELDS) {
            difficulty =
                CatchDifficulty.entries.getOrNull(header[19].toInt()) ?: CatchDifficulty.STANDARD
        }
        items.clear()
        items.addAll(newItems)
        particles.clear()
        particles.addAll(newParticles)
        phase = GamePhase.RUNNING
        snapshotDiscarded = false
        return true
    }

    /** 推进一帧：委托 [BearCatchLogic] 引擎，并消费事件更新状态。 */
    fun tick(
        dt: Float,
        onCaught: (BearCatchLogic.ItemKind) -> Unit,
        onHit: (BearCatchLogic.ItemKind) -> Unit,
        onDeath: (Int) -> Unit,
        /** 护盾吸收伤害时触发（区别于 [onHit] 的真扣血）：供屏幕播放「格挡」表现。 */
        onBlocked: (BearCatchLogic.ItemKind) -> Unit = {},
        /** 跨过 500 / 1000 / 2000 里程碑时回调一次（不阻塞操作）。 */
        onMilestone: (Int) -> Unit = {},
    ) {
        if (size == IntSize.Zero) return
        time += dt

        if (doubleT > 0f) doubleT = max(0f, doubleT - dt)
        if (shieldCooldownT > 0f) shieldCooldownT = max(0f, shieldCooldownT - dt)

        // 粒子任何阶段都继续飞
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.vy += 900f * s * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
        if (squashT >= 0f) {
            squashT += dt
            if (squashT > 0.16f) squashT = -1f
        }
        if (shakeT > 0f) shakeT = max(0f, shakeT - dt)
        if (invincibleT > 0f) invincibleT = max(0f, invincibleT - dt)

        if (phase == GamePhase.READY) return
        if (phase == GamePhase.OVER) {
            // 结算期间累计死亡时间：即便整局 time 很大，淡出也从 0 开始
            deadT += dt
            return
        }

        // 小熊跟随手指水平移动（截距式：取 touchX，无触摸时惯性衰减）
        val tx = touchX
        if (tx != null) {
            val target = safeClampX(tx, catchR, size.width - catchR)
            catchX += (target - catchX) * min(1f, dt * 18f)
        }

        var pendingMilestone: Int? = null

        /** 加分并记录本次跨过的里程碑（一次跨多个取最高）。 */
        fun addScore(gained: Int) {
            val before = score
            score += gained
            BearCatchLogic.milestoneFor(before, score)?.let { pendingMilestone = it }
        }

        val ev =
            BearCatchLogic.stepCatchWorldDefault(
                items = items,
                score = score,
                scene = scene,
                s = s,
                dt = dt,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                catchX = catchX,
                catchY = catchY,
                catchR = catchR,
                spawnAccum = spawnAccum,
                random = rng,
                time = time,
                difficulty = difficulty,
            )
        spawnAccum = ev.spawnAccum
        inBurstWave = ev.inBurstWave

        if (ev.missedGood > 0) combo = 0

        for (kind in ev.caught) {
            when (kind) {
                BearCatchLogic.ItemKind.GIFT -> {
                    combo++
                    // 与普通得分物同口径：双倍期间再吃礼物也翻倍（先算分再续时长）
                    val mult =
                        (if (doubleT > 0f) 2 else 1) *
                            BearCatchLogic.comboScoreMultiplier(combo)
                    addScore(BearCatchLogic.scoreOf(kind) * mult)
                    doubleT = BearCatchLogic.DOUBLE_TIME_SEC
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 12,
                        color = Color(0xFFFF8F00),
                    )
                }
                BearCatchLogic.ItemKind.BUBBLE -> {
                    // 冷却期间拾取不再叠加；只有在没护盾且没冷却时才给一次性护盾
                    if (!hasShield && shieldCooldownT <= 0f) {
                        hasShield = true
                    }
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 10,
                        color = Color(0xFF80DEEA),
                    )
                }
                else -> {
                    combo++
                    val base = BearCatchLogic.scoreOf(kind)
                    val mult =
                        (if (doubleT > 0f) 2 else 1) *
                            BearCatchLogic.comboScoreMultiplier(combo)
                    addScore(base * mult)
                    squashT = 0f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.5f,
                        count = 6,
                        color = burstColorOf(kind),
                    )
                }
            }
            onCaught(kind)
        }
        pendingMilestone?.let(onMilestone)
        // 命中处理：同帧最多只生效一次伤害/护盾消耗，
        // 避免「4 块乌云同帧砸中 → 只扣 1 层护盾/扣 1 心」的吞伤问题。
        // 优先级：闪电 > 冰雹 > 乌云（越致命越靠前；闪电无视护盾直接扣血）。
        var effectiveHit: BearCatchLogic.ItemKind? = null
        var bestPriority = Int.MAX_VALUE
        for (kind in ev.hit) {
            val priority = hitPriority(kind)
            if (priority < bestPriority) {
                bestPriority = priority
                effectiveHit = kind
            }
        }
        if (effectiveHit != null) {
            if (invincibleT <= 0f) {
                // 闪电无视护盾：扣 2 心并打满无敌；其余物品优先用护盾挡一次
                if (effectiveHit == BearCatchLogic.ItemKind.LIGHTNING) {
                    hearts -= BearCatchLogic.damageOf(effectiveHit)
                    combo = 0
                    shakeT = 0.30f
                    invincibleT = BearCatchLogic.HIT_INVINCIBLE_SEC
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 14,
                        color = Color(0xFFFFD600),
                    )
                    onHit(effectiveHit)
                } else if (hasShield) {
                    hasShield = false
                    shieldCooldownT = BearCatchLogic.SHIELD_COOLDOWN_SEC
                    shakeT = 0.12f
                    invincibleT = 0.3f
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 12,
                        color = Color(0xFF80DEEA),
                    )
                    onBlocked(effectiveHit)
                } else {
                    hearts -= BearCatchLogic.damageOf(effectiveHit)
                    combo = 0
                    shakeT = 0.22f
                    invincibleT = BearCatchLogic.HIT_INVINCIBLE_SEC
                    burst(
                        cx = catchX,
                        cy = catchY - catchR * 0.6f,
                        count = 10,
                        color = Color(0xFF9E9E9E),
                    )
                    onHit(effectiveHit)
                }
                if (hearts <= 0) {
                    hearts = 0
                    deadT = 0f
                    phase = GamePhase.OVER
                    onDeath(score)
                }
            } else {
                // 已在无敌帧：本帧所有命中全部忽略；物品已在 stepCatchWorld 中移除
            }
        }
    }

    /** 接住某类掉落物的爆点颜色（纯数据，不触碰 Android 图形 API，保证可单测）。 */
    fun burstColorOf(kind: BearCatchLogic.ItemKind): Color =
        when (kind) {
            BearCatchLogic.ItemKind.COIN -> Color(0xFFFFCA28)
            BearCatchLogic.ItemKind.RAINDROP -> Color(0xFF8FC3F0)
            BearCatchLogic.ItemKind.SNOWFLAKE -> Color(0xFFE8F2FF)
            BearCatchLogic.ItemKind.PETAL -> Color(0xFFF8BBD0)
            BearCatchLogic.ItemKind.CLOUD -> Color(0xFF8A94A0)
            BearCatchLogic.ItemKind.HAIL -> Color(0xFFC4CFDA)
            BearCatchLogic.ItemKind.LIGHTNING -> Color(0xFFFFEB3B)
            BearCatchLogic.ItemKind.GIFT -> Color(0xFFFF8F00)
            BearCatchLogic.ItemKind.BUBBLE -> Color(0xFF80DEEA)
        }

    /** 接住/命中时的爆点粒子（对齐 jump 的 burst 手法）。 */
    fun burst(
        cx: Float,
        cy: Float,
        count: Int,
        color: Color,
    ) {
        val spread = 180f * s
        repeat(count) {
            val a = kotlin.random.Random.nextFloat() * (Math.PI * 2).toFloat()
            val v = (0.4f + kotlin.random.Random.nextFloat() * 0.6f) * spread
            val life = 0.4f + kotlin.random.Random.nextFloat() * 0.35f
            particles +=
                Particle(
                    x = cx,
                    y = cy,
                    vx = kotlin.math.cos(a) * v,
                    vy = kotlin.math.sin(a) * v,
                    life = life,
                    maxLife = life,
                    color = color,
                )
        }
    }

    /** 持久化当前难度档位（标题界面切换时调用）。 */
    fun persistDifficulty(prefs: SharedPreferences) {
        prefs.edit { putString(PREFS_DIFFICULTY_KEY, difficulty.id) }
    }

    /** 刷新并持久化最高分；未刷新返回 false（供结算调用）。 */
    fun recordBestScore(
        prefs: SharedPreferences,
        score: Int,
    ): Boolean {
        if (score <= best) return false
        best = score
        prefs.edit { putInt(PREFS_BEST_SCORE_KEY, score) }
        return true
    }

    companion object {
        const val MAX_HEARTS = 3

        /** 难度档位持久化键。 */
        const val PREFS_DIFFICULTY_KEY = "catch_difficulty"

        /** 最高分持久化键。 */
        const val PREFS_BEST_SCORE_KEY = "catch_best_score"

        /** 旧版最高分键：首次读取时迁移到 [PREFS_BEST_SCORE_KEY]。 */
        const val PREFS_BEST_SCORE_LEGACY_KEY = "best_catch"

        /** 读取最高分；旧键有值而新键没有时迁移，避免升级后丢纪录。 */
        fun loadBestScore(prefs: SharedPreferences): Int {
            val saved = prefs.getInt(PREFS_BEST_SCORE_KEY, -1)
            if (saved >= 0) return saved
            val legacy = prefs.getInt(PREFS_BEST_SCORE_LEGACY_KEY, 0)
            if (legacy > 0) prefs.edit { putInt(PREFS_BEST_SCORE_KEY, legacy) }
            return legacy
        }

        /** 读取难度档位；缺省 / 未知 id 回退 [CatchDifficulty.STANDARD]。 */
        fun loadDifficulty(prefs: SharedPreferences): CatchDifficulty = CatchDifficulty.fromId(prefs.getString(PREFS_DIFFICULTY_KEY, null))
    }
}

/** 惩罚物致命优先级：闪电 > 冰雹 > 乌云；非惩罚物不参与结算。 */
private fun hitPriority(kind: BearCatchLogic.ItemKind): Int =
    when (kind) {
        BearCatchLogic.ItemKind.LIGHTNING -> 0
        BearCatchLogic.ItemKind.HAIL -> 1
        BearCatchLogic.ItemKind.CLOUD -> 2
        else -> Int.MAX_VALUE
    }
