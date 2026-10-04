package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntryProvider
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.ServiceLoader

class BearReversiEntryProviderTest {
    @Test
    fun provider_loadsFromServiceLoader() {
        val provider =
            ServiceLoader
                .load(GameEntryProvider::class.java)
                .toList()
                .single { it.javaClass.name.endsWith("BearReversiEntryProvider") }
        assertSame(BearReversiEntry, provider.entry)
    }
}
