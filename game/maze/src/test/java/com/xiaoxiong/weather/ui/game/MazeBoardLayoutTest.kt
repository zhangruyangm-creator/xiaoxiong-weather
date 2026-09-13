package com.xiaoxiong.weather.ui.game

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 棋盘布局的 HUD 预留、低高度窗口裁剪与位图尺寸量化：
 *  - 1080×1920@2.75x 下棋盘必须完全落在顶栏与 D-pad/导航栏之间；
 *  - 横屏/分屏/inset=0 等低高度窗口下预留按比例裁剪，cell 必须仍 > 0，
 *    棋盘 bounds 不得越过裁剪后的预留区。
 */
class MazeBoardLayoutTest {
    @Test
    fun layout_keepsBoardBetweenHudReserves_on1080x1920() {
        val density = 2.75f
        val statusBars = 24f * density
        val navBars = 48f * density
        val topReserve = statusBars + MAZE_HUD_TOP_DP * density
        val bottomReserve = navBars + MAZE_HUD_BOTTOM_DP * density
        val size = Size(1080f, 1920f)

        // 空间充足时不做裁剪，预留原样生效
        val reserves = mazeEffectiveReserves(size, topReserve, bottomReserve)
        assertEquals(topReserve, reserves.top, 1e-3f)
        assertEquals(bottomReserve, reserves.bottom, 1e-3f)

        val layout =
            mazeBoardLayout(
                rows = 12,
                cols = 12,
                size = size,
                topReservePx = topReserve,
                bottomReservePx = bottomReserve,
            )

        assertPlayable(size, topReserve, bottomReserve)
        assertTrue("棋盘应放得下", layout.cell > 0f)
        assertTrue("棋盘顶部不能压住 HUD", layout.oy >= topReserve)
        assertTrue(
            "棋盘底部不能压住 D-pad",
            layout.oy + layout.cell * 12 <= size.height - bottomReserve,
        )
        assertTrue(
            "棋盘宽度不能超出画布",
            layout.ox >= 0f && layout.ox + layout.cell * 12 <= size.width,
        )
    }

    @Test
    fun layout_landscape_capsReservesAndKeepsBoardPlayable() {
        val density = 2.75f
        val size = Size(1920f, 1080f)
        val topReserve = MAZE_HUD_TOP_DP * density
        val bottomReserve = 48f * density + MAZE_HUD_BOTTOM_DP * density

        val reserves = mazeEffectiveReserves(size, topReserve, bottomReserve)
        assertTrue("横屏必须裁剪顶栏预留", reserves.top < topReserve)
        assertTrue("横屏必须裁剪底部预留", reserves.bottom < bottomReserve)
        assertPlayable(size, topReserve, bottomReserve)
    }

    @Test
    fun layout_landscapeZeroInsets_capsHudReserves() {
        val density = 2.75f
        val size = Size(1920f, 900f)
        val topReserve = MAZE_HUD_TOP_DP * density
        val bottomReserve = MAZE_HUD_BOTTOM_DP * density

        val reserves = mazeEffectiveReserves(size, topReserve, bottomReserve)
        assertTrue("inset=0 时仅 HUD 预留也能吃满低高度窗口", reserves.top < topReserve)
        assertTrue("裁剪后顶部预留不能为零", reserves.top > 0f)
        assertTrue("裁剪后底部预留不能为零", reserves.bottom > 0f)
        assertPlayable(size, topReserve, bottomReserve)
    }

    @Test
    fun layout_shortWindowWithZeroInsets_keepsCellPositive() {
        val density = 2.75f
        val size = Size(900f, 420f)
        val topReserve = MAZE_HUD_TOP_DP * density
        val bottomReserve = MAZE_HUD_BOTTOM_DP * density

        val layout = mazeBoardLayout(12, 12, size, topReserve, bottomReserve)
        assertTrue("低高度窗口 cell 必须 > 0", layout.cell > 0f)
        assertPlayable(size, topReserve, bottomReserve)
    }

    @Test
    fun layout_extremeSmallWindow_stillDrawsPlayableBoard() {
        val size = Size(240f, 120f)
        val topReserve = MAZE_HUD_TOP_DP
        val bottomReserve = MAZE_HUD_BOTTOM_DP

        val layout = mazeBoardLayout(12, 12, size, topReserve, bottomReserve)
        assertTrue("极端小窗也要给出 > 0 的 cell", layout.cell > 0f)
        assertPlayable(size, topReserve, bottomReserve)
    }

    /** 断言：cell > 0，且棋盘 bounds 不越过裁剪后的顶/底预留。 */
    private fun assertPlayable(
        size: Size,
        topReservePx: Float,
        bottomReservePx: Float,
        rows: Int = 12,
        cols: Int = 12,
    ) {
        val reserves = mazeEffectiveReserves(size, topReservePx, bottomReservePx)
        val layout = mazeBoardLayout(rows, cols, size, topReservePx, bottomReservePx)
        assertTrue("cell 必须 > 0（${size.width}x${size.height}）", layout.cell > 0f)
        assertTrue(
            "裁剪后必须留有可用高度",
            size.height - reserves.top - reserves.bottom > 0f,
        )
        val boardW = layout.cell * cols
        val boardH = layout.cell * rows
        assertTrue("棋盘顶部不能越过预留", layout.oy >= reserves.top - 1e-3f)
        assertTrue(
            "棋盘底部不能越过预留",
            layout.oy + boardH <= size.height - reserves.bottom + 1e-3f,
        )
        assertTrue(
            "棋盘必须在画布内",
            layout.ox >= 0f && layout.ox + boardW <= size.width + 1e-3f,
        )
    }

    @Test
    fun quantizeBitmapPx_roundsUpTo4pxGrid() {
        assertEquals(0, quantizeBitmapPx(0f))
        assertEquals(4, quantizeBitmapPx(0.1f))
        assertEquals(4, quantizeBitmapPx(4f))
        assertEquals(8, quantizeBitmapPx(4.1f))
        assertEquals(1084, quantizeBitmapPx(1080.4f))
    }
}
