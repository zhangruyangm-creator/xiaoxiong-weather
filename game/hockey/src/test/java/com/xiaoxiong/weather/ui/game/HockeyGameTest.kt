package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HockeyGame] 纯 JVM 单测：阶段流转、决胜分结算与幂等、非法尺寸早退、
 * 局中改设置的延迟生效、发球方向。
 */
class HockeyGameTest {
    private fun startedGame(winScore: Int = 5): HockeyGame =
        HockeyGame().also {
            it.winScore = winScore
            it.resize(400f, 800f, 20f, 40f)
            it.startMatch()
        }

    @Test
    fun flow_serveToPlayingToScoredToMatchOver() {
        val game = startedGame(winScore = 1)
        assertEquals(Phase.SERVE, game.phase)

        // 发球倒数结束进入对局
        game.tick(2.1f)
        assertEquals(Phase.PLAYING, game.phase)

        // 把球放到上球门附近并朝球门推去
        val t = game.table!!
        game.puck.x = t.midX
        game.puck.y = t.top + t.puckR * 0.5f + 1f
        game.puck.vx = 0f
        game.puck.vy = -600f
        game.tick(1f / 60f)
        assertEquals(Phase.SCORED, game.phase)
        assertEquals(1, game.playerScore)

        // 进球横幅结束：达到决胜分 → MATCH_OVER，胜场只加一次
        game.tick(1.2f)
        assertEquals(Phase.MATCH_OVER, game.phase)
        assertEquals(Scorer.PLAYER, game.matchWinner)
        assertEquals(1, game.bestWins)

        // 幂等：MATCH_OVER 后继续 tick 不再叠加胜场
        game.tick(0.5f)
        assertEquals(1, game.bestWins)
    }

    @Test
    fun flow_decidingGoalBannerPlaysBeforeMatchOver() {
        val game = startedGame(winScore = 1)
        game.tick(2.1f)
        assertEquals(Phase.PLAYING, game.phase)

        val t = game.table!!
        game.puck.x = t.midX
        game.puck.y = t.top + t.puckR * 0.5f + 1f
        game.puck.vx = 0f
        game.puck.vy = -600f
        game.tick(1f / 60f)
        assertEquals(Phase.SCORED, game.phase)

        // 界面在横幅期间提前补结算（退出 / 提交榜单）：不抢相位、胜场只加一次
        assertTrue(game.settleDecidedMatch())
        assertEquals(Phase.SCORED, game.phase)
        assertEquals(1, game.bestWins)
        assertTrue(game.settleDecidedMatch())
        assertEquals(1, game.bestWins)

        // 横幅内 seek 不提前 MATCH_OVER
        game.tick(0.5f)
        assertEquals(Phase.SCORED, game.phase)
        // 横幅播完由 tick 推相位，结算不重复
        game.tick(0.7f)
        assertEquals(Phase.MATCH_OVER, game.phase)
        assertEquals(1, game.bestWins)
    }

    @Test
    fun settleDecidedMatch_returnsFalseBeforeDecided() {
        val game = startedGame(winScore = 5)
        game.phase = Phase.PLAYING
        game.playerScore = 1
        assertFalse(game.settleDecidedMatch())
        assertEquals(0, game.bestWins)
        assertEquals(Phase.PLAYING, game.phase)
    }

    @Test
    fun startMatch_locksDifficultyForSettlement() {
        val game = startedGame()
        game.difficulty = Difficulty.EASY
        game.startMatch()
        assertEquals(Difficulty.EASY, game.matchDifficulty)

        // 局中改难度立即影响 AI，但本局记账难度保持开局值
        game.applySettings(Difficulty.MASTER, 5, 5, 1f, true, 1f, 1f)
        assertEquals(Difficulty.MASTER, game.difficulty)
        assertEquals(Difficulty.EASY, game.matchDifficulty)

        game.startMatch()
        assertEquals(Difficulty.MASTER, game.matchDifficulty)
    }

    @Test
    fun resize_illegalSizeEarlyReturn() {
        assertNull(HockeyGame().also { it.resize(300f, 300f, 200f, 200f) }.table)

        val game = HockeyGame()
        game.resize(400f, 800f, 20f, 40f)
        val before = game.table
        game.resize(300f, 300f, 200f, 200f)
        assertSame("非法尺寸应保留上一张球桌", before, game.table)
    }

