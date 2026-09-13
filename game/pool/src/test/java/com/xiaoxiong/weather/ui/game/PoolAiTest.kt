package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolAi.Difficulty
import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import com.xiaoxiong.weather.ui.game.PoolBallKind.SOLID
import com.xiaoxiong.weather.ui.game.PoolBallKind.STRIPE
import com.xiaoxiong.weather.ui.game.PoolGroup.SOLIDS
import com.xiaoxiong.weather.ui.game.PoolGroup.STRIPES
import com.xiaoxiong.weather.ui.game.PoolSide.PLAYER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
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
        // 最坏路径：解球 18 杆 + 安全球 5 目标 × 3 偏移，每杆最多 360 步
        assertTrue("master budget should stay bounded: $budget", budget <= 12_000)
        assertTrue(
            "低难度预算不得超过 MASTER",
            PoolAi.worstCaseSimulationSteps(Difficulty.NOVICE) <= budget,
        )
    }
}
