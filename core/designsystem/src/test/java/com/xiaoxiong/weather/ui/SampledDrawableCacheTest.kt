package com.xiaoxiong.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SampledDrawableCacheTest {
    @Test
    fun sampleSize_isPowerOfTwo_andDecodedMaxDimNotAboveTarget() {
        val cases =
            listOf(
                Triple(1024, 1024, 256),
                Triple(1024, 1024, 512),
                Triple(1024, 1024, 1024),
                Triple(1024, 1536, 512),
                Triple(1024, 1536, 768),
                Triple(720, 1280, 1280),
            )
        for ((width, height, target) in cases) {
            val sample = sampledDrawableSampleSize(width, height, target)
            assertTrue("sample 必须是 2 的幂", sample > 0 && (sample and (sample - 1)) == 0)
            val maxDim = maxOf(width, height)
            assertTrue("$width×$height 采样 $sample 后仍超过 $target", maxDim / sample <= target)
            assertTrue("应该用更小的采样而不浪费分辨率", sample == 1 || maxDim / (sample / 2) > target)
        }
    }

    @Test
    fun sampleSize_expectedValues() {
        assertEquals(4, sampledDrawableSampleSize(1024, 1024, 256))
        assertEquals(2, sampledDrawableSampleSize(1024, 1024, 512))
        assertEquals(1, sampledDrawableSampleSize(1024, 1024, 1024))
        assertEquals(4, sampledDrawableSampleSize(1024, 1536, 512))
        assertEquals(2, sampledDrawableSampleSize(1024, 1536, 768))
        assertEquals(1, sampledDrawableSampleSize(720, 1280, 1280))
        assertEquals(1, sampledDrawableSampleSize(1024, 1024, 0))
    }

    @Test
    fun decodeLocks_sharedPerKey_independentAcrossKeys() {
        val a = SampledDrawableCache.decodeLockFor("12@256")
        val again = SampledDrawableCache.decodeLockFor("12@256")
        val other = SampledDrawableCache.decodeLockFor("12@512")
        assertSame(a, again)
        assertNotSame(a, other)
    }
}
