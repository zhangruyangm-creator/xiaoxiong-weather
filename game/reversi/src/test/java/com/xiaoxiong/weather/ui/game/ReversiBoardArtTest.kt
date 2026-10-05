package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReversiBoardArtTest {
    @Test
    fun hitIndex_mapsCellAndIgnoresFrame() {
        val layout = reversiBoardLayout(800f, 800f)
        val inside = reversiHitIndex(layout, layout.left + layout.cell * 0.5f, layout.top + layout.cell * 0.5f)
        assertEquals(reversiIndex(0, 0), inside)
        assertNull(reversiHitIndex(layout, layout.left - 4f, layout.top + layout.cell * 0.5f))
        assertNull(reversiHitIndex(layout, layout.left + layout.side + 4f, layout.top + 10f))
        val last =
            reversiHitIndex(
                layout,
                layout.left + layout.side - 1f,
                layout.top + layout.side - 1f,
            )
        assertEquals(reversiIndex(REVERSI_SIZE - 1, REVERSI_SIZE - 1), last)
    }
}
