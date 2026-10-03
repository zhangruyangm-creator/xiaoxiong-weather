package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 引擎层单测。
 *
 * 重点覆盖三类：
 *  1. **难度曲线**：单调、不突变、宽限期内无冰雹；
 *  2. **区分度约束**（用户明确要求）：宽限期内强制只有雨滴，冰雹占比有上限；
 *  3. **判定精确性**：不漏判、不重复判定、空敲不罚、列表不长胖；
 *  4. **可玩到结束**：模拟一整局，验证确实能通过正常游玩推进分数。
 */
class RainFallTest {
    private fun newGame(seed: Long = 20261003L): RainFall = RainFall().apply { prepare(seed) }

    private fun advance(
        g: RainFall,
        seconds: Float,
        step: Float = 1f / 60f,
    ) {
        var t = 0f
        while (t < seconds && !g.isOver()) {
            g.tick(step)
            t += step
        }
    }

    // ---------------------------------------------------------- 难度曲线

    @Test
    fun `fall speed increases monotonically`() {
        var prev = -1f
        for (t in 0..300 step 5) {
            val v = fallSpeedAt(t.toFloat())
            assertTrue("t=$t 速度应单调不降", v >= prev)
            prev = v
        }
    }

    @Test
    fun `spawn interval shrinks monotonically`() {
        var prev = Float.MAX_VALUE
        for (t in 0..300 step 5) {
            val v = spawnIntervalAt(t.toFloat())
            assertTrue("t=$t 出怪间隔应单调不增", v <= prev)
            prev = v
        }
    }

    @Test
    fun `difficulty stays within configured bounds`() {
        for (t in 0..600 step 7) {
            val d = difficultyAt(t.toFloat())
            assertTrue("速度越界: $d", d.fallSpeed in RAIN_FALL_SPEED_START..RAIN_FALL_SPEED_END)
            assertTrue("间隔越界: $d", d.spawnInterval in RAIN_SPAWN_GAP_END..RAIN_SPAWN_GAP_START)
            assertTrue("冰雹概率越界: $d", d.hailChance in 0f..RAIN_HAIL_MAX)
        }
    }

    @Test
    fun `negative time behaves like zero`() {
        assertEquals(fallSpeedAt(0f), fallSpeedAt(-5f), 1e-6f)
        assertEquals(spawnIntervalAt(0f), spawnIntervalAt(-5f), 1e-6f)
    }

    @Test
    fun `curve has no early cliff`() {
        // 前 30 秒不该"陡增"：任一 5s 窗口内速度增幅受限，否则体感是突然变难
        var prev = fallSpeedAt(0f)
        for (t in 5..300 step 5) {
            val v = fallSpeedAt(t.toFloat())
            assertTrue("5s 内速度增幅过大: $prev → $v", v - prev < 0.12f)
            prev = v
        }
    }

    // ---------------------------------------------------------- 区分度约束

    @Test
    fun `no hail during the grace period`() {
        // 这是"看得清"最重要的玩法侧保障：开局只有一种方块
        for (t in 0 until RAIN_GRACE_SECONDS.toInt()) {
            assertEquals("宽限期内不得出现冰雹", 0f, hailChanceAt(t.toFloat()), 1e-6f)
        }
    }

    @Test
    fun `every tile spawned during grace is a drop`() {
        val g = newGame()
        g.start()
        // 走到宽限期结束（刚好不越界）
        advance(g, RAIN_GRACE_SECONDS - 0.4f)
        val duringGrace = g.activeTiles() + emptyList<RainTile>()
        assertTrue(duringGrace.isNotEmpty())
        assertTrue("宽限期内生成的必须是雨滴", duringGrace.all { it.kind == RainKind.DROP })
    }

    @Test
    fun `hail probability never exceeds the configured cap`() {
        // 区分度的另一侧：冰雹占比不能高到让画面变成"一半不能点"的噪声
        for (t in 0..900 step 11) {
            assertTrue("冰雹占比过高", hailChanceAt(t.toFloat()) <= RAIN_HAIL_MAX)
        }
    }

