package com.xiaoxiong.weather.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SelectionWriteGate] 的纯逻辑测试：
 * - check-then-act 必须串行，两个并发写入只有一个能成功；
 * - 写入失败不能被报告为 true（DataStore I/O 重试语义）；
 * - 失败后门闩仍可用。
 */
class SelectedCityStoreWriteGateTest {
    private fun selection(
        lat: Double = 36.65,
        lon: Double = 117.12,
        useLocation: Boolean = true,
    ) = SelectedCityStore.PersistedSelection(
        city = City(id = 1L, name = "测试市", latitude = lat, longitude = lon),
        useLocation = useLocation,
    )

    @Test
    fun concurrentSaveIfUnchanged_onlyFirstMatchingWriteWins() =
        runBlocking {
            val gate = SelectionWriteGate()
            val stateLock = Mutex()
            val expected = selection()
            var current: SelectedCityStore.PersistedSelection? = expected
            var writes = 0

            // 第一个写入后当前选择已偏离 expected，第二个必须被拒绝
            val first = selection(lat = 36.7, lon = 117.2)
            val second = selection(lat = 36.66, lon = 117.13)

            suspend fun save(next: SelectedCityStore.PersistedSelection) =
                gate.saveIfUnchanged(
                    expected = expected,
                    read = {
                        delay(10) // 放大 check-then-act 竞态窗口
                        stateLock.withLock { current }
                    },
                    write = {
                        stateLock.withLock {
                            current = next
                            writes++
                        }
                    },
                )

            val a = async { save(first) }
            val b = async { save(second) }
            val results = listOf(a.await(), b.await())

            assertEquals(1, results.count { it })
            assertEquals(1, writes)
        }

    @Test
    fun saveIfUnchanged_returnsFalseWhenCurrentDiffers() =
        runBlocking {
            val gate = SelectionWriteGate()
            var wrote = false
            val result =
                gate.saveIfUnchanged(
                    expected = selection(lat = 36.65, lon = 117.12),
                    read = { selection(lat = 40.0, lon = 116.0) },
                    write = { wrote = true },
                )
            assertFalse(result)
            assertFalse(wrote)
        }

    @Test
    fun writeFailure_propagatesAndNeverReportsSuccess() =
        runBlocking {
            val gate = SelectionWriteGate()
            var attempts = 0
            val error =
                runCatching {
                    gate.saveIfUnchanged(
                        expected = selection(),
                        read = { selection() },
                        write = {
                            attempts++
                            throw IllegalStateException("disk full")
                        },
                    )
                }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals(1, attempts)
            // 与「DataStore transform 重试后仍失败」不同：这里异常上抛，不会返回 true
        }

    @Test
    fun gateRemainsUsableAfterFailure() =
        runBlocking {
            val gate = SelectionWriteGate()
            var current: SelectedCityStore.PersistedSelection? = selection()
            runCatching {
                gate.saveIfUnchanged(
                    expected = selection(),
                    read = { current },
                    write = { throw IllegalStateException("boom") },
                )
            }
            val next = selection(lat = 36.7, lon = 117.2)
            val ok =
                gate.saveIfUnchanged(
                    expected = selection(),
                    read = { current },
                    write = { current = next },
                )
            assertTrue(ok)
            assertEquals(next, current)
        }

    @Test
    fun runSerializesWithSaveIfUnchanged() =
        runBlocking {
            val gate = SelectionWriteGate()
            var inWrite = false
            var observedOverlap = false
            val slowWrite =
                launch {
                    gate.saveIfUnchanged(
                        expected = selection(),
                        read = { selection() },
                        write = {
                            inWrite = true
                            delay(30)
                            inWrite = false
                        },
                    )
                }
            val direct =
                launch {
                    delay(10)
                    gate.run {
                        if (inWrite) observedOverlap = true
                    }
                }
            slowWrite.join()
            direct.join()
            assertFalse(observedOverlap)
        }
}
