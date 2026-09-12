package com.xiaoxiong.weather.ui.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePauseStateTest {
    @Test
    fun `initial state is not paused`() {
        val state = GamePauseState()
        assertFalse(state.paused)
        assertFalse(state.showMenu)
    }

    @Test
    fun `open sets paused and shows menu`() {
        val state = GamePauseState()
        state.open()
        assertTrue(state.paused)
        assertTrue(state.showMenu)
    }

    @Test
    fun `resume clears paused and menu`() {
        val state = GamePauseState().apply { open() }
        state.resume()
        assertFalse(state.paused)
        assertFalse(state.showMenu)
    }

    @Test
    fun `toggle flips between paused and resumed`() {
        val state = GamePauseState()
        state.toggle()
        assertTrue(state.paused)
        assertTrue(state.showMenu)
        state.toggle()
        assertFalse(state.paused)
        assertFalse(state.showMenu)
    }

    @Test
    fun `open is idempotent`() {
        val state = GamePauseState()
        state.open()
        state.open()
        assertTrue(state.paused)
        assertTrue(state.showMenu)
    }
}
