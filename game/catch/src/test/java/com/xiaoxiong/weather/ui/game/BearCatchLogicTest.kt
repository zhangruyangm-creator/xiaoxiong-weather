package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class BearCatchLogicTest {
    // ── 分布决策 ────────────────────────────────────────────────

    @Test
    fun `sunny scene leans coins and petals over rain`() {
        val counts = IntArray(ItemKind.entries.size)
        val rnd = Random(42)
        repeat(1000) {
            val kind = BearCatchLogic.itemKindFor(CatchScene.SUNNY, rnd.nextFloat())
            counts[kind.ordinal]++
        }
        // 晴天以金币/花瓣为主力得分物
        val sunnyGood = counts[ItemKind.COIN.ordinal] + counts[ItemKind.PETAL.ordinal]
        assertTrue("晴天金币+花瓣应超 500，实际 $sunnyGood", sunnyGood > 500)
        val rain = counts[ItemKind.RAINDROP.ordinal]
        assertTrue("晴天雨滴应偏少，实际 $rain", rain < 400)
    }

    @Test
    fun `snow scene is dominated by snowflakes`() {
        val rnd = Random(7)
        val good = (1..500).count { BearCatchLogic.itemKindFor(CatchScene.SNOW, rnd.nextFloat()) == ItemKind.SNOWFLAKE }
        assertTrue("雪天雪花应超 400，实际 $good", good > 400)
    }

    @Test
    fun `rain scenes rarely spawn lightning`() {
        val rnd = Random(13)
        val storm =
            (1..500).map { BearCatchLogic.itemKindFor(CatchScene.STORM, rnd.nextFloat()) }
        assertTrue(storm.any { it == ItemKind.LIGHTNING })
        assertTrue(storm.any { it == ItemKind.CLOUD })

        val rnd2 = Random(29)
        val rain =
            (1..500).map { BearCatchLogic.itemKindFor(CatchScene.RAIN, rnd2.nextFloat()) }
        assertTrue("普通雨天不应出现闪电", rain.none { it == ItemKind.LIGHTNING })
    }

    // ── 分值 / 扣血 ──────────────────────────────────────────────

    @Test
    fun `scoring and damage table`() {
        assertEquals(25, BearCatchLogic.scoreOf(ItemKind.COIN))
        assertEquals(10, BearCatchLogic.scoreOf(ItemKind.RAINDROP))
        assertEquals(10, BearCatchLogic.scoreOf(ItemKind.SNOWFLAKE))
        assertEquals(8, BearCatchLogic.scoreOf(ItemKind.PETAL))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.CLOUD))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.HAIL))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.LIGHTNING))

        assertEquals(1, BearCatchLogic.damageOf(ItemKind.CLOUD))
        assertEquals(1, BearCatchLogic.damageOf(ItemKind.HAIL))
        assertEquals(2, BearCatchLogic.damageOf(ItemKind.LIGHTNING))
        assertEquals(0, BearCatchLogic.damageOf(ItemKind.COIN))
    }

    // ── 难度曲线 ─────────────────────────────────────────────────

    @Test
    fun `spawn gap shrinks monotonically with score`() {
        var prev = Float.MAX_VALUE
        for (score in listOf(0, 50, 100, 150, 200, 300)) {
            val gap = BearCatchLogic.spawnGapSeconds(score)
            assertTrue("score=$score gap=$gap 应小于等于上次 $prev", gap <= prev)
            prev = gap
        }
        assertTrue("gaps capped", BearCatchLogic.spawnGapSeconds(300) >= 0.36f)
    }

    @Test
    fun `fall speed increases with score and snow is slowest`() {
        assertTrue(
            "雪天应最慢",
            BearCatchLogic.fallSpeed(CatchScene.SNOW, 0, 1f) <
                BearCatchLogic.fallSpeed(CatchScene.RAIN, 0, 1f),
        )
        assertTrue(
            "雨天应比小雨快",
            BearCatchLogic.fallSpeed(CatchScene.RAIN, 0, 1f) >
                BearCatchLogic.fallSpeed(CatchScene.RAIN_LIGHT, 0, 1f),
        )
        assertTrue(
            "冰雹应最快",
            BearCatchLogic.fallSpeed(CatchScene.HAIL, 0, 1f) >
                BearCatchLogic.fallSpeed(CatchScene.RAIN_HEAVY, 0, 1f),
        )
        assertTrue(
            "高分更快",
            BearCatchLogic.fallSpeed(CatchScene.RAIN, 300, 1f) >
                BearCatchLogic.fallSpeed(CatchScene.RAIN, 0, 1f),
        )
    }

    // ── 世界推进：生成 / 移动 / 碰撞 / 清理 ───────────────────────

    @Test
    fun `step spawns one item per gap over time`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        var accum = 0f
        var totalGenerated = 0
        // 跑 10 秒，dt=1s，雪天生成间隔约 0.85s → 累计约生成 10 个
        for (i in 0 until 10) {
            val ev =
                BearCatchLogic.stepCatchWorld(
                    items = items,
                    score = 0,
                    scene = CatchScene.SNOW,
                    s = 1f,
                    dt = 1f,
                    width = 400f,
                    height = 800f,
                    catchX = 200f,
                    catchY = 720f,
                    catchR = 40f,
                    spawnAccum = accum,
                    random = Random((i * 7 + 1).toLong()),
                )
            accum = ev.spawnAccum
            totalGenerated += ev.spawnedCount
        }
        assertTrue("10s 应累计生成至少 8 个，实际 $totalGenerated", totalGenerated >= 8)
    }

    @Test
    fun `catch removes item and reports it`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        // 手动放一个正好在小熊上方的雨滴
        items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.RAINDROP,
                    x = 200f,
                    y = 800f * 0.9f,
                    r = 13f,
                    phase = 0f,
                ).also { it.vy = 100f },
        )
        val ev =
            BearCatchLogic.stepCatchWorld(
                items = items,
                score = 0,
                scene = CatchScene.RAIN,
                s = 1f,
                dt = 0.01f,
                width = 400f,
                height = 800f,
                catchX = 200f,
                catchY = 800f * 0.9f,
                catchR = 40f,
                spawnAccum = 0f,
                random = Random(1),
            )
        assertTrue(items.isEmpty())
        assertEquals(listOf(ItemKind.RAINDROP), ev.caught)
    }

    @Test
    fun `penalty item hit reports hit kind`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.CLOUD,
                    x = 200f,
                    y = 800f * 0.9f,
                    r = 30f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        val ev =
            BearCatchLogic.stepCatchWorld(
                items = items,
                score = 0,
                scene = CatchScene.RAIN,
                s = 1f,
                dt = 0.001f,
                width = 400f,
                height = 800f,
                catchX = 200f,
                catchY = 800f * 0.9f,
                catchR = 40f,
                spawnAccum = 0f,
                random = Random(1),
            )
        assertTrue(items.isEmpty())
        assertEquals(listOf(ItemKind.CLOUD), ev.hit)
        assertTrue(ev.caught.isEmpty())
    }

    @Test
    fun `items falling below screen are removed and good missed counted`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.RAINDROP,
                    x = 200f,
                    y = 850f,
                    r = 13f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        val ev =
            BearCatchLogic.stepCatchWorld(
                items = items,
                score = 0,
                scene = CatchScene.RAIN,
                s = 1f,
                dt = 0.001f,
                width = 400f,
                height = 800f,
                catchX = 200f,
                catchY = 800f * 0.9f,
                catchR = 40f,
                spawnAccum = 0f,
                random = Random(1),
            )
        assertTrue("出界应被移除", items.isEmpty())
        assertEquals(1, ev.missedGood)
    }

    @Test
    fun `spawn respects MAX_ITEMS cap`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        repeat(BearCatchLogic.MAX_ITEMS) {
            items.add(BearCatchLogic.Item(ItemKind.COIN, x = 10f, y = 10f, r = 1f, phase = 0f).also { it.vy = 0f })
        }
        // 已满再推进不会多生成
        val ev =
            BearCatchLogic.stepCatchWorld(
                items = items,
                score = 0,
                scene = CatchScene.SUNNY,
                s = 1f,
                dt = 10f,
                width = 400f,
                height = 800f,
                catchX = 200f,
                catchY = 720f,
                catchR = 40f,
                spawnAccum = 0f,
                random = Random(3),
            )
        assertEquals(BearCatchLogic.MAX_ITEMS, items.size)
        // 因为满则跳过生成，accum 会累积到下一次
        assertTrue(ev.spawnAccum > 9f)
    }

    @Test
    fun `spawn accum resets after spawn and continues counting`() {
        val items = mutableListOf<BearCatchLogic.Item>()
        // 一开始积累了超过间隔的量
        var accum = BearCatchLogic.spawnGapSeconds(0) + 0.01f
        val ev =
            BearCatchLogic.stepCatchWorld(
                items = items,
                score = 0,
                scene = CatchScene.RAIN,
                s = 1f,
                dt = 0.01f,
                width = 400f,
                height = 800f,
                catchX = 200f,
                catchY = 720f,
                catchR = 40f,
                spawnAccum = accum,
                random = Random(4),
            )
        // 生成了 1 个：accum -= gap，剩 spawnAccum+dt-gap = 0.01+0.01 = 0.02
        assertEquals(1, items.size)
        assertEquals(0.02f, ev.spawnAccum, 1e-4f)
        assertEquals(1, ev.spawnedCount)
    }

    @Test
    fun `game container lifecycle transitions`() {
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        assertEquals(GamePhase.READY, game.phase)
        assertEquals(BearCatchGame.MAX_HEARTS, game.hearts)
        game.start()
        assertEquals(GamePhase.RUNNING, game.phase)
        // 快速过一帧
        game.tick(
            dt = 0.01f,
            onCaught = {},
            onHit = {},
            onDeath = {},
        )
        assertEquals(GamePhase.RUNNING, game.phase)
    }

    @Test
    fun `catching a good item triggers squash and burst particles`() {
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        game.start()
        // 手动塞一个正好落进接住半径的雨滴
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.RAINDROP,
                    x = game.catchX,
                    y = game.catchY,
                    r = 13f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        val caught = mutableListOf<ItemKind>()
        game.tick(
            dt = 0.001f,
            onCaught = { caught.add(it) },
            onHit = {},
            onDeath = {},
        )
        assertEquals(listOf(ItemKind.RAINDROP), caught)
        assertEquals(10, game.score)
        assertTrue("接住应触发挤压", game.squashT in 0f..0.16f)
        assertTrue("接住应爆粒子", game.particles.isNotEmpty())
    }

    @Test
    fun `hitting a storm cloud shakes and loses heart`() {
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        game.start()
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.CLOUD,
                    x = game.catchX,
                    y = game.catchY,
                    r = 30f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        val hits = mutableListOf<ItemKind>()
        game.tick(
            dt = 0.001f,
            onCaught = {},
            onHit = { hits.add(it) },
            onDeath = {},
        )
        assertEquals(listOf(ItemKind.CLOUD), hits)
        assertEquals(BearCatchGame.MAX_HEARTS - 1, game.hearts)
        assertTrue("命中应触发屏幕震动", game.shakeT > 0f)
        assertEquals(GamePhase.RUNNING, game.phase)
    }

    @Test
    fun `two lightning hits end the game`() {
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        game.start()
        var deathScore = -1
        // 闪电伤害 2，MAX_HEARTS=3：第 2 击必死（中间需过无敌时间）
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.LIGHTNING,
                    x = game.catchX,
                    y = game.catchY,
                    r = 28f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(1, game.hearts)

        // 受击后 0.75s 无敌，需等待过期再受击
        game.tick(dt = 0.8f, onCaught = {}, onHit = {}, onDeath = {})

        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.LIGHTNING,
                    x = game.catchX,
                    y = game.catchY,
                    r = 28f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(
            dt = 0.001f,
            onCaught = {},
            onHit = {},
            onDeath = { deathScore = it },
        )
        assertEquals(GamePhase.OVER, game.phase)
        assertEquals(0, game.hearts)
        assertTrue(deathScore >= 0)
    }
}
