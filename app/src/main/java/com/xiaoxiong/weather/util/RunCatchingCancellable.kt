package com.xiaoxiong.weather.util

import kotlinx.coroutines.CancellationException

/**
 * 与 [runCatching] 相同，但 [CancellationException] 原样抛出：
 * 协程取消不是业务失败，吞掉会让挂起的调用方变成「僵尸」或误走降级分支。
 * 用于包裹可能挂起的调用（DataStore 读写、定位、Glance 查询等）。
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
