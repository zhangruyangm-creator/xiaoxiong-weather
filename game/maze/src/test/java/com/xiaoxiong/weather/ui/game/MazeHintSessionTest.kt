package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示会话状态机的单测：覆盖「同局面缓存命中」「切关取消后 busy 复位」
 * 「旧代次结果丢弃且不影响新任务的忙碌态」。
 */
class MazeHintSessionTest {
    private fun request(keys: Int = 0): MazeHintRequest =
        MazeHintRequest(
            map = listOf("###", "#S#", "###"),
            bearR = 1,
            bearC = 1,
            keys = keys,
        )

    @Test
    fun begin_marksBusy_andCompleteCachesResult() {
        val session = MazeHintSession()
        val request = request()
        val token = session.begin(request)
        assertTrue("求解期间必须处于忙碌态", session.busy)

        val hint = MazeHint.Direction(MazeDir.RIGHT)
        assertTrue("当前代次的结果应被采纳", session.complete(token, hint))
        assertFalse("完成后必须复位忙碌态", session.busy)
        assertEquals("同一局面应命中缓存", hint, session.cachedFor(request))
    }

    @Test
    fun cachedFor_comparesRequestContent() {
        val session = MazeHintSession()
        val token = session.begin(request(keys = 0))
        session.complete(token, MazeHint.TooLarge)
        // 内容相同的请求视为同一局面
        assertEquals(MazeHint.TooLarge, session.cachedFor(request(keys = 0)))
        assertNull("内容不同的局面不应命中缓存", session.cachedFor(request(keys = 1)))
    }

    @Test
    fun cancel_resetsBusyAndDiscardsInFlightResult() {
        val session = MazeHintSession()
        val request = request()
        val token = session.begin(request)
        assertTrue(session.busy)

        // 模拟切换关卡：取消在途任务，按钮必须立即恢复可用
        session.cancel()
        assertFalse("切关后 busy 必须立即复位", session.busy)
        assertNull("切关后缓存必须清空", session.cachedFor(request))

        // 旧 BFS 结束后回写：代次已过期，结果必须丢弃
        assertFalse("旧结果必须被丢弃", session.complete(token, MazeHint.Direction(MazeDir.UP)))
        assertFalse(session.busy)
    }

    @Test
    fun staleFinish_doesNotClearNewTaskBusy() {
        val session = MazeHintSession()
        val staleToken = session.begin(request(keys = 0))
        session.cancel()
        val freshToken = session.begin(request(keys = 1))
        assertTrue(session.busy)

        // 旧任务的 finally 晚到，不能把新任务的忙碌态复位
        session.finish(staleToken)
        assertTrue("旧代次 finally 不能影响新任务", session.busy)

        assertTrue(session.complete(freshToken, MazeHint.NoSolution))
        assertFalse(session.busy)
        assertEquals(MazeHint.NoSolution, session.cachedFor(request(keys = 1)))
    }
}
