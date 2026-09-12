package com.xiaoxiong.weather.ui.game

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BearCatchGame] 局中快照的 JVM 单测：进行中局面存读字段一致、空档/坏档不崩、清档后不可恢复。
 */
class CatchSnapshotTest {
    private val snapshotKey = "catch_snapshot_v1"

    @Test
    fun saveRestore_keepsRunningState() {
        val prefs = FakePrefs()
        val source = BearCatchGame()
        source.resize(IntSize(400, 800))
        source.start()
        source.scene = CatchScene.STORM
        source.score = 120
        source.hearts = 2
        source.combo = 3
        source.doubleT = 4.5f
        source.hasShield = true
        source.shieldCooldownT = 7f
        source.invincibleT = 0.5f
        source.spawnAccum = 1.25f
        source.time = 33f
        source.inBurstWave = true
        source.shakeT = 0.2f
        source.squashT = 0.05f
        source.catchX = 123f
        source.touchX = 150f
        source.best = 999
        source.items +=
            BearCatchLogic
                .Item(
                    kind = ItemKind.GIFT,
                    x = 50f,
                    y = 60f,
                    r = 18f,
                    phase = 1.2f,
                ).also { it.vy = 300f }
        source.particles +=
            Particle(
                x = 1f,
                y = 2f,
                vx = 3f,
                vy = 4f,
                life = 0.5f,
                maxLife = 0.6f,
                color = Color(0.2f, 0.4f, 0.6f, 0.8f),
            )
        source.saveSnapshot(prefs)
        assertTrue(prefs.contains(snapshotKey))

        val restored = BearCatchGame()
        assertTrue(restored.restoreSnapshot(prefs))

        assertEquals(GamePhase.RUNNING, restored.phase)
        assertEquals(120, restored.score)
        assertEquals(2, restored.hearts)
        assertEquals(3, restored.combo)
        assertEquals(4.5f, restored.doubleT, 1e-4f)
        assertTrue(restored.hasShield)
        assertEquals(7f, restored.shieldCooldownT, 1e-4f)
        assertEquals(0.5f, restored.invincibleT, 1e-4f)
        assertEquals(1.25f, restored.spawnAccum, 1e-4f)
        assertEquals(33f, restored.time, 1e-4f)
        assertTrue(restored.inBurstWave)
        assertEquals(0.2f, restored.shakeT, 1e-4f)
        assertEquals(0.05f, restored.squashT, 1e-4f)
        assertEquals(CatchScene.STORM, restored.scene)
        assertEquals(IntSize(400, 800), restored.size)
        assertEquals(123f, restored.catchX, 1e-3f)
        // 触摸点不落盘：恢复后必须为空，避免小熊朝旧触点漂移
        assertNull(restored.touchX)
        assertEquals(999, restored.best)

        assertEquals(1, restored.items.size)
        val item = restored.items.first()
        assertEquals(ItemKind.GIFT, item.kind)
        assertEquals(50f, item.x, 1e-3f)
        assertEquals(60f, item.y, 1e-3f)
        assertEquals(18f, item.r, 1e-3f)
        assertEquals(300f, item.vy, 1e-3f)
        assertEquals(1.2f, item.phase, 1e-3f)

        assertEquals(1, restored.particles.size)
        val p = restored.particles.first()
        assertEquals(1f, p.x, 1e-3f)
        assertEquals(0.5f, p.life, 1e-3f)
        assertEquals(0.2f, p.color.red, 1e-3f)
        assertEquals(0.4f, p.color.green, 1e-3f)
        assertEquals(0.6f, p.color.blue, 1e-3f)
        assertEquals(0.8f, p.color.alpha, 1e-3f)
    }

    @Test
    fun restore_emptyPrefs_returnsFalse() {
        val restored = BearCatchGame()
        assertFalse(restored.restoreSnapshot(FakePrefs()))
        assertEquals(GamePhase.READY, restored.phase)
    }

    @Test
    fun restore_corruptPayload_returnsFalseWithoutCrash() {
        val prefs = FakePrefs()
        prefs.edit().putString(snapshotKey, "not-a-snapshot;;;").apply()
        assertFalse(BearCatchGame().restoreSnapshot(prefs))

        val shortHeader = FakePrefs()
        shortHeader.edit().putString(snapshotKey, "0,1,2,3").apply()
        assertFalse(BearCatchGame().restoreSnapshot(shortHeader))
    }

