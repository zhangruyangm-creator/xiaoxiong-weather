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
    fun difficultyParams_hardFasterThanEasy() {
        val easy = HockeyLogic.params(Difficulty.EASY)
        val hard = HockeyLogic.params(Difficulty.HARD)
        assertTrue(hard.aiMaxSpeedMul > easy.aiMaxSpeedMul)
        assertTrue(hard.reactionDelay < easy.reactionDelay)
        assertTrue(hard.predict > easy.predict)
    }

    @Test
    fun customLevel_interpolatesBetweenEasyAndHard() {
        val easy = HockeyLogic.params(Difficulty.CUSTOM, 1)
        val mid = HockeyLogic.params(Difficulty.CUSTOM, 5)
        val hard = HockeyLogic.params(Difficulty.CUSTOM, 10)
        assertEquals(HockeyLogic.params(Difficulty.EASY).aiMaxSpeedMul, easy.aiMaxSpeedMul, 1e-4f)
        assertEquals(HockeyLogic.params(Difficulty.HARD).aiMaxSpeedMul, hard.aiMaxSpeedMul, 1e-4f)
        assertTrue(mid.aiMaxSpeedMul > easy.aiMaxSpeedMul)
        assertTrue(mid.aiMaxSpeedMul < hard.aiMaxSpeedMul)
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
}
