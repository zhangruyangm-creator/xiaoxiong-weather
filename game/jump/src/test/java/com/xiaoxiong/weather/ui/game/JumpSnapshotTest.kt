package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.ui.unit.IntSize
import com.xiaoxiong.weather.game.stringOr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BearGame] 对局快照的 JVM 单测：进行中续档、无档/坏档容错、结束/重开清档。 */
class JumpSnapshotTest {
    private val key = "jump_snapshot_v1"

    @Test
    fun saveThenRestore_roundTripsRunningState() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        source.distance = 1234.5f
        source.speed = 480.25f
        source.coinCount = 7
        source.coinScore = 95
        source.combo = 4
        source.maxCombo = 6
        source.grazeCount = 3
        source.difficulty = RunDifficulty.CHALLENGE
        source.bearY = -321.5f
        source.bearVY = 456.75f
        source.jumpsLeft = 1
        source.grounded = false
        source.runPhase = 1.25f
        source.bob = -2.5f
        source.time = 9.75f
        source.flipT = 0.3f
        source.squashT = 0.05f
        source.shakeT = 0.2f
        source.dustT = 0.04f
        source.nextGap = 333.5f
        source.best = 999
        source.scene = RunScene.SNOW
        source.obstacles += Obstacle(ObstacleType.ICE_WALL, 100f, 46f, 210f, grazed = true)
        source.obstacles += Obstacle(ObstacleType.PUDDLE, 200f, 78f, 20f, grazed = false)
        source.coins += Coin(50f, 60f, 11f)
        source.clouds.clear()
        source.clouds += BgCloud(10f, 20f, 0.8f, 0.15f)
        source.saveSnapshot(prefs)

        val restored = BearGame()
        assertTrue(restored.restoreSnapshot(prefs))

