package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

class PoolTutorialTest {
    private fun newGame(): PoolGame =
        PoolGame().apply {
            resize(420f, 820f)
            raceTo = 1
        }

    private fun runShot(
        game: PoolGame,
        angle: Float,
        power: Float,
    ) {
        game.shoot(angle, power)
        var t = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && t < 30f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
    }

    /** 瞄准白球 → 指定球心的方向，用于直线球。 */
    private fun aimAt(
        game: PoolGame,
        targetNumber: Int,
    ): Float {
        val cue = game.cueBall()!!
        val target = game.balls.first { it.number == targetNumber }
        return atan2(target.y - cue.y, target.x - cue.x)
    }

    @Test
    fun lessons_haveFiveLegalUniqueLayouts() {
        val lessons = PoolTutorial.lessons()
        assertEquals(5, lessons.size)
        assertEquals((1..5).toList(), lessons.map { it.id })
        lessons.forEach { lesson ->
            assertTrue("lesson ${lesson.id} layout invalid", PoolTutorial.isLayoutValid(lesson))
            assertEquals("exactly one cue ball", 1, lesson.setup.count { it.kind == PoolBallKind.CUE })
            assertTrue("lesson ${lesson.id} has targets", lesson.targetCount >= 1)
        }
    }

    @Test
    fun lessons_coverAllGoals() {
        val goals = PoolTutorial.lessons().map { it.goal }.toSet()
        assertEquals(Goal.entries.toSet(), goals)
    }

    @Test
    fun potOneLesson_isReachableByPhysics() {
        val game = newGame()
        val lesson = PoolTutorial.lessons().first { it.goal == Goal.POT_ONE }
        game.startLesson(lesson)

        val target = lesson.targetNumber!!
        runShot(game, aimAt(game, target), 0.75f)

        assertTrue("POT_ONE lesson should pass", game.lessonPassed)
        assertEquals(PoolGame.Phase.MATCH_OVER, game.phase)
        assertEquals(PoolSide.PLAYER, game.frameWinner)
    }

    @Test
    fun clearLesson_isReachableByPhysics() {
        val game = newGame()
        val lesson = PoolTutorial.lessons().first { it.goal == Goal.CLEAR }
        game.startLesson(lesson)

        val target = lesson.targetNumber!!
        runShot(game, aimAt(game, target), 0.75f)

        assertTrue("CLEAR lesson should pass", game.lessonPassed)
        assertEquals(0, PoolRules.remaining(game.balls).total)
    }

    @Test
    fun drawBackLesson_drawSpinPullsCueBack() {
        val game = newGame()
        val lesson = PoolTutorial.lessons().first { it.goal == Goal.DRAW_BACK }
        game.startLesson(lesson)
        val cue = game.cueBall()!!
        val startY = cue.y
        val target = lesson.targetNumber!!
        game.shoot(aimAt(game, target), 0.7f, PoolGame.Spin(vertical = -1f))
        var t = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && t < 30f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
        assertTrue("cue should draw back: $startY -> ${cue.y}", cue.y < startY - cue.radius)
    }

    @Test
    fun potAnyLesson_isReachableByPhysics() {
        val game = newGame()
        val lesson = PoolTutorial.lessons().first { it.goal == Goal.POT_ANY }
        game.startLesson(lesson)

        val target = lesson.targetNumber!!
        runShot(game, aimAt(game, target), 0.7f)

        assertTrue("POT_ANY lesson should pass", game.lessonPassed)
    }

    // ---------------------------------------------------------------------
    // 精瞄微调
    // ---------------------------------------------------------------------

    @Test
    fun nudgeAim_changesAngleAndStaysNormalized() {
        val game = newGame()
        game.startLesson(PoolTutorial.lessons().first())

        val before = game.aimAngle
        game.nudgeAim(0.05f)
        assertTrue("nudge should change the aim angle", abs(before - game.aimAngle) > 1e-6f)

        // 大量同向微调后仍落在 (-π, π]
        repeat(2000) { game.nudgeAim(1f) }
        assertTrue("angle <= π", game.aimAngle <= PI.toFloat() + 1e-4f)
        assertTrue("angle > -π", game.aimAngle > -PI.toFloat() - 1e-4f)
    }

    @Test
    fun nudgeAim_ignoredWhileShooting() {
        val game = newGame()
        game.startLesson(PoolTutorial.lessons().first())
        game.shoot((Math.PI / 2).toFloat(), 0.9f)
        assertEquals(PoolGame.Phase.SHOOTING, game.phase)

        val before = game.aimAngle
        game.nudgeAim(0.2f)
        assertEquals("nudge must be ignored outside aiming", before, game.aimAngle, 0f)
    }

    // ---------------------------------------------------------------------
    // 状态复位 / 击球历史
    // ---------------------------------------------------------------------

    @Test
    fun startLesson_resetsPassedStateAndHistory() {
        val game = newGame()
        val lesson = PoolTutorial.lessons().first { it.goal == Goal.POT_ANY }
        game.startLesson(lesson)
        runShot(game, aimAt(game, lesson.targetNumber!!), 0.7f)
        assertTrue("a shot should be recorded", game.shotHistory.isNotEmpty())

        // 重开同一节：通过态与历史都应复位
        game.startLesson(lesson)
        assertFalse(game.lessonPassed)
        assertTrue(game.shotHistory.isEmpty())
        assertEquals(PoolGame.Phase.AIMING, game.phase)
        assertEquals(0, game.shots)
    }

    @Test
    fun shotHistory_recordsTurnPotsAndFoul() {
        val game = newGame()
        game.mode = PoolMode.PRACTICE
        game.startMatch(PoolSide.PLAYER, seed = 31L)

        runShot(game, (Math.PI / 2).toFloat(), 0.85f)

        assertTrue(game.shotHistory.isNotEmpty())
        val shot = game.shotHistory.first()
        assertEquals(1, shot.index)
        assertEquals(PoolSide.PLAYER, shot.turn)
        assertTrue("practice never counts a foul", !shot.foul)
    }

    @Test
    fun startMatch_clearsLessonState() {
        val game = newGame()
        game.startLesson(PoolTutorial.lessons().first())
        game.startMatch(PoolSide.PLAYER, seed = 7L)
        assertNull(game.lesson)
        assertFalse(game.lessonPassed)
        assertEquals(PoolMode.EIGHT_BALL, game.match.mode)
    }

    @Test
    fun returnToReady_clearsLesson() {
        val game = newGame()
        game.startLesson(PoolTutorial.lessons().first())
        game.returnToReady()
        assertNull(game.lesson)
        assertFalse(game.lessonPassed)
        assertEquals(PoolGame.Phase.READY, game.phase)
    }
}
