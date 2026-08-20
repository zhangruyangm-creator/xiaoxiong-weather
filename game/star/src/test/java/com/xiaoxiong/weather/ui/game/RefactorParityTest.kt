package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.min

/**
 * 重构差分验证：用同一组输入，逐字节对比"纯逻辑函数输出"与"原始内联逻辑输出"，
 * 证明档位 B 引擎分离未引入数值/边界偏差。
 */
class StarRefactorParityTest {

    private val s = 1f

    @Test
    fun platformShape_matchesOriginalInline() {
        val w = 1000f
        val margin = 8f * s
        val maxTravel = (w - margin * 2f).coerceAtLeast(1f)
        // progress 只测 [0,1]，与原 genPlatforms 传入的已 coerce 值域一致
        val progressSamples = listOf(0f, 0.1f, 0.3f, 0.5f, 0.8f, 1f)
        val rollSamples = listOf(0.03f, 0.10f, 0.25f, 0.30f, 0.5f, 0.99f)
        for (progress in progressSamples) {
            for (roll in rollSamples) {
                for (early in listOf(true, false)) {
                    // 原内联
                    var opw = (150f - 26f * progress) * s
                    val otype =
                        when {
                            early -> PlatType.NORMAL
                            roll < 0.07f -> PlatType.SPRING
                            roll < 0.17f + 0.08f * progress -> PlatType.BREAK
                            roll < 0.32f + 0.18f * progress -> PlatType.MOVING
                            else -> PlatType.NORMAL
                        }
                    opw =
                        if (otype == PlatType.MOVING) min(opw, maxTravel * 0.55f).coerceAtLeast(48f * s)
                        else min(opw, maxTravel)
                    // 纯函数
                    val (type, pw) = StarJumpLogic.platformShape(progress, early, roll, w, s)
                    assertEquals("type@p=$progress,r=$roll,e=$early:", otype, type)
                    assertEquals("pw:", opw, pw, 1e-4f)
                }
            }
        }
    }

    @Test
    fun stepFalling_matchesOriginalInline() {
        val g = 2600f * s
        for (y in listOf(-500f, -100f, 0f, 100f)) {
            for (vy0 in listOf(-2350f, -1350f, 0f, 400f, 1200f)) {
                for (dt in listOf(0.016f, 0.033f)) {
                    val (ny, nvy) = StarJumpLogic.stepFalling(y, vy0, s, dt)
                    val ovy = vy0 + g * dt
                    val oy = y + ovy * dt
                    assertEquals("y:", oy, ny, 1e-4f)
                    assertEquals("vy:", ovy, nvy, 1e-4f)
                }
            }
        }
    }

    @Test
    fun scoreAndAltitude_matchOriginalInline() {
        for (mh in listOf(-1200f, -720f, -60f, 10f, 1000f)) {
            for (sc in listOf(0, 1, 4)) {
                val expected = ((-mh) / (60f * s)).toInt().coerceAtLeast(0) + sc * 25
                assertEquals(expected, StarJumpLogic.scoreFor(mh, sc, s))
            }
        }
        for (cam in listOf(0f, -2400f, -24000f, 100f)) {
            val expected = (-cam / (2400f * s)).coerceAtLeast(0f)
            assertEquals(expected, StarJumpLogic.altitudeKm(cam, s), 1e-4f)
        }
    }

    // ── resolveContact 与原 tick 踩台内联等价 ──────────────────

    private data class OriginalContact(
        val hit: Boolean,
        val platform: Platform?,
        val landY: Float,
        val bounceVY: Float,
    )

    private fun originalResolveContact(
        bearX: Float,
        prevFeetY: Float,
        newFeetY: Float,
        bearVY: Float,
        platforms: List<Platform>,
        w: Float,
        h: Float,
        s: Float,
        cameraY: Float,
    ): OriginalContact {
        val bs = 58f * s
        if (bearVY <= 0f) return OriginalContact(false, null, newFeetY - bs / 2f, 0f)
        for (p in platforms) {
            if (p.broken) continue
            if (p.y - cameraY > h + bs) continue
            if (prevFeetY <= p.y && newFeetY >= p.y) {
                var dx = bearX - (p.x + p.w / 2f)
                if (dx > w / 2f) dx -= w + bs
                if (dx < -w / 2f) dx += w + bs
                if (kotlin.math.abs(dx) < p.w / 2f + bs * 0.24f) {
                    val landY = p.y - bs / 2f
                    val vy = if (p.type == PlatType.SPRING) -2350f * s else -1350f * s
                    return OriginalContact(true, p, landY, vy)
                }
            }
        }
        return OriginalContact(false, null, newFeetY - bs / 2f, 0f)
    }

    @Test
    fun resolveContact_matchesOriginalInline() {
        val w = 1000f
        val h = 800f
        val cameraY = -600f
        val platforms =
            listOf(
                Platform(x = 100f, y = -300f, w = 120f, type = PlatType.NORMAL),
                Platform(x = 400f, y = -250f, w = 90f, type = PlatType.SPRING),
                Platform(x = 700f, y = -200f, w = 60f, type = PlatType.MOVING, broken = true),
                Platform(x = 200f, y = 400f, w = 100f, type = PlatType.NORMAL), // 死亡线下
            )
        for (bearX in listOf(0f, 150f, 400f, 660f, 900f)) {
            for (bearY in listOf(-330f, -280f, -240f, -180f, 500f)) {
                for (prevY in listOf(-400f, -330f, -280f)) {
                    for (vy in listOf(-300f, 0f, 300f, 800f)) {
                        val bs = StarJumpLogic.bearSize(s)
                        val got =
                            StarJumpLogic.resolveContact(
                                bearX, prevY + bs / 2f, bearY + bs / 2f, vy,
                                platforms, w, h, s, cameraY,
                            )
                        val exp = originalResolveContact(bearX, prevY + bs / 2f, bearY + bs / 2f, vy, platforms, w, h, s, cameraY)
                        assertEquals("hit@x=$bearX,y=$bearY,p=$prevY,vy=$vy:", exp.hit, got.hit)
                        assertEquals("platform:", exp.platform, got.platform)
                        assertEquals("landY:", exp.landY, got.landY, 1e-4f)
                        assertEquals("bounceVY:", exp.bounceVY, got.bounceVY, 1e-4f)
                    }
                }
            }
        }
    }
}
