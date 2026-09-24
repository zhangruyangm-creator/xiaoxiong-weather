package com.xiaoxiong.weather.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * 卡片拖拽排序的换位计算。
 *
 * 关注三类容易出错的地方：越过列表首尾的钳制、跨多行时的残余位移回填
 * （残余错了会出现"松手跳回去"）、以及未测高/单行列表的除零与越界保护。
 */
class CardsSettingsDragTest {
    private fun assertOffset(
        expected: Float,
        actual: Float,
    ) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) < 0.01f)
    }

    private fun assertTrue(
        message: String,
        condition: Boolean,
    ) {
        org.junit.Assert.assertTrue(message, condition)
    }

    @Test
    fun belowHalfStride_staysInPlace() {
        val step = cardDragStep(from = 1, offsetY = 39f, stridePx = 80f, itemCount = 5)
        assertEquals(1, step.to)
        // 未换位时残余位移原样保留，拖过阈值后立即生效
        assertOffset(39f, step.remainingOffsetY)
    }

    @Test
    fun crossingStride_movesDownOneAndKeepsRemainder() {
        val step = cardDragStep(from = 1, offsetY = 100f, stridePx = 80f, itemCount = 5)
        assertEquals(2, step.to)
        // 消费掉 1 个整步（80px），剩余 20px 继续参与后续判定
        assertOffset(20f, step.remainingOffsetY)
    }

    @Test
    fun upwardDrag_movesUpAndConsumesStep() {
        // -110/80 = -1.375 → roundToInt = -1（远离零取整），上移 1 行
        val step = cardDragStep(from = 3, offsetY = -110f, stridePx = 80f, itemCount = 6)
        assertEquals(2, step.to)
        // 消费 1 个整步（-80px），残余 -30px
        assertOffset(-30f, step.remainingOffsetY)
    }

    @Test
    fun overshootTop_clampsToFirstIndexAndDropsRemainder() {
        val step = cardDragStep(from = 1, offsetY = -500f, stridePx = 80f, itemCount = 4)
        assertEquals(0, step.to)
        // 已到顶：多出的位移不能留成下次下拉的"预加载"，否则松手会回弹
        assertOffset(0f, step.remainingOffsetY)
    }

    @Test
    fun overshootBottom_clampsToLastIndexAndDropsRemainder() {
        val step = cardDragStep(from = 2, offsetY = 999f, stridePx = 80f, itemCount = 4)
        assertEquals(3, step.to)
        assertOffset(0f, step.remainingOffsetY)
    }

    @Test
    fun nonPositiveStride_isNoOp() {
        val step = cardDragStep(from = 1, offsetY = 200f, stridePx = 0f, itemCount = 5)
        assertEquals(1, step.to)
        assertOffset(200f, step.remainingOffsetY)
    }

    @Test
    fun singleItemList_isNoOp() {
        val step = cardDragStep(from = 0, offsetY = 200f, stridePx = 80f, itemCount = 1)
        assertEquals(0, step.to)
        assertOffset(200f, step.remainingOffsetY)
    }

    @Test
    fun outOfRangeFromIndex_isNoOp() {
        val step = cardDragStep(from = 9, offsetY = 200f, stridePx = 80f, itemCount = 5)
        assertEquals(9, step.to)
        assertOffset(200f, step.remainingOffsetY)
    }

    @Test
    fun multiStepDragInOneEvent_landsOnClampedTarget() {
        // 单帧位移很大（例如补帧后一次收到 3 步）；4 行列表从 0 拖 500px 直接撞到末位
        val step = cardDragStep(from = 0, offsetY = 500f, stridePx = 80f, itemCount = 4)
        assertEquals(3, step.to)
        // 已在末位，残余归零
        assertOffset(0f, step.remainingOffsetY)
    }

    @Test
    fun multiStepDragWithinBounds_keepsPartialRemainder() {
        // 6 行列表从 1 拖 260px：3 个整步（240px）+ 20px 余量
        val step = cardDragStep(from = 1, offsetY = 260f, stridePx = 80f, itemCount = 6)
        assertEquals(4, step.to)
        assertOffset(20f, step.remainingOffsetY)
    }
}
