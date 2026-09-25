package com.xiaoxiong.weather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * [runCatchingCancellable] 必须原样抛出取消信号。
 *
 * 背景：`runCatching` 捕获 `Throwable`，而 `CancellationException` 是其子类，
 * 于是「协程被取消」被当成「业务失败」转成 null/emptyList。页面切走或切城时，
 * 旧 effect 的取消会被吞掉并继续走收尾逻辑，甚至回写新页面状态。
 */
class CancellableRunCatchingTest {
    @Test
    fun `cancellation is rethrown instead of captured`() {
        val thrown =
            try {
                runCatchingCancellable { throw CancellationException("cancel") }
                null
            } catch (e: CancellationException) {
                e
            }
        assertTrue("取消必须原样抛出，不能变成 Result.failure", thrown != null)
    }

    @Test
    fun `real failures are still captured`() {
        val result = runCatchingCancellable { throw IllegalStateException("boom") }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `success returns value`() {
        val result = runCatchingCancellable { 42 }
        assertEquals(42, result.getOrNull())
    }
}
