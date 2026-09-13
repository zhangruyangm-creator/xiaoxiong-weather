package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolAi.Difficulty
import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import com.xiaoxiong.weather.ui.game.PoolBallKind.SOLID
import com.xiaoxiong.weather.ui.game.PoolBallKind.STRIPE
import com.xiaoxiong.weather.ui.game.PoolGroup.SOLIDS
import com.xiaoxiong.weather.ui.game.PoolGroup.STRIPES
import com.xiaoxiong.weather.ui.game.PoolSide.PLAYER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class PoolAiTest {
    private val table = PoolTable.standard()

    private fun ball(
        id: Int,
        kind: PoolBallKind,
        x: Float,
        y: Float,
    ) = PoolBall(id, kind, x, y)

    @Test
    fun plan_findsStraightShotToBottomPocket() {
        // 目标球在「右下角袋 → 台心」连线上，白球在其正后方：直线球必进右下角袋
        val corner = table.pockets.first { it.corner && it.x == table.right && it.y == table.bottom }
        val dx = table.centerX - corner.x
        val dy = table.centerY - corner.y
        val len = kotlin.math.hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val target = ball(1, SOLID, corner.x + ux * 0.6f, corner.y + uy * 0.6f)
        val cue = ball(0, CUE, corner.x + ux * 1.1f, corner.y + uy * 1.1f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val plan = PoolAi.plan(listOf(cue, target), table, state, Difficulty.MASTER, Random(42))
        assertNotNull(plan)
        assertEquals(1, plan!!.targetBallId)
        assertEquals(table.pockets.indexOf(corner), plan.pocketIndex)
        val expected = atan2((target.y - cue.y), (target.x - cue.x))
        assertEquals(expected, plan.angle, 0.05f)
    }

    @Test
    fun plan_prefersDirectPotOverSafety() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.35f)
        val target = ball(1, SOLID, table.centerX, table.top + 1.0f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val plan = PoolAi.plan(listOf(cue, target), table, state, Difficulty.MASTER, Random(42))
        assertNotNull(plan)
        assertTrue("should be a real pot attempt, not safety", !plan!!.safety)
        assertEquals(1, plan.targetBallId)
    }

    @Test
    fun plan_respectsPlayerGroup() {
        // 全色球在更远处、花色球在袋口附近：玩家被分到全色，应打全色
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val solid = ball(1, SOLID, table.centerX, table.top + 1.2f)
        val stripe = ball(9, STRIPE, table.centerX + 0.2f, table.bottom - 0.12f)
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val plan = PoolAi.plan(listOf(cue, solid, stripe), table, state, Difficulty.MASTER, Random(7))
        assertNotNull(plan)
        assertEquals(1, plan!!.targetBallId)
    }

    @Test
    fun plan_blocked_cueToBall_returnsSafety() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.3f)
        val target = ball(1, SOLID, table.centerX, table.centerY)
        val balls = listOf(cue, target) + ringAround(target)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val plan = PoolAi.plan(balls, table, state, Difficulty.MASTER, Random(3))
        assertNotNull(plan)
        assertTrue("should fall back to safety", plan!!.safety)
    }

    /** 在 [target] 周围 6 个袋口方向放一圈球，堵死所有幽灵球点。 */
    private fun ringAround(target: PoolBall): List<PoolBall> {
        val d = target.radius * 2.4f
        return listOf(
            ball(2, SOLID, target.x + d, target.y),
            ball(3, SOLID, target.x - d, target.y),
            ball(4, SOLID, target.x, target.y + d),
            ball(5, SOLID, target.x, target.y - d),
            ball(6, SOLID, target.x + d * 0.7f, target.y + d * 0.7f),
            ball(7, SOLID, target.x - d * 0.7f, target.y - d * 0.7f),
        )
    }

    @Test
    fun plan_targetsEightWhenGroupCleared() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val eight = ball(8, PoolBallKind.EIGHT, table.centerX, table.top + 1.0f)
        val remainingStripe = ball(9, STRIPE, table.right - 0.15f, table.top + 0.5f)
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        // 全色已清（台面没有全色），目标应为 8
        val plan = PoolAi.plan(listOf(cue, eight, remainingStripe), table, state, Difficulty.MASTER, Random(11))
        assertNotNull(plan)
        assertEquals(8, plan!!.targetBallId)
    }

    @Test
    fun estimatePower_increasesWithDistanceAndCut() {
        val near = PoolAi.estimatePower(0.1f, 0.1f, 0f)
        val far = PoolAi.estimatePower(1.2f, 1.0f, 0f)
        val cut = PoolAi.estimatePower(0.1f, 0.1f, 1.2f)
        assertTrue(far > near)
        assertTrue(cut > near)
    }

    @Test
    fun placeCueBall_avoidsOverlapAndStaysInBounds() {
        val cue = ball(0, CUE, table.centerX, table.centerY)
        val target = ball(1, SOLID, table.centerX, table.top + 1.0f)
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        val (x, y) = PoolAi.placeCueBall(listOf(cue, target), table, state, Random(5))
        assertTrue(table.contains(x, y, margin = cue.radius))
        assertTrue(kotlin.math.hypot(target.x - x, target.y - y) >= target.radius + cue.radius)
    }

    // ---------------------------------------------------------------------
    // 九球 / 练习 / 5 档
    // ---------------------------------------------------------------------

    @Test
    fun nineBall_planTargetsLowestBall() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val low = ball(1, SOLID, table.centerX, table.top + 1.0f)
        val high = ball(9, STRIPE, table.centerX, table.bottom - 0.3f)
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.NINE_BALL)
        val plan = PoolAi.plan(listOf(cue, low, high), table, state, Difficulty.MASTER, Random(3))
        assertNotNull(plan)
        assertEquals(1, plan!!.targetBallId)
    }

    @Test
    fun nineBall_blockedLowest_returnsSafety() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.3f)
        val low = ball(1, SOLID, table.centerX, table.top + 1.3f)
        val blocker = ball(2, SOLID, table.centerX, table.top + 0.8f)
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.NINE_BALL)
        val plan = PoolAi.plan(listOf(cue, low, blocker), table, state, Difficulty.MASTER, Random(9))
        assertNotNull(plan)
        assertTrue("nine-ball safety expected", plan!!.safety)
    }

    @Test
    fun practice_plan_allowsAnyBall() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val a = ball(1, SOLID, table.centerX, table.top + 1.0f)
        val b = ball(9, STRIPE, table.centerX + 0.2f, table.top + 1.2f)
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.PRACTICE)
        val plan = PoolAi.plan(listOf(cue, a, b), table, state, Difficulty.MASTER, Random(4))
        assertNotNull(plan)
        assertTrue(plan!!.targetBallId != 0)
    }

    @Test
    fun noviceDifficulty_hasNoVerification() {
        assertEquals(0, Difficulty.NOVICE.verifyShots)
        assertTrue(Difficulty.NOVICE.aimErrorDeg > Difficulty.MASTER.aimErrorDeg)
    }

    // ---------------------------------------------------------------------
    // 两杆走位（HARD/MASTER）
    // ---------------------------------------------------------------------

    @Test
    fun positionCandidates_onlyEnabledForHardAndMaster() {
        assertEquals(0, Difficulty.NOVICE.positionCandidates)
        assertEquals(0, Difficulty.EASY.positionCandidates)
        assertEquals(0, Difficulty.NORMAL.positionCandidates)
        assertTrue(Difficulty.HARD.positionCandidates in 3..6)
        assertTrue(Difficulty.MASTER.positionCandidates in 3..6)
        assertTrue(Difficulty.MASTER.positionPlay > Difficulty.HARD.positionPlay)
    }

    @Test
    fun masterPositionPlay_isDeterministicForSameSeed() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val a = ball(1, SOLID, table.centerX - 0.18f, table.top + 1.0f)
        val b = ball(2, SOLID, table.centerX + 0.18f, table.top + 1.1f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val first = PoolAi.plan(listOf(cue, a, b), table, state, Difficulty.MASTER, Random(2024))
        val second = PoolAi.plan(listOf(cue, a, b), table, state, Difficulty.MASTER, Random(2024))
        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun hardBlocked_stillFallsBackToSafety() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.3f)
        val target = ball(1, SOLID, table.centerX, table.centerY)
        val balls = listOf(cue, target) + ringAround(target)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val plan = PoolAi.plan(balls, table, state, Difficulty.HARD, Random(3))
        assertNotNull(plan)
        assertTrue("HARD should keep safety fallback", plan!!.safety)
    }

    @Test
    fun master_neverChoosesCueBallAsTarget() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.4f)
        val a = ball(1, SOLID, table.centerX, table.top + 1.0f)
        val b = ball(9, STRIPE, table.centerX + 0.25f, table.top + 1.2f)
        val state = PoolMatchState(turn = PLAYER, mode = PoolMode.CHALLENGE)
        val plan = PoolAi.plan(listOf(cue, a, b), table, state, Difficulty.MASTER, Random(8))
        assertNotNull(plan)
        assertTrue(plan!!.targetBallId != 0)
    }

    // ---------------------------------------------------------------------
    // 推演速度 / 摆位质量 / 主线程预算
    // ---------------------------------------------------------------------

    @Test
    fun simulationSpeed_matchesRealShotForSamePower() {
        val power = 0.42f
        assertEquals(
            "AI 推演速度必须等于真实出杆的速度映射",
            PoolConfig.shotSpeedMps(power),
            PoolAi.simulationSpeed(power),
            0f,
        )

        val game =
            PoolGame().apply {
                resize(420f, 820f)
                mode = PoolMode.PRACTICE
            }
        game.startMatch(PoolSide.PLAYER, seed = 2L)
        game.shoot((Math.PI / 2).toFloat(), power)
        assertEquals(
            "同 power 的真实出杆速度应与推演一致",
            PoolAi.simulationSpeed(power),
            game.cueBall()!!.speed,
            1e-4f,
        )
    }

    @Test
    fun placementQuality_clearLineBeatsBlockedLine() {
        val cue = ball(0, CUE, table.centerX, table.centerY)
        val target = ball(1, SOLID, 0.635f, 1.1f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val pocket = table.pockets.first { it.corner }
        val dx = target.x - pocket.x
        val dy = target.y - pocket.y
        val l = kotlin.math.hypot(dx, dy)
        val px = target.x + dx / l * (target.radius * 2f + 0.12f)
        val py = target.y + dy / l * (target.radius * 2f + 0.12f)
        val ghostX = target.x + dx / l * (target.radius * 2f)
        val ghostY = target.y + dy / l * (target.radius * 2f)

        val clear = PoolAi.placementQuality(listOf(cue, target), table, state, cue, px, py)
        val blocker = ball(2, SOLID, (px + ghostX) * 0.5f, (py + ghostY) * 0.5f)
        val blocked = PoolAi.placementQuality(listOf(cue, target, blocker), table, state, cue, px, py)
        assertTrue("直球摆位应有正质量: $clear", clear > 0f)
        assertTrue("被挡摆位质量应低于直球: clear=$clear blocked=$blocked", clear > blocked)
    }

    @Test
    fun placeCueBall_qualityIndependentOfCandidateOrder() {
        val cue = ball(0, CUE, table.centerX, table.bottom - 0.25f)
        val a = ball(1, SOLID, 0.42f, 1.0f)
        val b = ball(2, SOLID, 0.85f, 1.6f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val first = PoolAi.placeCueBall(listOf(cue, a, b), table, state, Random(1))
        val second = PoolAi.placeCueBall(listOf(cue, b, a), table, state, Random(1))
        val q1 = PoolAi.placementQuality(listOf(cue, a, b), table, state, cue, first.first, first.second)
        val q2 = PoolAi.placementQuality(listOf(cue, b, a), table, state, cue, second.first, second.second)
        assertTrue("摆位应有可打的直球: q1=$q1 q2=$q2", q1 > 0f)
        assertEquals("交换候选顺序不应选到更差的位置", q1, q2, 1e-6f)
    }

    @Test
    fun masterWorstCaseSimulation_isBounded() {
        val budget = PoolAi.worstCaseSimulationSteps(Difficulty.MASTER)
        // 确定性总预算：所有推演共享，最坏耗时 ≈ budget × 单步耗时（主线程约 20ms）
        assertTrue("master budget should stay bounded: $budget", budget <= 12_000)
        assertTrue(
            "低难度预算不得超过 MASTER",
            PoolAi.worstCaseSimulationSteps(Difficulty.NOVICE) <= budget,
        )
    }

    @Test
    fun simBudget_stageQuotas_areIndependent() {
        // 拆分后的三段配额：verify 耗尽不影响 kick/safety 各自保留的份额
        val budget = PoolAi.SimBudget(maxSteps = 100, verifyQuota = 30, kickQuota = 20)
        var verify = 0
        while (budget.tryConsume(PoolAi.SimStage.VERIFY)) verify++
        assertEquals("verify 只能用自己的配额", 30, verify)
        assertTrue(budget.exhausted(PoolAi.SimStage.VERIFY))
        assertFalse("verify 用尽不应连带封死 kick", budget.exhausted(PoolAi.SimStage.KICK))

        var kick = 0
        while (budget.tryConsume(PoolAi.SimStage.KICK)) kick++
        assertEquals(20, kick)
        assertTrue(budget.exhausted(PoolAi.SimStage.KICK))
        assertFalse(budget.exhausted(PoolAi.SimStage.SAFETY))

        var safety = 0
        while (budget.tryConsume(PoolAi.SimStage.SAFETY)) safety++
        assertEquals(50, safety)
        assertTrue("总预算耗尽", budget.exhausted)
        assertEquals(100, budget.used)
    }

    @Test
    fun simulate_budgetExhausted_marksUnverifiedNotFailed() {
        // 大力直球 2s 内不会静止：必然触发推演上限，用于区分「预算截断」与「固定步数截断」
        val cue = ball(0, CUE, table.centerX, table.top + 0.3f)
        val target = ball(1, SOLID, table.centerX, table.bottom - 0.3f)
        val balls = listOf(cue, target)
        val tiny = PoolAi.SimBudget(10)
        val truncated =
            PoolAi.simulate(
                balls,
                table,
                angle = (Math.PI / 2).toFloat(),
                power = 1f,
                targetId = 1,
                budget = tiny,
                stage = PoolAi.SimStage.VERIFY,
            )
        assertTrue("预算耗尽必须标记为未验证: $truncated", truncated.truncated)
        assertTrue("必须能区分预算截断与固定步数截断", truncated.budgetExhausted)

        val medium = PoolAi.SimBudget(600)
        val byStepCap =
            PoolAi.simulate(
                balls,
                table,
                angle = (Math.PI / 2).toFloat(),
                power = 1f,
                targetId = 1,
                budget = medium,
                stage = PoolAi.SimStage.VERIFY,
            )
        assertTrue("2s 内未静止应标记未跑完", byStepCap.truncated)
        assertFalse("仍有余量时不是预算截断", byStepCap.budgetExhausted)
    }

    @Test
    fun plan_zeroBudget_returnsBestGeometricCandidate() {
        // 预算为 0：验证阶段直接放弃推演，但仍应返回最佳几何候选而不是 null
        val rack = PoolRack.rack(table, PoolRack.standardOrder(5L)).toMutableList()
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val budget = PoolAi.SimBudget(maxSteps = 0)
        val plan = PoolAi.plan(rack, table, state, Difficulty.MASTER, Random(2024), budget)
        assertNotNull("预算耗尽不应导致 plan 为空", plan)
        assertEquals("不能再消耗任何推演步数", 0, budget.used)
    }

    @Test
    fun plan_blockedWithZeroBudget_stillReturnsSafetyShot() {
        val cue = ball(0, CUE, table.centerX, table.top + 0.3f)
        val target = ball(1, SOLID, table.centerX, table.centerY)
        val balls = listOf(cue, target) + ringAround(target)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val budget = PoolAi.SimBudget(maxSteps = 0)
        val plan = PoolAi.plan(balls, table, state, Difficulty.MASTER, Random(3), budget)
        assertNotNull(plan)
        assertTrue("无预算时安全球仍应可用", plan!!.safety)
        assertEquals(0, budget.used)
    }

    @Test
    fun kickPlan_zeroBudget_returnsNull() {
        val cue = ball(0, CUE, table.centerX, table.centerY)
        val target = ball(1, SOLID, table.centerX - 0.25f, table.top + 0.5f)
        val state = PoolMatchState(openTable = true, turn = PLAYER)
        val plan = PoolAi.kickPlan(listOf(cue, target), table, state, cue, Random(7), PoolAi.SimBudget(0))
        assertNull("解球配额为 0 时不应伪造方案", plan)
    }

    // ---------------------------------------------------------------------
    // 解球候选（首目标被挡、次目标可解）
    // ---------------------------------------------------------------------

    @Test
    fun kickCandidates_coverMultipleTargetsWithinShotLimit() {
        val cue = ball(0, CUE, table.centerX, table.centerY)
        val a = ball(1, SOLID, table.centerX - 0.25f, table.top + 0.5f)
        val b = ball(2, SOLID, table.centerX + 0.25f, table.bottom - 0.5f)
        val kicks = PoolAi.kickCandidates(table, listOf(a, b), cue)
        assertTrue("kick candidates should exist", kicks.isNotEmpty())
        assertTrue("只应对 6 段主库生成候选（每目标最多 1 个/库）: ${kicks.size}", kicks.size <= 12)
        val covered = kicks.take(18).map { it.second.id }.toSet()
        assertTrue("first 18 kick candidates should cover both targets: $covered", covered.containsAll(setOf(1, 2)))
    }

    @Test
    fun kickPlan_firstTargetUnreachable_findsSecondTarget() {
        // 目标 1 被花色球围死；目标 2 可用左侧库一库踢进顶右角袋
        val cue = ball(0, CUE, 0.305f, 1.24f)
        val boxed = ball(1, SOLID, table.centerX, table.centerY)
        val solvable = ball(2, SOLID, 0.254f, 0.8f)
        val others = ArrayList<PoolBall>()
        val ringR = boxed.radius * 2f
        for (i in 0 until 6) {
            val ang = i * (Math.PI.toFloat() / 3f)
            others += ball(9 + i, STRIPE, boxed.x + cos(ang) * ringR, boxed.y + sin(ang) * ringR)
        }
        val balls = listOf(cue, boxed, solvable) + others
        val state = PoolMatchState(openTable = false, playerGroup = SOLIDS, aiGroup = STRIPES, turn = PLAYER)
        // 预算放宽以隔离「候选生成顺序」本身：首目标的解球全部失败后仍应轮到次目标
        val plan = PoolAi.kickPlan(balls, table, state, cue, Random(7), PoolAi.SimBudget(6000))
        assertNotNull(plan)
        assertEquals("被围死的首目标不应被选中", 2, plan!!.targetBallId)
    }

    // ---------------------------------------------------------------------
    // 自由球摆位合法性（AI 与玩家共用同一判定）
    // ---------------------------------------------------------------------

    @Test
    fun isLegalCuePlacement_rejectsPocketCaptureOverlapAndOutOfBounds() {
        val cue = ball(0, CUE, table.centerX, table.centerY)
        val target = ball(1, SOLID, table.centerX + 0.2f, table.centerY)
        val balls = listOf(cue, target)
        assertTrue(
            "台内空位应合法",
            PoolPhysics.isLegalCuePlacement(balls, table, cue, table.centerX, table.centerY - 0.3f),
        )
        assertFalse(
            "出界应拒绝",
            PoolPhysics.isLegalCuePlacement(balls, table, cue, table.right + 0.01f, table.centerY),
        )
        assertFalse(
            "与目标球重叠应拒绝",
            PoolPhysics.isLegalCuePlacement(balls, table, cue, target.x, target.y),
        )
        for (p in table.pockets) {
            assertFalse(
                "袋口捕获圈内不可摆球",
                PoolPhysics.isLegalCuePlacement(balls, table, cue, p.x, p.y),
            )
            val r = PoolPhysics.pocketCaptureRadius(p, cue.radius)
            val dx = table.centerX - p.x
            val dy = table.centerY - p.y
            val l = hypot(dx, dy).coerceAtLeast(1e-6f)
            val px = p.x + dx / l * r * 0.95f
            val py = p.y + dy / l * r * 0.95f
            assertFalse(
                "捕获圈边缘应拒绝 ($px,$py)",
                PoolPhysics.isLegalCuePlacement(balls, table, cue, px, py),
            )
        }
    }

    @Test
    fun placeCueBall_neverReturnsIllegalSpot() {
        // 目标球贴着每个袋口：AI 候选摆位必须过滤袋口捕获圈，非法时退回合法扫描点
        for (pocket in table.pockets) {
            val dx = table.centerX - pocket.x
            val dy = table.centerY - pocket.y
            val l = hypot(dx, dy)
            val target = ball(1, SOLID, pocket.x + dx / l * 0.1f, pocket.y + dy / l * 0.1f)
            val cue = ball(0, CUE, table.centerX, table.centerY)
            val balls = listOf(cue, target)
            val state = PoolMatchState(openTable = true, turn = PLAYER)
            for (seed in 0L..7L) {
                val (x, y) = PoolAi.placeCueBall(balls, table, state, Random(seed))
                assertTrue(
                    "pocket=${table.pockets.indexOf(pocket)} seed=$seed 非法摆位 ($x,$y)",
                    PoolPhysics.isLegalCuePlacement(balls, table, cue, x, y),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // 白球落袋防护 / 完美机器人满局回归
    // ---------------------------------------------------------------------

    /**
     * 「唯一能进球的线路必然白球跟袋」：目标球在左上角袋与台心的连线上、
     * 白球在切线位——直接进球线的推演结果必为 pot + scratch，其余线路均超出切角上限。
     * MASTER 不允许主动选这杆：返回的要么是不落袋的安全球，要么是同样不落袋的杆。
     */
    @Test
    fun master_avoidsScratchingPotLine() {
        val pocket = table.pockets.first { it.corner && it.x == table.left && it.y == table.top }
        val cue = ball(0, CUE, 0.4333f, 0.5982f)
        val target = ball(1, SOLID, 0.1789f, 0.3578f)
        val balls = listOf(cue, target)
        val state = PoolMatchState(openTable = true, turn = PLAYER)

        val dx = target.x - pocket.x
        val dy = target.y - pocket.y
        val len = hypot(dx, dy)
        val ghostX = target.x + dx / len * (target.radius * 2f)
        val ghostY = target.y + dy / len * (target.radius * 2f)
        val cut = PoolMath.angleBetween(ghostX - cue.x, ghostY - cue.y, -dx / len, -dy / len)
        val directAngle = atan2(ghostY - cue.y, ghostX - cue.x)
        val directPower =
            PoolAi.estimatePower(
                hypot(ghostX - cue.x, ghostY - cue.y),
                (len - PoolPhysics.pocketCaptureRadius(pocket, target.radius)).coerceAtLeast(0f),
                cut,
            )
        val direct = PoolAi.simulate(balls, table, directAngle, directPower, 1)
        assertTrue("场景前提：唯一进球线路必须先进球", direct.targetPotted)
        assertTrue("场景前提：唯一进球线路必须白球跟袋", direct.cueScratched)

        for (seed in 0L..7L) {
            val plan = PoolAi.plan(balls, table, state, Difficulty.MASTER, Random(seed))
            assertNotNull(plan)
            val res =
                PoolAi.simulate(
                    balls,
                    table,
                    plan!!.angle,
                    plan.power,
                    plan.targetBallId,
                    spinX = plan.spinX,
                    spinY = plan.spinY,
                )
            assertFalse("MASTER seed=$seed 不允许主动选白球落袋的杆: $plan", res.cueScratched)
        }
    }

    /**
     * 完美机器人（玩家侧用 [Difficulty.MASTER] 代打）与游戏 AI 打满 20 杆，
     * 固定 seed 下犯规数应显著低于修复前（修复前同一脚本 20 杆 11 犯、9 次白球落袋）。
     */
    @Test
    fun perfectBot_fixedSeedMatch_keepsFoulsLow() {
        val game =
            PoolGame().apply {
                resize(420f, 820f)
                mode = PoolMode.EIGHT_BALL
                raceTo = 1
                startMatch(PoolSide.PLAYER, seed = 1L)
            }
        var guard = 0
        while (game.phase != PoolGame.Phase.MATCH_OVER && game.shotHistory.size < 20 && guard < 6000) {
            guard++
            when (game.phase) {
                PoolGame.Phase.AIMING -> {
                    if (game.match.turn == PoolSide.PLAYER) {
                        if (game.ballInHand) {
                            val (x, y) = PoolAi.placeCueBall(game.balls, game.table, game.match, Random(guard.toLong()))
                            game.beginPlacing()
                            if (!game.tryPlaceCue(x, y)) {
                                game.tryPlaceCue(game.table.centerX, game.table.headStringY)
                            }
                        }
                        val plan =
                            PoolAi.plan(
                                game.balls,
                                game.table,
                                game.match,
                                Difficulty.MASTER,
                                Random(guard * 31L + 7),
                            )
                        if (plan != null) {
                            game.shoot(plan.angle, plan.power, PoolGame.Spin(plan.spinX, plan.spinY))
                        } else {
                            game.shoot(0f, 0.5f)
                        }
                    } else {
                        game.tick(1f / 60f)
                    }
                }
                else -> game.tick(1f / 60f)
            }
        }
        val shots = game.shotHistory.take(20)
        val fouls = shots.count { it.foul }
        val scratches = shots.count { it.scratch }
        println("perfectBot match: shots=${shots.size} fouls=$fouls scratches=$scratches phase=${game.phase} guard=$guard")
        shots.forEach {
            println("  shot ${it.index} turn=${it.turn} foul=${it.foul} scratch=${it.scratch} potted=${it.pottedNumbers}")
        }
        assertTrue("未打满 20 杆也未结束，guard=$guard", shots.size >= 20 || game.phase == PoolGame.Phase.MATCH_OVER)
        assertTrue("20 杆内犯规应显著下降: fouls=$fouls scratches=$scratches", fouls <= 4)
    }

    // ---------------------------------------------------------------------
    // 难度校准：蒙特卡洛命中率回归（固定场景 + 固定 seed）
    // ---------------------------------------------------------------------

    /** 一个校准场景：白球 + 目标球（目标球在「袋口 → 台心」方向的固定位置）。 */
    private data class AimScenario(
        val balls: List<PoolBall>,
        val cut: Float,
    )

    /** 6 袋 × 4 距离 × 4 切角 × 2 白球距离 的固定场景集（越界样本过滤后约 190 个）。 */
    private fun calibrationScenarios(): List<AimScenario> {
        val out = ArrayList<AimScenario>()
        for (pocket in table.pockets) {
            val dx = table.centerX - pocket.x
            val dy = table.centerY - pocket.y
            val l = hypot(dx, dy)
            val ux = dx / l
            val uy = dy / l
            for (targetDist in listOf(0.3f, 0.45f, 0.6f, 0.75f)) {
                for (cut in listOf(0f, 0.2f, 0.4f, 0.6f)) {
                    for (cueDist in listOf(0.3f, 0.5f)) {
                        val bx = pocket.x + ux * targetDist
                        val by = pocket.y + uy * targetDist
                        val cux = bx + (ux * cos(cut) - uy * sin(cut)) * cueDist
                        val cuy = by + (ux * sin(cut) + uy * cos(cut)) * cueDist
                        if (!table.contains(bx, by, margin = -PoolConfig.BALL_RADIUS)) continue
                        if (!table.contains(cux, cuy, margin = -PoolConfig.BALL_RADIUS)) continue
                        out +=
                            AimScenario(
                                listOf(
                                    ball(0, CUE, cux, cuy),
                                    ball(1, SOLID, bx, by),
                                ),
                                cut,
                            )
                    }
                }
            }
        }
        return out
    }

    /** 按与 [PoolGame] 真实出杆一致的参数（squirt / 高低杆 / 侧旋）把 [plan] 施加到克隆球桌并推演。 */
    private fun executePlan(
        balls: List<PoolBall>,
        plan: PoolAi.ShotPlan,
    ): Boolean {
        val clones =
            balls.map {
                PoolBall(it.id, it.kind, it.x, it.y, it.vx, it.vy, it.wx, it.wy, it.wz, it.radius).also { clone ->
                    clone.qw = it.qw
                    clone.qx = it.qx
                    clone.qy = it.qy
                    clone.qz = it.qz
                    clone.onTable = it.onTable
                    clone.potted = it.potted
                }
            }
        val cue = clones.first { it.kind == CUE }
        val speed = PoolConfig.shotSpeedMps(plan.power)
        val squirt = -plan.spinX * PoolMath.radians(0.85f) * (0.35f + 0.65f * plan.power)
        val dir = plan.angle + squirt
        cue.vx = cos(dir) * speed
        cue.vy = sin(dir) * speed
        val r = cue.radius
        when {
            plan.spinY > 0f -> {
                val k = plan.spinY
                cue.wx = -cue.vy / r * k
                cue.wy = cue.vx / r * k
            }
            plan.spinY < 0f -> {
                val k = -plan.spinY
                cue.wx = cue.vy / r * k
                cue.wy = -cue.vx / r * k
            }
        }
        cue.wz = plan.spinX * (12f * plan.power + 4f)
        PoolPhysics.simulateUntilSettled(clones, table, maxSeconds = 20f)
        return clones.first { it.id == plan.targetBallId }.potted
    }

    @Test
    fun difficultyHitRates_fallInCalibratedBands() {
        val state = PoolMatchState(openTable = true, turn = PLAYER, mode = PoolMode.PRACTICE)
        val scenarios = calibrationScenarios()
        val bands =
            linkedMapOf(
                Difficulty.NOVICE to 0.45f..0.65f,
                Difficulty.EASY to 0.60f..0.75f,
                Difficulty.NORMAL to 0.75f..0.87f,
                Difficulty.HARD to 0.88f..1.0f,
                Difficulty.MASTER to 0.93f..1.0f,
            )
        val measured = LinkedHashMap<Difficulty, Float>()
        for (difficulty in Difficulty.entries) {
            val rng = Random(CALIBRATION_SEED + difficulty.ordinal)
            var hits = 0
            var plans = 0
            for (scenario in scenarios) {
                val plan = PoolAi.plan(scenario.balls, table, state, difficulty, rng) ?: continue
                plans++
                if (executePlan(scenario.balls, plan)) hits++
            }
            measured[difficulty] = hits.toFloat() / plans
        }
        println("PoolAi calibration: " + measured.entries.joinToString { "${it.key}=${it.value}" })
        for ((difficulty, band) in bands) {
            val rate = measured.getValue(difficulty)
            assertTrue(
                "${difficulty.name} hit rate $rate outside $band scenarios=${scenarios.size}",
                rate in band,
            )
        }
    }

    private companion object {
        /** 校准固定 seed：同一场景集下命中率可复现。 */
        const val CALIBRATION_SEED = 20_250_913L
    }
}
