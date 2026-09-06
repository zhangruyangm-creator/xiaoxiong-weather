package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Body
import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HockeyLogicTest {
    private fun table() =
        HockeyLogic.layoutTable(
            canvasW = 400f,
            canvasH = 800f,
            padH = 20f,
            padV = 40f,
        )

    @Test
    fun layout_midAndGoalsSensible() {
        val t = table()
        assertTrue(t.width > 0f)
        assertTrue(t.height > 0f)
        assertEquals((t.top + t.bottom) / 2f, t.midY, 0.01f)
        assertTrue(t.goalWidth < t.width)
        assertTrue(t.paddleR > t.puckR)
    }

    @Test
    fun clampPaddle_staysInOwnHalf() {
        val t = table()
        val (px, py) = HockeyLogic.clampPaddle(t, t.midX, t.top, isPlayer = true)
        assertTrue(py >= t.midY)
        val (ax, ay) = HockeyLogic.clampPaddle(t, t.midX, t.bottom, isPlayer = false)
        assertTrue(ay <= t.midY)
        assertTrue(px in t.left..t.right)
        assertTrue(ax in t.left..t.right)
    }

    @Test
    fun clampPaddle_customRadiusShrinksBounds() {
        val t = table()
        val big = t.paddleR * 1.6f
        val (_, yDefault) = HockeyLogic.clampPaddle(t, t.midX, t.bottom, isPlayer = true)
        val (_, yBig) = HockeyLogic.clampPaddle(t, t.midX, t.bottom, isPlayer = true, paddleR = big)
        assertEquals(t.bottom - t.paddleR, yDefault, 0.01f)
        assertEquals(t.bottom - big, yBig, 0.01f)
    }

    @Test
    fun wallBounce_leftReflects() {
        val t = table()
        val puck = Body(t.left + 2f, t.midY, vx = -200f, vy = 50f, r = t.puckR)
        val r = HockeyLogic.integratePuck(puck, t, dt = 1f / 60f, friction = 0f, minSpeedMul = 0f)
        assertTrue(r.hitWall)
        assertTrue(puck.vx > 0f)
        assertTrue(puck.x >= t.left + puck.r - 0.1f)
    }

    @Test
    fun goal_whenPuckEntersTopGoal() {
        val t = table()
        val puck =
            Body(
                x = t.midX,
                y = t.top + t.puckR * 0.5f,
                vx = 0f,
                vy = -400f,
                r = t.puckR,
            )
        val r = HockeyLogic.integratePuck(puck, t, dt = 1f / 30f, friction = 0f, minSpeedMul = 0f)
        assertEquals(Scorer.PLAYER, r.scored)
    }

    @Test
    fun paddleCollision_transfersImpulse() {
        val t = table()
        val paddle = Body(t.midX, t.bottom - t.height * 0.2f, r = t.paddleR)
        val puck =
            Body(
                x = paddle.x,
                y = paddle.y - t.paddleR - t.puckR + 2f,
                vx = 0f,
                vy = 80f,
                r = t.puckR,
            )
        val hit = HockeyLogic.collidePaddle(puck, paddle, paddleVx = 0f, paddleVy = -500f, table = t)
        assertTrue(hit)
        assertTrue(puck.vy < 0f)
    }

    @Test
    fun serveVelocity_nonzero() {
        val t = table()
        val (vx, vy) = HockeyLogic.serveVelocity(t, towardPlayer = true, random = Random(1))
        assertTrue(kotlin.math.hypot(vx, vy) > 10f)
        assertTrue(vy > 0f)
    }

    @Test
    fun matchOver_andWinner() {
        assertFalse(HockeyLogic.matchOver(2, 1, 5))
        assertTrue(HockeyLogic.matchOver(5, 3, 5))
        assertEquals(Scorer.PLAYER, HockeyLogic.winner(5, 3))
        assertEquals(Scorer.AI, HockeyLogic.winner(2, 5))
    }

    @Test
    fun difficultyParams_sixPresetsOrderedByStrength() {
        val ordered =
            listOf(
                Difficulty.BEGINNER,
                Difficulty.EASY,
                Difficulty.NORMAL,
                Difficulty.ADVANCED,
                Difficulty.HARD,
                Difficulty.MASTER,
            ).map { HockeyLogic.params(it) }
        ordered.zipWithNext().forEach { (weaker, stronger) ->
            assertTrue(stronger.aiMaxSpeedMul > weaker.aiMaxSpeedMul)
            assertTrue(stronger.reactionDelay < weaker.reactionDelay)
            assertTrue(stronger.predict > weaker.predict)
        }
    }

    @Test
    fun customLevel_interpolatesBetweenBeginnerAndMaster() {
        val min = HockeyLogic.params(Difficulty.CUSTOM, 1)
        val mid = HockeyLogic.params(Difficulty.CUSTOM, 5)
        val max = HockeyLogic.params(Difficulty.CUSTOM, 10)
        assertEquals(HockeyLogic.params(Difficulty.BEGINNER).aiMaxSpeedMul, min.aiMaxSpeedMul, 1e-4f)
        assertEquals(HockeyLogic.params(Difficulty.MASTER).aiMaxSpeedMul, max.aiMaxSpeedMul, 1e-4f)
        assertTrue(mid.aiMaxSpeedMul > min.aiMaxSpeedMul)
        assertTrue(mid.aiMaxSpeedMul < max.aiMaxSpeedMul)
    }

    @Test
    fun aiTarget_refreshesAfterDelay() {
        val t = table()
        val puck = Body(t.midX + 40f, t.top + 80f, vx = -20f, vy = 10f, r = t.puckR)
        val params = HockeyLogic.params(Difficulty.NORMAL)
        val cached = HockeyLogic.updateAiTarget(t, puck, params, reactionAge = 0f, cachedTargetX = 0f, cachedTargetY = 0f)
        assertFalse(cached.third)
        val fresh = HockeyLogic.updateAiTarget(t, puck, params, reactionAge = 1f, cachedTargetX = 0f, cachedTargetY = 0f)
        assertTrue(fresh.third)
        assertNotNull(fresh.first)
    }

    @Test
    fun cornerEscapeKick_pointsAwayFromCorner() {
        val t = table()
        val puck = Body(t.left + t.puckR, t.top + t.puckR, vx = -50f, vy = -30f, r = t.puckR)
        HockeyLogic.cornerEscapeKick(t, puck, Random(7))
        // 速度朝场内：远离左墙与上墙
        assertTrue(puck.vx > 0f)
        assertTrue(puck.vy > 0f)
        // 位置被抬离角落且不越界
        assertTrue(puck.x > t.left + t.puckR)
        assertTrue(puck.y > t.top + t.puckR)
        assertTrue(puck.x <= t.right - t.puckR)
        assertTrue(puck.y <= t.bottom - t.puckR)
    }

    @Test
    fun cornerEscapeKick_clearsPressingPaddle() {
        val t = table()
        // 最坏情况：挡板把球完全顶进角落
        val puck = Body(t.left + t.puckR, t.top + t.puckR, r = t.puckR)
        val paddle = Body(t.left + t.paddleR, t.top + t.paddleR, r = t.paddleR)
        HockeyLogic.cornerEscapeKick(t, puck, Random(3))
        val d = kotlin.math.hypot(puck.x - paddle.x, puck.y - paddle.y)
        assertTrue(d >= t.paddleR + t.puckR)
    }

    @Test
    fun cornerEscapeKick_bottomCornerPointsUp() {
        val t = table()
        val puck = Body(t.right - t.puckR, t.bottom - t.puckR, vx = 30f, vy = 40f, r = t.puckR)
        HockeyLogic.cornerEscapeKick(t, puck, Random(11))
        // 右下角：速度应远离右墙与下墙（朝场内，不会踢进自家球门）
        assertTrue(puck.vx < 0f)
        assertTrue(puck.vy < 0f)
    }
}
