package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [StarJumpGame] 对局快照的 JVM 单测：进行中续档、无档/坏档容错、结束/重开清档。 */
class StarSnapshotTest {
    private val key = "star_snapshot_v1"

    @Test
    fun saveThenRestore_roundTripsRunningState() {
        val prefs = FakePrefs()
        val source =
            StarJumpGame().apply {
                w = 1080f
                h = 1920f
                s = 1f
            }
        source.start()
        source.platforms.clear()
        source.stars.clear()
        source.time = 12.5f
        source.bearX = 500f
        source.bearY = -800f
        source.bearVX = 120f
        source.bearVY = -300f
        source.cameraY = -1200f
        source.maxHeight = -1500f
        source.starsCollected = 4
        source.score = 250
        source.squashT = 0.03f
        source.shakeT = 0.15f
        source.deadT = 0.5f
        source.best = 321
        source.scene = JumpScene.SNOW
        source.nextPlatY = -900f
        source.platforms += Platform(100f, -200f, 120f, PlatType.MOVING, vx = 55f)
        source.platforms += Platform(300f, -400f, 90f, PlatType.BREAK, broken = true, breakT = 0.5f)
        source.stars += StarItem(200f, -300f, taken = true, seed = 1.23f)
        source.saveSnapshot(prefs)

        val restored = StarJumpGame()
        assertTrue(restored.restoreSnapshot(prefs))

        assertEquals(GamePhase.RUNNING, restored.phase)
        assertEquals(12.5f, restored.time, 1e-3f)
        assertEquals(500f, restored.bearX, 1e-3f)
        assertEquals(-800f, restored.bearY, 1e-3f)
        assertEquals(120f, restored.bearVX, 1e-3f)
        assertEquals(-300f, restored.bearVY, 1e-3f)
        assertEquals(-1200f, restored.cameraY, 1e-3f)
        assertEquals(-1500f, restored.maxHeight, 1e-3f)
        assertEquals(4, restored.starsCollected)
        assertEquals(250, restored.score)
        assertEquals(0.03f, restored.squashT, 1e-3f)
        assertEquals(0.15f, restored.shakeT, 1e-3f)
        assertEquals(0.5f, restored.deadT, 1e-3f)
        assertEquals(321, restored.best)
        assertEquals(JumpScene.SNOW, restored.scene)
        assertEquals(-900f, restored.nextPlatY, 1e-3f)
        assertNull(restored.touchX)
        assertEquals(2, restored.platforms.size)
        assertEquals(PlatType.MOVING, restored.platforms[0].type)
        assertEquals(100f, restored.platforms[0].x, 1e-3f)
        assertEquals(-200f, restored.platforms[0].y, 1e-3f)
        assertEquals(120f, restored.platforms[0].w, 1e-3f)
        assertEquals(55f, restored.platforms[0].vx, 1e-3f)
        assertFalse(restored.platforms[0].broken)
        assertEquals(PlatType.BREAK, restored.platforms[1].type)
        assertTrue(restored.platforms[1].broken)
        assertEquals(0.5f, restored.platforms[1].breakT, 1e-3f)
        assertEquals(1, restored.stars.size)
        assertEquals(200f, restored.stars[0].x, 1e-3f)
        assertEquals(-300f, restored.stars[0].y, 1e-3f)
        assertTrue(restored.stars[0].taken)
        assertEquals(1.23f, restored.stars[0].seed, 1e-3f)
    }

    @Test
    fun restore_emptyPrefs_returnsFalse() {
        assertFalse(StarJumpGame().restoreSnapshot(FakePrefs()))
    }

