package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

/**
 * [PoolGame] 对局快照的 JVM 单测：进行中对局 save→restore 后状态一致；
 * 空档 / 坏档 / 清档返回 false 且不崩；挑战 / 教学恢复后关联对象不为 null。
 *
 * 注意：这是「续玩快照」，与 `BearPoolScreen` 的 `pool_last_replay_v2` 回放是两件事，
 * 两者 key 不同、互不覆盖。
 */
class PoolSnapshotTest {
    private val key = POOL_SNAPSHOT_KEY
    private val replayKey = "pool_last_replay_v2"

    private fun newGame(): PoolGame =
        PoolGame().apply {
            resize(420f, 820f)
            raceTo = 2
        }

    private data class BallSnapshot(
        val id: Int,
        val x: Float,
        val y: Float,
        val onTable: Boolean,
    )

    private fun snapshot(game: PoolGame): List<BallSnapshot> = game.balls.map { BallSnapshot(it.id, it.x, it.y, it.onTable) }

    @Test
    fun saveRestore_inProgressMatch_keepsBallsPhaseAndScore() {
        val prefs = FakePrefs()
        val source = newGame()
        source.mode = PoolMode.PRACTICE
        source.startMatch(PoolSide.PLAYER, seed = 777L)
        // 手动挪开白球，验证坐标确实被持久化（而非靠重新摆球）
        val cue = source.cueBall()!!
        cue.x = 0.5f
        cue.y = 0.9f
        val expectedBalls = snapshot(source)

        source.saveSnapshot(prefs)
        assertTrue("进行中应写入快照", prefs.contains(key))

        val restored = newGame()
        assertTrue("应能从快照恢复", restored.restoreSnapshot(prefs))
        assertEquals(PoolGame.Phase.AIMING, restored.phase)
        assertEquals(source.match, restored.match)
        assertEquals(source.shots, restored.shots)
        assertEquals(source.raceTo, restored.raceTo)
        assertEquals(expectedBalls.size, restored.balls.size)
        source.balls.zip(restored.balls).forEach { (a, b) ->
            assertEquals(a.id, b.id)
            assertEquals(a.x, b.x, 1e-4f)
            assertEquals(a.y, b.y, 1e-4f)
            assertEquals(a.onTable, b.onTable)
        }
    }

    @Test
    fun restoreSnapshot_outOfRangeRaceTo_isClamped() {
        val prefs = FakePrefs()
        val source = newGame()
        source.mode = PoolMode.PRACTICE
        source.startMatch(PoolSide.PLAYER, seed = 3L)
        source.saveSnapshot(prefs)
        val raw = prefs.getString(key, null)
        assertNotNull(raw)
        val tampered = raw!!.replace(Regex("raceTo=\\d+"), "raceTo=99")
        prefs.edit().putString(key, tampered).apply()

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("越界赛制应被钳制到上限", POOL_RACE_TO_MAX, restored.raceTo)
    }

    @Test
    fun saveRestore_shooting_resumesFlyingBallAndSettles() {
        val prefs = FakePrefs()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 99L)
        source.shoot((Math.PI / 2).toFloat(), 1f)
        source.tick(1f / 60f)
        assertEquals(PoolGame.Phase.SHOOTING, source.phase)
        val cueVx = source.cueBall()!!.vx
        val cueVy = source.cueBall()!!.vy

        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("飞行中的球应恢复到 SHOOTING", PoolGame.Phase.SHOOTING, restored.phase)
        assertEquals(cueVx, restored.cueBall()!!.vx, 1e-4f)
        assertEquals(cueVy, restored.cueBall()!!.vy, 1e-4f)

