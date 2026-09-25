package com.xiaoxiong.weather.util

// kotlinx.coroutines.CancellationException 就是 java.util.concurrent.CancellationException
// 的 typealias；用 JDK 类型避免让最底层的 core:common 反向依赖协程库。
import java.util.concurrent.CancellationException

/**
 * 不会吞掉协程取消的 [runCatching]。
 *
 * `runCatching` 捕获 `Throwable`，而 `CancellationException` 是 `Throwable` 的子类，
 * 于是「协程被取消」会被当成「业务失败」转成 `null` / `emptyList()`，调用方继续往下走：
 * - 页面切走或切城时，旧 effect 的取消被吞成 null，随后仍可能回写新页面的状态；
 * - 取消后还会继续做收尾工作（白跑一次解码、发起后续请求）。
 *
 * 这里把取消原样抛出，只捕获真正的业务异常。用于所有包住**挂起调用**的位置。
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