        assertEquals(GamePhase.RUNNING, restored.phase)
        assertEquals(1234.5f, restored.distance, 1e-3f)
        assertEquals(480.25f, restored.speed, 1e-3f)
        assertEquals(7, restored.coinCount)
        assertEquals(95, restored.coinScore)
        assertEquals(4, restored.combo)
        assertEquals(6, restored.maxCombo)
        assertEquals(3, restored.grazeCount)
        assertEquals(RunDifficulty.CHALLENGE, restored.difficulty)
        assertEquals(-321.5f, restored.bearY, 1e-3f)
        assertEquals(456.75f, restored.bearVY, 1e-3f)
        assertEquals(1, restored.jumpsLeft)
        assertFalse(restored.grounded)
        assertEquals(1.25f, restored.runPhase, 1e-3f)
        assertEquals(-2.5f, restored.bob, 1e-3f)
        assertEquals(9.75f, restored.time, 1e-3f)
        assertEquals(0.3f, restored.flipT, 1e-3f)
        assertEquals(0.05f, restored.squashT, 1e-3f)
        assertEquals(0.2f, restored.shakeT, 1e-3f)
        assertEquals(0.04f, restored.dustT, 1e-3f)
        assertEquals(333.5f, restored.nextGap, 1e-3f)
        assertEquals(999, restored.best)
        assertEquals(RunScene.SNOW, restored.scene)
        assertEquals(2, restored.obstacles.size)
        assertEquals(ObstacleType.ICE_WALL, restored.obstacles[0].type)
        assertEquals(100f, restored.obstacles[0].x, 1e-3f)
        assertEquals(46f, restored.obstacles[0].w, 1e-3f)
        assertEquals(210f, restored.obstacles[0].h, 1e-3f)
        assertTrue("已擦边障碍恢复后不得再计分", restored.obstacles[0].grazed)
        assertFalse(restored.obstacles[1].grazed)
        assertEquals(1, restored.coins.size)
        assertEquals(50f, restored.coins[0].x, 1e-3f)
        assertEquals(60f, restored.coins[0].y, 1e-3f)
        assertEquals(11f, restored.coins[0].r, 1e-3f)
        assertEquals(1, restored.clouds.size)
        assertEquals(10f, restored.clouds[0].x, 1e-3f)
        assertEquals(0.8f, restored.clouds[0].scale, 1e-3f)
    }

    @Test
    fun restore_legacyMeta_acceptsWithoutComboAndDefaultsCoinScore() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        source.coinCount = 3
        source.coinScore = 40
        source.combo = 3
        source.saveSnapshot(prefs)
        // 截断 meta 为旧 22 字段，模拟 legacy 档
        val raw = prefs.stringOr(key)!!
        val lines = raw.split('\n').toMutableList()
        val meta = lines[0].split(',').take(22)
        lines[0] = meta.joinToString(",")
        prefs.edit().putString(key, lines.joinToString("\n")).apply()

        val restored = BearGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(0, restored.combo)
        assertEquals(3 * BearGameLogic.COIN_BASE, restored.coinScore)
        assertEquals(RunDifficulty.STANDARD, restored.difficulty)
    }

    @Test
    fun restore_emptyPrefs_returnsFalse() {
        assertFalse(BearGame().restoreSnapshot(FakePrefs()))
    }

    @Test
    fun restore_corruptText_returnsFalseAndDoesNotCrash() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, "not-a-snapshot").apply()
        assertFalse(BearGame().restoreSnapshot(prefs))
    }

    @Test
    fun restore_truncatedMeta_returnsFalse() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, "1,2,3").apply()
        assertFalse(BearGame().restoreSnapshot(prefs))
    }

    @Test
    fun saveSnapshot_ready_doesNotWrite() {
        val prefs = FakePrefs()
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.saveSnapshot(prefs)
        assertFalse(prefs.contains(key))
    }

    @Test
    fun clearSnapshot_afterSave_makesRestoreFail() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        source.clearSnapshot(prefs)

        assertFalse(prefs.contains(key))
        assertFalse(BearGame().restoreSnapshot(prefs))
    }

    @Test
    fun restore_nonRunningPhase_returnsFalse() {
        for (phase in listOf(GamePhase.READY, GamePhase.OVER)) {
            val prefs = FakePrefs()
            val source = BearGame()
            source.resize(IntSize(1080, 1920))
            source.start()
            source.saveSnapshot(prefs)
            rewritePhase(prefs, phase.ordinal)

            val restored = BearGame()
            assertFalse("phase=$phase 的旧档不应恢复", restored.restoreSnapshot(prefs))
            assertEquals(GamePhase.READY, restored.phase)
        }
    }

    @Test
    fun restore_bestMergesWithCurrentValue() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        source.best = 100
        source.saveSnapshot(prefs)

        val restored = BearGame()
        restored.best = 500
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("旧快照不得把最高分改小", 500, restored.best)
    }

    @Test
    fun restoreBeforeSizeReady_doesNotReplayMilestones() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        val s = 1920f / 820f
        // 目标分约 1600：500/1000/1500 三个里程碑在存档前已越过
        source.distance = 1600.5f * 60f * s
        source.coinCount = 0
        source.saveSnapshot(prefs)

        // 模拟恢复早于首次 onSizeChanged（size 仍为 Zero）
        val restored = BearGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(IntSize(1080, 1920), restored.size)
        val v = restored.hudScore
        assertTrue("恢复后应按存档尺寸算出真实分数，实际 $v", v in 1500..1999)
        assertEquals((v / 500 + 1) * 500, restored.nextMilestone)
        assertTrue("下一里程碑必须大于当前分", restored.nextMilestone > v)

        restored.tick(dt = 0.001f, onCoin = {}, onDeath = {})
        assertTrue("不应重播已越过的 500/1000/1500 里程碑", restored.texts.none { it.text.endsWith("!") })
    }

    @Test
    fun resizeWhileOver_keepsScoreAndPhase() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.distance = 6000f
        game.hudScore = game.score()
        val before = game.score()
        game.phase = GamePhase.OVER

        game.resize(IntSize(800, 1600))

        assertEquals(GamePhase.OVER, game.phase)
        assertEquals("结算时改窗口不应改变分数", before, game.score())
        assertEquals(before, game.hudScore)
    }

    @Test
    fun resizeWhilePaused_preservesRunningGame() {
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.distance = 6000f
        val before = game.score()

        // 暂停中改窗口尺寸（分屏/折叠）：保留对局，避免暂停菜单分数与实际进度不一致
        game.resize(IntSize(800, 1600), preserveRunning = true)

        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(before, game.score())
    }

    @Test
    fun resizeResettingRunningGame_invalidatesSnapshot() {
        val prefs = FakePrefs()
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        // 改窗口导致整局回准备态：旧档必须失效，停在 READY 被兜底落盘也不能复活
        game.resize(IntSize(800, 1600))
        assertEquals(GamePhase.READY, game.phase)
        game.saveSnapshot(prefs)

        assertFalse("改窗口重开后旧档应被清掉", prefs.contains(key))
        assertFalse(BearGame().restoreSnapshot(prefs))
    }

    @Test
    fun resizeWhilePaused_preservesSnapshotValidity() {
        val prefs = FakePrefs()
        val game = BearGame()
        game.resize(IntSize(1080, 1920))
        game.start()
        game.saveSnapshot(prefs)

        // 暂停中改窗口保留对局：快照仍应可写可恢复，不能被误清
        game.resize(IntSize(800, 1600), preserveRunning = true)
        assertEquals(GamePhase.RUNNING, game.phase)
        game.saveSnapshot(prefs)

        assertTrue(prefs.contains(key))
        assertTrue(BearGame().restoreSnapshot(prefs))
    }

    /** 把快照第一行（meta）的 phase 字段改写为指定档位，模拟旧档/异常档。 */
    private fun rewritePhase(
        prefs: FakePrefs,
        phaseOrdinal: Int,
    ) {
        val raw = prefs.stringOr(key)!!
        val lines = raw.split('\n').toMutableList()
        val meta = lines[0].split(',').toMutableList()
        meta[0] = phaseOrdinal.toString()
        lines[0] = meta.joinToString(",")
        prefs.edit().putString(key, lines.joinToString("\n")).apply()
    }

    /** 覆盖 BearGame 用到的 SharedPreferences 子集（String/Int/putString/putInt/remove）。 */
    @Test
    fun restore_keepsOnScreenObstacleGrazed_noDoubleScore() {
        val prefs = FakePrefs()
        val source = BearGame()
        source.resize(IntSize(1080, 1920))
        source.start()
        source.obstacles.clear()
        // 仍在小熊前方视野内、但本局已擦边：续玩后不得再加分
        val near =
            Obstacle(
                type = ObstacleType.PUDDLE,
                x = source.bearX + source.bearSize * 0.4f,
                w = 60f * source.s,
                h = 20f * source.s,
                grazed = true,
            )
        source.obstacles += near
        source.grazeCount = 1
        source.coinScore = BearGameLogic.GRAZE_SCORE
        source.saveSnapshot(prefs)

        val restored = BearGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(1, restored.obstacles.size)
        assertTrue(restored.obstacles[0].grazed)
        assertEquals(1, restored.grazeCount)
        val scoreBefore = restored.score()
        restored.tick(dt = 1f / 60f, onCoin = {}, onDeath = {})
        assertEquals("已擦边障碍续玩后不得再加分", scoreBefore, restored.score())
        assertEquals(1, restored.grazeCount)
    }

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
