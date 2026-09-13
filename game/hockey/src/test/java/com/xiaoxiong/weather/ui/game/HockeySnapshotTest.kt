package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.HockeyLogic.Difficulty
import com.xiaoxiong.weather.ui.game.HockeyLogic.Phase
import com.xiaoxiong.weather.ui.game.HockeyLogic.Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HockeyGame] 局中快照的 JVM 单测：进行中局面存读字段一致、球桌重建路径、
 * 画布尺寸变化后的半径重算、空档/坏档不崩、结算清档后不可恢复。
 */
class HockeySnapshotTest {
    private val snapshotKey = "hockey_snapshot_v1"

    private fun startedGame(): HockeyGame {
        val game = HockeyGame()
        game.difficulty = Difficulty.HARD
        game.customLevel = 9
        game.winScore = 7
        game.paddleScale = 1.3f
        game.puckScale = 0.75f
        game.goalScale = 1.2f
        game.resize(400f, 800f, 20f, 40f)
        game.startMatch()
        game.phase = Phase.PLAYING
        game.playerScore = 3
        game.aiScore = 2
        game.serveTimer = 1.5f
        game.scoreFlash = 0.4f
        game.hitFlash = 0.7f
        game.bestWins = 4
        game.matchWinner = null
        game.lastScorer = Scorer.PLAYER
        game.time = 42f
        game.player.x = 100f
        game.player.y = 650f
        game.player.vx = 10f
        game.player.vy = -20f
        game.ai.x = 300f
        game.ai.y = 150f
        game.ai.vx = 5f
        game.ai.vy = 6f
        game.puck.x = 200f
        game.puck.y = 400f
        game.puck.vx = -30f
        game.puck.vy = 40f
        game.trail.clear()
        game.trail.addLast(201f to 401f)
        game.trail.addLast(202f to 402f)
        return game
    }

    /** 手工拼一份表头 21 字段 + 难度名的旧版快照（无拖尾字段）。 */
    private fun legacySnapshotPayload(
        phase: Phase = Phase.PLAYING,
        bodyRadius: Float = 35.1f,
    ): String {
        val header =
            mutableListOf<Number>(
                phase.ordinal,
                3,
                2,
                1.5f,
                0.4f,
                0.7f,
                9,
                7,
                1.3f,
                0.75f,
                1.2f,
                4,
                -1,
                0,
                0,
                42f,
                400f,
                800f,
                20f,
                40f,
            )
        header += Difficulty.HARD.name.length
        Difficulty.HARD.name.forEach { header += it.code }
        val groups =
            listOf(
                header,
                listOf<Number>(100f, 650f, 10f, -20f, bodyRadius),
                listOf<Number>(300f, 150f, 5f, 6f, bodyRadius),
                listOf<Number>(200f, 400f, -30f, 40f, bodyRadius),
            )
        return SnapshotCodec.encodeGroups(groups)
    }

    /** 手工拼一份 1.10 旧格式快照：当前主题与「局中暂存主题」分别落盘。 */
    private fun snapshotPayloadWithPendingTheme(
        current: RinkTheme,
        pending: RinkTheme,
    ): String {
        val header =
            mutableListOf<Number>(
                Phase.PLAYING.ordinal,
                3,
                2,
                1.5f,
                0.4f,
                0.7f,
                9,
                7,
                1.3f,
                0.75f,
                1.2f,
                4,
                -1,
                0,
                0,
                42f,
                400f,
                800f,
                20f,
                40f,
            )
        header += Difficulty.HARD.name.length
        Difficulty.HARD.name.forEach { header += it.code }
        // 拖尾：paddleScaleBoth + matchDifficulty + 5 个非主题 pending（均无暂存）
        header += 1f
        header += Difficulty.HARD.ordinal
        repeat(5) { header += -1f }
        // 当前主题 + 旧语义的「下一局主题」
        header += current.id.length
        current.id.forEach { header += it.code }
        header += pending.id.length
        pending.id.forEach { header += it.code }
        val groups =
            listOf(
                header,
                listOf<Number>(100f, 650f, 10f, -20f, 35.1f),
                listOf<Number>(300f, 150f, 5f, 6f, 35.1f),
                listOf<Number>(200f, 400f, -30f, 40f, 35.1f),
            )
        return SnapshotCodec.encodeGroups(groups)
    }

