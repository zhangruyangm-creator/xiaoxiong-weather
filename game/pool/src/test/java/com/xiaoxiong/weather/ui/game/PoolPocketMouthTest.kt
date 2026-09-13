package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 袋口口线几何（[pocketMouth] / [isPocketJaw]）的纯几何测试：
 * 鼻点位置、口线方向（袋心在保留侧、台心在裁掉侧）与钳口筛选。
 */
class PoolPocketMouthTest {
    private val table = PoolTable.standard()

    private fun assertNear(
        expected: Float,
        actual: Float,
    ) = assertEquals(expected, actual, 1e-5f)

    private fun assertOnMouthLine(
        mouth: PocketMouth,
        x: Float,
        y: Float,
    ) = assertNear(0f, mouth.outwardDistance(x, y))

    @Test
    fun cornerPockets_nosePointsSitOnBothCushionLinesAtMouthDistance() {
        val mouthDist = PoolConfig.CORNER_MOUTH
        val corners = table.pockets.filter { it.corner }
        assertEquals(4, corners.size)
        for (pocket in corners) {
            val sx = if (pocket.x <= table.centerX) 1f else -1f
            val sy = if (pocket.y <= table.centerY) 1f else -1f
            val mouth = pocketMouth(pocket, table)
            // 水平库鼻点：(x + sx*mouth, y)
            assertNear(pocket.x + sx * mouthDist, mouth.noseAX)
            assertNear(pocket.y, mouth.noseAY)
            // 竖直库鼻点：(x, y + sy*mouth)
            assertNear(pocket.x, mouth.noseBX)
            assertNear(pocket.y + sy * mouthDist, mouth.noseBY)
            // 两鼻点到袋心等距且都落在口线上
            assertNear(mouthDist, hypot(mouth.noseAX - pocket.x, mouth.noseAY - pocket.y))
            assertNear(mouthDist, hypot(mouth.noseBX - pocket.x, mouth.noseBY - pocket.y))
            assertOnMouthLine(mouth, mouth.noseAX, mouth.noseAY)
            assertOnMouthLine(mouth, mouth.noseBX, mouth.noseBY)
        }
    }

    @Test
    fun sidePockets_nosePointsStraddlePocketCenterOnCushionLine() {
        val half = PoolConfig.SIDE_MOUTH * 0.5f
        val sides = table.pockets.filter { !it.corner }
        assertEquals(2, sides.size)
        for (pocket in sides) {
            val mouth = pocketMouth(pocket, table)
            assertNear(pocket.x, mouth.noseAX)
            assertNear(pocket.y - half, mouth.noseAY)
            assertNear(pocket.x, mouth.noseBX)
            assertNear(pocket.y + half, mouth.noseBY)
            assertOnMouthLine(mouth, mouth.noseAX, mouth.noseAY)
            assertOnMouthLine(mouth, mouth.noseBX, mouth.noseBY)
        }
    }

    @Test
    fun mouthLine_liesBetweenPocketCenterAndTableCenter_forAllPockets() {
        for (pocket in table.pockets) {
            val mouth = pocketMouth(pocket, table)
            val pocketSide = mouth.outwardDistance(pocket.x, pocket.y)
            val tableSide = mouth.outwardDistance(table.centerX, table.centerY)
            // 袋心在保留侧（中袋口线即库线，袋心恰在口线上，允许等于 0）
            assertTrue("pocket=(${pocket.x},${pocket.y}) pocketSide=$pocketSide", pocketSide >= -1e-5f)
            // 台心在裁掉侧：口线位于袋心与台心之间
            assertTrue("pocket=(${pocket.x},${pocket.y}) tableSide=$tableSide", tableSide < -1e-4f)
            // 外法线为单位向量
            assertNear(1f, hypot(mouth.outwardX, mouth.outwardY))
        }
    }

    @Test
    fun outwardNormals_pointAwayFromTableCenter() {
        for (pocket in table.pockets) {
            val mouth = pocketMouth(pocket, table)
            val step = 0.01f
            val before = hypot(pocket.x - table.centerX, pocket.y - table.centerY)
            val after =
                hypot(
                    pocket.x + mouth.outwardX * step - table.centerX,
                    pocket.y + mouth.outwardY * step - table.centerY,
                )
            assertTrue("pocket=(${pocket.x},${pocket.y}) should move away", after > before)
        }
    }

    @Test
    fun jaws_areShortSegmentsNearPocketMouths() {
        val jaws = table.rails.filter { isPocketJaw(it, table) }
        assertEquals(12, jaws.size)
        for (jaw in jaws) {
            val len = hypot(jaw.x2 - jaw.x1, jaw.y2 - jaw.y1)
            assertTrue("jaw too long: $len", len < table.ballRadius)
            val touches =
                table.pockets.any { pocket ->
                    hypot(jaw.x1 - pocket.x, jaw.y1 - pocket.y) <= pocket.radius * 1.5f ||
                        hypot(jaw.x2 - pocket.x, jaw.y2 - pocket.y) <= pocket.radius * 1.5f
                }
            assertTrue("jaw not near any pocket", touches)
        }
    }

    @Test
    fun mainCushionRails_areNotTreatedAsJaws() {
        val mains = table.rails.filterNot { isPocketJaw(it, table) }
        assertEquals(6, mains.size)
        for (rail in mains) {
            val len = hypot(rail.x2 - rail.x1, rail.y2 - rail.y1)
            assertTrue("main cushion rail should be long: $len", len > table.ballRadius * 10f)
        }
    }

    @Test
    fun cornerMouthChord_isDiagonal_withEqualAxisComponents() {
        for (pocket in table.pockets.filter { it.corner }) {
            val mouth = pocketMouth(pocket, table)
            val dx = abs(mouth.noseBX - mouth.noseAX)
            val dy = abs(mouth.noseBY - mouth.noseAY)
            assertNear(dx, dy)
        }
    }
}
