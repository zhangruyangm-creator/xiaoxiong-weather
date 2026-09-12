package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
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
            val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
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
            val (ax, ay) = HockeyLogic.clampPaddle(t, ai.x, ai.y, isPlayer = false, paddleR = ai.r)
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