    @Test
    fun saveRestore_keepsRunningState() {
        val prefs = TestPrefs()
        val source = startedGame()
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(snapshotKey))

        val restored = HockeyGame()
        // 模拟 Screen 的 resize 先于恢复执行
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))

        assertEquals(Phase.PLAYING, restored.phase)
        assertEquals(3, restored.playerScore)
        assertEquals(2, restored.aiScore)
        assertEquals(1.5f, restored.serveTimer, 1e-4f)
        assertEquals(0.4f, restored.scoreFlash, 1e-4f)
        assertEquals(0.7f, restored.hitFlash, 1e-4f)
        assertEquals(Difficulty.HARD, restored.difficulty)
        assertEquals(Difficulty.HARD, restored.matchDifficulty)
        assertEquals(9, restored.customLevel)
        assertEquals(7, restored.winScore)
        assertEquals(1.3f, restored.paddleScale, 1e-4f)
        assertTrue(restored.paddleScaleBoth)
        assertEquals(0.75f, restored.puckScale, 1e-4f)
        assertEquals(1.2f, restored.goalScale, 1e-4f)
        assertEquals(4, restored.bestWins)
        assertEquals(Scorer.PLAYER, restored.lastScorer)
        assertEquals(42f, restored.time, 1e-4f)
        assertNotNull(restored.table)

        assertEquals(100f, restored.player.x, 1e-3f)
        assertEquals(650f, restored.player.y, 1e-3f)
        assertEquals(10f, restored.player.vx, 1e-3f)
        assertEquals(-20f, restored.player.vy, 1e-3f)
        assertEquals(300f, restored.ai.x, 1e-3f)
        assertEquals(150f, restored.ai.y, 1e-3f)
        assertEquals(200f, restored.puck.x, 1e-3f)
        assertEquals(400f, restored.puck.y, 1e-3f)
        assertEquals(-30f, restored.puck.vx, 1e-3f)
        assertEquals(40f, restored.puck.vy, 1e-3f)
        assertEquals(2, restored.trail.size)
        assertEquals(201f, restored.trail.first().first, 1e-3f)
    }

    @Test
    fun restore_beforeResize_rebuildsTableFromSavedCanvas() {
        val prefs = TestPrefs()
        startedGame().saveSnapshot(prefs)

        val restored = HockeyGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertNotNull("恢复时应按存档画布尺寸先建球桌", restored.table)
        assertEquals(Phase.PLAYING, restored.phase)
        assertEquals(100f, restored.player.x, 1e-3f)
        assertEquals(300f, restored.ai.x, 1e-3f)
    }

    @Test
    fun saveRestore_keepsPaddleScaleBothFalse() {
        val prefs = TestPrefs()
        val source = startedGame()
        source.paddleScaleBoth = false
        source.saveSnapshot(prefs)

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))

        assertFalse(restored.paddleScaleBoth)
        // 「仅玩家变大」：玩家半径按倍率，AI 保持标准挡板
        val t = restored.table!!
        assertEquals(t.paddleR * 1.3f, restored.player.r, 1e-4f)
        assertEquals(t.paddleR, restored.ai.r, 1e-4f)
    }

    @Test
    fun restore_legacySnapshotWithoutTrailingFields_defaultsBothTrue() {
        val prefs = TestPrefs()
        prefs.edit().putString(snapshotKey, legacySnapshotPayload()).apply()

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue("旧档必须仍可恢复", restored.restoreSnapshot(prefs))
        assertTrue("旧档缺 paddleScaleBoth 时回退 true", restored.paddleScaleBoth)
        assertEquals("旧档缺 matchDifficulty 时回退存档难度", Difficulty.HARD, restored.matchDifficulty)
    }

    @Test
    fun restore_afterCanvasChange_recomputesRadiiAndClampsPositions() {
        val prefs = TestPrefs()
        startedGame().saveSnapshot(prefs)

        val restored = HockeyGame()
        // 画布横竖互换：短边变小，存档里的旧半径不再适用
        restored.resize(800f, 400f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))

        val t = restored.table!!
        assertEquals(t.paddleR * 1.3f, restored.player.r, 1e-4f)
        assertEquals(t.paddleR * 1.3f, restored.ai.r, 1e-4f)
        assertEquals(t.puckR, restored.puck.r, 1e-4f)

        // 坐标被钳回新球桌有效范围（player.y=650 / puck.y=400 超出新桌下边界）
        assertEquals(t.bottom - restored.player.r, restored.player.y, 1e-3f)
        assertEquals(t.bottom - restored.puck.r, restored.puck.y, 1e-3f)
        assertTrue(restored.player.x in t.left + restored.player.r..t.right - restored.player.r)
        assertTrue(restored.player.y in t.midY + restored.player.r * 0.35f..t.bottom - restored.player.r)
        assertTrue(restored.ai.y in t.top + restored.ai.r..t.midY - restored.ai.r * 0.35f)
        assertTrue(restored.puck.x in t.left + restored.puck.r..t.right - restored.puck.r)
        assertTrue(restored.puck.y in t.top + restored.puck.r..t.bottom - restored.puck.r)
    }

    @Test
    fun restore_oversizedSavedRadii_doesNotThrowOrDropSnapshot() {
        val prefs = TestPrefs()
        prefs.edit().putString(snapshotKey, legacySnapshotPayload(bodyRadius = 10_000f)).apply()

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue("异常大的存档半径不得导致整档静默丢失", restored.restoreSnapshot(prefs))
        assertTrue(prefs.contains(snapshotKey))
        assertEquals(restored.table!!.paddleR * 1.3f, restored.player.r, 1e-4f)
    }

    @Test
    fun restore_emptyPrefs_returnsFalse() {
        val restored = HockeyGame()
        assertFalse(restored.restoreSnapshot(TestPrefs()))
        assertEquals(Phase.READY, restored.phase)
    }

    @Test
    fun restore_corruptPayload_returnsFalseWithoutCrash() {
        val prefs = TestPrefs()
        prefs.edit().putString(snapshotKey, "broken;;;").apply()
        assertFalse(HockeyGame().restoreSnapshot(prefs))

        val shortHeader = TestPrefs()
        shortHeader.edit().putString(snapshotKey, "1,2,3").apply()
        assertFalse(HockeyGame().restoreSnapshot(shortHeader))
    }

    @Test
    fun restore_resetsAiTargetToRestoredPosition() {
        val prefs = TestPrefs()
        val source = startedGame()
        source.ai.x = 300f
        source.ai.y = 150f
        source.saveSnapshot(prefs)

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))

        // 恢复后 AI 追踪目标必须同步到自身位置，否则会朝初值 (0,0) 猛冲
        assertEquals(restored.ai.x, restored.aiTargetX, 1e-3f)
        assertEquals(restored.ai.y, restored.aiTargetY, 1e-3f)
        assertEquals(99f, restored.aiReactionAge, 1e-3f)
    }

    @Test
    fun restore_keepsMatchDifficultyWhenChangedMidMatch() {
        val prefs = TestPrefs()
        val source = startedGame()
        // 开局 HARD，局中改成 MASTER：本局记账难度应保持 HARD
        source.difficulty = Difficulty.MASTER
        assertEquals(Difficulty.HARD, source.matchDifficulty)
        source.saveSnapshot(prefs)

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(Difficulty.MASTER, restored.difficulty)
        assertEquals(Difficulty.HARD, restored.matchDifficulty)
    }

    /**
     * 局中改设置 → 被杀 → 恢复 → 下一局：暂存（pending）必须随快照保留，
     * 否则恢复后被 header 旧值覆盖、pending 丢失，设置永不生效。
     */
    @Test
    fun saveRestore_keepsPendingRulesForNextMatch() {
        val prefs = TestPrefs()
        val source = startedGame()
        assertTrue("对局进行中改设置应暂存", source.rulesLocked)
        source.applySettings(
            difficulty = Difficulty.MASTER,
            customLevel = 8,
            winScore = 9,
            paddleScale = 1.4f,
            paddleScaleBoth = false,
            puckScale = 0.8f,
            goalScale = 1.3f,
        )
        assertTrue(source.hasPendingRules)
        source.saveSnapshot(prefs)

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))
        assertTrue("恢复后暂存设置必须仍在", restored.hasPendingRules)
        assertEquals(9, restored.displayWinScore())
        assertEquals(1.4f, restored.displayPaddleScale(), 1e-4f)
        assertFalse(restored.displayPaddleScaleBoth())
        assertEquals(0.8f, restored.displayPuckScale(), 1e-4f)
        assertEquals(1.3f, restored.displayGoalScale(), 1e-4f)

        // 再来一局：套用暂存值
        restored.startMatch()
        assertEquals(9, restored.winScore)
        assertEquals(1.4f, restored.paddleScale, 1e-4f)
        assertFalse(restored.paddleScaleBoth)
        assertEquals(0.8f, restored.puckScale, 1e-4f)
        assertEquals(1.3f, restored.goalScale, 1e-4f)
        assertFalse(restored.hasPendingRules)
    }

    /** 旧档没有 pending 拖尾字段：恢复后不得凭空产生暂存设置（保持旧行为）。 */
    @Test
    fun restore_legacySnapshot_hasNoPendingRules() {
        val prefs = TestPrefs()
        prefs.edit().putString(snapshotKey, legacySnapshotPayload()).apply()

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))
        assertFalse(restored.hasPendingRules)
    }

    /** 场地主题是皮肤：局中切换立即生效，随快照按「当前主题」往返。 */
    @Test
    fun saveRestore_keepsRinkTheme() {
        val prefs = TestPrefs()
        val source = startedGame()
        source.rinkTheme = RinkTheme.BEAR
        source.applySettings(
            difficulty = Difficulty.HARD,
            customLevel = 9,
            winScore = 7,
            paddleScale = 1.3f,
            paddleScaleBoth = true,
            puckScale = 0.75f,
            goalScale = 1.2f,
            rinkTheme = RinkTheme.CONCRETE,
        )
        assertTrue(source.rulesLocked)
        assertEquals("局中主题立即生效", RinkTheme.CONCRETE, source.rinkTheme)
        source.saveSnapshot(prefs)

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("主题随快照保留", RinkTheme.CONCRETE, restored.rinkTheme)
        assertEquals(RinkTheme.CONCRETE, restored.displayRinkTheme())

        restored.startMatch()
        assertEquals(RinkTheme.CONCRETE, restored.rinkTheme)
        assertEquals(RinkTheme.CONCRETE, restored.displayRinkTheme())
        assertFalse(restored.hasPendingRules)
    }

    /**
     * 1.10 旧档带「下一局主题」：新版主题是即时皮肤，恢复时把暂存主题
     * 折叠为当前主题并清空暂存，避免设置弹窗显示木桌、场上却还是冰面。
     */
    @Test
    fun restore_legacySnapshotFoldsPendingRinkTheme() {
        val prefs = TestPrefs()
        prefs
            .edit()
            .putString(snapshotKey, snapshotPayloadWithPendingTheme(RinkTheme.BEAR, RinkTheme.WOOD))
            .apply()

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue("旧档必须仍可恢复", restored.restoreSnapshot(prefs))
        assertEquals("旧档暂存主题折叠为当前主题", RinkTheme.WOOD, restored.rinkTheme)
        assertEquals(RinkTheme.WOOD, restored.displayRinkTheme())
        assertFalse("主题暂存应被折叠清空", restored.hasPendingRules)

        restored.startMatch()
        assertEquals("下一局不再套用旧暂存", RinkTheme.WOOD, restored.rinkTheme)
    }

    /** 旧档没有主题拖尾字段：回退默认 ICE、无暂存主题。 */
    @Test
    fun restore_legacySnapshot_defaultsRinkThemeIce() {
        val prefs = TestPrefs()
        prefs.edit().putString(snapshotKey, legacySnapshotPayload()).apply()

        val restored = HockeyGame()
        restored.resize(400f, 800f, 20f, 40f)
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(RinkTheme.ICE, restored.rinkTheme)
        assertEquals(RinkTheme.ICE, restored.displayRinkTheme())
        assertFalse(restored.hasPendingRules)
    }

    @Test
    fun clear_afterMatchOver_thenRestoreFalse() {
        val prefs = TestPrefs()
        val game = startedGame()
        game.saveSnapshot(prefs)
        assertTrue(prefs.contains(snapshotKey))

        game.phase = Phase.MATCH_OVER
        game.clearSnapshot(prefs)
        assertFalse(prefs.contains(snapshotKey))
        assertFalse(HockeyGame().restoreSnapshot(prefs))
    }
}
