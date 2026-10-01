package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchDifficulty
import com.xiaoxiong.weather.ui.game.BearCatchLogic.CatchScene
import com.xiaoxiong.weather.ui.game.BearCatchLogic.ItemKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/**
 * 确定性机器人模拟：用简单「追踪好物、躲开危险」策略跑固定时长，
 * 断言高难档位的压力指标（危险物生成数 / 同屏危险峰值）高于休闲档。
 *
 * 指标可复现（固定 seed、固定步长、固定场景），不对存活时长做易碎断言；
 * 危险物生成数由「每步新增物品里非好物的数量」统计，不依赖引擎返回值扩展。
 */
class CatchBotSimulationTest {
    @Test
    fun `challenge tier spawns more danger than casual in deterministic bot run`() {
        val casual = runBot(CatchDifficulty.CASUAL)
        val standard = runBot(CatchDifficulty.STANDARD)
        val challenge = runBot(CatchDifficulty.CHALLENGE)
        assertTrue(
            "CHALLENGE 危险生成应多于 CASUAL（casual=${casual.dangerSpawned}, challenge=${challenge.dangerSpawned}）",
            challenge.dangerSpawned > casual.dangerSpawned,
        )
        assertTrue(
            "STANDARD 危险生成应多于 CASUAL（casual=${casual.dangerSpawned}, standard=${standard.dangerSpawned}）",
            standard.dangerSpawned > casual.dangerSpawned,
        )
        assertTrue(
            "CHALLENGE 同屏危险峰值应不低于 CASUAL（casual=${casual.dangerPeak}, challenge=${challenge.dangerPeak}）",
            challenge.dangerPeak >= casual.dangerPeak,
        )
        assertTrue("机器人应至少接住一些好物（casual=${casual.goodCaught}）", casual.goodCaught > 0)
        assertTrue("机器人应累计到一定分数（casual=${casual.finalScore}）", casual.finalScore > 0)
    }

    /** 模拟结果：危险生成数 / 同屏危险峰值 / 接住好物 / 受伤数 / 最终分数。 */
    private data class BotReport(
        val dangerSpawned: Int,
        val dangerPeak: Int,
        val goodCaught: Int,
        val hits: Int,
        val finalScore: Int,
    )

    private fun runBot(
        difficulty: CatchDifficulty,
        seed: Long = 20240913L,
        seconds: Float = 120f,
    ): BotReport {
        val rng = Random(seed)
        val items = mutableListOf<BearCatchLogic.Item>()
        val width = 400f
        val height = 800f
        val catchR = height * 0.085f
        val catchY = height * 0.90f
        val dt = 1f / 60f
        var catchX = width / 2f
        var accum = 0f
        var score = 0
        var time = 0f
        var dangerSpawned = 0
        var dangerPeak = 0
        var goodCaught = 0
        var hits = 0
        val steps = (seconds / dt).toInt()
        repeat(steps) {
            catchX = steer(items, catchX, catchY, catchR, width, dt)
            val ev =
                BearCatchLogic.stepCatchWorld(
                    items = items,
                    score = score,
                    scene = CatchScene.RAIN,
                    s = 1f,
                    dt = dt,
                    width = width,
                    height = height,
                    catchX = catchX,
                    catchY = catchY,
                    catchR = catchR,
                    spawnAccum = accum,
                    random = rng,
                    time = time,
                    difficulty = difficulty,
                )
            accum = ev.spawnAccum
            if (ev.spawnedCount > 0) {
                // 新生成物品追加在列表尾部
                val from = (items.size - ev.spawnedCount).coerceAtLeast(0)
                for (i in from until items.size) {
                    if (!BearCatchLogic.isGood(items[i].kind)) dangerSpawned++
                }
            }
            dangerPeak = maxOf(dangerPeak, items.count { !BearCatchLogic.isGood(it.kind) })
            goodCaught += ev.caught.count { it.kind != ItemKind.BUBBLE }
            hits += ev.hit.count { BearCatchLogic.damageOf(it) > 0 }
            score += ev.caught.sumOf { BearCatchLogic.scoreOf(it.kind) }
            time += dt
        }
        return BotReport(
            dangerSpawned = dangerSpawned,
            dangerPeak = dangerPeak,
            goodCaught = goodCaught,
            hits = hits,
            finalScore = score,
        )
    }

    /** 简易策略：优先躲开即将命中且最近的危险物，否则朝最接近地面的好物移动；步长有限。 */
    private fun steer(
        items: List<BearCatchLogic.Item>,
        catchX: Float,
        catchY: Float,
        catchR: Float,
        width: Float,
        dt: Float,
    ): Float {
        val threat =
            items
                .filter { !BearCatchLogic.isGood(it.kind) }
                .filter { it.y > catchY - 260f && abs(it.x - catchX) < it.r + catchR + 16f }
                .minByOrNull { abs(it.x - catchX) }
        val target =
            if (threat != null) {
                if (threat.x >= catchX) catchX - 170f else catchX + 170f
            } else {
                items
                    .filter { BearCatchLogic.isGood(it.kind) }
                    .maxByOrNull { it.y }
                    ?.x ?: catchX
            }
        val step = 400f * dt
        return (catchX + (target - catchX).coerceIn(-step, step)).coerceIn(catchR, width - catchR)
    }
}
