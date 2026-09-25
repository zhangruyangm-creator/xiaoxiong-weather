package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import com.xiaoxiong.weather.game.stringOr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GameSnapshotSession] / [GameSnapshotController] / [ExitGuard] 的纯逻辑单测：
 * 覆盖「进入恢复 → 阶段清档 → ON_STOP 落盘 → onDispose 兜底 → 退出抑制」时序；
 * Compose 生命周期壳（GameSnapshotLifecycle）不在 JVM 单测范围内。
 */
class GameSnapshotSessionTest {
    private enum class TestPhase { READY, RUNNING, OVER }

    private val prefs = FakePrefs()
    private val session = FakeSession()

    private val clearOnOver: (TestPhase) -> Boolean = { it == TestPhase.OVER }

    // ── 适配器 ────────────────────────────────────────────────

    @Test
    fun `adapter delegates save restore clear to lambdas`() {
        val adapter =
            gameSnapshotSession(
                save = { p -> GameSnapshot.save(p, "k", "payload") },
                restore = { p -> GameSnapshot.load(p, "k") != null },
                clear = { p -> GameSnapshot.clear(p, "k") },
            )

        adapter.saveSnapshot(prefs)
        assertEquals("payload", prefs.stringOr("k"))
        assertTrue(adapter.restoreSnapshot(prefs))
        adapter.clearSnapshot(prefs)
        assertFalse(adapter.restoreSnapshot(prefs))
        assertNull(prefs.stringOr("k"))
    }

    // ── 进入恢复 ──────────────────────────────────────────────

    @Test
    fun `restore on enter restores and invokes callback once`() {
        val controller = controller()
        var restored = 0

        assertTrue(controller.restoreOnEnter { restored++ })
        assertEquals(1, session.restoreCount)
        assertEquals(1, restored)
    }

    @Test
    fun `restore on enter skips callback when no snapshot`() {
        session.restoreResult = false
        val controller = controller()
        var restored = 0

        assertFalse(controller.restoreOnEnter { restored++ })
        assertEquals(1, session.restoreCount)
        assertEquals(0, restored)
    }

    // ── 阶段清档 ──────────────────────────────────────────────

    @Test
    fun `on phase changed clears only terminal phase`() {
        val controller = controller()

        controller.onPhaseChanged(TestPhase.RUNNING, clearOnOver)
        assertEquals(0, session.clearCount)

        controller.onPhaseChanged(TestPhase.OVER, clearOnOver)
        assertEquals(1, session.clearCount)
    }

    // ── ON_STOP / onDispose 落盘 ──────────────────────────────

    @Test
    fun `persist saves while phase is resumable`() {
        val controller = controller()
        controller.persist(TestPhase.RUNNING, clearOnOver)
        assertEquals(1, session.saveCount)
        assertEquals(0, session.clearCount)
    }

    @Test
    fun `persist clears stale snapshot on terminal phase`() {
        val controller = controller()
        controller.persist(TestPhase.OVER, clearOnOver)
        assertEquals(0, session.saveCount)
        assertEquals(1, session.clearCount)
    }

    @Test
    fun `on stop then dispose saves twice as fallback`() {
        val controller = controller()
        controller.persist(TestPhase.RUNNING, clearOnOver)
        controller.persist(TestPhase.RUNNING, clearOnOver)
        assertEquals(2, session.saveCount)
    }

    // ── 退出抑制 ──────────────────────────────────────────────

    @Test
    fun `exit clears snapshot and suppresses fallback save`() {
        val controller = controller()

        controller.exit()
        assertEquals(1, session.clearCount)

        controller.persist(TestPhase.RUNNING, clearOnOver)
        assertEquals(0, session.saveCount)
        assertEquals(1, session.clearCount)
    }

    @Test
    fun `suppress is idempotent and reset restores saving`() {
        val controller = controller()

        controller.exitGuard.suppress()
        controller.exitGuard.suppress()
        assertTrue(controller.exitGuard.suppressed)

        controller.exitGuard.reset()
        assertFalse(controller.exitGuard.suppressed)
        controller.persist(TestPhase.RUNNING, clearOnOver)
        assertEquals(1, session.saveCount)
    }

    @Test
    fun `suppressed guard also skips terminal clear (already cleared on exit)`() {
        val controller = controller()
        controller.exitGuard.suppress()
        controller.persist(TestPhase.OVER, clearOnOver)
        assertEquals(0, session.saveCount)
        assertEquals(0, session.clearCount)
    }

    // ── 落盘决策纯函数 ────────────────────────────────────────

    @Test
    fun `persist action picks skip clear save in order`() {
        assertEquals(
            SnapshotPersistAction.SKIP,
            snapshotPersistAction(suppressed = true, phase = TestPhase.OVER, shouldClear = clearOnOver),
        )
        assertEquals(
            SnapshotPersistAction.CLEAR,
            snapshotPersistAction(suppressed = false, phase = TestPhase.OVER, shouldClear = clearOnOver),
        )
        assertEquals(
            SnapshotPersistAction.SAVE,
            snapshotPersistAction(suppressed = false, phase = TestPhase.RUNNING, shouldClear = clearOnOver),
        )
    }

    private fun controller(): GameSnapshotController = GameSnapshotController(session, prefs)

    private class FakeSession : GameSnapshotSession {
        var saveCount = 0
            private set
        var restoreCount = 0
            private set
        var clearCount = 0
            private set
        var restoreResult = true

        override fun saveSnapshot(prefs: SharedPreferences) {
            saveCount++
        }

        override fun restoreSnapshot(prefs: SharedPreferences): Boolean {
            restoreCount++
            return restoreResult
        }

        override fun clearSnapshot(prefs: SharedPreferences) {
            clearCount++
        }
    }

    /** 覆盖 GameSnapshot / 适配器用到的 SharedPreferences 子集（String）。 */
    private class FakePrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = defValue

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
            ): SharedPreferences.Editor = this

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor = this

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor = this

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor = this

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor = this

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
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }
        }
    }
}
