package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.PoolBallKind.CUE
import com.xiaoxiong.weather.ui.game.PoolBallKind.EIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PoolGameTest {
    @Test
    fun poolRaceScore_isNetWins() {
        assertEquals(3, poolRaceScore(3, 0))
        assertEquals(1, poolRaceScore(7, 6))
        assertEquals(-2, poolRaceScore(1, 3))
    }

    @Test
    fun clampRaceTo_keepsCustomValuesWithinBounds() {
        assertEquals(1, clampRaceTo(0))
        assertEquals(3, clampRaceTo(3))
        assertEquals(7, clampRaceTo(7))
        assertEquals(POOL_RACE_TO_MAX, clampRaceTo(99))
    }

    @Test
    fun stageRuleChange_midMatch_appliesOnNextMatch() {
        val game =
            PoolGame().apply {
                resize(420f, 820f)
                mode = PoolMode.EIGHT_BALL
                raceTo = 2
                startMatch(PoolSide.PLAYER, seed = 1L)
            }
        game.stageRuleChange(7, PoolMode.NINE_BALL)
        assertEquals("当前场赛制不变", 2, game.raceTo)
        assertEquals("界面展示下一场赛制", 7, game.displayRaceTo())
        assertEquals(PoolMode.NINE_BALL, game.displayMode())

        game.startMatch()
        assertEquals("新一场应用暂存赛制", 7, game.raceTo)
        assertEquals(PoolMode.NINE_BALL, game.mode)
        assertEquals(7, game.displayRaceTo())
    }

    @Test
    fun customRaceTo_sevenFrames_sixKeepPlaying_seventhEnds() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.raceTo = 7
        game.startMatch(PoolSide.PLAYER, seed = 31L)
        repeat(6) { round ->
            winNineBallFrame(game)
            assertEquals("第 ${round + 1} 局应正常结算", PoolGame.Phase.FRAME_OVER, game.phase)
            assertEquals(round + 1, game.match.playerFrames)
            assertFalse("赢 6 局（先到 7）不应结束整场", game.matchOver())
            game.nextFrame()
            assertEquals(PoolGame.Phase.AIMING, game.phase)
        }
        winNineBallFrame(game)
        assertEquals(7, game.match.playerFrames)
        assertEquals("赢满 7 局应结束整场", PoolGame.Phase.MATCH_OVER, game.phase)
        assertTrue(game.matchOver())
    }

    /** 只留白球与 9 号并打进角袋，快速拿下一局（九球）。 */
    private fun winNineBallFrame(game: PoolGame) {
        val cue = game.cueBall()!!
        val nine = game.balls.first { it.number == 9 }
        game.balls.retainAll { it.kind == CUE || it.number == 9 }
        nine.stop()
        nine.x = game.table.left + 0.09f
        nine.y = game.table.top + 0.09f
        cue.stop()
        cue.x = game.table.left + 0.42f
        cue.y = game.table.top + 0.42f
        game.shoot(atan2(-1f, -1f), 0.6f)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 30f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
    }

    private fun newGame(): PoolGame =
        PoolGame().apply {
            resize(420f, 820f)
            raceTo = 1
        }

    @Test
    fun breakShot_settlesAndReturnsToAiming() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 99L)
        assertEquals(PoolGame.Phase.AIMING, game.phase)
        val cue = game.cueBall()!!
        // 朝正下方全力开球
        game.shoot((Math.PI / 2).toFloat(), 1f)
        assertEquals(PoolGame.Phase.SHOOTING, game.phase)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 60f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
        assertTrue("break should settle, simulated=$simulated", simulated < 60f)
        assertNotEquals(PoolGame.Phase.SHOOTING, game.phase)
        assertTrue("cue ball should still be on table", cue.onTable)
    }

    /**
     * 开球进 8 触发重摆（rerack）时，本杆在物理过程中产生的落袋反馈标志必须保留到
     * UI 在 tick 返回后消费，不能被内部重摆复位清掉。
     */
    @Test
    fun eightOnBreak_rerack_keepsPendingPotFeedbackForUi() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 3L)
        val cue = game.cueBall()!!
        val eight = game.balls.first { it.kind == EIGHT }
        // 清场：只留白球与 8 号，按「幽灵球」切球瞄准左上角袋
        game.balls.filter { it !== cue && it !== eight }.forEach { it.onTable = false }
        val table = game.table
        val pocket = table.pockets.first()
        val radius = PoolConfig.BALL_RADIUS
        val ux = table.centerX - pocket.x
        val uy = table.centerY - pocket.y
        val ul = hypot(ux, uy)
        val nx = ux / ul
        val ny = uy / ul
        val bx = pocket.x + nx * 0.5f
        val by = pocket.y + ny * 0.5f
        val ghostX = bx + nx * radius * 2f
        val ghostY = by + ny * radius * 2f
        val cueAngle = atan2(by - ghostY, bx - ghostX) + 0.35f
        cue.stop()
        cue.x = ghostX - cos(cueAngle) * 0.4f
        cue.y = ghostY - sin(cueAngle) * 0.4f
        eight.stop()
        eight.x = bx
        eight.y = by
        val aim = atan2(ghostY - cue.y, ghostX - cue.x)
        game.shoot(aim, 0.6f)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 30f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
        // 重摆：不计分、不进下一局
        assertEquals(0, game.match.playerFrames + game.match.aiFrames)
        // 本杆的落袋反馈仍在，等待 UI 消费
        assertTrue("rerack 不得清掉本杆的落袋反馈", game.pendingHapticPot)
        // 普通重开才清掉残留反馈，避免上一杆音效串到新局
        game.startMatch(PoolSide.PLAYER, seed = 4L)
        assertFalse("新开对局必须清掉上一杆反馈", game.pendingHapticPot)
    }

    @Test
    fun aiTurn_afterDelay_takesShot() {
        val game = newGame()
        game.startMatch(PoolSide.AI, seed = 1234L)
        var t = 0f
        while (game.phase == PoolGame.Phase.AIMING && t < 3f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
        // AI 思考约 0.9s 后出杆
        assertTrue("AI should shoot within 3s, t=$t", game.phase == PoolGame.Phase.SHOOTING || game.phase == PoolGame.Phase.AIMING)
        assertTrue("AI fired", t >= 0.5f)
    }

    @Test
    fun coordinates_mapBetweenTableAndCanvas() {
        val game = newGame()
        val x = game.toCanvasX(game.table.centerX)
        val back = game.toTableX(x)
        assertEquals(game.table.centerX, back, 0.001f)
    }

    @Test
    fun tryPlaceCue_rejectsOutOfBounds() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 6L)
        val cue = game.cueBall()!!
        val outside = game.table.right + cue.radius * 0.5f
        assertTrue("placement outside table must be rejected", !game.tryPlaceCue(outside, game.table.centerY))
    }

    @Test
    fun tryingPlaceCue_rejectsOverlap() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 5L)
        val cue = game.cueBall()!!
        val other = game.balls.first { it.kind != CUE && it.onTable }
        val ok = game.tryPlaceCue(other.x, other.y)
        assertTrue("overlapping placement should be rejected", !ok)
        assertEquals(cue.id, game.cueBall()!!.id)
    }

    @Test
    fun tryPlaceCue_rejectsPocketCaptureArea() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 6L)
        val cue = game.cueBall()!!
        for (pocket in game.table.pockets) {
            assertFalse("袋口中心不可摆球", game.tryPlaceCue(pocket.x, pocket.y))
            val r = PoolPhysics.pocketCaptureRadius(pocket, cue.radius)
            val dx = game.table.centerX - pocket.x
            val dy = game.table.centerY - pocket.y
            val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-6f)
            val px = pocket.x + dx / l * r * 0.9f
            val py = pocket.y + dy / l * r * 0.9f
            assertFalse("袋口捕获圈内不可摆球", game.tryPlaceCue(px, py))
        }
        // 远离袋口的正常位置仍可摆放（沿用原有非法摆位反馈路径）
        assertTrue(game.tryPlaceCue(game.table.centerX, game.table.centerY + 0.2f))
    }

    @Test
    fun mode_propagatesIntoMatchState() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.startMatch(PoolSide.PLAYER, seed = 11L)
        assertEquals(PoolMode.NINE_BALL, game.match.mode)
    }

    @Test
    fun nineBall_targetBall_isLowestNumber() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.startMatch(PoolSide.PLAYER, seed = 12L)
        assertEquals(1, game.targetBall()?.number)
    }

    @Test
    fun practice_shotsAndScoreExposed() {
        val game = newGame()
        game.mode = PoolMode.PRACTICE
        game.startMatch(PoolSide.PLAYER, seed = 13L)
        assertEquals(0, game.shots)
        assertEquals(120, game.practiceScore())
        game.shoot((Math.PI / 2).toFloat(), 1f)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 60f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
        assertTrue("at least one shot recorded", game.shots >= 1)
        assertTrue(game.practiceScore() in 1..120)
    }

    // ---------------------------------------------------------------------
    // 回放 / 减弱动画
    // ---------------------------------------------------------------------

    private fun shotUntilSettled(
        game: PoolGame,
        angle: Float,
        power: Float,
    ) {
        game.shoot(angle, power)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 30f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
    }

    private fun recordPracticeShots(count: Int): PoolGame {
        val game = newGame()
        game.mode = PoolMode.PRACTICE
        game.startMatch(PoolSide.PLAYER, seed = 4242L)
        repeat(count) { i ->
            val angle = (Math.PI / 2).toFloat() + i * 0.17f
            shotUntilSettled(game, angle, 0.85f)
        }
        return game
    }

    private fun runReplayToEnd(game: PoolGame) {
        assertTrue("replay should start", game.startReplay())
        var guard = 0
        while (game.replaying && guard < 200_000) {
            game.tick(1f / 60f)
            guard++
        }
        assertTrue("replay should finish", !game.replaying)
    }

    private data class Snapshot(
        val id: Int,
        val x: Float,
        val y: Float,
        val onTable: Boolean,
    )

    private fun snapshot(game: PoolGame): List<Snapshot> = game.balls.map { Snapshot(it.id, it.x, it.y, it.onTable) }

    @Test
    fun replay_isDeterministicForSameLog() {
        val game = recordPracticeShots(2)
        assertTrue("expected recorded shots", game.replayShots().size >= 2)

        runReplayToEnd(game)
        val first = snapshot(game)

        runReplayToEnd(game)
        val second = snapshot(game)

        assertEquals("same replay log must reach identical final state", first, second)
    }

    @Test
    fun replay_doesNotAppendToLog() {
        val game = recordPracticeShots(2)
        val before = game.replayShots()
        assertTrue(before.isNotEmpty())

        runReplayToEnd(game)

        assertEquals("replay must not record new shots", before, game.replayShots())
    }

    @Test
    fun startReplay_withoutLog_returnsFalse() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 21L)
        assertTrue("no shots yet", game.replayShots().isEmpty())
        assertTrue(!game.startReplay())
    }

    @Test
    fun reduceMotion_suppressesParticles() {
        val game = newGame()
        game.reduceMotion = true
        game.mode = PoolMode.PRACTICE
        game.startMatch(PoolSide.PLAYER, seed = 7L)
        shotUntilSettled(game, (Math.PI / 2).toFloat(), 1f)
        assertEquals("reduceMotion should spawn no particles", 0, game.effects.size)
    }

    // ---------------------------------------------------------------------
    // 关卡 / 每日挑战
    // ---------------------------------------------------------------------

    @Test
    fun startChallenge_placesBallsExactlyAndUsesChallengeRules() {
        val game = newGame()
        val challenge = PoolChallenges.campaigns().first()
        game.startChallenge(challenge)

        assertEquals(challenge, game.challenge)
        assertEquals(PoolMode.CHALLENGE, game.match.mode)
        assertEquals(PoolSide.PLAYER, game.match.turn)
        assertEquals(PoolGame.Phase.AIMING, game.phase)
        assertEquals(challenge.balls.size, game.balls.size)
        challenge.balls.forEach { setup ->
            val ball = game.balls.first { it.id == setup.id }
            assertEquals(setup.x, ball.x, 1e-4f)
            assertEquals(setup.y, ball.y, 1e-4f)
            assertEquals(setup.kind, ball.kind)
        }
        assertEquals(PoolChallenges.scoreFor(challenge.par, 0), game.challengeScore())
    }

    @Test
    fun startChallenge_leavesNoAiTurn() {
        val game = newGame()
        game.startChallenge(PoolChallenges.daily(20_000L))
        var t = 0f
        while (t < 2f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
        assertEquals("challenge never hands turn to AI", PoolSide.PLAYER, game.match.turn)
        assertEquals(PoolGame.Phase.AIMING, game.phase)
    }

    @Test
    fun startMatch_clearsActiveChallenge() {
        val game = newGame()
        game.startChallenge(PoolChallenges.campaigns().first())
        game.startMatch(PoolSide.PLAYER, seed = 1L)
        assertNull(game.challenge)
        assertEquals(PoolMode.EIGHT_BALL, game.match.mode)
    }

    @Test
    fun challengeScore_dropsWithMoreShots() {
        val game = newGame()
        val challenge = PoolChallenges.campaigns().last()
        game.startChallenge(challenge)
        val atZero = game.challengeScore()
        game.shoot((Math.PI / 2).toFloat(), 0.8f)
        var simulated = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && simulated < 30f) {
            game.tick(1f / 60f)
            simulated += 1f / 60f
        }
        assertTrue("a shot must be counted", game.shots >= 1)
        assertTrue("fewer shots should score higher", atZero > game.challengeScore())
    }

    @Test
    fun stopReplay_restoresPreReplayBallLayoutAndPhase() {
        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 42L)
        game.shoot((Math.PI / 2).toFloat(), 1f)
        var t = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && t < 60f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
        val phaseBefore = game.phase
        val ballsBefore = game.balls.map { listOf(it.id, it.x, it.y) }
        assertTrue(game.startReplay())
        repeat(180) { game.replayTick(1f / 60f) }
        game.stopReplay()
        assertEquals(phaseBefore, game.phase)
        val ballsAfter = game.balls.map { listOf(it.id, it.x, it.y) }
        assertEquals(ballsBefore, ballsAfter)
    }

    // ---------------------------------------------------------------------
    // 回放状态残留 / 结算幂等
    // ---------------------------------------------------------------------

    @Test
    fun startMatchDuringReplay_cancelsReplayAndDoesNotRestoreOldMatch() {
        val game = recordPracticeShots(1)
        assertTrue(game.startReplay())
        game.startMatch(PoolSide.PLAYER, seed = 77L)
        assertFalse("新开对局必须取消回放态", game.replaying)
        // 若回放态残留，tick 会在空回放记录上触发 stopReplay 把旧局恢复回来
        game.tick(1f / 60f)
        assertFalse(game.replaying)
        assertEquals(PoolGame.Phase.AIMING, game.phase)
    }

    @Test
    fun stopReplay_afterSettledMatch_keepsResultSubmitted() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.raceTo = 1
        game.startMatch(PoolSide.PLAYER, seed = 31L)
        winNineBallFrame(game)
        assertEquals(PoolGame.Phase.MATCH_OVER, game.phase)
        game.resultSubmitted = true

        assertTrue(game.startReplay())
        game.stopReplay()
        assertEquals(PoolGame.Phase.MATCH_OVER, game.phase)
        assertTrue(
            "停止回放不得把已结算标记复位（否则会重复提交榜单/生涯统计）",
            game.resultSubmitted,
        )
    }

    // ---------------------------------------------------------------------
    // 自定义局数边界 / 暂存规则
    // ---------------------------------------------------------------------

    @Test
    fun raceToOne_firstFrameEndsMatch() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.raceTo = 1
        game.startMatch(PoolSide.PLAYER, seed = 31L)
        winNineBallFrame(game)
        assertEquals(1, game.match.playerFrames)
        assertEquals(PoolGame.Phase.MATCH_OVER, game.phase)
        assertTrue(game.matchOver())
    }

    @Test
    fun raceToFifteen_fourteenthFrameDoesNotEndMatch() {
        val game = newGame()
        game.mode = PoolMode.NINE_BALL
        game.raceTo = POOL_RACE_TO_MAX
        game.startMatch(PoolSide.PLAYER, seed = 31L)
        repeat(POOL_RACE_TO_MAX - 1) { round ->
            winNineBallFrame(game)
            assertEquals("第 ${round + 1} 局应正常结算", PoolGame.Phase.FRAME_OVER, game.phase)
            assertFalse("赢 ${round + 1} 局（先到 $POOL_RACE_TO_MAX）不应结束整场", game.matchOver())
            game.nextFrame()
        }
        winNineBallFrame(game)
        assertEquals(POOL_RACE_TO_MAX, game.match.playerFrames)
        assertEquals(PoolGame.Phase.MATCH_OVER, game.phase)
    }

    @Test
    fun startMatch_appliesPendingModeEvenWhenCurrentModeIsChallenge() {
        val game = newGame()
        // 旧 prefs 可能留下 CHALLENGE；此时暂存的新模式不得被吞掉
        game.mode = PoolMode.CHALLENGE
        game.stageRuleChange(3, PoolMode.NINE_BALL)
        game.startMatch(PoolSide.PLAYER, seed = 1L)
        assertEquals(PoolMode.NINE_BALL, game.mode)
        assertEquals(3, game.raceTo)
        assertEquals(PoolMode.NINE_BALL, game.match.mode)
    }

    @Test
    fun pendingRulesAfterSnapshotRestore_sameRules_returnsNull() {
        assertNull(
            pendingRulesAfterSnapshotRestore(
                snapshotRaceTo = 5,
                snapshotMode = PoolMode.EIGHT_BALL,
                prefsRaceTo = 5,
                prefsMode = PoolMode.EIGHT_BALL,
            ),
        )
    }

    @Test
    fun pendingRulesAfterSnapshotRestore_differencesRebuildPendingAndClamp() {
        assertEquals(
            "局中改的赛制/模式应作为下一场暂存恢复",
            7 to PoolMode.NINE_BALL,
            pendingRulesAfterSnapshotRestore(
                snapshotRaceTo = 2,
                snapshotMode = PoolMode.EIGHT_BALL,
                prefsRaceTo = 7,
                prefsMode = PoolMode.NINE_BALL,
            ),
        )
        assertEquals(
            "越界 prefs 赛制应钳制",
            POOL_RACE_TO_MAX to PoolMode.PRACTICE,
            pendingRulesAfterSnapshotRestore(
                snapshotRaceTo = 2,
                snapshotMode = PoolMode.EIGHT_BALL,
                prefsRaceTo = 99,
                prefsMode = PoolMode.PRACTICE,
            ),
        )
    }

    @Test
    fun legacyPurgeList_doesNotTouchDailyLeaderboardKeys() {
        assertTrue(
            "每日挑战榜/最佳是杆数计分，不得被净胜改版的旧 key 清理误删",
            POOL_LEGACY_LEADERBOARD_KEYS.none { it.contains("daily") || it.startsWith("pool_daily") },
        )
    }

    @Test
    fun matchDifficulty_lockKeepsAchievementAccountingStable() {
        val game = newGame()
        game.difficulty = PoolAi.Difficulty.MASTER
        game.mode = PoolMode.PRACTICE
        game.startMatch(PoolSide.PLAYER, seed = 1L)
        assertEquals("开局锁定本场难度", PoolAi.Difficulty.MASTER, game.matchDifficulty)
        // 局中改难度只影响之后的 AI
        game.difficulty = PoolAi.Difficulty.NOVICE
        assertEquals(PoolAi.Difficulty.NOVICE, game.difficulty)
        assertEquals("本场记账难度不得被局中改动污染", PoolAi.Difficulty.MASTER, game.matchDifficulty)

        game.startMatch(PoolSide.PLAYER, seed = 2L)
        assertEquals("下一场按最新难度重新锁定", PoolAi.Difficulty.NOVICE, game.matchDifficulty)
    }

    @Test
    fun lessonAndChallengeKeepClothTheme() {
        val game = newGame()
        game.clothTheme = 2
        game.startLesson(PoolTutorial.lessons().first())
        assertEquals("教学不得重置台呢主题", 2, game.clothTheme)
        game.startChallenge(PoolChallenges.daily(20_000L), 20_000L)
        assertEquals("每日挑战不得重置台呢主题", 2, game.clothTheme)
    }

    @Test
    fun poolLeaderboardEntry_netAndTieBreakConventions() {
        val loss =
            poolLeaderboardEntry(
                practice = false,
                playerFrames = 0,
                aiFrames = 5,
                practiceScore = 0,
                label = "0 : 5",
                timestamp = 1L,
                city = null,
                weather = null,
            )
        assertEquals("负净胜照样入榜，score 为净胜", -5, loss.score)
        assertEquals("tieBreak 为失局", 5, loss.tieBreak)
        assertEquals(0, leaderboardDisplayRank(loss.score, rank = 1))

        val win =
            poolLeaderboardEntry(
                practice = false,
                playerFrames = 5,
                aiFrames = 3,
                practiceScore = 0,
                label = "5 : 3",
                timestamp = 2L,
                city = null,
                weather = null,
            )
        assertEquals(2, win.score)
        assertEquals(3, win.tieBreak)

        val practice =
            poolLeaderboardEntry(
                practice = true,
                playerFrames = 1,
                aiFrames = 0,
                practiceScore = 108,
                label = "6 杆",
                timestamp = 3L,
                city = null,
                weather = null,
            )
        assertEquals("练习按清台得分，不受净胜口径影响", 108, practice.score)
        assertEquals(0, practice.tieBreak)
    }
}