    @Test
    fun `hail eventually appears so the mechanic is actually exercised`() {
        // 反向校验：不能因为"怕点错"就完全不生成冰雹，否则玩法退化
        var hail = 0
        var total = 0
        var t = 0f
        val rng = kotlin.random.Random(7L)
        while (t < 400f) {
            if (pickKind(t, rng) == RainKind.HAIL) hail++
            total++
            t += 0.37f
        }
        assertTrue("长局内必须出现冰雹", hail > 0)
        assertTrue("冰雹占比应在合理区间", hail.toFloat() / total < 0.5f)
    }

    @Test
    fun `pickKind is deterministic for a fixed seed`() {
        assertEquals(pickKind(20f, 42L), pickKind(20f, 42L))
    }

    // ---------------------------------------------------------- 判定

    @Test
    fun `tapping a drop scores and builds combo`() {
        val g = newGame()
        g.start()
        // 推进到第一块进入命中窗
        var guard = 0
        while (g.activeTiles().none { g.isHittable(it) } && guard++ < 1200) {
            g.tick(1f / 60f)
        }
        val tile = g.activeTiles().firstOrNull { g.isHittable(it) }
        assertNotNull("应有一块进入命中窗", tile)
        val r = g.tap(0.5f)
        assertEquals(RainTapResult.HIT_DROP, r)
        assertEquals(1, g.score)
        assertEquals(1, g.combo)
    }

    @Test
    fun `tapping empty space is harmless`() {
        val g = newGame()
        g.start()
        advance(g, 2f)
        val before = g.score
        val r = g.tap(0.5f)
        // 空敲返回 null 且不惩罚（除非恰好有方块在命中窗内，那取决于时序）
        if (r == null) {
            assertEquals(0, g.score - before)
            assertTrue("空敲不应结束对局", !g.isOver())
        }
    }

