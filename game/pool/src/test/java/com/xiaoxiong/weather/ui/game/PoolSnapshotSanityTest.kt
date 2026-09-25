package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PoolSnapshot.isSane] 的守卫测试。
 *
 * 背景：codec 用 `toFloatOrNull()` 解析数字，它接受 "NaN"/"Infinity"，
 * 坏档能把非有限值带进物理循环并永久污染球的位置与速度。
 */
class PoolSnapshotSanityTest {
    private fun ball(
        id: Int = 0,
        x: Float = 100f,
        y: Float = 200f,
        kind: PoolBallKind = if (id == 0) PoolBallKind.CUE else PoolBallKind.SOLID,
    ) = PoolBallSnapshot(
        id = id,
        kind = kind,
        x = x,
        y = y,
        vx = 0f,
        vy = 0f,
        wx = 0f,
        wy = 0f,
        wz = 0f,
        z = 0f,
        vz = 0f,
        curve = 0f,
        qw = 1f,
        qx = 0f,
        qy = 0f,
        qz = 0f,
        onTable = true,
        potted = false,
        pocketIndex = -1,
        pocketAnim = 0f,
    )

    private fun tracking(cueStartY: Float = 0f) =
        PoolShotTrackingSnapshot(
            firstContact = null,
            firstContactNumber = null,
            contactHappened = false,
            railAfter = false,
            potted = emptyList(),
            pottedNumbers = emptyList(),
            lowestBefore = null,
            cuePotted = false,
            cueStartY = cueStartY,
            railedBalls = emptySet(),
        )

    private fun snapshot(
        balls: List<PoolBallSnapshot> = listOf(ball(0), ball(1)),
        aimAngle: Float = 0.3f,
        elevation: Float = 0.1f,
        spinSide: Float = 0.2f,
        spinVertical: Float = -0.1f,
        shotCount: Int = 4,
        cueStartY: Float = 0f,
    ) = PoolSnapshot(
        phase = PoolGame.Phase.AIMING,
        mode = PoolMode.NINE_BALL,
        openTable = false,
        playerGroup = PoolGroup.SOLIDS,
        aiGroup = PoolGroup.STRIPES,
        turn = PoolSide.PLAYER,
        playerFrames = 0,
        aiFrames = 0,
        matchMode = PoolMode.NINE_BALL,
        raceTo = 3,
        best = 0,
        matchDifficulty = null,
        challengeId = null,
        challengePar = -1,
        challengeDate = 0L,
        lessonId = null,
        shotCount = shotCount,
        isBreak = false,
        shooterClearedBefore = false,
        ballInHand = false,
        placingCue = false,
        aimAngle = aimAngle,
        elevation = elevation,
        spinSide = spinSide,
        spinVertical = spinVertical,
        frameSeed = 0L,
        breaker = PoolSide.PLAYER,
        frameWinner = null,
        lastFrameWinner = null,
        breakRunsThisMatch = 0,
        potsThisMatch = 0,
        bestShotPots = 0,
        lastShotPots = 0,
        frameBreaker = PoolSide.PLAYER,
        aiTouchedInFrame = false,
        breakAndRunFrame = false,
        balls = balls,
        tracking = tracking(cueStartY),
        history = emptyList(),
    )

    @Test
    fun saneSnapshot_passes() {
        assertTrue(snapshot().isSane())
    }

    @Test
    fun nanBallPosition_isRejected() {
        assertFalse(snapshot(balls = listOf(ball(0), ball(1, x = Float.NaN))).isSane())
    }

    @Test
    fun infiniteBallVelocity_isRejected() {
        val fast = ball(1).copy(vx = Float.POSITIVE_INFINITY)
        assertFalse(snapshot(balls = listOf(ball(0), fast)).isSane())
    }

    @Test
    fun nanSpin_isRejected() {
        assertFalse(snapshot(spinVertical = Float.NaN).isSane())
    }

    @Test
    fun nanAimAngle_isRejected() {
        assertFalse(snapshot(aimAngle = Float.NaN).isSane())
    }

    @Test
    fun emptyBallList_isRejected() {
        assertFalse(snapshot(balls = emptyList()).isSane())
    }

    @Test
    fun duplicateBallIds_areRejected() {
        assertFalse(snapshot(balls = listOf(ball(3), ball(3))).isSane())
    }

    @Test
    fun absurdBallCount_isRejected() {
        val many = (0..POOL_SNAPSHOT_MAX_BALLS).map { ball(it) }
        assertFalse(snapshot(balls = many).isSane())
    }

    @Test
    fun fullRackSize_isAccepted() {
        val rack = (0 until 15).map { ball(it) }
        assertTrue(snapshot(balls = rack).isSane())
    }

    @Test
    fun negativeShotCount_isRejected() {
        assertFalse(snapshot(shotCount = -1).isSane())
    }

    @Test
    fun `nanCueStartY_isRejected`() {
        assertFalse(snapshot(cueStartY = Float.NaN).isSane())
    }

    @Test
    fun `missingCueBall_isRejectedToAvoidSoftLock`() {
        val noCue = listOf(ball(1), ball(2))
        assertFalse("无白球时 shoot() 静默 return，玩家无法出杆也不能察觉原因", snapshot(balls = noCue).isSane())
    }

    @Test
    fun twoCueBalls_areRejected() {
        val twoCue = listOf(ball(0, kind = PoolBallKind.CUE), ball(9, kind = PoolBallKind.CUE))
        assertFalse(snapshot(balls = twoCue).isSane())
    }

    @Test
    fun `exactlyOneCueBall_isAccepted`() {
        assertTrue(snapshot(balls = listOf(ball(0, kind = PoolBallKind.CUE), ball(1))).isSane())
    }
}