    @Test
    fun applySettings_midMatchDefersRuleChanges() {
        val game = startedGame()
        assertTrue(game.rulesLocked)
        assertEquals(5, game.winScore)

        game.applySettings(
            difficulty = Difficulty.HARD,
            customLevel = 9,
            winScore = 9,
            paddleScale = 1.3f,
            paddleScaleBoth = false,
            puckScale = 0.75f,
            goalScale = 1.2f,
        )

        // 难度立即生效；获胜分数与尺寸延迟到下一局
        assertEquals(Difficulty.HARD, game.difficulty)
        assertEquals(9, game.customLevel)
        assertEquals(5, game.winScore)
        assertEquals(1f, game.paddleScale, 1e-4f)
        assertEquals(1f, game.puckScale, 1e-4f)
        assertTrue(game.hasPendingRules)
        assertEquals(9, game.displayWinScore())

        // 下一局开始应用暂存值
        game.startMatch()
        assertEquals(9, game.winScore)
        assertEquals(1.3f, game.paddleScale, 1e-4f)
        assertEquals(0.75f, game.puckScale, 1e-4f)
        assertFalse(game.hasPendingRules)
    }

    @Test
    fun applySettings_readyAppliesImmediately() {
        val game = HockeyGame()
        game.resize(400f, 800f, 20f, 40f)
        assertFalse(game.rulesLocked)

        game.applySettings(Difficulty.EASY, 3, 7, 1.3f, false, 0.75f, 1.2f)

        assertEquals(7, game.winScore)
        assertEquals(1.3f, game.paddleScale, 1e-4f)
        assertEquals(0.75f, game.puckScale, 1e-4f)
        assertEquals(1.2f, game.goalScale, 1e-4f)
        assertFalse(game.hasPendingRules)
    }

    /** 场地主题是皮肤：局中切换也立即生效（视觉与摩擦同步），尺寸仍下一局生效。 */
    @Test
    fun applySettings_midMatchAppliesRinkThemeImmediately() {
        val game = startedGame()
        assertTrue(game.rulesLocked)
        assertEquals(RinkTheme.ICE, game.rinkTheme)

        game.applySettings(
            difficulty = Difficulty.NORMAL,
            customLevel = 5,
            winScore = 5,
            paddleScale = 1f,
            paddleScaleBoth = true,
            puckScale = 1f,
            goalScale = 1f,
            rinkTheme = RinkTheme.WOOD,
        )

        assertEquals("局中主题立即生效", RinkTheme.WOOD, game.rinkTheme)
        assertEquals(RinkTheme.WOOD, game.displayRinkTheme())

        game.startMatch()
        assertEquals(RinkTheme.WOOD, game.rinkTheme)
        assertEquals(RinkTheme.WOOD, game.displayRinkTheme())
        assertFalse(game.hasPendingRules)
    }

    @Test
    fun applySettings_readyAppliesRinkThemeImmediately() {
        val game = HockeyGame()
        game.resize(400f, 800f, 20f, 40f)
        assertFalse(game.rulesLocked)

        game.applySettings(Difficulty.NORMAL, 5, 5, 1f, true, 1f, 1f, RinkTheme.BEAR)

        assertEquals(RinkTheme.BEAR, game.rinkTheme)
        assertEquals(RinkTheme.BEAR, game.displayRinkTheme())
        assertFalse(game.hasPendingRules)
    }

    @Test
    fun applySettings_readyClearsStalePending() {
        val game = startedGame()
        // 局中改成 9 分 → 暂存
        game.applySettings(Difficulty.HARD, 5, 9, 1.3f, true, 1f, 1f)
        assertTrue(game.hasPendingRules)

        // 对局结束回到结算后再保存新值：应直接生效且不残留旧暂存
        game.phase = Phase.MATCH_OVER
        game.applySettings(Difficulty.NORMAL, 5, 7, 1f, true, 1f, 1f)
        assertEquals(7, game.winScore)
        assertFalse(game.hasPendingRules)

        game.startMatch()
        assertEquals(7, game.winScore)
        assertEquals(1f, game.paddleScale, 1e-4f)
    }

    @Test
    fun startMatch_resetsTransientState() {
        val game = startedGame()
        game.phase = Phase.PLAYING
        game.time = 42f
        game.playerScore = 3
        game.pendingHapticHit = true
        game.pendingHapticGoal = true

        game.startMatch()

        assertEquals(0f, game.time, 1e-4f)
        assertEquals(0, game.playerScore)
        assertFalse(game.pendingHapticHit)
        assertFalse(game.pendingHapticGoal)
        assertEquals(Phase.SERVE, game.phase)
    }