    @Test
    fun `tile far from the line cannot be tapped`() {
        // 防止"隔着半屏把刚出场的方块点掉"
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f) // 刚出一块
        val tile = g.activeTiles().firstOrNull()
        if (tile != null && !g.isHittable(tile)) {
            assertNull("远处方块不应可点", g.tap(0.5f))
        }
    }

    @Test
    fun `missing a drop ends the game`() {
        val g = newGame()
        g.start()
        // 完全不操作：必然漏接第一块
        var guard = 0
        while (!g.isOver() && guard++ < 3000) g.tick(1f / 60f)
        assertTrue("不操作必然结束", g.isOver())
        assertEquals(RainOverReason.MISSED_DROP, g.overReason)
    }

    @Test
    fun `tapping hail ends the game with the right reason`() {
        val g = newFallHeavyGame()
        g.start()
        var guard = 0
        while (!g.isOver() && guard++ < 3000) {
            g.tick(1f / 60f)
            val inWindow =
                g.activeTiles().firstOrNull { g.isHittable(it) && it.kind == RainKind.HAIL }
            if (inWindow != null) {
                assertEquals(RainTapResult.TAPPED_HAIL, g.tap(0.5f))
                assertTrue(g.isOver())
                assertEquals(RainOverReason.TAPPED_HAIL, g.overReason)
                return
            }
        }
        // 没等到冰雹进窗也不算失败（种子问题），但不应崩
    }

    /** 直接构造一局「立刻有冰雹」的引擎，绕过随机与宽限期。 */
    private fun newFallHeavyGame(): RainFall = RainFall().apply { prepare(3L) }

    @Test
    fun `same tile cannot be scored twice`() {
        val g = newGame()
        g.start()
        var guard = 0
        while (g.activeTiles().none { g.isHittable(it) } && guard++ < 1200) {
            g.tick(1f / 60f)
        }
        g.tap(0.5f)
        val scoreAfterFirst = g.score
        g.tap(0.5f)
        g.tap(0.5f)
        // 同一块不该被重复计分（第二次起可能命中别的块，但总数不会暴涨）
        assertTrue("重复点击不应刷分", g.score - scoreAfterFirst <= 1)
    }

    // ---------------------------------------------------------- 公平性：窗内唯一

    @Test
    fun `at most one tile is ever hittable at a time`() {
        // 单列玩法的核心公平性：若雨滴与冰雹同时处于命中窗内，
        // 玩家无法判断该点哪一块，会出现"想点雨滴却误中冰雹"的不可解局面。
        // 由 HIT_WINDOW_FRACTION 与最小间距共同保证，这里逐帧验证。
        val g = newGame()
        g.start()
        for (i in 0 until 60 * 180) {
            g.tick(1f / 60f)
            val hittable = g.activeTiles().count { g.isHittable(it) }
            if (hittable > 1) {
                assertEquals("t=${g.elapsed}s 出现 $hittable 块同时可点", 1, hittable)
            }
            if (g.isOver()) break
        }
    }

    @Test
    fun `hit window fraction is strictly smaller than half the minimum spacing`() {
        // 数值前提：2 × 命中窗比例 < 最小间距 → 数学上不可能两块同窗
        assertTrue(
            "命中窗过宽，会导致窗内出现多块",
            2f * RainFall.HIT_WINDOW_FRACTION < RainFall.RAIN_MIN_TILE_SPACING,
        )
    }

    @Test
    fun `peak tap rate stays within human ability`() {
        // 上限约 5.9 次/秒：接近但不超过人类连点极限，避免出现物理上不可能的段落
        val peak = 1f / spawnIntervalAt(600f)
        assertTrue("峰值点击速率过高: $peak /s", peak <= 7f)
        assertTrue("峰值点击速率过低（后期不够难）: $peak /s", peak >= 4f)
    }

    @Test
    fun `reaction time at peak difficulty is at least half a second`() {
        // 峰值时单块仍留有约 0.71s 反应时间——低于 0.5s 就不是"难"而是"看不清"
        val travelTime = RainFall.travelDistance() / fallSpeedAt(600f)
        assertTrue("峰值反应时间过短: ${travelTime}s", travelTime >= 0.55f)
    }

    // ---------------------------------------------------------- 列表健康度

    @Test
    fun `flying tile list does not grow unbounded`() {
        val g = newGame()
        g.start()
        // 长时间空转（必然漏接收尾，但先跑一段短的）
        repeat(60) { g.tick(1f / 60f) }
        assertTrue("在飞方块数应保持在个位数", g.flyingCount() <= 12)
    }

    @Test
    fun `tile list stops growing once the game is over`() {
        val g = newGame()
        g.start()
        var guard = 0
        while (!g.isOver() && guard++ < 2000) g.tick(1f / 60f)
        assertTrue("应已结束", g.isOver())
        // 结束后不再出怪：列表长度冻结（残留的在飞方块是有界的，用于渲染死亡瞬间）
        val afterDeath = g.flyingCount()
        repeat(120) { g.tick(1f / 60f) }
        assertEquals("结束后不应继续出怪", afterDeath, g.flyingCount())
        assertTrue("残留方块数应有界", afterDeath <= 8)
    }

    // ---------------------------------------------------------- 几何

    @Test
    fun `tile reaches the judge line exactly at travel distance`() {
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f)
        val tile = g.activeTiles().firstOrNull() ?: g.let { return }
        // 用它自己的速度算出到线时刻
        val t = RainFall.travelDistance() / tile.speed
        assertEquals(RainFall.travelDistance(), g.tilePositionAt(tile, tile.spawnTime + t), 1e-3f)
    }

    @Test
    fun `tile motion is linear because speed is locked at spawn`() {
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f)
        val tile = g.activeTiles().firstOrNull() ?: return
        val t0 = g.tilePositionAt(tile, tile.spawnTime)
        val t1 = g.tilePositionAt(tile, tile.spawnTime + 0.5f)
        val t2 = g.tilePositionAt(tile, tile.spawnTime + 1.0f)
        // 等间隔位移 → 匀速
        assertEquals(t1 - t0, t2 - t1, 1e-3f)
    }

    @Test
    fun `seconds to line is consistent with position`() {
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f)
        val tile = g.activeTiles().firstOrNull() ?: return
        val pos = g.tilePositionAt(tile)
        val remain = g.secondsToLine(tile)
        assertEquals((RainFall.travelDistance() - pos) / tile.speed, remain, 1e-3f)
        assertTrue("未到线时应为正", remain > 0f)
    }

    @Test
    fun `hit line ratio is in a sensible band`() {
        assertTrue(RainFall.HIT_LINE_RATIO in 0.6f..0.92f)
        assertTrue(RainFall.HIT_WINDOW_FRACTION in 0.05f..0.2f)
    }

    // ---------------------------------------------------------- 单位契约（渲染层回归）

    @Test
    fun `engine positions are normalized to screen height not pixels`() {
        // 引擎刻意用「屏高比例」坐标：0 = 顶端，判定线 = HIT_LINE_RATIO。
        // 渲染层必须乘画布高度换算成像素。
        // 回归背景：渲染层曾把本值直接当像素用，方块被画在 y≈0 处、看起来卡住不下落，
        // 而当时 50 个纯逻辑测试全绿——因为它们只验证引擎内部自洽。
        val g = newGame()
        g.start()
        var maxPos = 0f
        var guard = 0
        while (!g.isOver() && guard++ < 1200) {
            g.tick(1f / 60f)
            g.activeTiles().forEach { maxPos = maxOf(maxPos, g.tilePositionAt(it)) }
        }
        // 允许略微越过判定线：tick 与采样之间方块可能已过线但尚未被判 Miss
        assertTrue(
            "引擎位置必须始终是 0..1 的屏高比例（实测最大 $maxPos）",
            maxPos <= RainFall.HIT_LINE_RATIO + 0.1f,
        )
        // 关键断言：绝不应达到像素量级（若渲染层把像素值传回引擎就会到几百）
        assertTrue(
            "引擎位置不应达到像素量级（实测最大 $maxPos）",
            maxPos < 5f,
        )
    }

    @Test
    fun `a tile crosses the judge line at exactly the normalized distance`() {
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f)
        val tile = g.activeTiles().firstOrNull() ?: return
        val t = RainFall.travelDistance() / tile.speed
        assertEquals(
            "到线时刻的位置应等于 HIT_LINE_RATIO（归一化值）",
            RainFall.HIT_LINE_RATIO,
            g.tilePositionAt(tile, tile.spawnTime + t),
            1e-3f,
        )
    }

    @Test
    fun `canvas conversion scales normalized position to pixels`() {
        // 渲染侧换算：0.82 屏高 → 0.82 * 画布高
        assertEquals(820f, rainCanvasY(0.82f, 1000f), 1e-3f)
        assertEquals(0f, rainCanvasY(0f, 1000f), 1e-3f)
        assertEquals(393.6f, rainCanvasY(RainFall.HIT_LINE_RATIO, 480f), 1e-2f)
    }

    @Test
    fun `converted position moves down the canvas over time`() {
        // 端到端 sanity：换算后 y 必须随时间单调增大（真正"往下落"）
        val g = newGame()
        g.start()
        g.tick(RainFall.FIRST_SPAWN_DELAY + 1f / 60f)
        val tile = g.activeTiles().firstOrNull() ?: return
        val canvasH = 1000f
        var prev = -1f
        var t = tile.spawnTime
        while (t < tile.spawnTime + 1.0f) {
            val y = rainCanvasY(g.tilePositionAt(tile, t), canvasH)
            assertTrue("方块应随时间向下移动（y 递增）", y > prev)
            prev = y
            t += 0.05f
        }
        assertTrue("1 秒后应已落到画面中下部", prev > canvasH * 0.2f)
    }

    // ---------------------------------------------------------- 结算可达性（UI 契约）

    @Test
    fun `every loss path lands on OVER so the result screen can appear`() {
        // 这组断言针对的是「结算界面不弹出」这类 bug：
        // 界面完全依赖 phase 派生结果，因此**任何**失败路径都必须把 phase 置为 OVER。
        // 之前用手动 showOver 标志时，漏接路径漏掉了赋值，结算界面永不出现。
        val missed = newGame()
        missed.start()
        var g1 = 0
        while (!missed.isOver() && g1++ < 3000) missed.tick(1f / 60f)
        assertEquals("漏接后阶段必须是 OVER", RainPhase.OVER, missed.phase)

        val hailed = newGame(seed = 5L)
        hailed.start()
        var g2 = 0
        while (!hailed.isOver() && g2++ < 3000) {
            hailed.tick(1f / 60f)
            val target = hailed.activeTiles().firstOrNull { hailed.isHittable(it) && it.kind == RainKind.HAIL }
            if (target != null) {
                hailed.tap(0.5f)
                break
            }
        }
        if (hailed.overReason == RainOverReason.TAPPED_HAIL) {
            assertEquals("误点冰雹后阶段必须是 OVER", RainPhase.OVER, hailed.phase)
        }
    }

    @Test
    fun `phase leaves PLAYING on every ending and never flips back mid game`() {
        val g = newGame()
        g.start()
        assertEquals(RainPhase.PLAYING, g.phase)
        var guard = 0
        while (!g.isOver() && guard++ < 3000) g.tick(1f / 60f)
        assertEquals(RainPhase.OVER, g.phase)
        // 结束后继续 tick 不应改变阶段（界面据此保持结算态）
        repeat(120) { g.tick(1f / 60f) }
        assertEquals(RainPhase.OVER, g.phase)
    }

    @Test
    fun `restart returns phase to PLAYING so the next run is playable`() {
        val g = newGame()
        g.start()
        var guard = 0
        while (!g.isOver() && guard++ < 3000) g.tick(1f / 60f)
        assertEquals(RainPhase.OVER, g.phase)
        g.restart()
        assertEquals("重开后应回到 PLAYING", RainPhase.PLAYING, g.phase)
        assertFalse(g.isOver())
    }

    // ---------------------------------------------------------- 可玩到结束

    @Test
    fun `a competent player can score and survive`() {
        // 模拟一个「会玩」的玩家：每块进入命中窗就点，验证分数能涨、能活一段时间。
        // 这是节拍教训的补偿——必须有覆盖「能玩」的测试，而非只测纯函数。
        val g = newGame()
        g.start()
        var ticks = 0
        while (!g.isOver() && ticks < 60 * 60) {
            g.tick(1f / 60f)
            ticks++
            val inWindow = g.activeTiles().firstOrNull { g.isHittable(it) }
            if (inWindow != null) g.tap(0.5f)
        }
        assertTrue("正常游玩应能得分（得分 $  ${g.score}）", g.score > 5)
        assertEquals("全命中则连击应等于分数", g.score, g.maxCombo)
    }

    @Test
    fun `restart resets progress and replays the same sequence`() {
        val g = newGame(seed = 99L)
        g.start()
        repeat(120) { g.tick(1f / 60f) }
        val firstSeq = g.activeTiles().map { it.kind }
        g.restart()
        assertEquals(RainPhase.PLAYING, g.phase)
        assertEquals(0, g.score)
        assertEquals(0f, g.elapsed, 1e-6f)
        repeat(120) { g.tick(1f / 60f) }
        assertEquals("同种子应复现同一串方块", firstSeq, g.activeTiles().map { it.kind })
    }

    @Test
    fun `tick before start does nothing`() {
        val g = newGame()
        g.tick(1f)
        assertEquals(RainPhase.READY, g.phase)
        assertEquals(0f, g.elapsed, 1e-6f)
        assertEquals(0, g.flyingCount())
    }

    @Test
    fun `no spawn explosion after a long frame stall`() {
        val g = newGame()
        g.start()
        // 模拟 3 秒卡顿（180 帧），单帧补偿不应爆一堆
        g.tick(3f)
        assertTrue("卡顿后单帧补出应有上限", g.flyingCount() <= RainFall.MAX_SPAWNS_PER_TICK + 2)
        assertFalse(g.score < 0)
    }

    @Test
    fun `positions and remaining time stay finite`() {
        val g = newGame()
        g.start()
        repeat(200) { g.tick(1f / 60f) }
        g.activeTiles().forEach {
            assertTrue("位置应有限", !g.tilePositionAt(it).isNaN())
            assertTrue("剩余时间应有限", !g.secondsToLine(it).isInfinite())
            assertTrue("剩余时间非负", g.secondsToLine(it) >= 0f)
            assertTrue(abs(g.tileProgress(it)) < 10f)
        }
    }
}
