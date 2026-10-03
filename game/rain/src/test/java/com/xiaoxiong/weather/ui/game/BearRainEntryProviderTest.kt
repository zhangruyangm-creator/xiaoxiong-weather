package com.xiaoxiong.weather.ui.game

import com.xiaoxiong.weather.game.GameEntryProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ServiceLoader

class BearRainEntryProviderTest {
    @Test
    fun provider_isLoadableByServiceLoader_withPublicNoArgConstructor() {
        val providers = ServiceLoader.load(GameEntryProvider::class.java).toList()
        val provider = providers.single { it.javaClass.name == "com.xiaoxiong.weather.ui.game.BearRainEntryProvider" }

        assertNotNull(provider.javaClass.getConstructor())
        assertTrue(provider.javaClass.getConstructor().modifiers and java.lang.reflect.Modifier.PUBLIC != 0)

        assertSame(BearRainEntry, provider.entry)
    }
}
