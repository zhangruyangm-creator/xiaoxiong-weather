package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 屏幕侧快照清档谓词的纯函数测试：只有结算态需要清档，READY/RUNNING 均可续玩。 */
class JumpSnapshotSessionTest {
    @Test
    fun shouldClear_forOver() {
        assertTrue(jumpSnapshotShouldClear(GamePhase.OVER))
    }

    @Test
    fun shouldClear_notForResumablePhases() {
        assertFalse(jumpSnapshotShouldClear(GamePhase.READY))
        assertFalse(jumpSnapshotShouldClear(GamePhase.RUNNING))
    }
}