        // 恢复后继续推进，应能正常结算回到非 SHOOTING
        var t = 0f
        while (restored.phase == PoolGame.Phase.SHOOTING && t < 60f) {
            restored.tick(1f / 60f)
            t += 1f / 60f
        }
        assertTrue("恢复后应能完成结算", t < 60f)
    }

    @Test
    fun restoreSnapshot_empty_returnsFalse() {
        val game = newGame()
        assertFalse(game.restoreSnapshot(FakePrefs()))
    }

    @Test
    fun restoreSnapshot_corrupt_returnsFalseAndClears() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, "这不是合法快照\nnot=valid").apply()
        val game = newGame()
        assertFalse("坏档不应崩溃，返回 false", game.restoreSnapshot(prefs))
        assertFalse("坏档应被清掉", prefs.contains(key))
    }

    @Test
    fun restoreSnapshot_oldGeometryVersion_rejectedAndCleared() {
        // 1.9.0 起袋口/台面几何改变（pocketIndex 4/5 含义不同），旧 v1 档必须拒绝
        val prefs = FakePrefs()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 9L)
        source.saveSnapshot(prefs)
        val payload = prefs.getString(key, null)
        assertNotNull(payload)
        assertTrue("新档应为 v2: $payload", payload!!.startsWith("v=2"))
        prefs.edit().putString(key, payload.replaceFirst("v=2", "v=1")).apply()

        val restored = newGame()
        assertFalse("旧版本 payload 不应被恢复", restored.restoreSnapshot(prefs))
        assertFalse("旧档应被清掉", prefs.contains(key))
    }

    @Test
    fun restoreSnapshot_onlyLegacyV1Key_returnsFalseAndPurges() {
        // v1 payload 写在「旧 key」上（真实旧档位置）：不得恢复，且旧 key 必须被清理
        val prefs = FakePrefs()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 9L)
        source.saveSnapshot(prefs)
        val v2 = prefs.getString(key, null)
        assertNotNull(v2)
        prefs
            .edit()
            .remove(key)
            .putString(POOL_LEGACY_SNAPSHOT_KEY, v2!!.replaceFirst("v=2", "v=1"))
            .putString(POOL_LEGACY_REPLAY_KEY, "sentinel-legacy-replay")
            .apply()

        val restored = newGame()
        assertFalse("旧 key 上的 v1 档不得恢复", restored.restoreSnapshot(prefs))
        assertEquals("坏档后应保持 READY", PoolGame.Phase.READY, restored.phase)
        assertFalse("旧快照 key 应被清理", prefs.contains(POOL_LEGACY_SNAPSHOT_KEY))
        assertFalse("旧回放 key 应被清理", prefs.contains(POOL_LEGACY_REPLAY_KEY))
    }

    @Test
    fun saveAndClearSnapshot_purgeLegacyKeys() {
        val prefs = FakePrefs()
        prefs
            .edit()
            .putString(POOL_LEGACY_SNAPSHOT_KEY, "v=1\nphase=AIMING")
            .putString(POOL_LEGACY_REPLAY_KEY, "sentinel-legacy-replay")
            .putString(replayKey, "keep-me")
            .apply()

        val game = newGame()
        game.startMatch(PoolSide.PLAYER, seed = 12L)
        game.saveSnapshot(prefs)
        assertFalse("保存 v2 时应清掉旧快照 key", prefs.contains(POOL_LEGACY_SNAPSHOT_KEY))
        assertFalse("保存 v2 时应清掉旧回放 key", prefs.contains(POOL_LEGACY_REPLAY_KEY))

        game.clearSnapshot(prefs)
        assertFalse(prefs.contains(key))
        assertFalse(prefs.contains(POOL_LEGACY_SNAPSHOT_KEY))
        assertFalse(prefs.contains(POOL_LEGACY_REPLAY_KEY))
        assertEquals("v2 回放不属于对局快照，不应误删", "keep-me", prefs.getString(replayKey, null))
    }

    @Test
    fun clearSnapshot_removesSnapshot() {
        val prefs = FakePrefs()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 3L)
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        source.clearSnapshot(prefs)
        assertFalse(prefs.contains(key))
        assertFalse("清档后不应恢复", newGame().restoreSnapshot(prefs))
    }

    @Test
    fun saveSnapshot_outsideActivePlay_clearsSnapshot() {
        val prefs = FakePrefs()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 4L)
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        // 新游戏仍是 READY：非进行中对局不应保留快照
        newGame().saveSnapshot(prefs)
        assertFalse(prefs.contains(key))
    }

    @Test
    fun challenge_restore_relinksChallengeById() {
        val prefs = FakePrefs()
        val challenge = PoolChallenges.campaigns().first()
        val source = newGame()
        source.startChallenge(challenge)
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertNotNull("挑战恢复后应重新关联 PoolChallenge", restored.challenge)
        assertEquals(challenge.id, restored.challenge!!.id)
        assertEquals(challenge.par, restored.challenge!!.par)
        assertEquals("普通关卡不是每日挑战", 0L, restored.challengeDate)
        assertEquals(PoolMode.CHALLENGE, restored.match.mode)
        assertEquals(challenge.balls.size, restored.balls.size)
    }

    @Test
    fun dailyChallenge_restore_keepsChallengeDate() {
        val prefs = FakePrefs()
        val date = 20_123L
        val daily = PoolChallenges.daily(date)
        val source = newGame()
        source.startChallenge(daily, date)
        assertEquals(date, source.challengeDate)
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("每日挑战日期必须随快照恢复", date, restored.challengeDate)
        assertEquals(PoolChallenges.DAILY_ID, restored.challenge!!.id)

        restored.startMatch(PoolSide.PLAYER, seed = 1L)
        assertEquals("普通对局应清空每日日期", 0L, restored.challengeDate)
    }

    @Test
    fun lesson_restore_relinksLessonById() {
        val prefs = FakePrefs()
        val lesson = PoolTutorial.lessons().first()
        val source = newGame()
        source.startLesson(lesson)
        source.saveSnapshot(prefs)

        val restored = newGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertNotNull("教学恢复后应重新关联 PoolLesson", restored.lesson)
        assertEquals(lesson.id, restored.lesson!!.id)
        assertEquals(PoolMode.CHALLENGE, restored.match.mode)
    }

    @Test
    fun saveSnapshot_duringReplay_keepsExistingSnapshot() {
        val prefs = FakePrefs()
        val source = newGame()
        source.mode = PoolMode.PRACTICE
        source.startMatch(PoolSide.PLAYER, seed = 8L)
        source.saveSnapshot(prefs)
        val saved = prefs.getString(key, null)
        assertNotNull(saved)

        source.shoot((Math.PI / 2).toFloat(), 0.8f)
        var t = 0f
        while (source.phase == PoolGame.Phase.SHOOTING && t < 30f) {
            source.tick(1f / 60f)
            t += 1f / 60f
        }
        assertTrue(source.replayShots().isNotEmpty())
        assertTrue(source.startReplay())
        source.tick(1f / 60f)

        source.saveSnapshot(prefs)
        assertEquals("回放中不应覆盖续玩快照", saved, prefs.getString(key, null))
    }

    @Test
    fun saveSnapshot_doesNotTouchReplayKey() {
        val prefs = FakePrefs()
        prefs.edit().putString(replayKey, "sentinel-replay").apply()
        val source = newGame()
        source.startMatch(PoolSide.PLAYER, seed = 5L)
        source.saveSnapshot(prefs)

        assertTrue(prefs.contains(key))
        assertEquals("对局快照不得覆盖回放", "sentinel-replay", prefs.getString(replayKey, null))
    }

    // ---------------------------------------------------------------------
    // FRAME_OVER / MATCH_OVER 快照
    // ---------------------------------------------------------------------

    /** 用九球快速赢下一局：只留白球与 9 号，直线打进角袋。 */
    private fun winNineBallFrame(
        game: PoolGame,
        raceTo: Int,
    ) {
        game.raceTo = raceTo
        game.mode = PoolMode.NINE_BALL
        game.startMatch(PoolSide.PLAYER, seed = 31L)
        val cue = game.cueBall()!!
        val nine = game.balls.first { it.number == 9 }
        game.balls.retainAll { it.kind == PoolBallKind.CUE || it.number == 9 }
        nine.x = game.table.left + 0.09f
        nine.y = game.table.top + 0.09f
        cue.x = game.table.left + 0.42f
        cue.y = game.table.top + 0.42f
        game.shoot(atan2(-1f, -1f), 0.6f)
        var t = 0f
        while (game.phase == PoolGame.Phase.SHOOTING && t < 30f) {
            game.tick(1f / 60f)
            t += 1f / 60f
        }
    }

    @Test
    fun frameOver_saveRestore_keepsScoreAndAllowsNextFrame() {
        val prefs = FakePrefs()
        val source = newGame()
        winNineBallFrame(source, raceTo = 2)
        assertEquals("九球赢一局应为 FRAME_OVER（raceTo=2）", PoolGame.Phase.FRAME_OVER, source.phase)
        assertEquals(PoolSide.PLAYER, source.frameWinner)
        val score = source.match
        assertEquals(1, score.playerFrames)

        source.saveSnapshot(prefs)
        assertTrue("FRAME_OVER 应保存续玩快照", prefs.contains(key))

        val restored = newGame()
        assertTrue("FRAME_OVER 快照应可恢复", restored.restoreSnapshot(prefs))
        assertEquals(PoolGame.Phase.FRAME_OVER, restored.phase)
        assertEquals(score, restored.match)
        assertEquals(PoolSide.PLAYER, restored.frameWinner)

        // 恢复后回到「本局结算」界面，应能直接点下一局
        restored.nextFrame()
        assertEquals(PoolGame.Phase.AIMING, restored.phase)
        assertEquals(score.playerFrames, restored.match.playerFrames)
    }

    @Test
    fun matchOver_save_clearsSnapshot_andRestoreFails() {
        val prefs = FakePrefs()
        val source = newGame()
        winNineBallFrame(source, raceTo = 1)
        assertEquals(PoolGame.Phase.MATCH_OVER, source.phase)

        source.saveSnapshot(prefs)
        assertFalse("MATCH_OVER 不应保留续玩快照", prefs.contains(key))
        assertFalse(newGame().restoreSnapshot(prefs))
    }

    /** 覆盖 PoolGame 用到的 SharedPreferences 子集（String/Int/putString/putInt/remove）。 */
    private class FakePrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (values[key] as? MutableSet<String>)?.toMutableSet() ?: defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = values?.toMutableSet()
                return this
            }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                pending.clear()
                values.clear()
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                pending.forEach { (key, value) ->
                    if (value == null) {
                        values.remove(key)
                    } else {
                        values[key] = value
                    }
                }
                pending.clear()
            }
        }
    }
}
