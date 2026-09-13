package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HockeyAiTest {
    private fun table() = HockeyLogic.layoutTable(400f, 800f, 20f, 40f)

    /** 模拟 AI 若干帧，返回是否出现「AI 乌龙（球进自家门 = 玩家得分）」。 */
    private fun simulate(
        t: HockeyLogic.Table,
        puck: Body,
        ai: Body,
        params: HockeyLogic.DifficultyParams,
        frames: Int = 900,
        dt: Float = 1f / 120f,
    ): Boolean {
        var cachedX = ai.x
        var cachedY = ai.y
        var ownGoal = false
        repeat(frames) {
            val (tx, ty, refreshed) =
                HockeyLogic.updateAiTarget(
                    table = t,
                    puck = puck,
                    params = params,
                    reactionAge = 1f,
                    cachedTargetX = cachedX,
                    cachedTargetY = cachedY,
                    paddleR = ai.r,
                    ai = ai,
                )
            if (refreshed) {
                cachedX = tx
                cachedY = ty
            }
            HockeyLogic.stepAiVelocity(ai, cachedX, cachedY, t, params, dt)
            ai.x += ai.vx * dt
            ai.y += ai.vy * dt
            val goalLineHug = HockeyLogic.aiGoalLineClearanceActive(t, puck, ai.r)
            val (ax, ay) =
                HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r, aiGoalLineHug = goalLineHug)
            ai.x = ax
            ai.y = ay
            val r = HockeyLogic.integratePuck(puck, t, dt)
            if (r.scored == Scorer.PLAYER) ownGoal = true
            HockeyLogic.collidePaddle(puck, ai, ai.vx, ai.vy, t)
        }
        return ownGoal
    }

    @Test
    fun aiBelowPuckNearOwnGoal_neverScoresOwnGoal() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        // 球停在自家球门正前方（球门区内），AI 挡板却在球的下方
        val puck = Body(t.midX, t.top + t.height * 0.10f, 0f, 0f, t.puckR)
        val ai = Body(t.midX, t.top + t.height * 0.22f, 0f, 0f, t.paddleR)
        assertFalse("AI must not knock the puck into its own goal", simulate(t, puck, ai, params))
    }

    @Test
    fun aiBelowPuckNearOwnGoal_clearsPuckAwayFromGoal() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val puck = Body(t.midX, t.top + t.height * 0.12f, 0f, 0f, t.puckR)
        val ai = Body(t.midX, t.top + t.height * 0.24f, 0f, 0f, t.paddleR)
        simulate(t, puck, ai, params, frames = 600)
        assertTrue("puck should be cleared out of the goal mouth: y=${puck.y}", puck.y > t.top + t.height * 0.16f)
    }

    @Test
    fun aiAbovePuck_drivesPuckTowardPlayerGoal() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val puck = Body(t.midX, t.midY - 40f, 0f, 0f, t.puckR)
        val ai = Body(t.midX, puck.y - (t.paddleR + t.puckR) - 4f, 0f, 0f, t.paddleR)
        val (_, ty, _) =
            HockeyLogic.updateAiTarget(
                table = t,
                puck = puck,
                params = params,
                reactionAge = 1f,
                cachedTargetX = ai.x,
                cachedTargetY = ai.y,
                paddleR = ai.r,
                ai = ai,
            )
        assertTrue("AI should drive down through the puck, targetY=$ty", ty > puck.y - t.puckR)
        simulate(t, puck, ai, params, frames = 300)
        assertTrue("puck should move toward player half: vy=${puck.vy} y=${puck.y}", puck.y > t.midY - 40f)
    }

    /** 模拟至第一次进球（或跑满时长），返回进球方；用于「不得乌龙」回归。 */
    private fun simulateUntilScore(
        t: HockeyLogic.Table,
        puck: Body,
        ai: Body,
        params: HockeyLogic.DifficultyParams,
        frames: Int = 720,
        dt: Float = 1f / 120f,
    ): Scorer? {
        var cachedX = ai.x
        var cachedY = ai.y
        repeat(frames) {
            val (tx, ty, refreshed) =
                HockeyLogic.updateAiTarget(
                    table = t,
                    puck = puck,
                    params = params,
                    reactionAge = 1f,
                    cachedTargetX = cachedX,
                    cachedTargetY = cachedY,
                    paddleR = ai.r,
                    ai = ai,
                )
            if (refreshed) {
                cachedX = tx
                cachedY = ty
            }
            HockeyLogic.stepAiVelocity(ai, cachedX, cachedY, t, params, dt)
            ai.x += ai.vx * dt
            ai.y += ai.vy * dt
            val goalLineHug = HockeyLogic.aiGoalLineClearanceActive(t, puck, ai.r)
            val (ax, ay) =
                HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r, aiGoalLineHug = goalLineHug)
            ai.x = ax
            ai.y = ay
            val r = HockeyLogic.integratePuck(puck, t, dt)
            HockeyLogic.collidePaddle(puck, ai, ai.vx, ai.vy, t)
            r.scored?.let { return it }
        }
        return null
    }

    /**
     * 反例回归：球在门柱外朝上飞（vy=-120），AI 从球下方 / 侧后方赶来时，
     * 旧逻辑直接贴射门线落点，接触法线带横向分量会把原本会弹上墙的球蹭进自家门。
     * 修复后 AI 先侧移绕到球上方：模拟 6s（到首次进球为止）不得出现 Scorer.PLAYER 乌龙。
     */
    @Test
    fun defense_fromBelowOutsidePost_neverOwnGoal() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val postX = t.midX + t.goalWidth * 0.5f
        val puckX = postX + t.puckR * 3f
        // 不同 AI 起点 / 初速：均在球下方或同一水平带，且横向在门柱内侧
        val starts =
            listOf(
                floatArrayOf(puckX + 40f, t.top + t.height * 0.30f, 0f, 0f),
                floatArrayOf(puckX - t.puckR * 2f, t.top + t.height * 0.25f, -30f, -80f),
                floatArrayOf(t.midX, t.top + t.height * 0.35f, 60f, 0f),
                floatArrayOf(t.midX + t.goalWidth * 0.25f, t.top + t.height * 0.18f, 0f, -120f),
                floatArrayOf(t.midX, t.top + t.height * 0.12f, 0f, -60f),
            )
        starts.forEachIndexed { i, s ->
            val puck = Body(puckX, t.top + t.height * 0.10f, 0f, -120f, t.puckR)
            val ai = Body(s[0], s[1], s[2], s[3], t.paddleR)
            val scored = simulateUntilScore(t, puck, ai, params, frames = 720)
            assertFalse(
                "case $i: AI 不得把门柱外的来球蹭进自家门",
                scored == Scorer.PLAYER,
            )
            // 防守完成：球未越过自家门线（未进自家门，可被解围或反击进对侧）
            assertTrue("case $i: 球进了自家门 (y=${puck.y})", puck.y > t.top + t.puckR - 1f)
        }
    }

    private class AiRunResult(
        val ownGoal: Boolean,
        val knockIn: Boolean,
        val aiTouches: Int,
        val scorer: Scorer?,
    )

    /**
     * 运行 AI 与球的完整循环，记录乌龙、AI 触球次数与首次进球方。
     *
     * [AiRunResult.knockIn] 是「AI 触球即自家丢分」的精确判定：AI 在自家门洞、门线附近
     * 触球后把球往门里顶（接触给球增加了朝自家门的动量）。正常比赛里 AI 出击/防守被
     * 反击破门不算——只查 AI 主动把球顶向自家门这种乌龙。
     */
    private fun runAi(
        t: HockeyLogic.Table,
        puck: Body,
        ai: Body,
        params: HockeyLogic.DifficultyParams,
        frames: Int = 900,
        dt: Float = 1f / 120f,
    ): AiRunResult {
        var cachedX = ai.x
        var cachedY = ai.y
        var ownGoal = false
        var knockIn = false
        var touches = 0
        var scorer: Scorer? = null
        for (i in 0 until frames) {
            val (tx, ty, refreshed) =
                HockeyLogic.updateAiTarget(
                    table = t,
                    puck = puck,
                    params = params,
                    reactionAge = 1f,
                    cachedTargetX = cachedX,
                    cachedTargetY = cachedY,
                    paddleR = ai.r,
                    ai = ai,
                )
            if (refreshed) {
                cachedX = tx
                cachedY = ty
            }
            HockeyLogic.stepAiVelocity(ai, cachedX, cachedY, t, params, dt)
            ai.x += ai.vx * dt
            ai.y += ai.vy * dt
            val goalLineHug = HockeyLogic.aiGoalLineClearanceActive(t, puck, ai.r)
            val (ax, ay) =
                HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r, aiGoalLineHug = goalLineHug)
            ai.x = ax
            ai.y = ay
            val r = HockeyLogic.integratePuck(puck, t, dt)
            if (r.scored != null) {
                scorer = r.scored
                if (r.scored == Scorer.PLAYER) ownGoal = true
                break
            }
            val preVy = puck.vy
            val preX = puck.x
            val preY = puck.y
            val hit = HockeyLogic.collidePaddle(puck, ai, ai.vx, ai.vy, t)
            if (hit) {
                touches++
                val inGoalMouth = kotlin.math.abs(preX - t.midX) <= t.goalWidth * 0.5f + puck.r
                val towardOwnGoal = puck.vy < -0.5f && puck.vy < preVy - 0.5f
                if (preY < t.top + t.paddleR * 2f && inGoalMouth && towardOwnGoal) knockIn = true
            }
        }
        return AiRunResult(ownGoal, knockIn, touches, scorer)
    }

    /**
     * 门线死区回归①：球静止在门内中轴附近，AI 从多组位置逼近，不得出现
     * 「AI 触球把球顶进自家门」（门洞内、门线附近接触给球增加朝自家门的动量）。
     */
    @Test
    fun aiNearOwnGoalLine_staticPuck_neverKnocksInOwnGoal() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val puckYs = listOf(t.top + t.puckR, t.top + t.puckR * 1.6f)
        val puckXs = listOf(t.midX - t.goalWidth * 0.3f, t.midX, t.midX + t.goalWidth * 0.3f)
        val aiXs = listOf(t.midX - t.paddleR * 3f, t.midX, t.midX + t.paddleR * 3f)
        val aiYs =
            listOf(
                t.top + t.puckR + (t.paddleR + t.puckR) * 1.1f,
                t.top + t.paddleR * 3f,
                t.top + t.paddleR * 6f,
            )
        for (py in puckYs) {
            for (px in puckXs) {
                for (ax in aiXs) {
                    for (ay in aiYs) {
                        if (kotlin.math.hypot(ax - px, ay - py) < t.paddleR + t.puckR + 1f) continue
                        val puck = Body(px, py, 0f, 0f, t.puckR)
                        val ai = Body(ax, ay, 0f, 0f, t.paddleR)
                        val result = runAi(t, puck, ai, params, frames = 900)
                        assertFalse(
                            "AI 从 ($ax,$ay) 逼近把球顶向自家门 (puck=($px,$py), ownGoal=${result.ownGoal})",
                            result.knockIn,
                        )
                    }
                }
            }
        }
    }

    /**
     * 门线死区回归②：球在门内以最小速度向玩家半场移动，AI 不得把它顶回自家门
     * （不得出现 Scorer.PLAYER，且球最终仍在门线之外）。
     */
    @Test
    fun aiNearOwnGoalLine_exitingPuck_notKnockedBack() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val minSpeed = t.shortSide * 0.12f
        val aiStarts =
            listOf(
                t.midX to t.top + t.puckR + (t.paddleR + t.puckR) * 1.2f,
                t.midX - t.paddleR * 3f to t.top + t.paddleR * 4f,
                t.midX + t.paddleR * 3f to t.top + t.paddleR * 5f,
                t.midX to t.top + t.paddleR * 7f,
            )
        aiStarts.forEachIndexed { i, (ax, ay) ->
            val puck = Body(t.midX, t.top + t.puckR * 0.5f, 0f, minSpeed, t.puckR)
            val ai = Body(ax, ay, 0f, 0f, t.paddleR)
            val result = runAi(t, puck, ai, params, frames = 900)
            assertFalse("case $i: 外出球被顶回自家门", result.knockIn)
            assertTrue("case $i: 球应离开门线 y=${puck.y}", puck.y > t.top + t.puckR)
        }
    }

    /** 门线死区回归③：球停在门柱附近，AI 必须在 15s 内触球（不得停在球侧不触球）。 */
    @Test
    fun aiNearOwnGoalLine_puckAtPost_touchesWithinFifteenSeconds() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val postX = t.midX + t.goalWidth * 0.5f
        val puck = Body(postX + t.puckR * 0.5f, t.top + t.puckR, 0f, 0f, t.puckR)
        val ai = Body(t.midX, t.top + t.height * 0.25f, 0f, 0f, t.paddleR)
        val result = runAi(t, puck, ai, params, frames = 1800)
        assertTrue("AI 必须在 15s 内触球（防死锁）", result.aiTouches > 0)
    }

    /**
     * 反例回归：贴顶库但远离门洞的慢球不得触发门线解围扫球。
     * 旧条件只看「贴门线 + 非快速外出」，整条顶库都会被 [HockeyLogic.aiGoalLineSweep] 接管，
     * 替代原有的跟球推进逻辑；修复后应仍按原逻辑把目标对准球心。
     */
    @Test
    fun topRailSlowPuckFarFromGoal_usesNormalFollowLogic() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        // 左库附近（远离门洞横向范围）贴顶库的静止球
        val puck = Body(t.left + t.paddleR * 1.5f, t.top + t.puckR, 0f, 0f, t.puckR)
        assertFalse(
            "远离门洞的贴顶库慢球不应触发门线解围",
            HockeyLogic.aiGoalLineClearanceActive(t, puck, t.paddleR),
        )
        // 对照：同在门线、门洞横向范围内的慢球仍须触发（门柱防死锁逻辑保留）
        val mouthPuck = Body(t.midX, t.top + t.puckR, 0f, 0f, t.puckR)
        assertTrue(
            "门洞内的门线慢球仍须解围",
            HockeyLogic.aiGoalLineClearanceActive(t, mouthPuck, t.paddleR),
        )

        // 原跟球逻辑：AI 已绕到球上方，目标对准球心推进；扫球则会把目标推到侧位 standX
        val ai = Body(puck.x, puck.y - (t.paddleR + t.puckR), 0f, 0f, t.paddleR)
        val (tx, _, refreshed) =
            HockeyLogic.updateAiTarget(
                table = t,
                puck = puck,
                params = params,
                reactionAge = 1f,
                cachedTargetX = ai.x,
                cachedTargetY = ai.y,
                paddleR = ai.r,
                ai = ai,
            )
        assertTrue(refreshed)
        assertEquals("应回到跟球推进目标 x", puck.x, tx, 1f)
    }

    @Test
    fun aiDefendsShotLine_withAi() {
        val t = table()
        val params = HockeyLogic.params(Difficulty.MASTER)
        val puck = Body(t.midX - 80f, t.midY - 40f, vx = 200f, vy = -400f, r = t.puckR)
        val ai = Body(t.midX, t.top + t.height * 0.2f, 0f, 0f, t.paddleR)
        val (tx, ty, refreshed) =
            HockeyLogic.updateAiTarget(
                table = t,
                puck = puck,
                params = params,
                reactionAge = 1f,
                cachedTargetX = ai.x,
                cachedTargetY = ai.y,
                paddleR = ai.r,
                ai = ai,
            )
        assertTrue(refreshed)
        val intercept = HockeyLogic.goalLineInterceptX(t, puck, t.top + t.paddleR * 0.85f)
        assertTrue(
            "should move toward intercept: tx=$tx intercept=$intercept",
            kotlin.math.abs(tx - intercept) < kotlin.math.abs(tx - puck.x) + 1f,
        )
        assertTrue("goalie should be above the puck: ty=$ty puckY=${puck.y}", ty <= puck.y)
    }
}