    /**
     * 角落挤压回归：球被挡板持续顶在墙角时不得被恢复系数泵到速度上限；
     * 多帧窗口的「卡住」判定应触发解脱（或至少速度被墙角限能压住）。
     */
    @Test
    fun cornerPump_paddlePress_doesNotRunawayAndEscapes() {
        val game = startedGame(winScore = 99)
        game.tick(2.1f)
        assertEquals(Phase.PLAYING, game.phase)
        val t = game.table!!
        game.puck.x = t.left + game.puck.r
        game.puck.y = t.bottom - game.puck.r
        game.puck.vx = 0f
        game.puck.vy = 0f
        game.touchX = t.left + game.player.r
        game.touchY = t.bottom - game.player.r
        var maxSpeed = 0f
        repeat(90) {
            game.tick(1f / 60f)
            maxSpeed = maxOf(maxSpeed, HockeyLogic.speed(game.puck))
        }
        assertTrue("墙角挤压应触发解脱：count=${game.cornerEscapeCount}", game.cornerEscapeCount > 0)
        assertTrue("挤压不得把球泵到速度上限：max=$maxSpeed", maxSpeed <= t.shortSide * 1.3f)
    }

    /** 减弱动态效果：只冻结装饰用 time，物理、计时与反馈闪烁仍按 dt 推进。 */
    @Test
    fun reduceMotion_freezesDecorativeTimeOnly() {
        val game = startedGame()
        game.reduceMotion = true

        // 发球倒数照常递减（计时不受影响）
        game.phase = Phase.SERVE
        val serveBefore = game.serveTimer
        game.tick(0.25f)
        assertEquals(serveBefore - 0.25f, game.serveTimer, 1e-4f)
        assertEquals(0f, game.time, 1e-4f)

        // 球的物理照常推进：位置变化、摩擦衰减速度
        game.phase = Phase.PLAYING
        val t = game.table!!
        game.puck.x = t.midX
        game.puck.y = t.midY
        game.puck.vx = 300f
        game.puck.vy = 0f
        val speedBefore = HockeyLogic.speed(game.puck)
        game.tick(0.1f)
        assertTrue("reduceMotion 下球应继续移动: x=${game.puck.x}", game.puck.x > t.midX)
        assertTrue("reduceMotion 下球速应继续按摩擦衰减", HockeyLogic.speed(game.puck) < speedBefore)
        assertEquals(0f, game.time, 1e-4f)

        // 击球 / 进球闪烁照常衰减
        game.hitFlash = 1f
        game.scoreFlash = 1f
        game.tick(0.1f)
        assertEquals(1f - 0.1f * 3.2f, game.hitFlash, 1e-4f)
        assertEquals(1f - 0.1f * 1.1f, game.scoreFlash, 1e-4f)
        assertEquals(0f, game.time, 1e-4f)

        game.reduceMotion = false
        game.tick(0.5f)
        assertEquals(0.5f, game.time, 1e-4f)
    }

    /**
     * 反例回归：沿直库（非角落）慢速推球不得触发墙角解脱。
     * 旧判定只看「持续接触 + 贴库 + 净位移小」，墙中段的正常贴库推球会被误判为卡死。
     */
    @Test
    fun straightWallPress_doesNotTriggerCornerEscape() {
        val game = startedGame(winScore = 99)
        game.tick(2.1f)
        assertEquals(Phase.PLAYING, game.phase)
        val t = game.table!!
        game.puck.x = t.left + game.puck.r
        // 左库中段（距最近角落远大于 2*puckR），排除墙角区域
        game.puck.y = t.midY + t.puckR * 4f
        game.puck.vx = 0f
        game.puck.vy = 0f
        repeat(90) {
            // 玩家挡板持续从右侧轻推贴左库的球
            game.touchX = game.puck.x + (game.player.r + game.puck.r) * 0.8f
            game.touchY = game.puck.y
            game.tick(1f / 60f)
        }
        assertEquals("直库慢推不应触发墙角解脱", 0, game.cornerEscapeCount)
    }

    @Test
    fun serveTowardPlayer_followsLastScorer() {
        val game = HockeyGame()
        assertNull(game.serveTowardPlayer)
        game.lastScorer = Scorer.AI
        assertEquals(true, game.serveTowardPlayer)
        game.lastScorer = Scorer.PLAYER
        assertEquals(false, game.serveTowardPlayer)
    }
}