    @Test
    fun restore_corruptText_returnsFalseAndDoesNotCrash() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, "not-a-snapshot").apply()
        assertFalse(StarJumpGame().restoreSnapshot(prefs))
    }

    @Test
    fun restore_truncatedMeta_returnsFalse() {
        val prefs = FakePrefs()
        prefs.edit().putString(key, "1,2,3").apply()
        assertFalse(StarJumpGame().restoreSnapshot(prefs))
    }

    @Test
    fun saveSnapshot_ready_doesNotWrite() {
        val prefs = FakePrefs()
        StarJumpGame().saveSnapshot(prefs)
        assertFalse(prefs.contains(key))
    }

    @Test
    fun clearSnapshot_afterSave_makesRestoreFail() {
        val prefs = FakePrefs()
        val source =
            StarJumpGame().apply {
                w = 1080f
                h = 1920f
                s = 1f
            }
        source.start()
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        source.clearSnapshot(prefs)

        assertFalse(prefs.contains(key))
        assertFalse(StarJumpGame().restoreSnapshot(prefs))
    }

    @Test
    fun restore_nonRunningPhase_returnsFalse() {
        for (phase in listOf(GamePhase.READY, GamePhase.OVER)) {
            val prefs = FakePrefs()
            val source =
                StarJumpGame().apply {
                    w = 1080f
                    h = 1920f
                    s = 1f
                }
            source.start()
            source.saveSnapshot(prefs)
            rewritePhase(prefs, phase.ordinal)

            val restored = StarJumpGame()
            assertFalse("phase=$phase 的旧档不应恢复", restored.restoreSnapshot(prefs))
            assertEquals(GamePhase.READY, restored.phase)
        }
    }

    @Test
    fun restore_bestMergesWithCurrentValue() {
        val prefs = FakePrefs()
        val source =
            StarJumpGame().apply {
                w = 1080f
                h = 1920f
                s = 1f
            }
        source.start()
        source.best = 100
        source.saveSnapshot(prefs)

        val restored = StarJumpGame()
        restored.best = 500
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals("旧快照不得把最高分改小", 500, restored.best)
    }

    @Test
    fun setDimsWhileOver_keepsScoreAndPhase() {
        val game = StarJumpGame()
        game.setDims(1080f, 1920f, 1f)
        game.start()
        game.score = 250
        game.phase = GamePhase.OVER

        game.setDims(800f, 1600f, 1.2f)

        assertEquals(GamePhase.OVER, game.phase)
        assertEquals("结算时改窗口不应清零分数", 250, game.score)
    }

    @Test
    fun setDimsWhilePaused_preservesRunningGame() {
        val game = StarJumpGame()
        game.setDims(1080f, 1920f, 1f)
        game.start()
        game.score = 250

        // 暂停中改窗口尺寸（分屏/折叠）：保留对局
        game.setDims(800f, 1600f, 1.2f, preserveRunning = true)

        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(250, game.score)
        assertTrue(game.platforms.isNotEmpty())
    }

    @Test
    fun setDimsResettingRunningGame_invalidatesSnapshot() {
        val prefs = FakePrefs()
        val game =
            StarJumpGame().apply {
                w = 1080f
                h = 1920f
                s = 1f
            }
        game.start()
        game.saveSnapshot(prefs)
        assertTrue(prefs.contains(key))

        // 改窗口导致整局回准备态：旧档必须失效，停在 READY 被兜底落盘也不能复活
        game.setDims(800f, 1600f, 1.2f)
        assertEquals(GamePhase.READY, game.phase)
        game.saveSnapshot(prefs)

        assertFalse("改窗口重开后旧档应被清掉", prefs.contains(key))
        assertFalse(StarJumpGame().restoreSnapshot(prefs))
    }

    @Test
    fun setDimsWhilePaused_preservesSnapshotValidity() {
        val prefs = FakePrefs()
        val game =
            StarJumpGame().apply {
                w = 1080f
                h = 1920f
                s = 1f
            }
        game.start()
        game.saveSnapshot(prefs)

        // 暂停中改窗口保留对局：快照仍应可写可恢复，不能被误清
        game.setDims(800f, 1600f, 1.2f, preserveRunning = true)
        assertEquals(GamePhase.RUNNING, game.phase)
        game.saveSnapshot(prefs)

        assertTrue(prefs.contains(key))
        assertTrue(StarJumpGame().restoreSnapshot(prefs))
    }

    /** 把快照第一行（meta）的 phase 字段改写为指定档位，模拟旧档/异常档。 */
    private fun rewritePhase(
        prefs: FakePrefs,
        phaseOrdinal: Int,
    ) {
        val raw = prefs.getString(key, null)!!
        val lines = raw.split('\n').toMutableList()
        val meta = lines[0].split(',').toMutableList()
        meta[0] = phaseOrdinal.toString()
        lines[0] = meta.joinToString(",")
        prefs.edit().putString(key, lines.joinToString("\n")).apply()
    }

    /** 覆盖 StarJumpGame 用到的 SharedPreferences 子集（String/Int/putString/putInt/remove）。 */
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
