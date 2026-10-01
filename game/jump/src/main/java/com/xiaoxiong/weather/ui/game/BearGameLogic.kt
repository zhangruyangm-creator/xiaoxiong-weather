package com.xiaoxiong.weather.ui.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 玩法难度：影响跑速 / 障碍间距 / 冰墙解锁门槛与得分倍率。
 *
 * [id] 用于 prefs 与排行榜分类键，**必须保持稳定**。
 */
enum class RunDifficulty(
    val id: String,
    /** 跑速倍率（<1 更慢）。 */
    val speedMul: Float,
    /** 障碍间距倍率（>1 更疏、更易）。 */
    val gapMul: Float,
    /** 冰墙解锁分数门槛倍率（>1 更晚出现）。 */
    val iceWallMinScoreMul: Float,
    /** 金币/擦边得分倍率。 */
    val scoreMul: Float,
) {
    CASUAL(
        id = "casual",
        speedMul = 0.85f,
        gapMul = 1.20f,
        iceWallMinScoreMul = 1.40f,
        scoreMul = 0.8f,
    ),
    STANDARD(
        id = "standard",
        speedMul = 1f,
        gapMul = 1f,
        iceWallMinScoreMul = 1f,
        scoreMul = 1f,
    ),
    CHALLENGE(
        id = "challenge",
        speedMul = 1.15f,
        gapMul = 0.85f,
        iceWallMinScoreMul = 0.70f,
        scoreMul = 1.5f,
    ),
    ;

    companion object {
        val DEFAULT: RunDifficulty = STANDARD

        fun fromId(id: String?): RunDifficulty = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 小熊跳跳的纯逻辑层：与 Compose / Android 完全解耦，可被普通 JUnit 单测。
 *
 * 屏幕尺寸缩放 [s]（= 屏高 / 820）在这里作为**显式参数**传入，而不是读取 Compose
 * [androidx.compose.ui.unit.IntSize]；这样同一套规则既能在 [BearGame]（Compose 状态
 * 容器/渲染适配）里跑，也能脱离渲染环境被确定性测试。
 *
 * 注意 [stepBearPhysics] 刻意**逐字复刻**原 [BearGame.tick] 的显式欧拉积分与落地判定，
 * 以保证重构前后帧级行为完全一致。
 */
object BearGameLogic {
    // ── 物理常量（尺寸缩放由调用方传入）────────────────────────

    fun gravity(s: Float): Float = 2500f * s

    fun jumpV(s: Float): Float = -960f * s

    fun jumpV2(s: Float): Float = -830f * s

    fun maxSpeed(s: Float): Float = 950f * s

    // ── 连击 / 擦边得分 ───────────────────────────────────────

    /** 单枚金币基础分。 */
    const val COIN_BASE = 10

    /** 连击奖励级数上限（与战机击杀掉落同档）。 */
    const val MAX_COMBO_BONUS_STEPS = 10

    /** 每一连击级额外加分。 */
    const val COMBO_BONUS_PER_STEP = 5

    /** 擦边基础分。 */
    const val GRAZE_SCORE = 10

    /** 擦边判定：相对小熊尺寸的外扩比例。 */
    const val GRAZE_PAD_FRAC = 0.22f

    // ── 纯计算 ─────────────────────────────────────────────────

    /**
     * 速度曲线：由纯距离分数（不含金币）驱动，而不是按时间线性加速。
     * 开局 340，约 5000 分时达极速 950；前期较快、越到后期增速越缓。
     * [speedMul] 由 [RunDifficulty] 注入。
     */
    fun speedAt(
        distanceScore: Float,
        s: Float,
        speedMul: Float = 1f,
    ): Float {
        val p = (distanceScore / 5000f).coerceIn(0f, 1f)
        return (340f + 610f * sqrt(p)) * s * speedMul
    }

    /**
     * 总分 = 距离分 + 已累计金币/擦边分。
     * [coinScore] 为实际累加值（含连击加成与难度倍率），不再用枚数×10。
     */
    fun score(
        distance: Float,
        coinScore: Int,
        s: Float,
    ): Int = if (s <= 0f) 0 else (distance / (60f * s)).toInt() + coinScore

    /**
     * 吃到第 [combo] 枚连击金币时的基础得分（尚未乘难度倍率）。
     * combo=1 → 10；之后每级 +5，最多 10 级（封顶 60）。
     */
    fun coinGain(combo: Int): Int = COIN_BASE + (combo - 1).coerceIn(0, MAX_COMBO_BONUS_STEPS) * COMBO_BONUS_PER_STEP

    /** 基础分 × 难度倍率，四舍五入且不为负。 */
    fun scaledPoints(
        base: Int,
        scoreMul: Float,
    ): Int = (base * scoreMul).roundToInt().coerceAtLeast(0)

    /** 奔跑循环相位速率（rad/s）：开局从容、随速度渐快，步频封顶，避免高速抽帧。 */
    fun runPhaseRate(
        speed: Float,
        s: Float,
    ): Float = min(speed, 640f * s) * 0.031f / s

    /** 下一障碍间距（逻辑像素，含难度疏密）。 */
    fun nextObstacleGap(
        roll: Float,
        s: Float,
        gapMul: Float = 1f,
    ): Float = (460f + roll.coerceIn(0f, 1f) * 340f) * s * gapMul

    // ── 障碍决策（冰墙几何/门槛）───────────────────────────────

    /**
     * 冰墙高度：单跳（~184·s）够不到、二段跳（~322·s）稳过；
     * 雪天比冻雨略矮，手感更宽松。
     */
    fun iceWallHeight(
        scene: RunScene,
        s: Float,
    ): Float =
        when (scene) {
            RunScene.SNOW -> 195f * s
            else -> 210f * s
        }

    /** 冰墙出现所需的最低分数（场景基准，未乘难度）。雪天 300、冻雨 200。 */
    fun iceWallMinScore(scene: RunScene): Int =
        when (scene) {
            RunScene.SNOW -> 300
            else -> 200
        }

    /** 含难度倍率的冰墙解锁门槛。 */
    fun iceWallMinScore(
        scene: RunScene,
        difficulty: RunDifficulty,
    ): Int = (iceWallMinScore(scene) * difficulty.iceWallMinScoreMul).roundToInt().coerceAtLeast(1)

    /**
     * 擦边：小熊判定盒未命中障碍碰撞盒，但落入外扩 [pad] 的近身带。
     * 每帧可查询；去重由调用方用障碍标记完成。
     */
    fun obstacleNearMiss(
        bearLeft: Float,
        bearTop: Float,
        bearRight: Float,
        bearBottom: Float,
        obLeft: Float,
        obTop: Float,
        obRight: Float,
        obBottom: Float,
        pad: Float,
    ): Boolean {
        if (pad <= 0f) return false
        // 已命中硬碰撞盒则不算擦边
        val hit =
            bearLeft < obRight &&
                bearRight > obLeft &&
                bearTop < obBottom &&
                bearBottom > obTop
        if (hit) return false
        val gLeft = obLeft - pad
        val gTop = obTop - pad
        val gRight = obRight + pad
        val gBottom = obBottom + pad
        return bearLeft < gRight &&
            bearRight > gLeft &&
            bearTop < gBottom &&
            bearBottom > gTop
    }

    // ── 障碍金币弧（冰墙防空陷阱）────────────────────────────

    /** 障碍上方生成金币弧的概率（与旧实现一致）。 */
    const val COIN_ARC_CHANCE = 0.4f

    /**
     * 障碍上方金币弧：返回 5 枚金币的中心 y（从高到低，首尾对称），不生成时返回空数组。
     *
     * 冰墙高 195–210·s、需二段跳才够，旧写法把金币弧生成在 `groundY-bearSize-…`
     * 处，正好落进墙体，让「吃金币」直接变成「撞墙」。冰墙因此不生成金币，
     * 其余障碍保持原有弧线手感。
     *
     * @param roll [0,1) 随机数，仅 `<` [COIN_ARC_CHANCE] 时生成
     * @param lift [0,1) 随机数，决定弧线基准高度
     */
    fun coinArcYs(
        type: ObstacleType,
        roll: Float,
        lift: Float,
        groundY: Float,
        bearSize: Float,
        s: Float,
    ): FloatArray {
        if (type == ObstacleType.ICE_WALL || roll >= COIN_ARC_CHANCE) return FloatArray(0)
        val cy = groundY - bearSize - (30f + lift * 46f) * s
        return FloatArray(5) { idx ->
            val i = idx - 2
            cy - (2 - abs(i)) * 14f * s
        }
    }

    // ── 跳跃手感：土狼时间 + 跳跃缓冲（纯决策，可测）─────────────

    /** 土狼时间（秒）：离地后仍可补第一段跳的宽限。 */
    const val COYOTE_TIME_SEC = 0.09f

    /** 跳跃缓冲（秒）：落地前的点按会被缓存，落地时立即消费。 */
    const val JUMP_BUFFER_SEC = 0.11f

    /** 一次点按希望触发的跳跃段位；NONE 时由调用方写入跳跃缓冲。 */
    enum class JumpIntent { FIRST, SECOND, NONE }

    /**
     * 解析一次点按的跳跃意图（无副作用）。
     * - [grounded] 或离地未超 [COYOTE_TIME_SEC]：可补第一段跳（需 [jumpsLeft] ≥ 2）；
     * - 否则若只剩一次跳跃则触发二段跳；
     * - 都没有则返回 [JumpIntent.NONE]，由调用方写入 [JUMP_BUFFER_SEC] 的缓冲。
     */
    fun resolveJumpIntent(
        grounded: Boolean,
        airTime: Float,
        jumpsLeft: Int,
    ): JumpIntent =
        when {
            (grounded || airTime <= COYOTE_TIME_SEC) && jumpsLeft >= 2 -> JumpIntent.FIRST
            jumpsLeft == 1 -> JumpIntent.SECOND
            else -> JumpIntent.NONE
        }

    // ── 物理推进（核心引擎，见类内 KDoc 的等价性说明）───────────

    data class BearPhysics(
        val bearY: Float,
        val bearVY: Float,
        val jumpsLeft: Int,
        val grounded: Boolean,
        /** 本帧是否从空中落到地面（用于触发挤压/扬尘等表现层副作用）。 */
        val landed: Boolean,
    )

    /**
     * 推进小熊一帧的弹跳物理。等价于原 [BearGame.tick] 中的顺序更新段：
     * `bearVY += gravity·dt` 再 `bearY += bearVY·dt`（显式欧拉），
     * 落地时夹到 [floorY]、清零垂直速度并重置二段跳。
     */
    fun stepBearPhysics(
        bearY: Float,
        bearVY: Float,
        jumpsLeft: Int,
        grounded: Boolean,
        floorY: Float,
        s: Float,
        dt: Float,
    ): BearPhysics {
        val g = gravity(s)
        var ny = bearY
        var nvy = bearVY + g * dt
        ny += nvy * dt
        var njumps = jumpsLeft
        var ngrounded = grounded
        var landed = false
        if (ny >= floorY) {
            landed = !ngrounded
            ny = floorY
            nvy = 0f
            njumps = 2
            ngrounded = true
        } else {
            ngrounded = false
        }
        return BearPhysics(ny, nvy, njumps, ngrounded, landed)
    }
}
