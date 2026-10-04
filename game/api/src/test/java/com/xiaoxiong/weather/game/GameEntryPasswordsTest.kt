package com.xiaoxiong.weather.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEntryPasswordsTest {
    private val whiteTiles =
        object : GameEntry {
            override val password: String = EASTER_EGG_BEAR_WHITE
            override val titleRes: Int = 0

            @androidx.compose.runtime.Composable
            override fun Content(
                sceneCode: Int?,
                isDay: Boolean?,
                cityName: String?,
                onExit: () -> Unit,
                onOpenGame: ((String) -> Unit)?,
                onOpenBearAlbum: (() -> Unit)?,
                bearHomeResumeRoom: String?,
                onBearHomeResumeRoomConsumed: (() -> Unit)?,
                otherGames: List<GameEntry>,
            ) {
            }
        }

    @Test
    fun whiteTilesEntry_matchesPrimaryAndLegacyPasswords() {
        assertTrue(whiteTiles.matchesGamePassword(EASTER_EGG_BEAR_WHITE))
        assertTrue(whiteTiles.matchesGamePassword(EASTER_EGG_BEAR_TILES))
        assertTrue(whiteTiles.matchesGamePassword(EASTER_EGG_BEAR_RAIN_LEGACY))
        assertFalse(whiteTiles.matchesGamePassword("#小熊跳跳"))
    }
}