    @Test
    fun clearSnapshot_thenRestoreReturnsFalse() {
        val prefs = FakePrefs()
        val game = BearCatchGame()
        game.resize(IntSize(400, 800))
        game.start()
        game.saveSnapshot(prefs)
        assertTrue(prefs.contains(snapshotKey))

        // 模拟结算：清档后不应再能续玩
        game.phase = GamePhase.OVER
        game.clearSnapshot(prefs)
        assertFalse(prefs.contains(snapshotKey))
        assertFalse(BearCatchGame().restoreSnapshot(prefs))
    }

    @Test
    fun resizeToSavedDims_keepsRunningGame() {
        val prefs = FakePrefs()
        val source = BearCatchGame()
        source.resize(IntSize(400, 800))
        source.start()
        source.score = 120
        source.combo = 3
        source.items +=
            BearCatchLogic
                .Item(kind = ItemKind.COIN, x = 50f, y = 60f, r = 15f, phase = 0f)
                .also { it.vy = 300f }
        source.saveSnapshot(prefs)

        // 恢复时当前尺寸已是非存档值，故存档尺寸被记为 restoredDims
        val restored = BearCatchGame()
        restored.resize(IntSize(300, 600))
        assertTrue(restored.restoreSnapshot(prefs))
        assertEquals(120, restored.score)
        assertEquals(1, restored.items.size)

        // 尺寸变为与存档一致：保留对局，只夹取 catchX
        restored.catchX = 9999f
        restored.resize(IntSize(400, 800))

        assertEquals(GamePhase.RUNNING, restored.phase)
        assertEquals(120, restored.score)
        assertEquals(3, restored.combo)
        assertEquals(1, restored.items.size)
        assertEquals(400f - restored.catchR, restored.catchX, 1e-3f)

        // 真正的尺寸变化（与存档不一致）仍重置
        restored.resize(IntSize(500, 900))
        assertEquals(GamePhase.READY, restored.phase)
        assertEquals(0, restored.score)
    }

    @Test
    fun resizeToNarrowWindow_clampsSafelyWithoutCrash() {
        // 细高窗口 width < 2·catchR：旧 coerceIn 区间非法会抛 IllegalArgumentException
        val game = BearCatchGame()
        game.resize(IntSize(200, 2000))
        assertEquals(GamePhase.READY, game.phase)
        assertEquals("细高窗口小熊应居中", 100f, game.catchX, 1e-3f)

        game.start()
        game.touchX = 5f
        game.tick(dt = 0.016f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals("tick 触摸目标夹取不应崩溃且保持居中", 100f, game.catchX, 1e-3f)

        // reset 路径同样安全
        game.reset()
        assertEquals(100f, game.catchX, 1e-3f)
    }

    @Test
    fun saveRestore_doesNotPersistTouchPoint() {
        val prefs = FakePrefs()
        val source = BearCatchGame()
        source.resize(IntSize(400, 800))
        source.start()
        source.catchX = 200f
        source.touchX = 380f
        source.saveSnapshot(prefs)

        val restored = BearCatchGame()
        assertTrue(restored.restoreSnapshot(prefs))
        assertNull("恢复后不应带触摸点", restored.touchX)
        val before = restored.catchX
        restored.tick(dt = 0.05f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals("恢复后不得朝旧触点自动漂移", before, restored.catchX, 1e-3f)
    }

    @Test
    fun death_clearsSnapshotThroughFallbackSave() {
        val prefs = FakePrefs()
        val game = BearCatchGame()
        game.resize(IntSize(400, 800))
        game.start()
        game.saveSnapshot(prefs)
        assertTrue(prefs.contains(snapshotKey))

        // 模拟死亡：进程可能在 LaunchedEffect 清档前被杀，ON_STOP/onDispose 的兜底落盘必须清档
        game.hearts = 1
        game.items +=
            BearCatchLogic
                .Item(
                    kind = ItemKind.LIGHTNING,
                    x = game.catchX,
                    y = game.catchY,
                    r = 28f,
                    phase = 0f,
                ).also { it.vy = 0f }
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(GamePhase.OVER, game.phase)

        game.saveSnapshot(prefs)
        assertFalse(prefs.contains(snapshotKey))
        assertFalse(BearCatchGame().restoreSnapshot(prefs))
    }

    @Test
    fun resizeWhilePaused_preservesRunningGame() {
        val game = BearCatchGame()
        game.resize(IntSize(400, 800))
        game.start()
        game.score = 10
        game.hearts = 2

        // 暂停中改窗口尺寸（分屏/折叠）：不应回 READY 清分，否则暂停菜单与实际进度不一致
        game.resize(IntSize(800, 1600), preserveRunning = true)

        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(10, game.score)
        assertEquals(2, game.hearts)
    }

    /** 覆盖 BearCatchGame 快照用到的 SharedPreferences 子集。 */
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
