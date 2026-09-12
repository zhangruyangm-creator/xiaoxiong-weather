package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小熊跳跳的手感与金币弧纯逻辑测试：
 * - 冰墙不再生成会落进墙体的金币（防「捡币 = 撞墙」陷阱）；
 * - 土狼时间 + 跳跃缓冲的意图判定与落地消费。
 */
class BearGameJumpFeelTest {
    private val s = 1f

    // ── A.1 金币弧：冰墙不生成 ──────────────────────────────

    @Test
    fun coinArcYs_iceWall_neverSpawnsCoins() {
        val ys =
            BearGameLogic.coinArcYs(
                type = ObstacleType.ICE_WALL,
                roll = 0f,
                lift = 0.5f,
                groundY = 1000f,
                bearSize = 100f,
                s = s,
            )
        assertEquals("冰墙上方不应生成金币", 0, ys.size)
    }

    @Test
    fun coinArcYs_otherObstacles_returnsFiveCoinsAboveGround() {
        for (type in listOf(ObstacleType.PUDDLE, ObstacleType.SNOWMAN, ObstacleType.STORM_CLOUD)) {
            val ys =
                BearGameLogic.coinArcYs(
                    type = type,
                    roll = 0f,
                    lift = 0.5f,
                    groundY = 1000f,
                    bearSize = 100f,
                    s = s,
                )
            assertEquals("$type 上方应有 5 枚金币", 5, ys.size)
            // 坐标系 y 向下为正：金币应在小熊头顶之上（y < groundY - bearSize）
            ys.forEach { y ->
                assertTrue("$type 金币 $y 应高于小熊头顶", y < 1000f - 100f)
            }
        }
    }

    @Test
    fun coinArcYs_noSpawnWhenRollAboveChance() {
        val ys =
            BearGameLogic.coinArcYs(
                type = ObstacleType.PUDDLE,
                roll = BearGameLogic.COIN_ARC_CHANCE,
                lift = 0.5f,
                groundY = 1000f,
                bearSize = 100f,
                s = s,
            )
        assertEquals(0, ys.size)
    }

    // ── A.2 跳跃意图：土狼时间 + 跳跃缓冲 ────────────────────

    @Test
    fun resolveJumpIntent_grounded_usesFirstJump() {
        assertEquals(
            BearGameLogic.JumpIntent.FIRST,
            BearGameLogic.resolveJumpIntent(grounded = true, airTime = 0f, jumpsLeft = 2),
        )
    }

    @Test
    fun resolveJumpIntent_withinCoyoteTime_allowsFirstJump() {
        val coyote = BearGameLogic.COYOTE_TIME_SEC
        assertEquals(
            BearGameLogic.JumpIntent.FIRST,
            BearGameLogic.resolveJumpIntent(grounded = false, airTime = coyote * 0.5f, jumpsLeft = 2),
        )
        assertEquals(
            BearGameLogic.JumpIntent.FIRST,
            BearGameLogic.resolveJumpIntent(grounded = false, airTime = coyote, jumpsLeft = 2),
        )
    }

    @Test
    fun resolveJumpIntent_airborneBeyondCoyote_usesSecondJump() {
        assertEquals(
            BearGameLogic.JumpIntent.SECOND,
            BearGameLogic.resolveJumpIntent(
                grounded = false,
                airTime = BearGameLogic.COYOTE_TIME_SEC + 0.01f,
                jumpsLeft = 1,
            ),
        )
    }

    @Test
    fun resolveJumpIntent_outOfJumps_returnsNoneForBuffering() {
        assertEquals(
            BearGameLogic.JumpIntent.NONE,
            BearGameLogic.resolveJumpIntent(
                grounded = false,
                airTime = BearGameLogic.COYOTE_TIME_SEC + 0.5f,
                jumpsLeft = 0,
            ),
        )
    }

    // ── A.2 落地缓冲集成 ────────────────────────────────────

    @Test
    fun tapBeforeLanding_isBufferedAndConsumedOnLanding() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        val floorY = game.groundY - game.bearSize
        // 二段跳用尽、贴着地面下落
        game.bearY = floorY - 0.5f
        game.bearVY = 150f
        game.jumpsLeft = 0
        game.grounded = false

        game.tap()
        assertEquals(BearGameLogic.JUMP_BUFFER_SEC, game.jumpBufferT, 1e-4f)

        game.obstacles.clear()
        game.coins.clear()
        game.tick(1f / 60f, onCoin = {}, onDeath = {})

        // 本帧落地后立即消费缓冲，重新起跳
        assertFalse("缓冲应转化为起跳", game.grounded)
        assertEquals(1, game.jumpsLeft)
        assertTrue("起跳瞬间应向上运动（vy < 0）", game.bearVY < 0f)
        assertEquals(0f, game.jumpBufferT, 1e-4f)
    }

    @Test
    fun tapOnGround_jumpsImmediatelyWithoutBuffer() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.obstacles.clear()
        game.coins.clear()

        game.tap()

        assertFalse(game.grounded)
        assertEquals(1, game.jumpsLeft)
        assertEquals(0f, game.jumpBufferT, 1e-4f)
        assertTrue(game.bearVY < 0f)
    }

    @Test
    fun missedCoin_resetsCombo() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.obstacles.clear()
        game.coins.clear()
        game.combo = 5
        val r = 11f * game.s
        // 放在熊身上收集一枚，连击继续
        game.coins += Coin(game.bearX + game.bearSize / 2f, game.bearY + game.bearSize / 2f, r)
        game.tick(1f / 60f, onCoin = {}, onDeath = {})
        assertEquals(6, game.combo)

        // 一枚金币飞出左边界未接住：断连击
        game.coins.clear()
        game.coins += Coin(-r - 100f, game.groundY, r)
        game.tick(1f / 60f, onCoin = {}, onDeath = {})
        assertEquals(0, game.combo)
    }
}
