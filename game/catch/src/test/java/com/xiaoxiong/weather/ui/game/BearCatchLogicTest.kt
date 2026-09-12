package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue("晴天金币+花瓣应超 420，实际 $sunnyGood", sunnyGood > 420)
        val rain = counts[ItemKind.RAINDROP.ordinal]
        assertTrue("晴天雨滴应偏少，实际 $rain", rain < 450)
        // 稀有道具也会偶发出现
        assertTrue(counts[ItemKind.GIFT.ordinal] > 0)
        assertTrue(counts[ItemKind.BUBBLE.ordinal] > 0)
    }

    @Test
    fun `snow scene is dominated by snowflakes`() {
        val rnd = Random(7)
        val good = (1..500).count { BearCatchLogic.itemKindFor(CatchScene.SNOW, rnd.nextFloat()) == ItemKind.SNOWFLAKE }
        assertTrue("雪天雪花应超 380，实际 $good", good > 380)
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
        assertEquals(20, BearCatchLogic.scoreOf(ItemKind.GIFT))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.BUBBLE))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.CLOUD))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.HAIL))
        assertEquals(0, BearCatchLogic.scoreOf(ItemKind.LIGHTNING))

        assertEquals(1, BearCatchLogic.damageOf(ItemKind.CLOUD))
        assertEquals(1, BearCatchLogic.damageOf(ItemKind.HAIL))
        assertEquals(2, BearCatchLogic.damageOf(ItemKind.LIGHTNING))
        assertEquals(0, BearCatchLogic.damageOf(ItemKind.COIN))

        assertEquals(1, BearCatchLogic.comboScoreMultiplier(0))
        assertEquals(1, BearCatchLogic.comboScoreMultiplier(1))
        assertEquals(2, BearCatchLogic.comboScoreMultiplier(2))
        assertEquals(5, BearCatchLogic.comboScoreMultiplier(5))
        assertEquals(5, BearCatchLogic.comboScoreMultiplier(9))
    }

    // ── 新道具：护盾 / 礼物盒 / 双倍 ─────────────────────────────

    @Test
    fun `gift box starts double score and bubble adds shield`() {
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()

        // 接住生日礼盒 → 双倍计时开始
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.GIFT,
                    x = game.catchX,
                    y = game.catchY,
                    r = 18f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        val caught = mutableListOf<ItemKind>()
        game.tick(dt = 0.001f, onCaught = { caught.add(it) }, onHit = {}, onDeath = {})
        assertEquals(listOf(ItemKind.GIFT), caught)
        assertEquals(20, game.score)
        assertTrue(game.doubleT > 0f)

        // 双倍期间接雨滴 → 双倍 × 连击(2) = 4 倍基础分
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
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(20 + 40, game.score)
        assertEquals(2, game.combo)

        // 接泡泡 → 获得一次性护盾，护盾抵挡一次惩罚不掉心，并进入冷却
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.BUBBLE,
                    x = game.catchX,
                    y = game.catchY,
                    r = 17f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertTrue(game.hasShield)
        assertEquals(0f, game.shieldCooldownT, 1e-4f)
        val heartsBefore = game.hearts

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
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals("护盾应抵挡本次伤害", heartsBefore, game.hearts)
        assertFalse("护盾消耗后应清空", game.hasShield)
        assertTrue("护盾消耗后应进入冷却", game.shieldCooldownT > 0f)
    }

    @Test
    fun `bubble does not stack during shield cooldown`() {
        // 一次性护盾：冷却中接泡泡不能叠加，避免反复堆盾把游戏变简单
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()
        // 手动标记已进入冷却且无护盾
        game.hasShield = false
        game.shieldCooldownT = 5f

        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.BUBBLE,
                    x = game.catchX,
                    y = game.catchY,
                    r = 17f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertFalse("冷却中接泡泡不应再给护盾", game.hasShield)
    }

    @Test
    fun `double time expires and score returns to normal`() {
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.GIFT,
                    x = game.catchX,
                    y = game.catchY,
                    r = 18f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(20, game.score)
        // 双倍到期后再接雨滴：连击若仍在则 ×combo，此处清空避免与双倍断言纠缠
        game.tick(dt = BearCatchLogic.DOUBLE_TIME_SEC + 0.1f, onCaught = {}, onHit = {}, onDeath = {})
        assertTrue(game.doubleT <= 0f)
        game.combo = 0
        game.items.clear()
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
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(30, game.score)
    }

    @Test
    fun `missed good item breaks combo`() {
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()
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
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(1, game.combo)
        // 漏接一个得分物（跑到屏幕下方外）→ 连击清零
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.RAINDROP,
                    x = 10f,
                    y = 850f,
                    r = 13f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(0, game.combo)
    }

    @Test
    fun `missed bubble neither counts as missed good nor breaks combo`() {
        // 引擎层：泡泡漏接不进入 missedGood
        val items = mutableListOf<BearCatchLogic.Item>()
        items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.BUBBLE,
                    x = 200f,
                    y = 850f,
                    r = 17f,
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
        assertTrue("泡泡漏接应被移除", items.isEmpty())
        assertEquals("泡泡不计入 missedGood", 0, ev.missedGood)

        // 容器层：漏接泡泡后连击保持不变
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        game.start()
        game.combo = 3
        game.items.add(
            BearCatchLogic
                .Item(
                    kind = ItemKind.BUBBLE,
                    x = 10f,
                    y = 850f,
                    r = 17f,
                    phase = 0f,
                ).also { it.vy = 0f },
        )
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals("漏接泡泡不应断连击", 3, game.combo)
    }

    // ── 天气机制：风暴波次 / 横风 ────────────────────────────────

    @Test
    fun `burst wave activates periodically in storm`() {
        assertTrue(BearCatchLogic.inBurstWave(0f))
        assertTrue(BearCatchLogic.inBurstWave(2f))
        assertFalse(BearCatchLogic.inBurstWave(10f))
        assertTrue("22s 后重新进入波次", BearCatchLogic.inBurstWave(22f))
    }

    @Test
    fun `wind drift grows with rain and score but zero on sunny`() {
        assertEquals(0f, BearCatchLogic.windDrift(CatchScene.SUNNY, 0, 1f))
        assertTrue(
            "暴雨横风应大于小雨",
            BearCatchLogic.windDrift(CatchScene.RAIN_HEAVY, 0, 1f) >
                BearCatchLogic.windDrift(CatchScene.RAIN_LIGHT, 0, 1f),
        )
        assertTrue(
            "雪天轻微横风",
            BearCatchLogic.windDrift(CatchScene.SNOW, 0, 1f) in 1f..60f,
        )
        assertTrue(
            "高分横风更明显",
            BearCatchLogic.windDrift(CatchScene.RAIN, 500, 1f) >
                BearCatchLogic.windDrift(CatchScene.RAIN, 0, 1f),
        )
    }

    // ── 难度曲线 ─────────────────────────────────────────────────

    @Test
    fun `spawn gap shrinks monotonically with score`() {
        var prev = Float.MAX_VALUE
        for (score in listOf(0, 50, 100, 150, 200, 300, 500)) {
            val gap = BearCatchLogic.spawnGapSeconds(score)
            assertTrue("score=$score gap=$gap 应小于等于上次 $prev", gap <= prev)
            prev = gap
        }
        assertTrue("gaps capped", BearCatchLogic.spawnGapSeconds(500) >= 0.25f)
        // 雷暴基础间隔比普通雨天更密
        assertTrue(
            "雷暴应更密",
            BearCatchLogic.spawnGapSeconds(0, CatchScene.STORM) <
                BearCatchLogic.spawnGapSeconds(0, CatchScene.RAIN),
        )
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
    fun `resize while over keeps score and hearts`() {
        val game = BearCatchGame()
        game.resize(
            androidx.compose.ui.unit
                .IntSize(400, 800),
        )
        game.start()
        // 接一个雨滴造分：+10
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
        game.tick(dt = 0.001f, onCaught = {}, onHit = {}, onDeath = {})
        assertEquals(10, game.score)

        // 用闪电（伤害 2）把仅剩 1 心打空，进入结算
        game.hearts = 1
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
        assertEquals(GamePhase.OVER, game.phase)
        assertEquals(0, game.hearts)

        // 结算时窗口变化（旋转/分屏）：只更新尺寸，分数与心数不被清零
        game.resize(
            androidx.compose.ui.unit
                .IntSize(800, 1600),
        )
        assertEquals(GamePhase.OVER, game.phase)
        assertEquals("结算时改窗口不应清零分数", 10, game.score)
        assertEquals("结算时改窗口不应重置心数", 0, game.hearts)
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

    @Test
    fun `multiple clouds same frame only deal one hit`() {
        // 一次 4 块乌云同时砸中：旧逻辑下第二块起会被无敌帧吞掉，
        // 看起来像「砸了 4 下只掉 1 颗心」，体验上像扣血被吞。
        // 修复后：同帧多个惩罚物只生效一次（最致命的那一个），且物品本身已从场景移除。
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()
        val hitCount = intArrayOf(0)
        repeat(4) {
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
        }
        game.tick(
            dt = 0.001f,
            onCaught = {},
            onHit = { hitCount[0]++ },
            onDeath = {},
        )
        assertEquals("同帧多块乌云只应触发一次伤害回调", 1, hitCount[0])
        assertEquals("一次掉 1 心", BearCatchGame.MAX_HEARTS - 1, game.hearts)
        assertEquals("4 块乌云全部从场景移除", 0, game.items.size)
    }

    @Test
    fun `lightning ignores shield and knocks two hearts directly`() {
        // 闪电是高致命品：有护盾也应直接扣血，避免「有盾就不怕闪电」的认知偏差。
        val game = BearCatchGame()
        game
            .resize(
                androidx.compose.ui.unit
                    .IntSize(400, 800),
            )
        game.start()
        game.hasShield = true
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
        assertEquals("闪电无视护盾直扣 2 心", 1, game.hearts)
        assertTrue("护盾不被消耗（闪电直接扣血）", game.hasShield)
    }
}
