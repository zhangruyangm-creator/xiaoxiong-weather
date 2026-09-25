package com.xiaoxiong.weather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天气缓存的新旧保护判定。
 *
 * 背景：ViewModel、Worker、Widget 三条路径会为同一城市并发取数。缓存原先用
 * **写入时刻**当 `savedAtEpochMs`，于是先发后至的旧数据会以"更新"的时间戳落盘并
 * 删掉同城新条目——冷启动/离线读到旧天气，却以为它是新鲜的。
 */
class WeatherCacheFreshnessGuardTest {
    @Test
    fun `older fetch arriving later is rejected`() {
        assertTrue(
            "先发后至的旧数据必须被拒绝，否则磁盘回退到旧天气",
            isStaleFetch(incomingFetchedAt = 1_000L, existingFetchedAt = 2_000L),
        )
    }

    @Test
    fun `newer fetch is accepted`() {
        assertFalse(isStaleFetch(incomingFetchedAt = 2_000L, existingFetchedAt = 1_000L))
    }

    @Test
    fun `same fetch time is accepted`() {
        assertFalse("同一时刻重复写不构成回退", isStaleFetch(1_000L, 1_000L))
    }

    @Test
    fun `no existing entry accepts anything`() {
        assertFalse(isStaleFetch(500L, null))
    }

    @Test
    fun `legacy entry without fetchedAt accepts write`() {
        assertFalse(
            "旧档 fetchedAt=0 不可比，不能把新数据挡在外面",
            isStaleFetch(incomingFetchedAt = 500L, existingFetchedAt = null),
        )
    }

    @Test
    fun `incoming zero means caller did not stamp and is always accepted`() {
        assertFalse(
            "未提供取数时刻时按旧行为放行，不能反过来拒掉正常写入",
            isStaleFetch(incomingFetchedAt = 0L, existingFetchedAt = 9_999L),
        )
    }
}
