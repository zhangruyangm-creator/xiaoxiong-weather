package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameAudioPrefsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioTest {
    @Test
    fun `coerceVolume clamps negative to min`() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefsContract.coerceVolume(-1f), 0f)
    }

    @Test
    fun `coerceVolume clamps values above max`() {
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefsContract.coerceVolume(0.61f), 0f)
        assertEquals(GameAudioPrefsContract.MAX_VOLUME, GameAudioPrefsContract.coerceVolume(1f), 0f)
    }

    @Test
    fun `coerceVolume keeps in-range value untouched`() {
        assertEquals(0.3f, GameAudioPrefsContract.coerceVolume(0.3f), 0f)
        assertEquals(
            GameAudioPrefsContract.MAX_VOLUME,
            GameAudioPrefsContract.coerceVolume(GameAudioPrefsContract.MAX_VOLUME),
            0f,
        )
    }

    @Test
    fun `coerceVolume maps NaN to min`() {
        assertEquals(GameAudioPrefsContract.MIN_VOLUME, GameAudioPrefsContract.coerceVolume(Float.NaN), 0f)
    }

    @Test
    fun `default master volume is quiet and inside the effective range`() {
        assertEquals(0.3f, GameAudioPrefsContract.DEFAULT_VOLUME, 0f)
        assertTrue(GameAudioPrefsContract.DEFAULT_VOLUME >= GameAudioPrefsContract.MIN_VOLUME)
        assertTrue(GameAudioPrefsContract.DEFAULT_VOLUME <= GameAudioPrefsContract.MAX_VOLUME)
        assertTrue(GameAudioPrefsContract.MAX_VOLUME <= 0.6f)
    }

    @Test
    fun `every cue has a valid raw resource`() {
        GameCue.entries.forEach { cue ->
            assertTrue(cue.name, cue.rawRes != 0)
        }
    }

    @Test
    fun `every cue gain is above zero and at most one`() {
        GameCue.entries.forEach { cue ->
            assertTrue(cue.name, cue.gain > 0f)
            assertTrue(cue.name, cue.gain <= 1f)
        }
    }

    @Test
    fun `cue raw resources are unique`() {
        val ids = GameCue.entries.map { it.rawRes }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `cue table exposes the full twenty-four-effect vocabulary`() {
        val expected =
            setOf(
                "UI_CLICK",
                "JUMP",
                "COIN",
                "STAR",
                "CATCH",
                "BLOCK",
                "HIT",
                "BUMP",
                "POT",
                "CUE",
                "RAIL",
                "KEY",
                "PORTAL",
                "GOAL",
                "FOUL",
                "WIN",
                "LOSE",
                "WHISTLE",
                "SHOT",
                "LASER",
                "EXPLOSION",
                "POWERUP",
                "BOMB",
                "WARNING",
            )
        assertEquals(expected, GameCue.entries.map { it.name }.toSet())
        assertEquals(24, GameCue.entries.size)
    }
}
