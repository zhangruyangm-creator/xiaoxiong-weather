package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 瞄准辅助分离线的纯几何测试：白球碰后沿切线方向 `v - (v·n)n` 分离，
 * 渲染层无 Compose 单测，这里直接验证抽出的方向函数。
 */
class PoolRenderGeometryTest {
    @Test
    fun cueSeparationDirection_followsCueTangentialVelocityTowardPositiveX() {
        // 目标在 +Y（n=(0,1)）；白球从左上入射（vx>0），碰后应向右（+X）分离
        val (sx, sy) =
            cueSeparationDirection(
                targetDirX = 0f,
                targetDirY = 1f,
                cueX = -0.1f,
                cueY = -0.3f,
                ghostX = 0f,
                ghostY = 0f,
            )
        assertTrue("碰后应向 +X 分离: sx=$sx", sx > 0f)
        assertEquals(0f, sy, 1e-6f)
    }

    @Test
    fun cueSeparationDirection_mirrorsWithIncomingSide() {
        // 白球从右上入射（vx<0），碰后应向左（-X）分离
        val (sx, sy) =
            cueSeparationDirection(
                targetDirX = 0f,
                targetDirY = 1f,
                cueX = 0.1f,
                cueY = -0.3f,
                ghostX = 0f,
                ghostY = 0f,
            )
        assertTrue("碰后应向 -X 分离: sx=$sx", sx < 0f)
        assertEquals(0f, sy, 1e-6f)
    }
}
