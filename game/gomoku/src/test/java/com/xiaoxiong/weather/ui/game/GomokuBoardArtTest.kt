package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GomokuBoardArtTest {
    @Test
    fun hitIndex_mapsIntersectionAndIgnoresOutside() {
        val layout = gomokuBoardLayout(1400f, 1400f)
        val center = gomokuIndex(7, 7)
        val cx = layout.left + 7 * layout.cell
        val cy = layout.top + 7 * layout.cell
        assertEquals(center, gomokuHitIndex(layout, cx, cy))
        assertEquals(center, gomokuHitIndex(layout, cx + layout.cell * 0.4f, cy))
        assertNull(gomokuHitIndex(layout, layout.left - layout.cell, cy))
        assertNull(gomokuHitIndex(layout, cx, layout.top + layout.side + layout.cell))
        assertEquals(gomokuIndex(0, 0), gomokuHitIndex(layout, layout.left, layout.top))
    }
}
