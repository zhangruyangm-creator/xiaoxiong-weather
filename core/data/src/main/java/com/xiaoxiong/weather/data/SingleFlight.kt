package com.xiaoxiong.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同 key 并发的 single-flight：首个调用执行 [run] 的 block，其余等待同一结果；
 * block 失败/取消会原样传递给所有等待者，并从 in-flight 表移除以便下次重试。
 * 纯逻辑、无调度器依赖，便于单测。
 *
 * 注意：owner 协程被取消时会 [CompletableDeferred.cancel] 等待者。
 * 若调用方希望「取消自己不影响共享请求」，请在 block 内用
 * `withContext(NonCancellable)` 包住实际工作，并在 complete 之后再让取消冒泡。
 */
internal class SingleFlight<K, V> {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(
        key: K,
        block: suspend () -> V,
    ): V {
        var owner = false
        val deferred =
            lock.withLock {
                inFlight[key]
                    ?: CompletableDeferred<V>().also {
                        inFlight[key] = it
                        owner = true
                    }
            }
        if (!owner) return deferred.await()
        try {
            val value = block()
            deferred.complete(value)
            return value
        } catch (e: CancellationException) {
            deferred.cancel(e)
            throw e
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            lock.withLock {
                if (inFlight[key] === deferred) inFlight.remove(key)
            }
        }
    }
}
